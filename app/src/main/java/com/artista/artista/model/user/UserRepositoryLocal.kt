// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.model.user

import com.artista.artista.model.error.AppException

/**
 * In-memory repository for user data used for local app development, demoing, and testing.
 *
 * @author 5kyPhy
 */
class UserRepositoryLocal : UserRepository {
  /** Stores all users existing in the local repository */
  private val users: HashMap<UserId, User> = hashMapOf()

  override suspend fun createUser(user: User) {
    if (users.keys.contains(user.uid))
        throw AppException.AlreadyExists("uid ${user.uid} already taken")
    users[user.uid] = user
  }

  override suspend fun getUser(userId: UserId): User {
    return users[userId] ?: throw AppException.NotFound("No user with uid $userId was found")
  }

  override suspend fun updateUser(
      userId: UserId,
      newValue: User,
  ) {
    val prevUser = getUser(newValue.uid)
    users.replace(prevUser.uid, newValue)
  }

  /**
   * Replaces the username of a user and keeps their preferences, creating the user with empty
   * preferences when there is none yet.
   *
   * @param userId the unique identifier of the user to rename
   * @param userName the new username
   * @author Timz3rr
   */
  override suspend fun updateUsername(userId: UserId, userName: String) {
    users[userId] =
        // Like the Firestore merge: only the name changes, every other field is kept.
        users[userId]?.copy(userName = userName)
            // Like the Firestore merge on a missing document: the profile is created.
            ?: User(uid = userId, userName = userName, preference = UserPreference())
  }

  /**
   * Replaces the picture of an existing user, leaving the rest of the profile as it was.
   *
   * @param userId the unique identifier of the user to update
   * @param photoUrl the URL of the picture to store
   * @throws AppException.NotFound if the user does not exist
   * @author timo-by
   */
  override suspend fun updatePhotoUrl(userId: UserId, photoUrl: String) {
    val prevUser = getUser(userId)
    users.replace(userId, prevUser.copy(photoUrl = photoUrl))
  }

  override suspend fun deleteUser(userId: UserId) {
    if (!users.keys.contains(userId))
        throw AppException.NotFound("No user with uid $userId was found")
    users.remove(userId)
  }
}
