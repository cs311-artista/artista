// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.authentication

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.artista.artista.R
import com.artista.artista.resources.C
import com.artista.artista.ui.error.launchCatching
import com.artista.artista.ui.theme.ArtistaTheme
import com.artista.artista.ui.theme.Dimensions

// Co-authored-by: DeepSeek Flash
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
/**
 * Stateless content of the authentication screen: renders the sign-in affordance and the error
 * message, and reports user intent through callbacks.
 *
 * Carries no authentication logic; [state] is hoisted so the composable is directly testable and
 * previewable without a ViewModel or Credential Manager.
 *
 * The screen has two failure origins and therefore two slots. [state] carries the failures of the
 * repository; [credentialErrorMessageRes] carries the outcome of the Credential Manager
 * interaction, which only the UI can observe. Resolving the precedence here, in the stateless
 * content, keeps the rule testable instead of hiding it in a `copy` at the call site.
 *
 * @param state the current authentication UI state, holding the repository failure if any
 * @param credentialErrorMessageRes message of the failed credential interaction, or `null` when the
 *   last attempt failed for another reason or did not fail at all
 * @param onSignInClick invoked when the user asks to sign in
 * @param onSignedIn invoked once when [state] transitions to a signed-in user
 * @param modifier the modifier applied to the root container
 * @author timo-by
 */
@Composable
fun AuthScreenContent(
    state: AuthUiState,
    @StringRes credentialErrorMessageRes: Int?,
    onSignInClick: () -> Unit,
    onSignedIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
  // Keyed on the user so the callback fires exactly once per sign-in transition, and not again when
  // unrelated parts of the state (e.g. isLoading) change.
  LaunchedEffect(state.user) { if (state.user != null) onSignedIn() }

  // One message at a time, and a repository failure wins: it means the attempt reached the backend,
  // so it is the more specific of the two. The callers clear both slots when an attempt starts, so
  // the two can never describe different attempts.
  // Resolved through the composition so the message follows a configuration change.
  val errorMessage =
      (state.repositoryErrorMessageRes ?: credentialErrorMessageRes)?.let { stringResource(it) }

  Column(
      modifier =
          modifier
              .fillMaxSize()
              .background(MaterialTheme.colorScheme.background)
              .testTag(C.Tag.auth_screen_container)
              .padding(
                  horizontal = Dimensions.authenticationScreenHorizontalPadding,
                  vertical = Dimensions.authenticationScreenVerticalPadding,
              ),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Spacer(Modifier.height(Dimensions.authenticationHeroTopSpacing))

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

    Spacer(Modifier.height(Dimensions.authenticationHeroTitleSpacing))

    Text(
        text = stringResource(R.string.app_name),
        style = MaterialTheme.typography.displayMedium.copy(fontFamily = FontFamily.Serif),
        color = MaterialTheme.colorScheme.onBackground,
    )

    Spacer(Modifier.height(Dimensions.authenticationTitleTaglineSpacing))

    Text(
        text = stringResource(R.string.auth_tagline),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )

    Spacer(Modifier.weight(1f))

    Button(
        onClick = onSignInClick,
        enabled = !state.isLoading,
        modifier =
            Modifier.height(Dimensions.standardTouchTargetSize)
                .testTag(C.Tag.auth_google_sign_in_button),
        shape = RoundedCornerShape(percent = Dimensions.authenticationButtonCornerRadiusPercent),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                disabledContainerColor = MaterialTheme.colorScheme.surface,
                disabledContentColor = MaterialTheme.colorScheme.onSurface,
            ),
        contentPadding =
            PaddingValues(
                horizontal = Dimensions.authenticationButtonHorizontalPadding,
                vertical = Dimensions.authenticationButtonVerticalPadding,
            ),
    ) {
      if (state.isLoading) {
        CircularProgressIndicator(
            modifier =
                Modifier.size(Dimensions.iconSizeMedium).testTag(C.Tag.auth_loading_indicator),
            strokeWidth = Dimensions.standardStrokeWidth,
            color = MaterialTheme.colorScheme.onSurface,
        )
      } else {
        Icon(
            painter = painterResource(R.drawable.ic_google_logo),
            contentDescription = null,
            modifier = Modifier.size(Dimensions.iconSizeMedium),
            // Keep the brand's multi-colour logo; the default tint would flatten it to one colour.
            tint = Color.Unspecified,
        )
        Spacer(Modifier.width(Dimensions.authenticationButtonIconLabelSpacing))
        Text(text = stringResource(R.string.sign_in_with_google))
      }
    }

    // Shown under the button rather than in a transient Snackbar: the message stays until the next
    // attempt, and the button itself is the retry, so nothing has to be re-discovered after a few
    // seconds. The text comes from the failure type, never from the exception's own message - that
    // one is meant for logs and would leak backend details to the user.
    if (errorMessage != null) {
      Spacer(Modifier.height(Dimensions.authenticationErrorMessageTopSpacing))

      Text(
          text = errorMessage,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.error,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth().testTag(C.Tag.auth_error_message),
      )
    }

    Spacer(Modifier.height(Dimensions.authenticationScreenBottomSpacing))
  }
}

/**
 * Authentication screen: wires [AuthViewModel] and [GoogleCredentialProvider] to the stateless
 * [AuthScreenContent].
 *
 * Owns the credential interaction and drives the Credential Manager flow on a sign-in tap. Its
 * outcome stays here rather than in the ViewModel: Credential Manager is a platform dialog the
 * ViewModel cannot reach, classifying the outcome needs the raw cause, and a message about an
 * interaction must not outlive the composition.
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
  val scope = rememberCoroutineScope()
  // A credential interaction is observed by the screen itself, so its outcome lives here rather
  // than in the ViewModel, which cannot reach Credential Manager. Saved so that a message about the
  // last attempt survives a rotation, while still dying with the composition.
  var credentialErrorMessageRes by rememberSaveable { mutableStateOf<Int?>(null) }

  AuthScreenContent(
      state = state,
      credentialErrorMessageRes = credentialErrorMessageRes,
      onSignInClick = {
        // A new attempt makes the outcome of the previous one irrelevant on both sides, so no
        // message can describe an interaction that is not the one that just ended.
        viewModel.clearRepositoryError()
        credentialErrorMessageRes = null
        scope.launchCatching(
            tag = TAG,
            // A failure the provider returns and one it throws both end up here, so the message is
            // resolved in a single place instead of once per callback.
            onError = { credentialErrorMessageRes = it.toCredentialMessageRes() },
        ) {
          viewModel.signIn(credentialProvider.getIdToken().getOrThrow())
        }
      },
      onSignedIn = onSignedIn,
      modifier = modifier,
  )
}

/** Preview of the signed-out sign-in screen. */
@Preview(showBackground = true)
@Composable
internal fun AuthScreenContentPreview() {
  ArtistaTheme {
    AuthScreenContent(
        state = AuthUiState(),
        credentialErrorMessageRes = null,
        onSignInClick = {},
        onSignedIn = {},
    )
  }
}

/** Log tag identifying failures reported by [AuthScreen]. */
private const val TAG = "AuthScreen"
