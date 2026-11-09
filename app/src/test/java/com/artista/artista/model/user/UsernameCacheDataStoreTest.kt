// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.model.user

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Tests [UsernameCacheDataStore] on a real preferences file, and its contract of acting as a cache
 * miss rather than throwing when the store fails.
 *
 * @author Timz3rr
 */
class UsernameCacheDataStoreTest {

  @get:Rule val temporaryFolder = TemporaryFolder()

  private companion object {
    const val UID = "uid-1"
    const val OTHER_UID = "uid-2"
  }

  private val storeJob = Job()
  private val cache: UsernameCache by lazy { newCache(storeJob) }

  @After
  fun tearDown() {
    storeJob.cancel()
  }

  /** Checks that a saved name can be read back. */
  @Test
  fun saveUsername_thenGetUsername_returnsName() = runBlocking {
    cache.saveUsername(UID, "Alice")

    assertEquals("Alice", cache.getUsername(UID))
  }

  /** Checks that saving again replaces the cached name. */
  @Test
  fun saveUsername_twice_keepsLatestName() = runBlocking {
    cache.saveUsername(UID, "Alice")
    cache.saveUsername(UID, "Bob")

    assertEquals("Bob", cache.getUsername(UID))
  }

  /** Checks that each user has their own entry. */
  @Test
  fun saveUsername_keepsEntriesSeparatePerUser() = runBlocking {
    cache.saveUsername(UID, "Alice")
    cache.saveUsername(OTHER_UID, "Bob")

    assertEquals("Alice", cache.getUsername(UID))
    assertEquals("Bob", cache.getUsername(OTHER_UID))
  }

  /** Checks that a name is still there when the store is reopened, as after a cold start. */
  @Test
  fun getUsername_afterStoreIsReopened_returnsSavedName() = runBlocking {
    val firstRunJob = Job()
    newCache(firstRunJob).saveUsername(UID, "Alice")
    // DataStore refuses two active instances on one file, so the first must be closed for good.
    firstRunJob.cancelAndJoin()

    assertEquals("Alice", cache.getUsername(UID))
  }

  /** Checks that a user with no entry has no cached name. */
  @Test
  fun getUsername_withoutEntry_returnsNull() = runBlocking { assertNull(cache.getUsername(UID)) }

  /** Checks that clearing removes the name of that user only. */
  @Test
  fun clearUsername_removesOnlyThatUser() = runBlocking {
    cache.saveUsername(UID, "Alice")
    cache.saveUsername(OTHER_UID, "Bob")

    cache.clearUsername(UID)

    assertNull(cache.getUsername(UID))
    assertEquals("Bob", cache.getUsername(OTHER_UID))
  }

  /** Checks that clearing a user with no entry does nothing and does not fail. */
  @Test
  fun clearUsername_withoutEntry_doesNotThrow() = runBlocking {
    cache.clearUsername(UID)

    assertNull(cache.getUsername(UID))
  }

  /** Checks that a failed read acts as a cache miss. */
  @Test
  fun getUsername_whenReadFails_returnsNull() = runBlocking {
    val failingCache = UsernameCacheDataStore(FailingDataStore(IOException("disk error")))

    assertNull(failingCache.getUsername(UID))
  }

  /**
   * Checks that a failed write does not throw: the local copy is best-effort, so losing it must
   * never make a rename fail.
   */
  @Test
  fun saveUsername_whenWriteFails_doesNotThrow() = runBlocking {
    val failingCache = UsernameCacheDataStore(FailingDataStore(IOException("disk error")))

    failingCache.saveUsername(UID, "Alice")
  }

  /** Checks that a failed clear does not throw, so that it cannot crash a sign-out. */
  @Test
  fun clearUsername_whenWriteFails_doesNotThrow() = runBlocking {
    val failingCache = UsernameCacheDataStore(FailingDataStore(IOException("disk error")))

    failingCache.clearUsername(UID)
  }

  /** Checks that the contract covers any failure of the store, not only disk errors. */
  @Test
  fun operations_whenStoreFailsUnexpectedly_actAsCacheMiss() = runBlocking {
    val failingCache = UsernameCacheDataStore(FailingDataStore(IllegalStateException("broken")))

    assertNull(failingCache.getUsername(UID))
    failingCache.saveUsername(UID, "Alice")
    failingCache.clearUsername(UID)
  }

  /**
   * Checks that cancellation goes through every operation: swallowing it would keep a cancelled
   * save running after a newer rename or a sign-out replaced it.
   */
  @Test
  fun operations_whenCancelled_rethrowCancellation() {
    val cancelledCache = UsernameCacheDataStore(FailingDataStore(CancellationException("stopped")))

    assertThrows(CancellationException::class.java) {
      runBlocking { cancelledCache.getUsername(UID) }
    }
    assertThrows(CancellationException::class.java) {
      runBlocking { cancelledCache.saveUsername(UID, "Alice") }
    }
    assertThrows(CancellationException::class.java) {
      runBlocking { cancelledCache.clearUsername(UID) }
    }
  }

  /**
   * Builds a cache on the preferences file of this test.
   *
   * @param job the job that owns the store, whose cancellation closes the file
   * @return a cache backed by a real [DataStore]
   */
  private fun newCache(job: Job): UsernameCache =
      UsernameCacheDataStore(
          PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) {
            File(temporaryFolder.root, "username_cache.preferences_pb")
          }
      )

  /**
   * [DataStore] whose reads and writes always fail.
   *
   * @param failure the exception thrown by every operation
   */
  private class FailingDataStore(private val failure: Throwable) : DataStore<Preferences> {
    override val data: Flow<Preferences> = flow { throw failure }

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences
    ): Preferences = throw failure
  }
}
