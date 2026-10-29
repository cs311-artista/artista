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
import com.artista.artista.model.authentication.runAuthCatching
import com.artista.artista.model.error.AppException
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
   * The request asks for the button flow rather than for the credential sheet, because the screen
   * that calls it is only reachable when there is no session: either the user signed out, or the
   * session was revoked. Who signs in next is therefore a decision, and the request must not make
   * it on the user's behalf:
   *
   * - No automatic selection is requested. Signing a user back in right after they tapped "Log out"
   *   is the one thing this flow must never do, and auto-select would also depend on device state
   *   (a single eligible account, no pending consent) that is not ours to inspect.
   * - The account list is not filtered to accounts already authorized for this application. Signing
   *   out is how a user with several Google accounts gets to switch, so every account has to be
   *   offered rather than only the one that was used before.
   *
   * The authenticity of the token is verified by Firebase Authentication when it is exchanged for a
   * session, not here.
   *
   * @return success with the Google ID token, or the classified failure documented on
   *   [GoogleCredentialProvider.getIdToken]
   * @author timo-by
   */
  override suspend fun getIdToken(): Result<String> =
      runAuthCatching(Throwable::toSignInFailure) {
        val request =
            GetCredentialRequest.Builder()
                .addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId).build())
                .build()

        requestCredential(request).credential.toGoogleIdToken()
      }
}

/**
 * Reads the Google ID token out of the credential Credential Manager returned.
 *
 * Passkeys and other credential types can appear in the same response, so the type is checked
 * before parsing: [GoogleIdTokenCredential.createFrom] would otherwise throw on a payload that is
 * not an ID token.
 *
 * @return the Google ID token this credential carries
 * @throws AppException.InvalidData if the credential is not a readable Google ID token
 * @author timo-by
 */
internal fun Credential.toGoogleIdToken(): String {
  // The type is only readable on a custom credential, so both halves are checked before parsing.
  if (
      this !is CustomCredential || type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
  ) {
    throw AppException.InvalidData("Credential Manager returned something other than an ID token")
  }
  return try {
    GoogleIdTokenCredential.createFrom(data).idToken
  } catch (e: GoogleIdTokenParsingException) {
    throw AppException.InvalidData(e.message, e)
  }
}

/**
 * Translates a Credential Manager failure into what the caller is given.
 *
 * The two outcomes that describe the interaction rather than a failure get their own types, so a
 * screen can stay quiet instead of reporting an error for a deliberate action. Everything else is
 * an [AppException] - a transport failure included, because Credential Manager reports it as a
 * generic unknown or interrupted exception that cannot be told apart, so it surfaces as
 * [AppException.Unknown] rather than as [AppException.Network], which is the repository's outcome
 * for a failure it can name.
 *
 * @return the failure the caller is given for this exception
 * @author timo-by
 */
internal fun Throwable.toSignInFailure(): Throwable =
    when (this) {
      is GetCredentialCancellationException -> SignInCancelledException()
      is NoCredentialException -> NoGoogleAccountException()
      else -> AppException.Unknown(message, this)
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
