package com.artista.artista.model.authentication

import com.artista.artista.model.error.AppException
import kotlinx.coroutines.CancellationException

// Co-authored-by: DeepSeek Flash
/**
 * Runs [block] and reports its failure as the failure [classify] returns for it.
 *
 * Authentication has two boundaries that fail the same way - the repository and the credential
 * provider - and both report through [Result], which can only fail with a [Throwable]. This wrapper
 * is what turns a platform exception into the failure the caller is given: an [AppException] for
 * the repository, and for the provider an [AppException] too, except for the two outcomes that
 * describe the interaction rather than a failure.
 *
 * The rule it encodes is the one a copy-pasted `try`/`catch` gets wrong: coroutine cancellation is
 * the caller being torn down, so it is rethrown rather than reported, or a screen that is already
 * gone shows a failure for work it cancelled.
 *
 * An [AppException] raised by [block] is already classified - a call may report a failure of its
 * own, such as a sign-in that yielded no session - and is passed through unchanged instead of being
 * translated a second time.
 *
 * @param classify maps a platform exception to the failure the caller is given
 * @param block the platform call to run
 * @return success with the value of [block], or failure carrying the classified failure
 * @author timo-by
 */
internal suspend fun <T> runAuthCatching(
    classify: (Throwable) -> Throwable,
    block: suspend () -> T,
): Result<T> =
    try {
      Result.success(block())
    } catch (e: CancellationException) {
      throw e
    } catch (e: AppException) {
      Result.failure(e)
    } catch (e: Exception) {
      Result.failure(classify(e))
    }
