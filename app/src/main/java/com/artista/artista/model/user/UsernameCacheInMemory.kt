// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.model.user

/**
 * [UsernameCache] kept in memory, for the previews and tests, like [UserRepositoryLocal].
 *
 * @author Timz3rr
 */
class UsernameCacheInMemory : UsernameCache {

  private val usernames = mutableMapOf<UserId, String>()

  override suspend fun getUsername(userId: UserId): String? = usernames[userId]

  override suspend fun saveUsername(userId: UserId, username: String) {
    usernames[userId] = username
  }

  override suspend fun clearUsername(userId: UserId) {
    usernames.remove(userId)
  }
}
