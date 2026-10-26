// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.model.user

import com.artista.artista.model.error.AppException

/**
 * Represents a repository that manages User related data such as username or preferences.
 *
 * Failures are reported as [AppException]. Besides the cases documented on each function, any
 * operation can fail with another [AppException] type when the backend cannot be reached or refuses
 * the request.
 *
 * @author Kaio R. Freitas Pereira Nascimento (krfpn)
 * @author Copilot (223556219+Copilot@users.noreply.github.com)
 */
interface UserRepository {

  /**
   * Creates a new User in the repository.
   *
   * @param user The User object to create.
   * @throws AppException.AlreadyExists if the user-id is already taken.
   */
  suspend fun createUser(user: User)

  /**
   * Retrieves a User information by its unique identifier.
   *
   * @param userId The unique identifier of the user to retrieve.
   * @return The User object with this specific id.
   * @throws AppException.NotFound if the user is not found.
   */
  suspend fun getUser(userId: UserId): User

  /**
   * Updates an existing User in the repository.
   *
   * @param userId The unique identifier of the user to edit.
   * @param newValue The new value for the User object.
   * @throws AppException.NotFound if the user is not found.
   */
  suspend fun updateUser(userId: UserId, newValue: User)

  /**
   * Deletes a User object from the repository.
   *
   * @param userId The unique identifier of the user to delete.
   * @throws AppException.NotFound if the user is not found.
   */
  suspend fun deleteUser(userId: UserId)
}
