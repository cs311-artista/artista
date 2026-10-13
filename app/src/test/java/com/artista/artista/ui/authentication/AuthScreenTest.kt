package com.artista.artista.ui.authentication

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.MainDispatcherRule
import com.artista.artista.model.authentication.FakeAuthRepository
import com.artista.artista.resources.C
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Co-authored-by: DeepSeek Flash
/**
 * Local (Robolectric) Compose UI tests for the stateful [AuthScreen].
 *
 * Wires a real [AuthViewModel] backed by a [FakeAuthRepository] to a
 * [FakeGoogleCredentialProvider], covering the event → Snackbar mapping and the
 * credential-acquisition seam. Elements are located exclusively by test tag.
 *
 * @author timo-by
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AuthScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  // The ViewModel launches on viewModelScope (Dispatchers.Main). Installing an unconfined test
  // dispatcher makes its coroutines run eagerly, so the assertions observe the resulting state
  // deterministically instead of relying on the real main looper being idled by waitForIdle().
  @get:Rule val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

  private lateinit var repository: FakeAuthRepository
  private lateinit var viewModel: AuthViewModel
  private lateinit var credentialProvider: FakeGoogleCredentialProvider

  @Before
  fun setUp() {
    repository = FakeAuthRepository()
    viewModel = AuthViewModel(repository)
    credentialProvider = FakeGoogleCredentialProvider()
  }

  /** Verifies that a ViewModel error event is rendered in the tagged error Snackbar. */
  @Test
  fun showErrorRendersSnackbar() {
    repository.signInError = RuntimeException(ERROR_MESSAGE)
    showScreen()
    composeTestRule.waitForIdle()

    viewModel.signIn(ID_TOKEN)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(C.Tag.auth_error_snackbar).assertIsDisplayed()
    composeTestRule.onNodeWithText(ERROR_MESSAGE).assertIsDisplayed()
  }

  /** Verifies that a successful credential acquisition forwards the token to the ViewModel. */
  @Test
  fun credentialSuccessPassesTokenToSignIn() {
    credentialProvider.idToken = GOOGLE_ID_TOKEN
    showScreen()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(C.Tag.auth_google_sign_in_button).performClick()
    composeTestRule.waitForIdle()

    assertEquals(1, credentialProvider.callCount)
    assertEquals(1, repository.signInCallCount)
    assertEquals(GOOGLE_ID_TOKEN, repository.lastIdToken)
  }

  /** Verifies that a credential failure surfaces a message and never calls signIn. */
  @Test
  fun credentialFailureShowsMessageAndSkipsSignIn() {
    credentialProvider.error = RuntimeException(CREDENTIAL_ERROR_MESSAGE)
    showScreen()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(C.Tag.auth_google_sign_in_button).performClick()
    composeTestRule.waitForIdle()

    assertEquals(0, repository.signInCallCount)
    composeTestRule.onNodeWithTag(C.Tag.auth_error_snackbar).assertIsDisplayed()
  }

  private fun showScreen() {
    composeTestRule.setContent {
      AuthScreen(
          viewModel = viewModel,
          credentialProvider = credentialProvider,
          onSignedIn = {},
      )
    }
  }

  private companion object {
    const val ERROR_MESSAGE = "boom"
    const val ID_TOKEN = "token"
    const val GOOGLE_ID_TOKEN = "token-123"
    const val CREDENTIAL_ERROR_MESSAGE = "no credential available"
  }
}
