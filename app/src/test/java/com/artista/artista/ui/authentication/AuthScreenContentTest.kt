package com.artista.artista.ui.authentication

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

// Co-authored-by: DeepSeek Flash
/**
 * Local (Robolectric) Compose UI tests for [AuthScreenContent].
 *
 * Drives the stateless content with an [AuthUiState] snapshot and lambdas, so no ViewModel and no
 * Credential Manager are involved. Elements are located exclusively by test tag.
 *
 * @author timo-by
 */
@RunWith(AndroidJUnit4::class)
class AuthScreenContentTest {

  @get:Rule val composeTestRule = createComposeRule()

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

  private fun setContent(
      state: () -> AuthUiState,
      onSignInClick: () -> Unit = {},
      onSignedIn: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      AuthScreenContent(
          state = state(),
          snackbarHostState = remember { SnackbarHostState() },
          onSignInClick = onSignInClick,
          onSignedIn = onSignedIn,
      )
    }
  }

  private companion object {
    const val USER_ID = "user-1"
  }
}
