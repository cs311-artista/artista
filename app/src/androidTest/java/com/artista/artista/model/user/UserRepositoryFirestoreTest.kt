package com.artista.artista.model.user

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.utils.FirebaseEmulatedTest
import com.artista.artista.utils.FirebaseEmulator
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the observable behavior of the Firestore-backed user repository.
 *
 * The tests use the local Firestore emulator so they exercise serialization, persistence, and error
 * handling without depending on the production database.
 *
 * @author Kaio R. Freitas Pereira Nascimento (krfpn)
 * @author Copilot (223556219+Copilot@users.noreply.github.com)
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

  /** Checks that nullable preference lists survive a Firestore round trip. */
  @Test
  fun createUserWithEmptyPreferencesThenGetUserPreservesNulls() = runTest {
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

    assertRepositoryThrows { repository.getUser(testUserId) }
  }

  /** Checks that reading an unknown identifier reports a missing user. */
  @Test
  fun getUserThrowsWhenUserDoesNotExist() = runTest {
    assertRepositoryThrows { repository.getUser(testUserId) }
  }

  /** Checks that updating an unknown identifier reports a missing user. */
  @Test
  fun updateUserThrowsWhenUserDoesNotExist() = runTest {
    assertRepositoryThrows { repository.updateUser(testUserId, userWith(uid = testUserId)) }
  }

  /** Checks that updating a user with a different identifier throws an exception. */
  @Test
  fun updateUserThrowsWhenUserIdDoesNotMatchReplacementUserId() = runTest {
    val replacementUser = userWith(uid = "$testUserId-replacement")

    assertRepositoryThrows { repository.updateUser(testUserId, replacementUser) }
  }

  /** Checks that deleting an unknown identifier reports a missing user. */
  @Test
  fun deleteUserThrowsWhenUserDoesNotExist() = runTest {
    assertRepositoryThrows { repository.deleteUser(testUserId) }
  }

  /** Checks that reading a document without a UID throws an exception. */
  @Test
  fun getUserThrowsWhenStoredUserIsMissingUid() = runTest {
    seedRawUserDocument(
        mapOf(
            "userName" to "Malformed User",
            "preference" to validPreferenceData(),
        )
    )

    assertRepositoryThrows { repository.getUser(testUserId) }
  }

  /** Checks that reading a document without preferences throws an exception. */
  @Test
  fun getUserThrowsWhenStoredUserIsMissingPreferences() = runTest {
    seedRawUserDocument(mapOf("uid" to testUserId, "userName" to "Malformed User"))

    assertRepositoryThrows { repository.getUser(testUserId) }
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

    assertRepositoryThrows { repository.getUser(testUserId) }
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

    assertRepositoryThrows { repository.getUser(testUserId) }
  }

  /**
   * Asserts that a suspending repository operation fails with an exception.
   *
   * @param action the repository operation expected to fail.
   */
  private suspend fun assertRepositoryThrows(action: suspend () -> Unit) {
    var threwException = false
    try {
      action()
    } catch (_: Exception) {
      threwException = true
    }
    assertTrue("Expected the repository operation to throw an exception.", threwException)
  }

  /**
   * Helper function to create a User.
   *
   * @param uid the unique identifier assigned to the user.
   * @param artists the preferred artists, or null when no artists are selected.
   * @param types the preferred artwork types, or null when no types are selected.
   * @param timePeriods the preferred time periods, or null when none are selected.
   * @return a user containing the supplied values and a test display name.
   */
  private fun userWith(
      uid: String,
      artists: List<String> = listOf("Van Gogh"),
      types: List<String> = listOf("Drawing"),
      timePeriods: List<String> = listOf("19th century"),
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
