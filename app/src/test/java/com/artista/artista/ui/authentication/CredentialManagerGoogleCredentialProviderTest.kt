package com.artista.artista.ui.authentication

import android.os.Bundle
import android.util.Base64
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.model.error.AppException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.util.concurrent.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

// Co-authored-by: DeepSeek Flash
/**
 * Unit tests for [CredentialManagerGoogleCredentialProvider], the class that turns a tap on the
 * 'Sign in with Google' button into a Credential Manager request.
 *
 * The Credential Manager call is substituted, so the request the screen would send is observable
 * here: which flow it asks for, and whether the system is left able to choose the account by
 * itself. Those are the decisions a wrong option makes, and they cannot be checked on a device
 * without several Google accounts.
 *
 * Only the wiring between those calls and the result is covered here; the classification of a
 * failure and the reading of a returned credential are pure and live in `CredentialMappingTest`.
 *
 * Robolectric is used only to supply a real [Bundle] for the credential payload.
 *
 * @author timo-by
 */
@RunWith(AndroidJUnit4::class)
class CredentialManagerGoogleCredentialProviderTest {

  /**
   * Verifies that the request asks Google's chooser for an ID token, which is the flow the screen
   * is documented to use.
   *
   * @author timo-by
   */
  @Test
  fun idTokenRequest_usesTheGoogleChooserFlow() = runTest {
    val request = captureRequest()

    val option = request.credentialOptions.single()

    assertTrue(option is GetSignInWithGoogleOption)
    assertEquals(SERVER_CLIENT_ID, (option as GetSignInWithGoogleOption).serverClientId)
  }

  /**
   * Verifies that the request does not fall back to the credential sheet option. That option is the
   * one whose auto-select and authorized-accounts flags decide whether the user is signed in
   * without seeing the chooser, which is the outcome this flow must never produce: signing in is
   * always a decision here, because the screen is only reachable once a session has ended.
   *
   * @author timo-by
   */
  @Test
  fun idTokenRequest_doesNotUseTheCredentialSheetOption() = runTest {
    val request = captureRequest()

    assertTrue(request.credentialOptions.none { it is GetGoogleIdOption })
  }

  /**
   * Verifies that the ID token carried by a Google credential is what the caller receives.
   *
   * Every other case here answers with a credential the provider refuses, so this is the only test
   * on the path a successful tap takes.
   *
   * @author timo-by
   */
  @Test
  fun idToken_returnsTheTokenOfAGoogleCredential() = runTest {
    val idToken = fakeIdToken()
    val provider = provider(requestCredential = { responseOf(googleIdToken(idToken)) })

    assertEquals(idToken, provider.getIdToken().getOrThrow())
  }

  /**
   * Verifies that a Google credential whose payload cannot be read is reported as
   * [AppException.InvalidData] instead of escaping as an exception: the response did arrive, but it
   * carries nothing this provider can read.
   *
   * @author timo-by
   */
  @Test
  fun idToken_reportsAnUnreadableGoogleCredentialAsInvalidData() = runTest {
    val provider = provider(requestCredential = { responseOf(unreadableGoogleIdToken()) })

    val failure = provider.getIdToken().exceptionOrNull()

    assertTrue(failure is AppException.InvalidData)
  }

  /**
   * Verifies that tearing the caller down is not reported as a provider failure, which would show a
   * Snackbar for a screen that is already gone.
   *
   * @author timo-by
   */
  @Test
  fun idToken_propagatesCancellation() = runTest {
    val provider = provider(requestCredential = { throw CancellationException(CANCELLED) })

    val thrown: CancellationException? =
        try {
          provider.getIdToken()
          null
        } catch (e: CancellationException) {
          e
        }

    assertNotNull(thrown)
  }

  /**
   * Sends one sign-in request through a provider that records it.
   *
   * @return the request the provider built
   * @author timo-by
   */
  private suspend fun captureRequest(): GetCredentialRequest {
    var captured: GetCredentialRequest? = null
    val provider =
        provider(
            requestCredential = { request ->
              captured = request
              responseOf(passkey())
            },
        )

    provider.getIdToken()

    return requireNotNull(captured)
  }

  /**
   * Builds the provider under test with a substituted Credential Manager call.
   *
   * @param requestCredential the call that answers a sign-in request
   * @return a provider bound to the test application context
   * @author timo-by
   */
  private fun provider(
      requestCredential: suspend (GetCredentialRequest) -> GetCredentialResponse = {
        responseOf(passkey())
      },
  ): CredentialManagerGoogleCredentialProvider =
      CredentialManagerGoogleCredentialProvider(
          context = ApplicationProvider.getApplicationContext(),
          serverClientId = SERVER_CLIENT_ID,
          requestCredential = requestCredential,
      )

  private companion object {
    const val SERVER_CLIENT_ID = "server-client-id.apps.googleusercontent.com"
    const val CANCELLED = "the caller is being torn down"
    const val PASSKEY_TYPE = "androidx.credentials.TYPE_PUBLIC_KEY_CREDENTIAL"
    const val TEST_GOOGLE_ID = "google-user-id"

    /**
     * Builds a credential of a type this provider does not accept, standing in for the passkey a
     * real response can carry.
     *
     * @return a custom credential that is not a Google ID token
     * @author timo-by
     */
    fun passkey(): CustomCredential = CustomCredential(type = PASSKEY_TYPE, data = Bundle())

    /**
     * Builds the credential Credential Manager returns after a successful Google sign-in.
     *
     * @param idToken the ID token the provider is expected to hand back
     * @return a Google ID token credential carrying it
     * @author timo-by
     */
    fun googleIdToken(idToken: String): CustomCredential =
        GoogleIdTokenCredential.Builder().setId(TEST_GOOGLE_ID).setIdToken(idToken).build()

    /**
     * Builds an ID token with the three segments a JWT has, which is the shape
     * [GoogleIdTokenCredential] insists on when it is built.
     *
     * @return an unsigned JWT-shaped token whose subject names the expected Google user
     * @author timo-by
     */
    fun fakeIdToken(): String =
        listOf(
                """{"alg":"RS256","typ":"JWT"}""",
                """{"sub":"$TEST_GOOGLE_ID"}""",
                "signature",
            )
            .joinToString(".") { segment ->
              Base64.encodeToString(
                  segment.toByteArray(),
                  Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
              )
            }

    /**
     * Builds a Google ID token credential whose payload is empty, so reading it fails.
     *
     * @return a credential of the right type with no readable content
     * @author timo-by
     */
    fun unreadableGoogleIdToken(): CustomCredential =
        CustomCredential(
            type = GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
            data = Bundle(),
        )

    /**
     * Wraps a credential the way Credential Manager returns it.
     *
     * @param credential the credential to return
     * @return the response carrying it
     * @author timo-by
     */
    fun responseOf(credential: CustomCredential): GetCredentialResponse =
        GetCredentialResponse(credential)
  }
}
