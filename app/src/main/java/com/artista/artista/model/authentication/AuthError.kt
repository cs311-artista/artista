package com.artista.artista.model.authentication

import kotlinx.coroutines.CancellationException

// Co-authored-by: DeepSeek Flash
/**
 * Failure kinds the authentication layer can report.
 *
 * A closed set of domain outcomes rather than raw SDK exceptions: this layer decides *what*
 * happened, the UI decides how to say it. Otherwise an English SDK message would reach a Snackbar
 * and a test would have to assert on it.
 *
 * @author timo-by
 */
sealed interface AuthError {

  /** The user dismissed the Google sign-in sheet without choosing an account. */
  data object Cancelled : AuthError

  /** No Google account is usable on this device, so there is nothing to sign in with. */
  data object NoGoogleAccount : AuthError

  /** The device could not reach Google or Firebase. */
  data object Network : AuthError

  /** Google or Firebase rejected the credential, for example an expired ID token. */
  data object InvalidCredentials : AuthError

  /** A failure the authentication layer could not classify. */
  data object Unknown : AuthError
}

/**
 * Carries an [AuthError] through [Result], which can only fail with a [Throwable].
 *
 * Callers recover the outcome with [error] and never inspect [cause], which is only kept so a crash
 * report still says what went wrong on the device.
 *
 * @param error the domain outcome to report
 * @param cause the platform exception that produced it, kept for logging
 * @author timo-by
 */
class AuthException(val error: AuthError, cause: Throwable? = null) :
    Exception("Authentication failed: $error", cause)

/**
 * Returns the [AuthError] this failure carries, or [AuthError.Unknown] when it carries none.
 *
 * The repository and the credential provider both report through [Result], so one helper keeps them
 * from each inventing a fallback.
 *
 * @return the domain outcome of this failure
 * @author timo-by
 */
fun Throwable.toAuthError(): AuthError = (this as? AuthException)?.error ?: AuthError.Unknown

/**
 * Runs [block] and reports a failure as an [AuthException] carrying the outcome [classify] returns.
 *
 * Coroutine cancellation is the caller being torn down, so it is rethrown rather than reported: a
 * screen that is already gone must not show a failure for work it cancelled. An [AuthException]
 * raised by [block] is already classified and is passed through instead of being wrapped twice.
 *
 * @param classify maps a platform exception to the domain outcome describing it
 * @param block the platform call to run
 * @return success with the value of [block], or failure carrying an [AuthException]
 * @author timo-by
 */
internal suspend fun <T> runAuthCatching(
    classify: (Throwable) -> AuthError,
    block: suspend () -> T,
): Result<T> =
    try {
      Result.success(block())
    } catch (e: CancellationException) {
      throw e
    } catch (e: AuthException) {
      Result.failure(e)
    } catch (e: Exception) {
      Result.failure(AuthException(classify(e), e))
    }
