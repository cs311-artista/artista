package com.artista.artista.model.authentication

import com.artista.artista.model.error.AppException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

// Co-authored-by: DeepSeek Flash
/**
 * Unit tests for [toAppException], the classification that decides what the user is told when a
 * Firebase authentication call fails.
 *
 * Run on the JVM rather than against the Authentication emulator, because the mapping is pure: it
 * only inspects the exception type. Covering it here keeps the most user-visible decision in the
 * repository on the fast path, where `./gradlew check` guards it on every change.
 *
 * @author timo-by
 */
class FirebaseAuthErrorClassifierTest {

  /**
   * Verifies that a rejected credential - an expired or forged ID token - is reported as
   * [AppException.Unauthorized], which tells the user to sign in again.
   *
   * @author timo-by
   */
  @Test
  fun rejectedCredentialIsReportedAsUnauthorized() {
    val cause = FirebaseAuthInvalidCredentialsException("ERROR_INVALID_CREDENTIAL", "bad token")

    assertTranslatedTo(AppException.Unauthorized::class.java, cause)
  }

  /**
   * Verifies that an unknown or disabled account is grouped with a rejected credential, since the
   * user's only recovery is to try a different account either way.
   *
   * @author timo-by
   */
  @Test
  fun unknownAccountIsReportedAsUnauthorized() {
    val cause = FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "no such user")

    assertTranslatedTo(AppException.Unauthorized::class.java, cause)
  }

  /**
   * Verifies that Firebase reporting a network failure is reported as [AppException.Network], so
   * the user is told to check their connection rather than that authentication failed.
   *
   * @author timo-by
   */
  @Test
  fun firebaseNetworkFailureIsReportedAsNetwork() {
    assertTranslatedTo(AppException.Network::class.java, FirebaseNetworkException("offline"))
  }

  /**
   * Verifies that throttling is reported as [AppException.Unknown].
   *
   * Retrying later is the only recovery and the screen already offers another attempt, so a
   * dedicated outcome would add a string resource without adding an action.
   *
   * @author timo-by
   */
  @Test
  fun throttlingIsReportedAsUnknown() {
    assertTranslatedTo(
        AppException.Unknown::class.java,
        FirebaseTooManyRequestsException("too many requests"),
    )
  }

  /**
   * Verifies that an account already linked to another credential is reported as
   * [AppException.Unknown].
   *
   * The user signs in with Google only, so the collision cannot be resolved from the screen and a
   * dedicated outcome would describe a situation the UI cannot act on.
   *
   * @author timo-by
   */
  @Test
  fun accountCollisionIsReportedAsUnknown() {
    assertTranslatedTo(
        AppException.Unknown::class.java,
        FirebaseAuthUserCollisionException(
            "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL",
            "already linked",
        ),
    )
  }

  /**
   * Verifies that a plain IO failure - which the SDK also raises when the transport drops - is
   * reported as [AppException.Network] and not as a generic failure.
   *
   * @author timo-by
   */
  @Test
  fun ioFailureIsReportedAsNetwork() {
    assertTranslatedTo(AppException.Network::class.java, IOException("connection reset"))
  }

  /**
   * Verifies that anything the repository cannot classify becomes [AppException.Unknown] instead of
   * escaping as an SDK message.
   *
   * This is also what covers every `FirebaseAuthException` subclass that is left unnamed, so the
   * classifier needs no arm on the parent type.
   *
   * @author timo-by
   */
  @Test
  fun unclassifiedFailureIsReportedAsUnknown() {
    assertTranslatedTo(AppException.Unknown::class.java, IllegalStateException("boom"))
  }

  /**
   * Asserts that [cause] is translated to [expected].
   *
   * Compares classes rather than instances so a failure prints the type that was produced, and so
   * the cause carried by the translation does not have to be rebuilt in every test.
   *
   * @param expected the [AppException] type the classifier must produce
   * @param cause the platform failure to translate
   * @author timo-by
   */
  private fun assertTranslatedTo(expected: Class<out AppException>, cause: Throwable) {
    assertEquals(expected, cause.toAppException().javaClass)
  }
}
