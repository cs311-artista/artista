package com.artista.artista.ui.authentication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.artista.artista.model.authentication.AuthRepository
import com.artista.artista.model.authentication.AuthUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Immutable UI state of the authentication flow.
 *
 * @property user the signed-in user, or `null` when signed out
 * @property isLoading whether an authentication operation is in progress
 * @author timo-by
 */
data class AuthUiState(val user: AuthUser? = null, val isLoading: Boolean = false)

/**
 * One-shot events emitted by the authentication flow and consumed by the UI.
 *
 * @author timo-by
 */
sealed interface AuthUiEvent {

  /**
   * Asks the UI to display an error, typically in a Snackbar.
   *
   * @property message the message to display
   * @author timo-by
   */
  data class ShowError(val message: String) : AuthUiEvent
}

/**
 * Coordinates authentication for the sign-in screen.
 *
 * Depends only on [AuthRepository], never on a concrete Firebase implementation, so the logic stays
 * unit-testable with a fake.
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

  private val _events = MutableSharedFlow<AuthUiEvent>()

  /**
   * One-shot events for the UI.
   *
   * Exposed as a hot flow with no replay, so an error is delivered once and not re-shown to a
   * collector that subscribes later (e.g. after a recomposition or rotation).
   *
   * @author timo-by
   */
  val events: Flow<AuthUiEvent> = _events.asSharedFlow()

  init {
    viewModelScope.launch {
      repository.authState.collect { user -> _uiState.update { it.copy(user = user) } }
    }
  }

  /**
   * Starts a sign-in from the given Google ID token.
   *
   * Sets [AuthUiState.isLoading] to `true` synchronously, before launching any coroutine, so the
   * loading state is observable as soon as this call returns and a re-entrant call (a double tap)
   * is ignored rather than starting a second sign-in. The call is also ignored while a user is
   * already signed in.
   *
   * @param idToken the Google ID token returned by Credential Manager
   * @author timo-by
   */
  fun signIn(idToken: String) {
    if (_uiState.value.isLoading || (_uiState.value.user != null)) return
    _uiState.update { it.copy(isLoading = true) }
    viewModelScope.launch {
      try {
        repository.signIn(idToken).onFailure { emitError(it) }
      } finally {
        _uiState.update { it.copy(isLoading = false) }
      }
    }
  }

  /**
   * Signs the current user out.
   *
   * Like [signIn], sets [AuthUiState.isLoading] to `true` synchronously so a re-entrant call is
   * ignored, and is a no-op while no user is signed in.
   *
   * @author timo-by
   */
  fun signOut() {
    if (_uiState.value.isLoading || (_uiState.value.user == null)) return
    _uiState.update { it.copy(isLoading = true) }
    viewModelScope.launch {
      try {
        repository.signOut().onFailure { emitError(it) }
      } finally {
        _uiState.update { it.copy(isLoading = false) }
      }
    }
  }

  /**
   * Turns a failed authentication operation into a one-shot [AuthUiEvent.ShowError].
   *
   * Falls back to a generic message when the cause carries none, so the UI always has something to
   * display.
   *
   * @param cause the failure returned by the repository
   * @author timo-by
   */
  private suspend fun emitError(cause: Throwable) {
    _events.emit(AuthUiEvent.ShowError(cause.message ?: "Authentication failed"))
  }
}
