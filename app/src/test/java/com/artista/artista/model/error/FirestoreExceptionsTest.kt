// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.model.error

import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import kotlin.reflect.KClass
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [toAppException] and [translateFirestoreErrors], the translation of Firestore
 * failures into [AppException].
 *
 * The translation is tested here rather than through `UserRepositoryFirestore` against the
 * emulator, because the emulator cannot reliably simulate a lost connection or a denied request.
 * Isolating it in [toAppException] lets every Firestore code be checked directly.
 *
 * @author Timz3rr
 */
class FirestoreExceptionsTest {

  @Test
  fun toAppException_mapsKnownCodesToMatchingAppException() {
    KNOWN_MAPPINGS.forEach { (code, expectedType) ->
      assertEquals(
          "Mapping of $code",
          expectedType,
          firestoreException(code).toAppException()::class,
      )
    }
  }

  @Test
  fun toAppException_mapsOtherCodesToUnknown() {
    // OK is excluded because Firestore refuses to build an exception with that code.
    val otherCodes = Code.values().filter { it != Code.OK && it !in KNOWN_MAPPINGS }

    otherCodes.forEach { code ->
      assertEquals(
          "Mapping of $code",
          AppException.Unknown::class,
          firestoreException(code).toAppException()::class,
      )
    }
  }

  @Test
  fun toAppException_keepsFirestoreExceptionAsCause() {
    val failure = firestoreException(Code.UNAVAILABLE)

    assertSame(failure, failure.toAppException().cause)
  }

  @Test
  fun translateFirestoreErrors_whenOperationSucceeds_returnsItsResult() = runTest {
    assertEquals("value", translateFirestoreErrors { "value" })
  }

  @Test
  fun translateFirestoreErrors_whenFirestoreFails_throwsMatchingAppException() = runTest {
    val failure = firestoreException(Code.UNAVAILABLE)

    val thrown = runCatching { translateFirestoreErrors { throw failure } }.exceptionOrNull()

    assertTrue(thrown is AppException.Network)
    assertSame(failure, thrown?.cause)
  }

  @Test
  fun translateFirestoreErrors_whenOperationThrowsAppException_rethrowsItUnchanged() = runTest {
    val failure = AppException.NotFound("Simulated missing document")

    val thrown = runCatching { translateFirestoreErrors { throw failure } }.exceptionOrNull()

    assertSame(failure, thrown)
  }

  /**
   * Creates a Firestore exception as the SDK would throw it.
   *
   * @param code the Firestore error code carried by the exception
   * @return a Firestore exception with the given code
   */
  private fun firestoreException(code: Code) =
      FirebaseFirestoreException("Simulated Firestore failure", code)

  private companion object {
    /**
     * Firestore codes with a dedicated [AppException] type; every other code is unknown.
     *
     * UNAVAILABLE and DEADLINE_EXCEEDED both mean the server could not be reached in time, so the
     * user is asked to check their connection. PERMISSION_DENIED is grouped with UNAUTHENTICATED
     * because security rules deny access when the session is missing or expired, and signing in
     * again is the only action the user can take. The remaining codes (ABORTED, INTERNAL,
     * RESOURCE_EXHAUSTED, ...) leave the user nothing to fix, so they fall back to Unknown.
     */
    val KNOWN_MAPPINGS: Map<Code, KClass<out AppException>> =
        mapOf(
            Code.UNAVAILABLE to AppException.Network::class,
            Code.DEADLINE_EXCEEDED to AppException.Network::class,
            Code.UNAUTHENTICATED to AppException.Unauthorized::class,
            Code.PERMISSION_DENIED to AppException.Unauthorized::class,
            Code.NOT_FOUND to AppException.NotFound::class,
            Code.ALREADY_EXISTS to AppException.AlreadyExists::class,
            Code.INVALID_ARGUMENT to AppException.InvalidInput::class,
            Code.DATA_LOSS to AppException.InvalidData::class,
        )
  }
}
