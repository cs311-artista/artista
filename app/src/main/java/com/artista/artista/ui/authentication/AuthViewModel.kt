// Co-authored-by: DeepSeek Flash
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.authentication

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.artista.artista.model.authentication.AuthRepository
import com.artista.artista.model.authentication.AuthUser
import com.artista.artista.model.error.AppException
import com.artista.artista.ui.error.launchCatching
import com.artista.artista.ui.error.toMessageRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Immutable UI state of the authentication flow.
 *
 * The failure of the last repository operation is part of the state rather than a one-shot event,
 * like on the other screens of the app: it survives a configuration change, and the screen keeps
 * showing what went wrong while the user retries.
 *
 * It carries the failures of the repository only. The outcome of the Credential Manager interaction
 * is kept by the screen instead: it comes from a platform dialog the ViewModel cannot see, and it
 * must not outlive the composition, so the two are never merged into a single field.
 *
 * @property user the signed-in user, or `null` when signed out
 * @property isLoading whether an authentication operation is in progress
 * @property repositoryErrorMessageRes localized message of the last failed repository operation, or
 *   `null` when there is nothing to report
 * @author timo-by
 */
data class AuthUiState(
    val user: AuthUser? = null,
    val isLoading: Boolean = false,
    @param:StringRes val repositoryErrorMessageRes: Int? = null,
)

/**
 * Coordinates authentication for the sign-in screen.
 *
 * Depends only on [AuthRepository], never on a concrete Firebase implementation, so the logic stays
 * unit-testable with a fake. Failures are reported as an [AppException] by [launchCatching] and
 * turned into a user-facing message by [toMessageRes], so the backend's own message - meant for
 * logs - never reaches the user.
 *
 * The Credential Manager interaction is not coordinated here: it can only be driven from the UI,
 * which reports its own outcome (see [AuthScreen]).
 *
 * @param repository the authentication repository
 * @author timo-by
 */
class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

  private val _uiState = MutableStateFlow(AuthUiState())

  /**
   * The UI state observed by the sign-in screen.
   *
   * Backed by [_uiState]; the signed-in user is kept in sync with [AuthRepository.authState] so the
   * single source of truth is the repository, not the ViewModel.
   *
   * @author timo-by
   */
  val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launchCatching(
        tag = TAG,
        // A stream that fails means the session can no longer be tracked at all, which the user has
        // to know about, so it is reported like a failed operation.
        onError = ::report,
    ) {
      repository.authState.collect { user -> _uiState.update { it.copy(user = user) } }
    }
  }

  /**
   * Starts a sign-in from the given Google ID token.
   *
   * Sets [AuthUiState.isLoading] to `true` synchronously, before launching any coroutine, so the
   * loading state is observable as soon as this call returns and a re-entrant call (a double tap)
   * is ignored rather than starting a second sign-in. The call is also ignored while a user is
   * already signed in. A new attempt dismisses the message of the previous one.
   *
   * @param idToken the Google ID token returned by Credential Manager
   * @author timo-by
   */
  fun signIn(idToken: String) {
    if (_uiState.value.isLoading || (_uiState.value.user != null)) return
    _uiState.update { it.copy(isLoading = true, repositoryErrorMessageRes = null) }
    viewModelScope.launchCatching(tag = TAG, onError = ::report) {
      try {
        // Throwing is what lets launchCatching classify the failure: an AppException from the
        // repository is reported as is, anything else becomes AppException.Unknown, and both are
        // logged.
        repository.signIn(idToken).getOrThrow()
      } finally {
        // Reset however the operation ends, so a failure does not leave the button spinning.
        _uiState.update { it.copy(isLoading = false) }
      }
    }
  }

  /**
   * Signs the current user out.
   *
   * Like [signIn], sets [AuthUiState.isLoading] to `true` synchronously so a re-entrant call is
   * ignored, is a no-op while no user is signed in, and dismisses the message of the previous
   * attempt.
   *
   * @author timo-by
   */
  fun signOut() {
    if (_uiState.value.isLoading || (_uiState.value.user == null)) return
    _uiState.update { it.copy(isLoading = true, repositoryErrorMessageRes = null) }
    viewModelScope.launchCatching(tag = TAG, onError = ::report) {
      try {
        repository.signOut().getOrThrow()
      } finally {
        _uiState.update { it.copy(isLoading = false) }
      }
    }
  }

  /**
   * Dismisses the message of the last failed repository operation.
   *
   * The screen calls this as soon as the user starts a new attempt, before the Credential Manager
   * interaction has even resolved. Without it, the message of the previous attempt would still be
   * set when a newer interaction ends, and the screen would describe an attempt that is not the one
   * that just finished.
   *
   * @author timo-by
   */
  fun clearRepositoryError() {
    _uiState.update { it.copy(repositoryErrorMessageRes = null) }
  }

  /**
   * Records the message of a failure so the screen keeps showing what went wrong.
   *
   * @param error the failure reported by [launchCatching]
   * @author timo-by
   */
  private fun report(error: AppException) {
    _uiState.update { it.copy(repositoryErrorMessageRes = error.toMessageRes()) }
  }

  companion object {
    /** Log tag identifying messages emitted by [AuthViewModel]. */
    private const val TAG = "AuthViewModel"
  }
}
