package com.artista.artista.model.authentication

import kotlinx.coroutines.CancellationException

// Co-authored-by: DeepSeek Flash
/**
 * Failure kinds the authentication layer can report.
 *
 * Deliberately a closed set of domain outcomes rather than raw SDK exceptions: the layer below
 * decides *what happened* (a cancelled sheet, a missing account, a rejected credential), and the UI
 * decides how to say it, in the user's language. Without this, an English SDK message would end up
 * in a Snackbar and a test would have to assert on it.
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
 * Callers recover the domain outcome with `error`, and never have to inspect [cause].
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
 * Authentication code has two failure paths that both end in the UI: the repository returns its
 * failures through [Result], and the credential provider does the same. Using one helper keeps both
 * paths from each inventing a fallback.
 *
 * @return the domain outcome of this failure
 * @author timo-by
 */
fun Throwable.toAuthError(): AuthError = (this as? AuthException)?.error ?: AuthError.Unknown

/**
 * Runs [block] and reports a failure as an [AuthException] carrying the outcome [classify] returns.
 *
 * Every boundary of the authentication layer adds the same wrapper to the platform call it makes,
 * and the rule that wrapper encodes is the one a copy gets wrong: coroutine cancellation is the
 * caller being torn down, so it must be rethrown rather than reported, or a screen that is already
 * gone shows a failure for work it cancelled. Keeping the rule in one place is the point of this
 * helper.
 *
 * An [AuthException] raised by [block] is already classified - a call may report a failure of its
 * own, such as a sign-in that yielded no session - and is passed through unchanged rather than
 * wrapped a second time.
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
