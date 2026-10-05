package com.artista.artista.ui.authentication

import androidx.lifecycle.ViewModel
import com.artista.artista.model.authentication.AuthRepository
import com.artista.artista.model.authentication.AuthUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

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

  /**
   * The UI state observed by the sign-in screen.
   *
   * @author timo-by
   */
  val uiState: StateFlow<AuthUiState> = MutableStateFlow(AuthUiState())

  /**
   * One-shot events for the UI.
   *
   * @author timo-by
   */
  val events: Flow<AuthUiEvent> = emptyFlow()

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
  fun signIn(idToken: String): Unit = TODO("Not yet implemented")

  /**
   * Signs the current user out.
   *
   * Like [signIn], sets [AuthUiState.isLoading] to `true` synchronously so a re-entrant call is
   * ignored, and is a no-op while no user is signed in.
   *
   * @author timo-by
   */
  fun signOut(): Unit = TODO("Not yet implemented")
}
