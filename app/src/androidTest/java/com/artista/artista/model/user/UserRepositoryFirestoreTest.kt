// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: DeepSeek Flash <noreply@deepseek.com>
package com.artista.artista.model.user

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.model.error.AppException
import com.artista.artista.utils.FirebaseEmulatedTest
import com.artista.artista.utils.FirebaseEmulator
import com.artista.artista.utils.TEST_OTHER_PICTURE_URL
import com.artista.artista.utils.TEST_PICTURE_URL
import kotlin.reflect.KClass
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the observable behavior of the Firestore-backed user repository.
 *
 * The tests use the local Firestore emulator so they exercise serialization, persistence, and error
 * handling without depending on the production database.
 *
 * Network and permission failures are not tested here because the emulator cannot reliably simulate
 * them; their translation into `AppException` is covered by `FirestoreExceptionsTest`.
 *
 * @author krfpn
 */
@RunWith(AndroidJUnit4::class)
class UserRepositoryFirestoreTest : FirebaseEmulatedTest() {

  /** Checks that a created user can be read back with all preference fields intact. */
  @Test
  fun createUserThenGetUserReturnsTheSameUser() = runTest {
    val user =
        userWith(
            uid = testUserId,
            artists = listOf("Kandinsky", "O'Keeffe"),
            types = listOf("Painting"),
            timePeriods = listOf("Modern"),
        )

    repository.createUser(user)

    assertEquals(user, repository.getUser(testUserId))
  }

  /** Checks that empty preference lists survive a Firestore round trip. */
  @Test
  fun createUserWithEmptyPreferencesThenGetUserPreservesEmptyLists() = runTest {
    val user =
        userWith(
            uid = testUserId,
            artists = emptyList(),
            types = emptyList(),
            timePeriods = emptyList(),
        )

    repository.createUser(user)

    assertEquals(user, repository.getUser(testUserId))
  }

  /** Checks that creating a user with an existing identifier throws an exception. */
  @Test
  fun createUserThrowsWhenUserIdAlreadyExists() = runTest {
    repository.createUser(userWith(uid = testUserId))

    assertRepositoryThrows(AppException.AlreadyExists::class) {
      repository.createUser(userWith(uid = testUserId))
    }
  }

  /** Checks that updating an existing user makes the replacement value observable. */
  @Test
  fun updateUserThenGetUserReturnsTheUpdatedUser() = runTest {
    repository.createUser(userWith(uid = testUserId, artists = listOf("Monet")))
    val updatedUser =
        userWith(
            uid = testUserId,
            artists = listOf("Monet", "Renoir"),
            types = listOf("Sculpture"),
            timePeriods = listOf("Impressionism"),
        )

    repository.updateUser(testUserId, updatedUser)

    assertEquals(updatedUser, repository.getUser(testUserId))
  }

  /** Checks that deleting an existing user removes it from the repository. */
  @Test
  fun deleteUserThenGetUserThrowsForTheDeletedUser() = runTest {
    repository.createUser(userWith(uid = testUserId))

    repository.deleteUser(testUserId)

    assertRepositoryThrows(AppException.NotFound::class) { repository.getUser(testUserId) }
  }

  /** Checks that reading an unknown identifier reports a missing user. */
  @Test
  fun getUserThrowsWhenUserDoesNotExist() = runTest {
    assertRepositoryThrows(AppException.NotFound::class) { repository.getUser(testUserId) }
  }

  /** Checks that updating an unknown identifier reports a missing user. */
  @Test
  fun updateUserThrowsWhenUserDoesNotExist() = runTest {
    assertRepositoryThrows(AppException.NotFound::class) {
      repository.updateUser(testUserId, userWith(uid = testUserId))
    }
  }

  /** Checks that updating a user with a different identifier throws an exception. */
  @Test
  fun updateUserThrowsWhenUserIdDoesNotMatchReplacementUserId() = runTest {
    val replacementUser = userWith(uid = "$testUserId-replacement")

    assertRepositoryThrows(AppException.InvalidInput::class) {
      repository.updateUser(testUserId, replacementUser)
    }
  }

  /** Checks that deleting an unknown identifier reports a missing user. */
  @Test
  fun deleteUserThrowsWhenUserDoesNotExist() = runTest {
    assertRepositoryThrows(AppException.NotFound::class) { repository.deleteUser(testUserId) }
  }

  /** Checks that reading a document without a UID throws an exception. */
  @Test
  fun getUserThrowsWhenStoredUserIsMissingUid() = runTest {
    // The rules reject a profile without its uid, so it is written as an admin.
    FirebaseEmulator.createDocumentBypassingRules(
        USER_COLLECTION_PATH,
        testUserId,
        """{"userName":{"stringValue":"Malformed User"},""" +
            """"preference":{"mapValue":{"fields":{"artists":{"arrayValue":""" +
            """{"values":[{"stringValue":"Van Gogh"}]}}}}}}""",
    )

    assertRepositoryThrows(AppException.InvalidData::class) { repository.getUser(testUserId) }
  }

