package com.artista.artista.ui.authentication

import android.os.Bundle
import androidx.credentials.CustomCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.model.error.AppException
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

// Co-authored-by: DeepSeek Flash
/**
 * Unit tests for the two pure seams of [CredentialManagerGoogleCredentialProvider]: how a
 * Credential Manager failure is translated, and how a returned credential is turned into an ID
 * token.
 *
 * Both functions are pure, so they are called directly. The provider's own test injects the two
 * Credential Manager calls and covers the wiring around them; what it cannot see is whether the
 * wiring translates or parses correctly, which is what these cases pin.
 *
 * Robolectric is used only to supply a real [Bundle] for the credential payload.
 *
 * @author timo-by
 */
@RunWith(AndroidJUnit4::class)
class CredentialMappingTest {

  /**
   * Verifies that dismissing the chooser is reported as [SignInCancelledException] and not as an
   * [AppException], so the screen can stay quiet instead of alarming the user for a decision they
   * made on purpose.
   *
   * @author timo-by
   */
  @Test
  fun cancellationIsReportedAsSignInCancelled() {
    assertTrue(GetCredentialCancellationException().toSignInFailure() is SignInCancelledException)
  }

  /**
   * Verifies that a device without a usable Google account is reported as
   * [NoGoogleAccountException] rather than as a generic failure, since it is the one outcome the
   * user can act on.
   *
   * @author timo-by
   */
  @Test
  fun noAccountIsReportedAsNoGoogleAccount() {
    assertTrue(NoCredentialException().toSignInFailure() is NoGoogleAccountException)
  }

  /**
   * Verifies that a failure the provider cannot classify is reported as [AppException.Unknown]
   * rather than escaping as an SDK message.
   *
   * @author timo-by
   */
  @Test
  fun anUnforeseenFailureIsReportedAsUnknown() {
    assertTrue(IllegalStateException("boom").toSignInFailure() is AppException.Unknown)
  }

  /**
   * Verifies that a credential which is not a Google ID token - a passkey, for example, since
   * Credential Manager returns both - is reported as [AppException.InvalidData] and never reaches
   * the parsing step, which would throw on a foreign payload.
   *
   * @author timo-by
   */
  @Test
  fun aNonGoogleCredentialIsReportedAsInvalidData() {
    val passkey =
        CustomCredential(
            type = "androidx.credentials.TYPE_PUBLIC_KEY_CREDENTIAL",
            data = Bundle(),
        )

    val thrown: AppException.InvalidData? =
        try {
          passkey.toGoogleIdToken()
          null
        } catch (e: AppException.InvalidData) {
          e
        }

    assertTrue(thrown != null)
  }
}
