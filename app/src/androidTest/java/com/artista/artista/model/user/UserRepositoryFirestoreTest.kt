package com.artista.artista.model.user

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.utils.FirebaseEmulatedTest
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
            artists = null,
            types = null,
            timePeriods = null,
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

  /** Checks that deleting an unknown identifier reports a missing user. */
  @Test
  fun deleteUserThrowsWhenUserDoesNotExist() = runTest {
    assertRepositoryThrows { repository.deleteUser(testUserId) }
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
      artists: List<String>? = listOf("Van Gogh"),
      types: List<String>? = listOf("Drawing"),
      timePeriods: List<String>? = listOf("19th century"),
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
}