  /**
   * Checks that a document without preferences loads with empty ones, as happens when a rename is
   * the first write of a profile or when only Firestore's local copy of an offline rename is known.
   */
  @Test
  fun getUserReturnsEmptyPreferencesWhenStoredUserIsMissingPreferences() = runTest {
    seedRawUserDocument(mapOf("uid" to testUserId, "userName" to "Partial User"))

    assertEquals(
        User(uid = testUserId, userName = "Partial User", preference = UserPreference()),
        repository.getUser(testUserId),
    )
  }

  /** Checks that a preference field that is present but not a map still throws an exception. */
  @Test
  fun getUserThrowsWhenPreferencesAreNotAMap() = runTest {
    seedRawUserDocument(mapOf("uid" to testUserId, "preference" to "not-a-map"))

    assertRepositoryThrows(AppException.InvalidData::class) { repository.getUser(testUserId) }
  }

  /** Checks that renaming a user replaces only their username and keeps their preferences. */
  @Test
  fun updateUsernameKeepsStoredPreferences() = runTest {
    val user = userWith(uid = testUserId, artists = listOf("Monet", "Renoir"))
    repository.createUser(user)

    repository.updateUsername(testUserId, "Renamed User")

    assertEquals(user.copy(userName = "Renamed User"), repository.getUser(testUserId))
  }

  /**
   * Checks that renaming a user with no profile creates one that loads with empty preferences. This
   * happens when creating the profile at sign-in failed, for example because the connection dropped
   * right after the Google sign-in: the user stays signed in without a profile, and the merge write
   * of the rename then creates it.
   */
  @Test
  fun updateUsernameCreatesMissingUserWithEmptyPreferences() = runTest {
    repository.updateUsername(testUserId, "New User")

    assertEquals(
        User(uid = testUserId, userName = "New User", preference = UserPreference()),
        repository.getUser(testUserId),
    )
  }

  /** Checks that a preference field with an invalid value type throws an exception. */
  @Test
  fun getUserThrowsWhenPreferenceFieldHasInvalidType() = runTest {
    seedRawUserDocument(
        mapOf(
            "uid" to testUserId,
            "preference" to validPreferenceData().apply { this["artists"] = "not-a-list" },
        )
    )

    assertRepositoryThrows(AppException.InvalidData::class) { repository.getUser(testUserId) }
  }

  /** Checks that a preference list containing a non-string value throws an exception. */
  @Test
  fun getUserThrowsWhenPreferenceListContainsNonStringValue() = runTest {
    seedRawUserDocument(
        mapOf(
            "uid" to testUserId,
            "preference" to
                validPreferenceData().apply { this["artists"] = listOf("Van Gogh", 42) },
        )
    )

    assertRepositoryThrows(AppException.InvalidData::class) { repository.getUser(testUserId) }
  }

  /** Checks that the picture URL of a created profile reaches Firestore. */
  @Test
  fun createUser_storesThePictureUrl() = runTest {
    repository.createUser(userWith(uid = testUserId, photoUrl = TEST_PICTURE_URL))

    val stored =
        FirebaseEmulator.firestore
            .collection(USER_COLLECTION_PATH)
            .document(testUserId)
            .get()
            .await()

    assertEquals(TEST_PICTURE_URL, stored.getString("photoUrl"))
  }

  /** Checks that the stored picture URL is read back through the repository. */
  @Test
  fun getUser_returnsTheStoredPictureUrl() = runTest {
    repository.createUser(userWith(uid = testUserId, photoUrl = TEST_PICTURE_URL))

    assertEquals(TEST_PICTURE_URL, repository.getUser(testUserId).photoUrl)
  }

  /**
   * Checks that a document written before the picture existed still loads, as a user without a
   * picture: no migration is needed.
   */
  @Test
  fun getUser_withoutPictureField_returnsNoPicture() = runTest {
    seedRawUserDocument(mapOf("uid" to testUserId, "preference" to validPreferenceData()))

    assertNull(repository.getUser(testUserId).photoUrl)
  }

  /** Checks that a blank picture URL is read as no picture. */
  @Test
  fun getUser_withBlankPictureField_returnsNoPicture() = runTest {
    seedRawUserDocument(
        mapOf(
            "uid" to testUserId,
            "preference" to validPreferenceData(),
            "photoUrl" to "   ",
        )
    )

    assertNull(repository.getUser(testUserId).photoUrl)
  }

  /**
   * Checks that a picture URL that is not a string is read as no picture, and that the rest of the
   * profile still loads.
   *
   * The picture is cosmetic while the name and the preferences are not, and the app could not
   * repair an unreadable value either, since the refresh that would overwrite it reads the profile
   * first: failing the lookup would hide those fields for good.
   */
  @Test
  fun getUser_withNonStringPictureField_returnsNoPicture() = runTest {
    seedRawUserDocument(
        mapOf(
            "uid" to testUserId,
            "userName" to "Test User",
            "preference" to validPreferenceData(),
            "photoUrl" to 42,
        )
    )

    val stored = repository.getUser(testUserId)

    assertNull(stored.photoUrl)
    assertEquals("Test User", stored.userName)
    assertEquals(listOf("Van Gogh"), stored.preference.artists)
  }

