package com.artista.artista.model.authentication

import com.artista.artista.model.error.AppException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

// Co-authored-by: DeepSeek Flash
/**
 * Unit tests for [runAuthCatching], the single wrapper every authentication boundary reports its
 * failures through.
 *
 * The rule it encodes - cancellation is the caller being torn down and is never a failure to report
 * - is the one that a copy-pasted `try`/`catch` gets wrong, so it is pinned once here rather than
 *   four times through the classes that use it.
 *
 * @author timo-by
 */
class AuthFailureHandlingTest {

  /**
   * Verifies that a successful call is returned as a success carrying its value, so the wrapper
   * does not change the outcome of the calls it wraps.
   *
   * @author timo-by
   */
  @Test
  fun aSuccessfulCallIsReturnedAsSuccess() = runTest {
    val result = runAuthCatching(::classify) { TOKEN }

    assertEquals(TOKEN, result.getOrThrow())
  }

  /**
   * Verifies that a platform failure is handed to the classifier, and that the classifier's
   * translation - not the raw exception - is what the caller receives, with the platform exception
   * still reachable as its cause so a crash report keeps showing where it came from.
   *
   * @author timo-by
   */
  @Test
  fun aPlatformFailureIsTranslatedAndKeepsItsCause() = runTest {
    val cause = IllegalArgumentException(FAILED)

    val failure = runAuthCatching(::classify) { throw cause }.exceptionOrNull() as AppException

    assertEquals(FAILED, failure.message)
    assertSame(cause, failure.cause)
  }

  /**
   * Verifies that an [AppException] raised inside the call is passed through unchanged instead of
   * being translated again, which would nest one classified failure inside another.
   *
   * This is the path a call takes when it reports a failure of its own, such as a sign-in that
   * produced no session.
   *
   * @author timo-by
   */
  @Test
  fun anAlreadyClassifiedFailureIsPassedThrough() = runTest {
    val raised = AppException.Unknown()

    val failure = runAuthCatching(::classify) { throw raised }.exceptionOrNull()

    assertSame(raised, failure)
  }

  /**
   * Verifies that cancellation is rethrown rather than reported, since a screen that is already
   * gone must not be shown a failure for work it cancelled.
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
   * Stands in for the classifiers the production callers pass in, forwarding the platform message
   * and exception the way the real ones do.
   *
   * @param cause the failure to classify
   * @return the outcome the assertions expect
   * @author timo-by
   */
  private fun classify(cause: Throwable): AppException =
      AppException.Unauthorized(cause.message, cause)

  private companion object {
    const val TOKEN = "google-id-token"
    const val FAILED = "the platform call failed"
    const val CANCELLED = "the caller is being torn down"
  }
}
