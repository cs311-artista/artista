// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.error

import androidx.annotation.StringRes
import com.artista.artista.R
import com.artista.artista.model.error.AppException

/**
 * Returns the default message shown to the user for this failure.
 *
 * A screen can show a more specific message instead when it knows more about what failed (e.g.
 * which artwork could not be removed).
 *
 * @return the string resource of the default message
 * @author Timz3rr
 */
@StringRes
fun AppException.toMessageRes(): Int =
    // No else branch: a new AppException type must not compile until it gets its own message.
    when (this) {
      is AppException.NotFound -> R.string.error_not_found
      is AppException.AlreadyExists -> R.string.error_already_exists
      is AppException.InvalidInput -> R.string.error_invalid_input
      is AppException.Network -> R.string.error_network
      is AppException.Unauthorized -> R.string.error_unauthorized
      is AppException.InvalidData -> R.string.error_invalid_data
      is AppException.Unknown -> R.string.error_unknown
    }
