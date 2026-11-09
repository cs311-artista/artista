// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.model.user

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** The device store holding the cached usernames. */
val Context.usernameDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "username_cache")

/**
 * [UsernameCache] that persists the names on the device, so they survive a cold start.
 *
 * DataStore is preferred to SharedPreferences because its suspend API matches the repositories and
 * its first read does not block the main thread.
 *
 * @param dataStore the store holding the names
 * @author Timz3rr
 */
class UsernameCacheDataStore(private val dataStore: DataStore<Preferences>) : UsernameCache {

  override suspend fun getUsername(userId: UserId): String? = missOnFailure {
    dataStore.data.first()[stringPreferencesKey(userId)]
  }

  override suspend fun saveUsername(userId: UserId, username: String) {
    missOnFailure { dataStore.edit { it[stringPreferencesKey(userId)] = username } }
  }

  override suspend fun clearUsername(userId: UserId) {
    missOnFailure { dataStore.edit { it.remove(stringPreferencesKey(userId)) } }
  }

  /**
   * Runs an operation on the store, turning its failure into a cache miss.
   *
   * @param block the operation to run
   * @return the result of [block], or null if it failed
   * @throws CancellationException if the calling coroutine is cancelled
   */
  private inline fun <T> missOnFailure(block: () -> T): T? =
      try {
        block()
      } catch (exception: CancellationException) {
        // Swallowing it would keep a save running after a newer rename or a sign-out replaced it.
        throw exception
      } catch (exception: Exception) {
        // Any failure counts, not only disk errors: losing the local copy must never fail a rename.
        Log.w(TAG, "Username cache operation failed.", exception)
        null
      }

  /** Holds the log tag of [UsernameCacheDataStore]. */
  private companion object {
    /** Log tag identifying messages emitted by [UsernameCacheDataStore]. */
    const val TAG = "UsernameCacheDataStore"
  }
}
