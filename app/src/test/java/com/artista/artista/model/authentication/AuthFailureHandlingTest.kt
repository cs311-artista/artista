package com.artista.artista.model.authentication

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

// Co-authored-by: DeepSeek Flash
/**
 * Unit tests for [runAuthCatching], the wrapper every authentication boundary reports failures
 * through.
 *
 * The rule it encodes - cancellation is the caller being torn down, never a failure to report - is
 * the one a copy-pasted `try`/`catch` gets wrong, so it is pinned once here instead of through each
 * caller.
 *
 * @author timo-by
 */
class AuthFailureHandlingTest {

  /**
   * Verifies that a successful call is returned as a success carrying its value.
   *
   * @author timo-by
   */
  @Test
  fun aSuccessfulCallIsReturnedAsSuccess() = runTest {
    val result = runAuthCatching(::classify) { TOKEN }

    assertEquals(TOKEN, result.getOrThrow())
  }

  /**
   * Verifies that a platform failure is classified and wrapped, keeping the original exception as
   * the cause so a crash report still shows it.
   *
   * @author timo-by
   */
  @Test
  fun aPlatformFailureIsClassified() = runTest {
    val cause = IllegalArgumentException(FAILED)

    val failure = runAuthCatching(::classify) { throw cause }.exceptionOrNull() as AuthException

    assertEquals(AuthError.InvalidCredentials, failure.error)
    assertSame(cause, failure.cause)
  }

  /**
   * Verifies that an [AuthException] raised inside the call is passed through instead of being
   * wrapped again, which would nest one classified failure inside another.
   *
   * This is the path a call takes when it reports a failure of its own, such as a sign-in that
   * produced no session.
   *
   * @author timo-by
   */
  @Test
  fun anAlreadyClassifiedFailureIsPassedThrough() = runTest {
    val raised = AuthException(AuthError.Unknown)

    val failure = runAuthCatching(::classify) { throw raised }.exceptionOrNull()

    assertSame(raised, failure)
  }

  /**
   * Verifies that cancellation is rethrown rather than reported.
   *
   * @author timo-by
   */
  @Test
  fun cancellationIsRethrown() = runTest {
    val thrown: CancellationException? =
        try {
          runAuthCatching(::classify) { throw CancellationException(CANCELLED) }
          null
        } catch (e: CancellationException) {
          e
        }

    assertTrue(thrown != null)
  }

  /**
   * Stands in for the classifiers the production callers pass in.
   *
   * @param cause the failure to classify
   * @return the outcome the assertions expect
   * @author timo-by
   */
  private fun classify(cause: Throwable): AuthError = AuthError.InvalidCredentials

  private companion object {
    const val TOKEN = "google-id-token"
    const val FAILED = "the platform call failed"
    const val CANCELLED = "the caller is being torn down"
  }
}
