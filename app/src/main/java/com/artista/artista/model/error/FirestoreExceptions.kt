// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.model.error

import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code

/**
 * Translates a Firestore failure into the matching [AppException], or into [AppException.Unknown]
 * when no type matches.
 *
 * The original exception is kept as the cause so logs still show the Firestore code and message.
 *
 * @return the [AppException] matching this exception's Firestore code
 * @author Timz3rr
 */
internal fun FirebaseFirestoreException.toAppException(): AppException =
    when (code) {
      // Both mean the server could not be reached in time, so the user is asked to check their
      // connection.
      Code.UNAVAILABLE,
      Code.DEADLINE_EXCEEDED -> AppException.Network(message, this)
      // Security rules deny access when the session is missing or expired, so PERMISSION_DENIED is
      // grouped with UNAUTHENTICATED: signing in again is the only action the user can take.
      Code.UNAUTHENTICATED,
      Code.PERMISSION_DENIED -> AppException.Unauthorized(message, this)
      Code.NOT_FOUND -> AppException.NotFound(message, this)
      Code.ALREADY_EXISTS -> AppException.AlreadyExists(message, this)
      Code.INVALID_ARGUMENT -> AppException.InvalidInput(message, this)
      Code.DATA_LOSS -> AppException.InvalidData(message, this)
      // The remaining codes (ABORTED, INTERNAL, RESOURCE_EXHAUSTED, ...) leave the user nothing to
      // fix, so they fall back to Unknown.
      else -> AppException.Unknown(message, this)
    }

/**
 * Runs a Firestore operation and translates its Firestore failures into [AppException].
 *
 * If [operation] succeeds, its result is returned as is. If Firestore throws while it runs, the
 * failure is translated with [toAppException] and the resulting [AppException] is thrown instead.
 *
 * It exists so that repositories do not repeat the same try/catch in every method: wrapping a
 * method body is enough, e.g. `override suspend fun getUser(userId: String): User =
 * translateFirestoreErrors { ... }`. Every Firestore repository can reuse it.
 *
 * Only [FirebaseFirestoreException] is translated: an [AppException] thrown by the operation itself
 * (e.g. a missing document detected by the repository) is rethrown unchanged.
 *
 * @param operation the Firestore work to run
 * @return the result of [operation]
 * @throws AppException if [operation] fails
 * @author Timz3rr
 */
internal suspend fun <T> translateFirestoreErrors(operation: suspend () -> T): T =
    try {
      operation()
    } catch (exception: FirebaseFirestoreException) {
      throw exception.toAppException()
    }
