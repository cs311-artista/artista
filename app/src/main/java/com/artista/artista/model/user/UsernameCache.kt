// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.model.user

/**
 * Best-effort local copy of usernames, keyed by user, so that a name can be displayed offline.
 *
 * Firestore stays the source of truth. Implementations never throw except
 * [kotlinx.coroutines.CancellationException]: a failure is logged and acts as a cache miss.
 *
 * @author Timz3rr
 */
interface UsernameCache {

  /**
   * Reads the cached name of a user.
   *
   * @param userId identifier of the user
   * @return the cached name, or null if there is none or it cannot be read
   * @throws kotlinx.coroutines.CancellationException if the calling coroutine is cancelled
   * @author Timz3rr
   */
  suspend fun getUsername(userId: UserId): String?

  /**
   * Stores the name of a user, replacing the one already cached.
   *
   * @param userId identifier of the user
   * @param username the name to cache
   * @throws kotlinx.coroutines.CancellationException if the calling coroutine is cancelled
   * @author Timz3rr
   */
  suspend fun saveUsername(userId: UserId, username: String)

  /**
   * Removes the cached name of a user.
   *
   * @param userId identifier of the user
   * @throws kotlinx.coroutines.CancellationException if the calling coroutine is cancelled
   * @author Timz3rr
   */
  suspend fun clearUsername(userId: UserId)
}
