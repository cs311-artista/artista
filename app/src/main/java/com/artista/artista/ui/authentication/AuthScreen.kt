package com.artista.artista.ui.authentication

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Stateless content of the authentication screen: renders the sign-in affordance and the error
 * Snackbar, and reports user intent through callbacks.
 *
 * Carries no authentication logic; [state] and the Snackbar host state are hoisted so the
 * composable is directly testable and previewable without a ViewModel or Credential Manager.
 *
 * @param state the current authentication UI state
 * @param snackbarHostState the host state backing the error Snackbar, owned by the caller
 * @param onSignInClick invoked when the user asks to sign in
 * @param onSignedIn invoked once when [state] transitions to a signed-in user
 * @param modifier the modifier applied to the root container
 * @author timo-by
 */
@Composable
fun AuthScreenContent(
    state: AuthUiState,
    snackbarHostState: SnackbarHostState,
    onSignInClick: () -> Unit,
    onSignedIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
  TODO("Not yet implemented")
}

/**
 * Authentication screen: wires [AuthViewModel] and [GoogleCredentialProvider] to the stateless
 * [AuthScreenContent].
 *
 * Owns the single snackbar host state used to surface both repository errors (from [AuthViewModel])
 * and credential-acquisition failures, and drives the Credential Manager flow on a sign-in tap.
 *
 * @param viewModel the authentication ViewModel
 * @param credentialProvider the seam used to obtain a Google ID token
 * @param onSignedIn invoked once a user is signed in
 * @param modifier the modifier applied to the root container
 * @author timo-by
 */
@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    credentialProvider: GoogleCredentialProvider,
    onSignedIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
  TODO("Not yet implemented")
}
