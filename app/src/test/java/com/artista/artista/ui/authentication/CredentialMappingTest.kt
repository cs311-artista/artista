package com.artista.artista.ui.authentication

import android.os.Bundle
import androidx.credentials.CustomCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.model.authentication.AuthError
import com.artista.artista.model.authentication.AuthException
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

// Co-authored-by: DeepSeek Flash
/**
 * Unit tests for the two pure seams of [CredentialManagerGoogleCredentialProvider]: how a
 * Credential Manager failure is classified, and how a returned credential is turned into an ID
 * token.
 *
 * Both functions are pure, so they are called directly. The provider's own test injects the two
 * Credential Manager calls and covers the wiring around them; what it cannot see is whether the
 * wiring classifies or parses correctly, which is what these cases pin.
 *
 * Robolectric is used only to supply a real [Bundle] for the credential payload.
 *
 * @author timo-by
 */
@RunWith(AndroidJUnit4::class)
class CredentialMappingTest {

  /**
   * Verifies that dismissing the Google sheet is reported as [AuthError.Cancelled] and not as a
   * generic failure, so the screen can stay quiet instead of alarming the user.
   *
   * @author timo-by
   */
  @Test
  fun cancellationIsReportedAsCancelled() {
    assertEquals(AuthError.Cancelled, GetCredentialCancellationException().toCredentialAuthError())
  }

  /**
   * Verifies that a device without a usable Google account is reported as
   * [AuthError.NoGoogleAccount], which is the only outcome the user can act on.
   *
   * @author timo-by
   */
  @Test
  fun noAccountIsReportedAsNoGoogleAccount() {
    assertEquals(AuthError.NoGoogleAccount, NoCredentialException().toCredentialAuthError())
  }

  /**
   * Verifies that a failure the provider cannot classify is reported as [AuthError.Unknown] rather
   * than escaping as an SDK message.
   *
   * @author timo-by
   */
  @Test
  fun anUnforeseenFailureIsReportedAsUnknown() {
    assertEquals(AuthError.Unknown, IllegalStateException("boom").toCredentialAuthError())
  }

  /**
   * Verifies that a credential which is not a Google ID token - a passkey, for example, since
   * Credential Manager returns both - is reported as [AuthError.InvalidCredentials] and does not
   * reach the parsing step, which would throw on a foreign payload.
   *
   * @author timo-by
   */
  @Test
  fun aNonGoogleCredentialIsReportedAsInvalidCredentials() {
    val passkey =
        CustomCredential(
            type = "androidx.credentials.TYPE_PUBLIC_KEY_CREDENTIAL",
            data = Bundle(),
        )

    val error = (passkey.toGoogleIdToken().exceptionOrNull() as AuthException).error

    assertEquals(AuthError.InvalidCredentials, error)
  }
}
