package com.artista.artista.model.user

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
        throw IllegalArgumentException("uid ${user.uid} already taken")
    users[user.uid] = user
  }

  override suspend fun getUser(userId: UserId): User {
    return users[userId] ?: throw NoSuchElementException("No user with uid $userId was found")
  }

  override suspend fun updateUser(
      userId: UserId,
      newValue: User,
  ) {
    if (!users.keys.contains(userId))
        throw NoSuchElementException("No user with uid $userId was found")
    users.replace(userId, newValue)
  }

  override suspend fun deleteUser(userId: UserId) {
    if (!users.keys.contains(userId))
        throw NoSuchElementException("No user with uid $userId was found")
    users.remove(userId)
  }
}
