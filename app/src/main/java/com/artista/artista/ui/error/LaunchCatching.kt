// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.error

import android.util.Log
import com.artista.artista.model.error.AppException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Launches a coroutine and reports its failure as an [AppException] instead of crashing the app.
 *
 * It replaces the try/catch block every ViewModel would otherwise repeat. A failure that is not an
 * [AppException] (a bug, or a repository that does not translate its errors) is reported as
 * [AppException.Unknown], with the original failure as its cause. Every failure is logged.
 *
 * @param tag the log tag of the caller, usually its class name
 * @param onError called with the failure when [block] throws
 * @param block the work to run
 * @return the launched job, e.g. to cancel a load that a newer one replaces
 * @author Timz3rr
 */
fun CoroutineScope.launchCatching(
    tag: String,
    onError: (AppException) -> Unit,
    block: suspend CoroutineScope.() -> Unit,
): Job = launch {
  try {
    block()
  } catch (exception: CancellationException) {
    // Cancellation is how a coroutine stops (e.g. the user left the screen), not a failure.
    // CancellationException extends Exception, so it must be rethrown before the catches below.
    throw exception
  } catch (exception: AppException) {
    Log.e(tag, "Operation failed.", exception)
    onError(exception)
  } catch (exception: Exception) {
    Log.e(tag, "Unexpected failure.", exception)
    onError(AppException.Unknown(exception.message, exception))
  }
}