  /** Checks that an update replaces the stored picture URL. */
  @Test
  fun updateUser_replacesThePictureUrl() = runTest {
    repository.createUser(userWith(uid = testUserId, photoUrl = TEST_PICTURE_URL))

    repository.updateUser(testUserId, userWith(uid = testUserId, photoUrl = TEST_OTHER_PICTURE_URL))

    assertEquals(TEST_OTHER_PICTURE_URL, repository.getUser(testUserId).photoUrl)
  }

  /**
   * Checks that a save which does not touch the picture keeps the stored one, which is what every
   * other screen of the app relies on when it reads a profile and writes it back.
   */
  @Test
  fun updateUser_withoutTouchingIt_keepsThePictureUrl() = runTest {
    repository.createUser(userWith(uid = testUserId, photoUrl = TEST_PICTURE_URL))
    val stored = repository.getUser(testUserId)

    repository.updateUser(testUserId, stored.copy(userName = "Renamed User"))

    assertEquals(TEST_PICTURE_URL, repository.getUser(testUserId).photoUrl)
  }

  /** Checks that a picture update stores the URL in the field the lookup reads. */
  @Test
  fun updatePhotoUrl_storesThePictureUrl() = runTest {
    repository.createUser(userWith(uid = testUserId))

    repository.updatePhotoUrl(testUserId, TEST_PICTURE_URL)

    assertEquals(TEST_PICTURE_URL, repository.getUser(testUserId).photoUrl)
  }

  /**
   * Checks that a picture update leaves every other field of the profile as it was, which is what
   * lets it run while another screen is saving the same profile.
   */
  @Test
  fun updatePhotoUrl_keepsTheOtherFields() = runTest {
    repository.createUser(userWith(uid = testUserId))

    repository.updatePhotoUrl(testUserId, TEST_PICTURE_URL)

    assertEquals(
        userWith(uid = testUserId, photoUrl = TEST_PICTURE_URL),
        repository.getUser(testUserId),
    )
  }

  /** Checks that updating the picture of a profile that does not exist reports a missing user. */
  @Test
  fun updatePhotoUrl_withoutProfile_throwsNotFound() = runTest {
    assertRepositoryThrows(AppException.NotFound::class) {
      repository.updatePhotoUrl(testUserId, TEST_PICTURE_URL)
    }
  }

  /**
   * Asserts that a suspending repository operation throws the expected exception type.
   *
   * @param expectedException the exact exception type expected from the operation.
   * @param action the repository operation expected to fail.
   */
  private suspend fun assertRepositoryThrows(
      expectedException: KClass<out Throwable>,
      action: suspend () -> Unit,
  ) {
    val actualException =
        try {
          action()
          null
        } catch (exception: Throwable) {
          exception
        }

    assertNotNull("Expected the repository operation to throw an exception.", actualException)
    assertEquals(expectedException, actualException!!::class)
  }

  /**
   * Helper function to create a User.
   *
   * @param uid the unique identifier assigned to the user.
   * @param artists the preferred artists; defaults to a one-item list, and an empty list means no
   *   artists are selected.
   * @param types the preferred artwork types; defaults to a one-item list, and an empty list means
   *   no types are selected.
   * @param timePeriods the preferred time periods; defaults to a one-item list, and an empty list
   *   means no time periods are selected.
   * @param photoUrl the URL of the profile picture of the user, if any.
   * @return a user containing the supplied values and a test display name.
   */
  private fun userWith(
      uid: String,
      artists: List<String> = listOf("Van Gogh"),
      types: List<String> = listOf("Drawing"),
      timePeriods: List<String> = listOf("19th century"),
      photoUrl: String? = null,
  ): User =
      User(
          uid = uid,
          userName = "Test User",
          preference =
              UserPreference(
                  artists = artists,
                  type = types,
                  timePeriod = timePeriods,
              ),
          photoUrl = photoUrl,
      )

  /**
   * Helper function that seeds a raw Firestore document for malformed-data tests.
   *
   * It bypasses the repository so tests can provide malformed-data that a valid [User] cannot have.
   *
   * @param data the fields to store in the test document.
   */
  private suspend fun seedRawUserDocument(data: Map<String, Any>) {
    FirebaseEmulator.firestore
        .collection(USER_COLLECTION_PATH)
        .document(testUserId)
        .set(data)
        .await()
  }

  /**
   * Helper function that creates valid raw preference data.
   *
   * Tests copy and modify the returned map to test specific malformed preference throw or behave as
   * expected.
   *
   * @return a mutable preference map that individual tests can corrupt.
   */
  private fun validPreferenceData(): MutableMap<String, Any> =
      mutableMapOf(
          "artists" to listOf("Van Gogh"),
          "type" to listOf("Drawing"),
          "timePeriod" to listOf("19th century"),
      )
}
