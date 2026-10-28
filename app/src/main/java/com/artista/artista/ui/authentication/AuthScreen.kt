// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.authentication

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import com.artista.artista.R
import com.artista.artista.resources.C
import com.artista.artista.ui.theme.ArtistaTheme
import com.artista.artista.ui.theme.Dimensions
import kotlinx.coroutines.launch

// Co-authored-by: DeepSeek Flash
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
  // Keyed on the user so the callback fires exactly once per sign-in transition, and not again when
  // unrelated parts of the state (e.g. isLoading) change.
  LaunchedEffect(state.user) { if (state.user != null) onSignedIn() }

  Box(
      modifier =
          modifier
              .fillMaxSize()
              .background(MaterialTheme.colorScheme.tertiaryContainer)
              .testTag(C.Tag.auth_screen_container),
  ) {
    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(horizontal = Dimensions.spacing24, vertical = Dimensions.spacing32),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Spacer(Modifier.height(Dimensions.spacing48))

      // Hero artwork for the authentication screen.
      Image(
          painter = painterResource(R.drawable.auth_hero),
          contentDescription = null,
          modifier =
              Modifier.fillMaxWidth(0.8f)
                  .aspectRatio(1.1f)
                  .clip(CircleShape)
                  .testTag(C.Tag.auth_hero_image),
          contentScale = ContentScale.Crop,
      )

      Spacer(Modifier.height(Dimensions.spacing16))

      Text(
          text = stringResource(R.string.app_name),
          style = MaterialTheme.typography.displayMedium.copy(fontFamily = FontFamily.Serif),
          color = MaterialTheme.colorScheme.onTertiaryContainer,
      )

      Spacer(Modifier.height(Dimensions.spacing8))

      Text(
          text = stringResource(R.string.auth_tagline),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onTertiaryContainer,
      )

      Spacer(Modifier.weight(1f))

      Button(
          onClick = onSignInClick,
          enabled = !state.isLoading,
          modifier =
              Modifier.height(Dimensions.authenticationButtonHeight)
                  .testTag(C.Tag.auth_google_sign_in_button),
          shape = RoundedCornerShape(percent = 50),
          colors =
              ButtonDefaults.buttonColors(
                  containerColor = Color.White,
                  contentColor = MaterialTheme.colorScheme.onSurface,
                  disabledContainerColor = Color.White,
                  disabledContentColor = MaterialTheme.colorScheme.onSurface,
              ),
          contentPadding =
              PaddingValues(horizontal = Dimensions.spacing20, vertical = Dimensions.spacingNone),
      ) {
        if (state.isLoading) {
          CircularProgressIndicator(
              modifier =
                  Modifier.size(Dimensions.iconSizeMedium).testTag(C.Tag.auth_loading_indicator),
              strokeWidth = Dimensions.progressIndicatorStrokeWidth,
              color = MaterialTheme.colorScheme.onSurface,
          )
        } else {
          Icon(
              painter = painterResource(R.drawable.ic_google_logo),
              contentDescription = null,
              modifier = Modifier.size(Dimensions.iconSizeMedium),
              // Keep the brand's multi-colour logo; the default tint would flatten it to one
              // colour.
              tint = Color.Unspecified,
          )
          Spacer(Modifier.width(Dimensions.spacing10))
          Text(text = stringResource(R.string.sign_in_with_google))
        }
      }

      Spacer(Modifier.height(Dimensions.spacing24))
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter),
    ) { data ->
      Snackbar(modifier = Modifier.testTag(C.Tag.auth_error_snackbar), snackbarData = data)
    }
  }
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
  val state by viewModel.uiState.collectAsState()
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  // Resolved here so the resource-backed fallbacks are usable from the non-composable lambdas
  // below (repository events and the credential flow).
  val authFailedMessage = stringResource(R.string.authentication_failed)
  val signInFailedMessage = stringResource(R.string.sign_in_failed)

  // Repository errors arrive as one-shot events; fold them into the same Snackbar the credential
  // failures use, so the UI has a single place to show authentication problems.
  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      when (event) {
        is AuthUiEvent.ShowError ->
            snackbarHostState.showSnackbar(event.message ?: authFailedMessage)
      }
    }
  }

  AuthScreenContent(
      state = state,
      snackbarHostState = snackbarHostState,
      onSignInClick = {
        scope.launch {
          credentialProvider.getIdToken().onSuccess(viewModel::signIn).onFailure { cause ->
            snackbarHostState.showSnackbar(cause.message ?: signInFailedMessage)
          }
        }
      },
      onSignedIn = onSignedIn,
      modifier = modifier,
  )
}

/** Preview of the signed-out sign-in screen. */
@Preview(showBackground = true, backgroundColor = 0xFFEFB8C8)
@Composable
private fun AuthScreenContentPreview() {
  ArtistaTheme {
    AuthScreenContent(
        state = AuthUiState(),
        snackbarHostState = remember { SnackbarHostState() },
        onSignInClick = {},
        onSignedIn = {},
    )
  }
}
