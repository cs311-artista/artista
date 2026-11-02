// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.authentication

import com.artista.artista.R
import com.artista.artista.model.error.AppException
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [toCredentialMessageRes], the message shown when the Credential Manager
 * interaction produces no token.
 *
 * The mapping is tested here rather than through the screen because Credential Manager cannot be
 * driven without the platform dialog; isolating it in a plain function lets every outcome be
 * checked directly.
 *
 * @author timo-by
 */
class CredentialMessagesTest {

  /**
   * Verifies that an interaction outcome - the sheet dismissed, no credential available - reads as
   * a failed sign-in instead of the generic unknown message.
   */
  @Test
  fun unclassifiedFailureBecomesSignInFailed() {
    assertEquals(
        R.string.sign_in_failed,
        AppException.Unknown("The user cancelled the sign-in").toCredentialMessageRes(),
    )
  }

  /** Verifies that a failure the provider already classified keeps its own message. */
  @Test
  fun classifiedFailureKeepsItsOwnMessage() {
    assertEquals(R.string.error_network, AppException.Network("offline").toCredentialMessageRes())
    assertEquals(R.string.error_unauthorized, AppException.Unauthorized().toCredentialMessageRes())
  }
}
