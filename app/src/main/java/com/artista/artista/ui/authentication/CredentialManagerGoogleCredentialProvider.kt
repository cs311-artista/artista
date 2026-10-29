package com.artista.artista.ui.authentication

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.credentials.ClearCredentialStateRequest
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
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

// Co-authored-by: DeepSeek Flash
/**
 * [GoogleCredentialProvider] backed by the Credential Manager API.
 *
 * @param context context able to launch the Credential Manager UI. The hosting activity is required
 *   for [getIdToken], which shows that UI, and the provider is bound to this context for its whole
 *   lifetime. An instance kept by a ViewModel is given the application context instead, so it may
 *   only be used for [clearSession], which starts no UI.
 * @param serverClientId the web client ID of the Firebase project; Credential Manager needs it to
 *   issue an ID token that Firebase can verify
 * @param requestCredential the Credential Manager call that shows the chooser, injectable so a test
 *   can observe the request it builds and answer it without the system UI
 * @param clearProviderState the Credential Manager call that forgets this application's session,
 *   injectable for the same reason
 * @author timo-by
 */
class CredentialManagerGoogleCredentialProvider(
    private val context: Context,
    private val serverClientId: String,
    private val requestCredential: suspend (GetCredentialRequest) -> GetCredentialResponse =
        { request ->
          CredentialManager.create(context).getCredential(context, request)
        },
    private val clearProviderState: suspend () -> Unit = {
      CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
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
   * A fresh nonce is hashed into every request so the resulting ID token is bound to this sign-in
   * attempt. It only becomes replay protection once something verifies it, which needs a backend
   * that compares it with the token's claim; today it is defence in depth, not an enforced check.
   * The authenticity of the token itself is verified by Firebase Authentication when the token is
   * exchanged for a session, not here.
   *
   * @return success with the Google ID token, or failure carrying an [AuthException]
   * @author timo-by
   */
  override suspend fun getIdToken(): Result<String> =
      runAuthCatching(Throwable::toCredentialAuthError) {
        val request =
            GetCredentialRequest.Builder()
                .addCredentialOption(
                    GetSignInWithGoogleOption.Builder(serverClientId)
                        .setNonce(hashedNonce(UUID.randomUUID().toString()))
                        .build(),
                )
                .build()

        // Reading the credential yields its own classified failure, so it is unwrapped here: the
        // wrapper in [runAuthCatching] passes that outcome through instead of nesting one Result in
        // another.
        requestCredential(request).credential.toGoogleIdToken().getOrThrow()
      }

  /**
   * Clears the session Credential Manager keeps for this application.
   *
   * @return success, or failure carrying an [AuthException]; the caller decides whether a failure
   *   is worth reporting, since the Firebase session is already gone by this point
   * @author timo-by
   */
  override suspend fun clearSession(): Result<Unit> =
      runAuthCatching(Throwable::toCredentialAuthError) { clearProviderState() }
}

/**
 * Reads the Google ID token out of the credential Credential Manager returned.
 *
 * Passkeys and custom credentials can appear in the same response, so the type is checked before
 * parsing: [GoogleIdTokenCredential.createFrom] would otherwise throw on a payload that is not an
 * ID token.
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
 * Both outcomes below extend `GetCredentialException`, so each is matched by its own type rather
 * than by the shared parent: an arm on the parent would collapse them into [AuthError.Unknown] and
 * the user would lose the reason. Every other Credential Manager failure - and anything
 * unforeseen - falls into the generic outcome, because the user has no different action to take for
 * it. A transport failure is one of them: Credential Manager reports it as a generic
 * `GetCredentialUnknownException` or `GetCredentialInterruptedException`, so it surfaces as
 * [AuthError.Unknown] rather than [AuthError.Network], which is the repository's outcome.
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
 * Hashes a raw nonce the way Credential Manager expects it.
 *
 * The raw value never leaves the device: only its SHA-256 digest is sent, and a verifier holding
 * the raw value can confirm the digest matches the token's nonce claim.
 *
 * @param rawNonce the unguessable value generated for this sign-in attempt
 * @return the lowercase hexadecimal SHA-256 digest of [rawNonce]
 * @author timo-by
 */
internal fun hashedNonce(rawNonce: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(rawNonce.toByteArray(StandardCharsets.UTF_8))
        // A Byte formats as its signed value, so it is masked back to the unsigned octet the digest
        // is made of.
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

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
