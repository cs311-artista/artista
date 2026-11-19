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
   * Saves the username of a user without reading their profile first, so that a rename made offline
   * can be queued even when the profile has never been read on this device.
   *
   * Every other field of the profile, such as the preferences, is left unchanged. When the user has
   * no profile yet, one is created with this username and empty preferences.
   *
   * @param userId The unique identifier of the user to rename.
   * @param userName The new username, already normalized by the caller.
   * @throws AppException if the backend refuses or cannot complete the write.
   * @author Timz3rr
   */
  suspend fun updateUsername(userId: UserId, userName: String)

  /**
   * Updates only the profile picture of an existing User, leaving its other fields untouched.
   *
   * Screens that read a profile and write it back rewrite all of its fields, so a picture written
   * through [updateUser] could revert a name or a preference saved meanwhile, and their save could
   * revert the picture.
   *
   * @param userId The unique identifier of the user to edit.
   * @param photoUrl The URL of the picture to store.
   * @throws AppException.NotFound if the user is not found.
   * @author timo-by
   */
  suspend fun updatePhotoUrl(userId: UserId, photoUrl: String)

  /**
   * Deletes a User object from the repository.
   *
   * @param userId The unique identifier of the user to delete.
   * @throws AppException.NotFound if the user is not found.
   */
  suspend fun deleteUser(userId: UserId)
}
