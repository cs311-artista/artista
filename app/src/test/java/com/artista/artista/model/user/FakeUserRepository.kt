// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.model.user

import com.artista.artista.model.error.AppException
import kotlinx.coroutines.delay

/**
 * In-memory [UserRepository] for unit tests, which can also simulate backend failures and slow
 * reads.
 *
 * [UserRepositoryLocal] stores users the same way but never fails, while the profile tests need to
 * check what the user sees when reading or saving their profile fails.
 *
 * @author Timz3rr
 */
class FakeUserRepository : UserRepository {

  private val users = mutableMapOf<UserId, User>()

  /** When set, [getUser] throws it instead of reading. */
  var readFailure: Throwable? = null

  /**
   * Delay in milliseconds before [getUser] returns, per user, to simulate a slow network for one
   * user while the others load at once.
   */
  val readDelays = mutableMapOf<UserId, Long>()

  /** When set, [createUser] and [updateUser] throw it instead of writing. */
  var writeFailure: Throwable? = null

  /** Number of calls to [createUser] and [updateUser], failed ones included. */
  var writeCount: Int = 0
    private set

  /**
   * Stores a user directly, without counting as a write, to set up the state a test starts from.
   *
   * @param user the user to store
   * @author Timz3rr
   */
  fun store(user: User) {
    users[user.uid] = user
  }

  /**
   * Reads a stored user without going through [getUser], so it never fails.
   *
   * @param userId the identifier of the user to read
   * @return the stored user, or null if there is none
   * @author Timz3rr
   */
  fun storedUser(userId: UserId): User? = users[userId]

  override suspend fun createUser(user: User) {
    writeCount++
    writeFailure?.let { throw it }
    if (user.uid in users) throw AppException.AlreadyExists("uid ${user.uid} already taken")
    users[user.uid] = user
  }

  override suspend fun getUser(userId: UserId): User {
    readDelays[userId]?.let { delay(it) }
    readFailure?.let { throw it }
    return users[userId] ?: throw AppException.NotFound("No user with uid $userId")
  }

  override suspend fun updateUser(userId: UserId, newValue: User) {
    writeCount++
    writeFailure?.let { throw it }
    if (userId !in users) throw AppException.NotFound("No user with uid $userId")
    users[userId] = newValue
  }

  override suspend fun deleteUser(userId: UserId) {
    users.remove(userId) ?: throw AppException.NotFound("No user with uid $userId")
  }
}
