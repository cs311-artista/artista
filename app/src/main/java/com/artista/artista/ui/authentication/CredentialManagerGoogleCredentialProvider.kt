package com.artista.artista.ui.authentication

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.artista.artista.R
import com.artista.artista.model.authentication.AuthError
import com.artista.artista.model.authentication.AuthException
import com.artista.artista.model.authentication.runAuthCatching
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

// Co-authored-by: DeepSeek Flash
/**
 * [GoogleCredentialProvider] backed by the Credential Manager API.
 *
 * @param context context able to launch the Credential Manager UI; the hosting activity is
 *   required, because [getIdToken] shows that UI
 * @param serverClientId the web client ID of the Firebase project; Credential Manager needs it to
 *   issue an ID token that Firebase can verify
 * @param requestCredential the Credential Manager call that shows the chooser, injectable so a test
 *   can observe the request it builds and answer it without the system UI
 * @author timo-by
 */
class CredentialManagerGoogleCredentialProvider(
    private val context: Context,
    private val serverClientId: String,
    private val requestCredential: suspend (GetCredentialRequest) -> GetCredentialResponse =
        { request ->
          CredentialManager.create(context).getCredential(context, request)
        },
) : GoogleCredentialProvider {

  /**
   * Shows Google's account chooser and returns the resulting ID token.
   *
   * The request asks for the button flow rather than the credential sheet, because the screen is
   * only reachable when there is no session and who signs in next is the user's decision: signing
   * someone back in right after they tapped "Log out" is the one thing this flow must never do, and
   * filtering the list to already-authorized accounts would stop a user with several Google
   * accounts from switching.
   *
   * The token's authenticity is verified by Firebase Authentication on exchange, not here.
   *
   * @return success with the Google ID token, or failure carrying an [AuthException]
   * @author timo-by
   */
  override suspend fun getIdToken(): Result<String> =
      runAuthCatching(Throwable::toCredentialAuthError) {
        val request =
            GetCredentialRequest.Builder()
                .addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId).build())
                .build()

        // Reading the credential yields its own classified failure, so it is unwrapped here: the
        // wrapper in [runAuthCatching] passes that outcome through instead of nesting one Result in
        // another.
        requestCredential(request).credential.toGoogleIdToken().getOrThrow()
      }
}

/**
 * Reads the Google ID token out of the credential Credential Manager returned.
 *
 * Passkeys can appear in the same response, so the type is checked before parsing:
 * [GoogleIdTokenCredential.createFrom] would otherwise throw on a payload that is not an ID token.
 *
 * @return success with the ID token, or failure carrying an [AuthException]
 * @author timo-by
 */
internal fun Credential.toGoogleIdToken(): Result<String> {
  // The type is only readable on a custom credential, so both halves are checked before parsing.
  if (
      this !is CustomCredential || type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
  ) {
    return Result.failure(AuthException(AuthError.InvalidCredentials))
  }
  return try {
    Result.success(GoogleIdTokenCredential.createFrom(data).idToken)
  } catch (e: GoogleIdTokenParsingException) {
    Result.failure(AuthException(AuthError.Unknown, e))
  }
}

/**
 * Classifies a failure raised by the Credential Manager flow.
 *
 * The two outcomes a user can act on extend the same parent, so each is matched by its own type: an
 * arm on the parent would collapse them into [AuthError.Unknown]. Every other failure, a transport
 * one included, falls into the generic outcome because the user has no different action to take.
 *
 * @return the domain outcome matching this failure
 * @author timo-by
 */
internal fun Throwable.toCredentialAuthError(): AuthError =
    when (this) {
      is GetCredentialCancellationException -> AuthError.Cancelled
      is NoCredentialException -> AuthError.NoGoogleAccount
      else -> AuthError.Unknown
    }

/**
 * Creates the Credential Manager backed [GoogleCredentialProvider] for the current composition.
 *
 * Reads the server client ID from the resource the Google Services plugin generates out of
 * `google-services.json`, so no client ID is hardcoded in the sources.
 *
 * @return a provider bound to the current activity context and Firebase project
 * @author timo-by
 */
@Composable
fun rememberGoogleCredentialProvider(): GoogleCredentialProvider {
  val context = LocalContext.current
  val serverClientId = stringResource(R.string.default_web_client_id)
  return remember(context, serverClientId) {
    CredentialManagerGoogleCredentialProvider(context = context, serverClientId = serverClientId)
  }
}
