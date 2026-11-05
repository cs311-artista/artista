// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: DeepSeek Flash
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.authentication

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.R
import com.artista.artista.model.authentication.FakeAuthUser
import com.artista.artista.resources.C
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Local (Robolectric) Compose UI tests for [AuthScreenContent].
 *
 * Drives the stateless content with an [AuthUiState] snapshot, a credential outcome and lambdas, so
 * no ViewModel and no Credential Manager are involved. Elements are located exclusively by test
 * tag.
 *
 * @author timo-by
 */
@RunWith(AndroidJUnit4::class)
class AuthScreenContentTest {

  @get:Rule val composeTestRule = createComposeRule()

  /** Verifies that the sign-in preview renders its primary action. */
  @Test
  fun authScreenPreview_displaysSignInAction() {
    composeTestRule.setContent { AuthScreenContentPreview() }

    composeTestRule.onNodeWithTag(C.Tag.auth_google_sign_in_button).assertIsDisplayed()
  }

  /**
   * Verifies that a signed-out state renders an enabled sign-in button and no progress indicator.
   */
  @Test
  fun signedOutShowsEnabledSignInButton() {
    setContent(state = { AuthUiState(isLoading = false) })

    composeTestRule
        .onNodeWithTag(C.Tag.auth_google_sign_in_button)
        .assertIsDisplayed()
        .assertIsEnabled()
        .assertTextEquals(signInLabel)
    composeTestRule.onNodeWithTag(C.Tag.auth_loading_indicator).assertDoesNotExist()
  }

  /** Verifies that a loading state shows the progress indicator and disables the button. */
  @Test
  fun loadingShowsIndicatorAndDisablesButton() {
    setContent(state = { AuthUiState(isLoading = true) })

    composeTestRule.onNodeWithTag(C.Tag.auth_loading_indicator).assertIsDisplayed()
    composeTestRule.onNodeWithTag(C.Tag.auth_google_sign_in_button).assertIsNotEnabled()
  }

  /** Verifies that a repository failure renders its message under the sign-in button. */
  @Test
  fun repositoryErrorStateShowsTheMessage() {
    setContent(state = { AuthUiState(repositoryErrorMessageRes = R.string.error_network) })

    composeTestRule
        .onNodeWithTag(C.Tag.auth_error_message)
        .assertIsDisplayed()
        .assertTextEquals(networkErrorMessage)
  }

  /** Verifies that the outcome of a failed credential interaction is rendered in the same slot. */
  @Test
  fun credentialErrorStateShowsTheMessage() {
    setContent(state = { AuthUiState() }, credentialErrorMessageRes = R.string.sign_in_failed)

    composeTestRule
        .onNodeWithTag(C.Tag.auth_error_message)
        .assertIsDisplayed()
        .assertTextEquals(signInFailedMessage)
  }

  /** Verifies that a state without any failure renders no message at all. */
  @Test
  fun noErrorHidesTheMessage() {
    setContent(state = { AuthUiState() })

    composeTestRule.onNodeWithTag(C.Tag.auth_error_message).assertDoesNotExist()
  }

  /**
   * Verifies that a repository failure is preferred when both slots are set: only one message can
   * be shown, and the attempt that reached the backend is the one that just ended.
   */
  @Test
  fun repositoryErrorIsPreferredOverTheCredentialOutcome() {
    setContent(
        state = { AuthUiState(repositoryErrorMessageRes = R.string.error_network) },
        credentialErrorMessageRes = R.string.sign_in_failed,
    )

    composeTestRule
        .onNodeWithTag(C.Tag.auth_error_message)
        .assertIsDisplayed()
        .assertTextEquals(networkErrorMessage)
  }

  /** Verifies that tapping the button while signed out invokes the click callback exactly once. */
  @Test
  fun tapInvokesOnSignInClickOnce() {
    var clicks = 0
    setContent(state = { AuthUiState(isLoading = false) }, onSignInClick = { clicks++ })

    composeTestRule.onNodeWithTag(C.Tag.auth_google_sign_in_button).performClick()
    composeTestRule.waitForIdle()

    assertEquals(1, clicks)
  }

  /** Verifies that tapping the disabled button while loading does not invoke the click callback. */
  @Test
  fun tapWhileLoadingIsIgnored() {
    var clicks = 0
    setContent(state = { AuthUiState(isLoading = true) }, onSignInClick = { clicks++ })

    composeTestRule.onNodeWithTag(C.Tag.auth_google_sign_in_button).performClick()
    composeTestRule.waitForIdle()

    assertEquals(0, clicks)
  }

  /**
   * Verifies that the content invokes the hoisted signed-in callback exactly once when the state
   * transitions to a signed-in user, and not again on unrelated recompositions.
   */
  @Test
  fun signedInInvokesOnSignedInOnce() {
    var signedInCount = 0
    var uiState by mutableStateOf(AuthUiState(user = null))
    setContent(state = { uiState }, onSignedIn = { signedInCount++ })

    composeTestRule.waitForIdle()
    assertEquals(0, signedInCount)

    uiState = AuthUiState(user = FakeAuthUser(uid = USER_ID))
    composeTestRule.waitForIdle()
    assertEquals(1, signedInCount)

    // Recomposition driven by an unrelated field must not invoke the callback a second time.
    uiState = AuthUiState(user = FakeAuthUser(uid = USER_ID), isLoading = true)
    composeTestRule.waitForIdle()
    uiState = AuthUiState(user = FakeAuthUser(uid = USER_ID), isLoading = false)
    composeTestRule.waitForIdle()
    assertEquals(1, signedInCount)
  }

  /** The sign-in label rendered by the button, read from the shared string resource. */
  private val signInLabel: String
    get() =
        ApplicationProvider.getApplicationContext<Context>().getString(R.string.sign_in_with_google)

  /** The message of a repository failure, read from the shared string resource. */
  private val networkErrorMessage: String
    get() = ApplicationProvider.getApplicationContext<Context>().getString(R.string.error_network)

  /** The message of a failed credential interaction, read from the shared string resource. */
  private val signInFailedMessage: String
    get() = ApplicationProvider.getApplicationContext<Context>().getString(R.string.sign_in_failed)

  private fun setContent(
      state: () -> AuthUiState,
      credentialErrorMessageRes: Int? = null,
      onSignInClick: () -> Unit = {},
      onSignedIn: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      AuthScreenContent(
          state = state(),
          credentialErrorMessageRes = credentialErrorMessageRes,
          onSignInClick = onSignInClick,
          onSignedIn = onSignedIn,
      )
    }
  }

  private companion object {
    const val USER_ID = "user-1"
  }
}
