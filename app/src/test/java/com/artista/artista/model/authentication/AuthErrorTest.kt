package com.artista.artista.model.authentication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

// Co-authored-by: DeepSeek Flash
/**
 * Unit tests for [AuthException] and [toAuthError], the pair the UI uses to turn a failed [Result]
 * back into the domain outcome it presents.
 *
 * Both authentication paths - the repository and the credential provider - report their failures
 * through [Result], so this is the one conversion every caller performs. It is pinned here because
 * a fallback that let an [AuthError.Unknown] through for a classified failure would silently turn a
 * specific message into a generic one.
 *
 * @author timo-by
 */
class AuthErrorTest {

  /**
   * Verifies that a failure carrying an [AuthException] is read back as the outcome it was created
   * with, which is what lets the UI branch on the reason instead of on a message.
   *
   * @author timo-by
   */
  @Test
  fun aClassifiedFailureIsReadBackAsItsOutcome() {
    val failure: Throwable = AuthException(AuthError.NoGoogleAccount)

    assertEquals(AuthError.NoGoogleAccount, failure.toAuthError())
  }

  /**
   * Verifies that a failure carrying no [AuthException] is reported as [AuthError.Unknown] rather
   * than escaping as a null or as the raw exception, since a caller has no other way to describe
   * it.
   *
   * @author timo-by
   */
  @Test
  fun anUnclassifiedFailureIsReportedAsUnknown() {
    assertEquals(AuthError.Unknown, IllegalStateException("boom").toAuthError())
  }

  /**
   * Verifies that the platform exception a failure was classified from is kept as its cause, so a
   * crash report still says what actually went wrong on the device.
   *
   * @author timo-by
   */
  @Test
  fun aClassifiedFailureKeepsThePlatformCause() {
    val cause = IllegalStateException("boom")

    val failure = AuthException(AuthError.Unknown, cause)

    assertSame(cause, failure.cause)
  }
}
