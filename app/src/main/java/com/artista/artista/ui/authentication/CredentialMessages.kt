// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.authentication

import androidx.annotation.StringRes
import com.artista.artista.R
import com.artista.artista.model.error.AppException
import com.artista.artista.ui.error.toMessageRes

/**
 * Returns the message shown when the Credential Manager interaction produces no token.
 *
 * The interaction outcomes - the user dismissed the Google sheet, no credential is available - are
 * not backend failures, so [com.artista.artista.ui.error.launchCatching] can only report them as
 * [Unknown], whose message says that something went wrong. The sign-in screen knows more about what
 * happened and says so instead, which is the exception documented on [toMessageRes].
 *
 * Declared at file level rather than inline in the composable so it stays a plain function that can
 * be unit-tested without Compose.
 *
 * @return the string resource of the message to show under the sign-in button
 * @author timo-by
 */
@StringRes
internal fun AppException.toCredentialMessageRes(): Int =
    when (this) {
      // Nothing classified this failure: the provider reported an interaction outcome, not an
      // error from a backend.
      is AppException.Unknown -> R.string.sign_in_failed
      // The provider already classified a genuine failure, such as a lost connection.
      else -> toMessageRes()
    }
