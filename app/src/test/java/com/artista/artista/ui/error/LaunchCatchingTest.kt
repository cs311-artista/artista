// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.error

import com.artista.artista.model.error.AppException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [launchCatching], the coroutine launcher that reports failures as [AppException].
 *
 * @author Timz3rr
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LaunchCatchingTest {

  /** Errors reported through `onError`, in the order they were received. */
  private val reportedErrors = mutableListOf<AppException>()

  @Test
  fun launchCatching_whenBlockSucceeds_runsBlockWithoutReportingError() = runTest {
    var blockCompleted = false

    launchCatching(TAG, onError = { reportedErrors += it }) { blockCompleted = true }
    advanceUntilIdle()

    assertTrue(blockCompleted)
    assertTrue(reportedErrors.isEmpty())
  }

  @Test
  fun launchCatching_whenBlockThrowsAppException_reportsTheSameException() = runTest {
    val failure = AppException.Network()

    launchCatching(TAG, onError = { reportedErrors += it }) { throw failure }
    advanceUntilIdle()

    assertEquals(1, reportedErrors.size)
    assertSame(failure, reportedErrors.single())
  }

  @Test
  fun launchCatching_whenBlockThrowsOtherException_reportsUnknownWithOriginalCause() = runTest {
    val failure = IllegalStateException("Simulated unexpected failure")

    launchCatching(TAG, onError = { reportedErrors += it }) { throw failure }
    advanceUntilIdle()

    val reportedError = reportedErrors.single()
    assertTrue(reportedError is AppException.Unknown)
    assertSame(failure, reportedError.cause)
  }

  @Test
  fun launchCatching_whenCancelled_doesNotReportError() = runTest {
    // Cancellation throws a CancellationException inside the block; it must stop the coroutine
    // instead of being reported as a failure, e.g. when the user leaves a screen mid-load.
    val job = launchCatching(TAG, onError = { reportedErrors += it }) { awaitCancellation() }
    advanceUntilIdle()

    job.cancel()
    advanceUntilIdle()

    assertTrue(job.isCancelled)
    assertTrue(reportedErrors.isEmpty())
  }

  private companion object {
    const val TAG = "LaunchCatchingTest"
  }
}
