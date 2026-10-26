// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.model.user

import com.artista.artista.model.error.AppException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests for [UserRepositoryLocal].
 *
 * @author 5kyPhy
 */
class UserRepositoryLocalTest {

  @Test
  fun createUserThenGetUserReturnsTheSameUser() = runTest {
    val repository = UserRepositoryLocal()
    val user = userWith("user-1")

    repository.createUser(user)

    assertEquals(user, repository.getUser("user-1"))
  }

  @Test
  fun createUserThrowsWhenUserIdAlreadyExists() = runTest {
    val repository = UserRepositoryLocal()
    repository.createUser(userWith("user-1"))

    assertRepositoryThrows<AppException.AlreadyExists> {
      repository.createUser(userWith("user-1", userName = "Different User"))
    }
  }

  @Test
  fun getUserThrowsWhenUserDoesNotExist() = runTest {
    val repository = UserRepositoryLocal()

    assertRepositoryThrows<AppException.NotFound> { repository.getUser("unknown") }
  }

  @Test
  fun updateUserThenGetUserReturnsTheUpdatedUser() = runTest {
    val repository = UserRepositoryLocal()
    repository.createUser(userWith("user-1", userName = "Original User"))
    val updatedUser = userWith("user-1", userName = "Updated User")

    repository.updateUser("user-1", updatedUser)

    assertEquals(updatedUser, repository.getUser("user-1"))
  }

  @Test
  fun updateUserThrowsWhenUserDoesNotExist() = runTest {
    val repository = UserRepositoryLocal()

    assertRepositoryThrows<AppException.NotFound> {
      repository.updateUser("unknown", userWith("unknown"))
    }
  }

  @Test
  fun deleteUserThenGetUserThrowsForTheDeletedUser() = runTest {
    val repository = UserRepositoryLocal()
    repository.createUser(userWith("user-1"))

    repository.deleteUser("user-1")

    assertRepositoryThrows<AppException.NotFound> { repository.getUser("user-1") }
  }

  @Test
  fun deleteUserThrowsWhenUserDoesNotExist() = runTest {
    val repository = UserRepositoryLocal()

    assertRepositoryThrows<AppException.NotFound> { repository.deleteUser("unknown") }
  }

  /**
   * Asserts that a suspending repository operation throws the expected exception type.
   *
   * @param action the repository operation expected to fail.
   */
  private suspend inline fun <reified T : Throwable> assertRepositoryThrows(
      crossinline action: suspend () -> Unit
  ) {
    val actualException =
        try {
          action()
          null
        } catch (exception: Throwable) {
          exception
        }

    assertNotNull("Expected the repository operation to throw an exception.", actualException)
    assertEquals(T::class, actualException!!::class)
  }

  /**
   * Creates a user for repository tests.
   *
   * @param uid the unique identifier assigned to the user.
   * @param userName the display name assigned to the user.
   * @return a user with deterministic preferences.
   */
  private fun userWith(uid: UserId, userName: String = "Test User"): User =
      User(
          uid = uid,
          userName = userName,
          preference =
              UserPreference(
                  artists = listOf("Van Gogh"),
                  type = listOf("Painting"),
                  timePeriod = listOf("19th century"),
              ),
      )
}
