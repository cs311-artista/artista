package com.artista.artista.model.user

/**
 * Represents a repository that manages User related data such as username or preferences.
 *
 * @author Kaio R. Freitas Pereira Nascimento (krfpn)
 * @author Copilot (223556219+Copilot@users.noreply.github.com)
 */
interface UserRepository {

  /**
   * Creates a new User in the repository.
   *
   * @param user The User object to create.
   * @throws Exception if the user-id is already taken.
   */
  suspend fun createUser(user: User)

  /**
   * Retrieves a User information by its unique identifier.
   *
   * @param userId The unique identifier of the user to retrieve.
   * @return The User object with this specific id.
   * @throws Exception if the user is not found.
   */
  suspend fun getUser(userId: String): User

  /**
   * Updates an existing User in the repository.
   *
   * @param userId The unique identifier of the user to edit.
   * @param newValue The new value for the User object.
   * @throws Exception if the user is not found.
   */
  suspend fun updateUser(userId: String, newValue: User)

  /**
   * Deletes a User object from the repository.
   *
   * @param userId The unique identifier of the user to delete.
   * @throws Exception if the user is not found.
   */
  suspend fun deleteUser(userId: String)
}
