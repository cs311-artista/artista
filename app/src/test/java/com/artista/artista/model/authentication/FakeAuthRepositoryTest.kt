// Co-authored-by: DeepSeek Flash <noreply@deepseek.com>
package com.artista.artista.model.authentication

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests the re-notification of [FakeAuthRepository].
 *
 * The authentication tests use it to express a backend notifying an unchanged user again, as it
 * does on a token refresh. A state holder drops a value equal to the one it holds, so the
 * notification is not reachable through [FakeAuthRepository.setAuthState], and nothing else checks
 * that it reaches a collector at all: without this test, the assertions those tests make on what a
 * repeated notification costs would hold even if it never arrived.
 *
 * @author timo-by
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FakeAuthRepositoryTest {

  /**
   * Checks that re-emitting reports the user of the current state to an active collector again.
   *
   * @author timo-by
   */
  @Test
  fun reemitAuthState_reportsTheCurrentUserAgain() = runTest {
    val user = FakeAuthUser(uid = "user-1")
    val repository = FakeAuthRepository(user)
    val observed = mutableListOf<AuthUser?>()

    // Started before the notification, since a sub-flow without replay drops what is emitted while
    // nobody collects it, exactly as the backend event it stands for would be missed.
    backgroundScope.launch { repository.authState.collect { observed += it } }
    runCurrent()

    repository.reemitAuthState()
    runCurrent()

    assertEquals(listOf<AuthUser?>(user, user), observed)
  }
}
