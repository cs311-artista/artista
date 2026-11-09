// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.model.user

import com.artista.artista.model.error.AppException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * In-memory [UserRepository] for unit and instrumented tests, which can also simulate backend
 * failures and slow reads.
 *
 * [UserRepositoryLocal] stores users the same way but never fails, while the profile and
 * preferences tests need to check what the user sees when reading or saving a profile fails. It
 * lives in the shared test sources so that both suites use this single copy.
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

  /**
   * Delay in milliseconds before [createUser] and [updateUser] reach the backend, per user, to
   * simulate a write that has not completed yet, as with Firestore while offline.
   */
  val writeDelays = mutableMapOf<UserId, Long>()

  /**
   * When true, a write in [writeDelays] runs to its end even once its caller is cancelled, to
   * simulate a backend reporting a failure after the save it belongs to was cancelled.
   */
  var writesIgnoreCancellation: Boolean = false

  /** When set, [createUser] and [updateUser] throw it instead of writing. */
  var writeFailure: Throwable? = null

  /**
   * Number of calls to [createUser] and [updateUser] that reached the backend, failed ones
   * included. A write cancelled during its [writeDelays] does not count.
   */
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
    waitForWrite(user.uid)
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
    waitForWrite(userId)
    writeCount++
    writeFailure?.let { throw it }
    if (userId !in users) throw AppException.NotFound("No user with uid $userId")
    users[userId] = newValue
  }

  override suspend fun deleteUser(userId: UserId) {
    users.remove(userId) ?: throw AppException.NotFound("No user with uid $userId")
  }

  /**
   * Waits for the delay configured in [writeDelays] for a user, if any.
   *
   * @param userId identifier of the user being written
   */
  private suspend fun waitForWrite(userId: UserId) {
    val writeDelay = writeDelays[userId] ?: return
    if (writesIgnoreCancellation) withContext(NonCancellable) { delay(writeDelay) }
    else delay(writeDelay)
  }
}
