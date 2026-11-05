package com.artista.artista.ui.authentication

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.MainDispatcherRule
import com.artista.artista.R
import com.artista.artista.model.authentication.FakeAuthRepository
import com.artista.artista.model.authentication.FakeAuthUser
import com.artista.artista.model.error.AppException
import com.artista.artista.resources.C
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Co-authored-by: DeepSeek Flash
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
/**
 * Local (Robolectric) Compose UI tests for the stateful [AuthScreen].
 *
 * Wires a real [AuthViewModel] backed by a [FakeAuthRepository] to a
 * [FakeGoogleCredentialProvider], covering the error message mapping and the credential-acquisition
 * seam. Elements are located exclusively by test tag.
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

  /** Verifies that a failure the ViewModel cannot classify is rendered in the error slot. */
  @Test
  fun signInFailureRendersTheUnknownMessage() {
    repository.signInError = RuntimeException(ERROR_MESSAGE)
    showScreen()
    composeTestRule.waitForIdle()

    viewModel.signIn(ID_TOKEN)
    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(C.Tag.auth_error_message)
        .assertIsDisplayed()
        .assertTextEquals(string(R.string.error_unknown))
  }

  /** Verifies that the error slot shows the message of the failure type, not the raw message. */
  @Test
  fun signInFailureRendersTheMessageMatchingTheFailureType() {
    repository.signInError = AppException.Network(ERROR_MESSAGE)
    showScreen()
    composeTestRule.waitForIdle()

    viewModel.signIn(ID_TOKEN)
    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithTag(C.Tag.auth_error_message)
        .assertIsDisplayed()
        .assertTextEquals(string(R.string.error_network))
  }

  /** Verifies that the message of a failed attempt is dismissed by the next attempt. */
  @Test
  fun aRetryDismissesTheMessageOfTheFailedAttempt() {
    repository.signInError = RuntimeException(ERROR_MESSAGE)
    showScreen()
    composeTestRule.waitForIdle()

    viewModel.signIn(ID_TOKEN)
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(C.Tag.auth_error_message).assertIsDisplayed()

    repository.signInError = null
    repository.user = FakeAuthUser(UID)
    viewModel.signIn(ID_TOKEN)
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(C.Tag.auth_error_message).assertDoesNotExist()
  }

  /**
   * Verifies that a new attempt reports its own outcome instead of keeping the message of the
   * previous one, which happens when the credential interaction fails before the repository is
   * reached.
   */
  @Test
  fun aCredentialFailureReplacesTheMessageOfThePreviousAttempt() {
    repository.signInError = AppException.Network(ERROR_MESSAGE)
    showScreen()
    composeTestRule.waitForIdle()

    viewModel.signIn(ID_TOKEN)
    composeTestRule.waitForIdle()
    composeTestRule
        .onNodeWithTag(C.Tag.auth_error_message)
        .assertIsDisplayed()
        .assertTextEquals(string(R.string.error_network))

    // The user retries, and this time the Google sheet fails before the repository is called.
    credentialProvider.error = RuntimeException(CREDENTIAL_ERROR_MESSAGE)
    composeTestRule.onNodeWithTag(C.Tag.auth_google_sign_in_button).performClick()
    composeTestRule.waitForIdle()

    assertEquals(1, repository.signInCallCount)
    composeTestRule
        .onNodeWithTag(C.Tag.auth_error_message)
        .assertIsDisplayed()
        .assertTextEquals(string(R.string.sign_in_failed))
  }

  /**
   * Verifies that dismissing the Google chooser reports nothing: the provider documents the
   * dismissal as an outcome of the interaction rather than a failure, so the screen stays quiet and
   * the repository is never reached.
   */
  @Test
  fun dismissingTheChooserShowsNoMessage() {
    credentialProvider.error = SignInCancelledException()
    showScreen()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(C.Tag.auth_google_sign_in_button).performClick()
    composeTestRule.waitForIdle()

    assertEquals(0, repository.signInCallCount)
    composeTestRule.onNodeWithTag(C.Tag.auth_error_message).assertDoesNotExist()
  }

  /**
   * Verifies that a device with no usable Google account shows its own message instead of the
   * generic sign-in failure, and never reaches the repository.
   */
  @Test
  fun noGoogleAccountShowsItsOwnMessage() {
    credentialProvider.error = NoGoogleAccountException()
    showScreen()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(C.Tag.auth_google_sign_in_button).performClick()
    composeTestRule.waitForIdle()

    assertEquals(0, repository.signInCallCount)
    composeTestRule
        .onNodeWithTag(C.Tag.auth_error_message)
        .assertIsDisplayed()
        .assertTextEquals(string(R.string.sign_in_no_account))
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
    composeTestRule
        .onNodeWithTag(C.Tag.auth_error_message)
        .assertIsDisplayed()
        .assertTextEquals(string(R.string.sign_in_failed))
  }

  /** Verifies that an already classified credential failure keeps its own message. */
  @Test
  fun classifiedCredentialFailureShowsItsOwnMessage() {
    credentialProvider.error = AppException.Network(CREDENTIAL_ERROR_MESSAGE)
    showScreen()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag(C.Tag.auth_google_sign_in_button).performClick()
    composeTestRule.waitForIdle()

    assertEquals(0, repository.signInCallCount)
    composeTestRule
        .onNodeWithTag(C.Tag.auth_error_message)
        .assertIsDisplayed()
        .assertTextEquals(string(R.string.error_network))
  }

  /** The text of [id] as the application under test resolves it. */
  private fun string(id: Int): String =
      ApplicationProvider.getApplicationContext<Context>().getString(id)

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
    const val UID = "user-1"
    const val ERROR_MESSAGE = "boom"
    const val ID_TOKEN = "token"
    const val GOOGLE_ID_TOKEN = "token-123"
    const val CREDENTIAL_ERROR_MESSAGE = "no credential available"
  }
}
