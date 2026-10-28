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

  override suspend fun deleteUser(userId: UserId) {
    if (!users.keys.contains(userId))
        throw AppException.NotFound("No user with uid $userId was found")
    users.remove(userId)
  }
}
