package com.artista.artista.model.authentication

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
 * Unit tests for [toFirebaseAuthError], the classification that decides what the user is told when
 * a Firebase authentication call fails.
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
   * [AuthError.InvalidCredentials], which the screen describes as "Google rejected the sign-in".
   *
   * @author timo-by
   */
  @Test
  fun rejectedCredentialIsReportedAsInvalidCredentials() {
    val cause = FirebaseAuthInvalidCredentialsException("ERROR_INVALID_CREDENTIAL", "bad token")

    assertEquals(AuthError.InvalidCredentials, cause.toFirebaseAuthError())
  }

  /**
   * Verifies that an unknown or disabled account is grouped with a rejected credential, since the
   * user's only recovery is to try a different account either way.
   *
   * @author timo-by
   */
  @Test
  fun unknownAccountIsReportedAsInvalidCredentials() {
    val cause = FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "no such user")

    assertEquals(AuthError.InvalidCredentials, cause.toFirebaseAuthError())
  }

  /**
   * Verifies that Firebase reporting a network failure is reported as [AuthError.Network], so the
   * user is told to check their connection rather than that authentication failed.
   *
   * @author timo-by
   */
  @Test
  fun firebaseNetworkFailureIsReportedAsNetwork() {
    assertEquals(AuthError.Network, FirebaseNetworkException("offline").toFirebaseAuthError())
  }

  /**
   * Verifies that throttling is reported as [AuthError.Unknown].
   *
   * Retrying later is the only recovery and the screen already offers another attempt, so a
   * dedicated outcome would add a string resource without adding an action.
   *
   * @author timo-by
   */
  @Test
  fun throttlingIsReportedAsUnknown() {
    val cause = FirebaseTooManyRequestsException("too many requests")

    assertEquals(AuthError.Unknown, cause.toFirebaseAuthError())
  }

  /**
   * Verifies that an account already linked to another credential is reported as
   * [AuthError.Unknown].
   *
   * The user signs in with Google only, so the collision cannot be resolved from the screen and a
   * dedicated outcome would describe a situation the UI cannot act on.
   *
   * @author timo-by
   */
  @Test
  fun accountCollisionIsReportedAsUnknown() {
    val cause =
        FirebaseAuthUserCollisionException(
            "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL",
            "already linked",
        )

    assertEquals(AuthError.Unknown, cause.toFirebaseAuthError())
  }

  /**
   * Verifies that a plain IO failure - which the SDK also raises when the transport drops - is
   * reported as [AuthError.Network] and not as a generic failure.
   *
   * @author timo-by
   */
  @Test
  fun ioFailureIsReportedAsNetwork() {
    assertEquals(AuthError.Network, IOException("connection reset").toFirebaseAuthError())
  }

  /**
   * Verifies that anything the repository cannot classify becomes [AuthError.Unknown] instead of
   * escaping as an SDK message.
   *
   * This is also what covers every `FirebaseAuthException` subclass that is left unnamed, so the
   * classifier needs no arm on the parent type.
   *
   * @author timo-by
   */
  @Test
  fun unclassifiedFailureIsReportedAsUnknown() {
    assertEquals(AuthError.Unknown, IllegalStateException("boom").toFirebaseAuthError())
  }
}
