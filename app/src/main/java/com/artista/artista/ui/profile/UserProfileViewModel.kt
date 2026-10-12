package com.artista.artista.ui.profile

import androidx.lifecycle.ViewModel
import com.artista.artista.model.user.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
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
    _uiState.value = UserProfileUiState(username = user.userName?.takeIf { it.isNotBlank() })
  }
}
