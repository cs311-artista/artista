// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.profile

import androidx.lifecycle.ViewModel
import com.artista.artista.model.user.User
import java.text.Normalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Maximum number of characters of a username, counted after trimming. */
const val MAX_USERNAME_LENGTH = 20

/**
 * Cleans up a username before it is validated or saved.
 *
 * @param name the username as typed by the user.
 * @return the trimmed username in Unicode NFC form.
 * @author Timz3rr
 */
fun normalizeUsername(name: String): String =
    // NFC turns "e" + combining acute accent into a single "é", so accents typed or pasted in
    // decomposed form count as letters and as one character for the length limit.
    Normalizer.normalize(name.trim(), Normalizer.Form.NFC)

/**
 * Checks whether a username entered by the user can be saved.
 *
 * The name is checked once normalized by [normalizeUsername], the form in which it is saved. It
 * must be non-empty, at most [MAX_USERNAME_LENGTH] characters long, and contain only letters
 * (accents included), digits and spaces.
 *
 * @param name the username as typed by the user.
 * @return true if the username can be saved, false otherwise.
 * @author Timz3rr
 */
fun isValidUsername(name: String): Boolean {
  val normalized = normalizeUsername(name)
  // A plain space is checked instead of isWhitespace() so that tabs and line breaks are rejected.
  return normalized.isNotEmpty() &&
      normalized.length <= MAX_USERNAME_LENGTH &&
      normalized.all { it.isLetterOrDigit() || it == ' ' }
}

/**
 * UI state of the user profile screen.
 *
 * @property username the display name of the user, or null when the user has not chosen one.
 * @author Timz3rr
 */
data class UserProfileUiState(val username: String? = null)

/**
 * Holds the state displayed by the user profile screen.
 *
 * @author Timz3rr
 */
class UserProfileViewModel : ViewModel() {

  private val _uiState = MutableStateFlow(UserProfileUiState())

  /**
   * The current state of the user profile screen.
   *
   * @author Timz3rr
   */
  val uiState: StateFlow<UserProfileUiState> = _uiState.asStateFlow()

  /**
   * Displays the given user on the profile screen.
   *
   * @param user the user whose profile is displayed.
   * @author Timz3rr
   */
  fun setUser(user: User) {
    // A blank name carries no information, if username null then USERNAME is displayed, else the
    // name is.
    _uiState.update { state -> state.copy(username = user.userName?.takeIf { it.isNotBlank() }) }
    // copy() keeps the other state fields instead of resetting them to their defaults.
  }

  /**
   * Replaces the displayed username with the one entered by the user.
   *
   * The username is only kept in memory: it is not persisted yet, so it is lost when the user signs
   * out or the app is closed.
   *
   * @param newName the username as typed by the user; ignored if [isValidUsername] rejects it.
   * @author Timz3rr
   */
  fun updateUsername(newName: String) {
    // The dialog already disables saving an invalid name; checking again keeps the state valid
    // whatever the caller.
    if (!isValidUsername(newName)) return
    _uiState.update { it.copy(username = normalizeUsername(newName)) }
  }
}
