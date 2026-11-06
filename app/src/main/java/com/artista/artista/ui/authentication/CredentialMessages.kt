// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.authentication

import androidx.annotation.StringRes
import com.artista.artista.R
import com.artista.artista.model.error.AppException
import com.artista.artista.ui.error.toMessageRes

/**
 * Returns the message shown when a Credential Manager failure produces no token.
 *
 * Only genuine failures reach this function: the sign-in screen answers the interaction outcomes -
 * the user dismissed the Google sheet, no credential is available - before they get here, so a
 * dismissal reports nothing and a missing account shows its own message. What is left is a real
 * failure, which keeps the message its type documents on [toMessageRes]; an unclassified one reads
 * as a failed sign-in instead of the generic unknown message, because "Sign in failed" says more
 * about an attempt that never reached the backend than "something went wrong" does.
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
      // Nothing classified this failure: the provider could not name what went wrong, so this
      // stays specific to the sign-in attempt instead of the generic unknown one.
      is AppException.Unknown -> R.string.sign_in_failed
      // The provider already classified a genuine failure, such as a lost connection.
      else -> toMessageRes()
    }
