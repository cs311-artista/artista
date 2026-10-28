// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.model.error

/**
 * Failure reported by a repository, whatever backend the data comes from.
 *
 * Every repository translates its own failures (Firestore, HTTP, in-memory data, ...) into one of
 * these types before they leave the `model` layer. ViewModels can then react to what went wrong
 * without importing the backend that produced the error, and switching a repository from mock data
 * to a real backend does not change what its callers have to handle.
 *
 * @param message details about the failure, meant for logs and never shown to the user, if any
 * @param cause the original failure, if any, kept so logs show where it came from
 * @author Timz3rr
 */
sealed class AppException(message: String? = null, cause: Throwable? = null) :
    Exception(message, cause) {

  /**
   * The requested element does not exist.
   *
   * @param message details about the failure, meant for logs, if any
   * @param cause the original failure, if any
   * @author Timz3rr
   */
  class NotFound(message: String? = null, cause: Throwable? = null) : AppException(message, cause)

  /**
   * The element cannot be created because one with the same identifier already exists.
   *
   * @param message details about the failure, meant for logs, if any
   * @param cause the original failure, if any
   * @author Timz3rr
   */
  class AlreadyExists(message: String? = null, cause: Throwable? = null) :
      AppException(message, cause)

  /**
   * The request was rejected because one of its arguments is invalid (e.g. a blank identifier).
   *
   * @param message details about the failure, meant for logs, if any
   * @param cause the original failure, if any
   * @author Timz3rr
   */
  class InvalidInput(message: String? = null, cause: Throwable? = null) :
      AppException(message, cause)

  /**
   * The backend could not be reached in time (no connection, timeout, server unavailable).
   *
   * @param message details about the failure, meant for logs, if any
   * @param cause the original failure, if any
   * @author Timz3rr
   */
  class Network(message: String? = null, cause: Throwable? = null) : AppException(message, cause)

  /**
   * The backend refused the request because the session is missing, expired, or not allowed.
   *
   * @param message details about the failure, meant for logs, if any
   * @param cause the original failure, if any
   * @author Timz3rr
   */
  class Unauthorized(message: String? = null, cause: Throwable? = null) :
      AppException(message, cause)

  /**
   * The backend returned data that cannot be read (missing fields, wrong types, corrupted values).
   *
   * @param message details about the failure, meant for logs, if any
   * @param cause the original failure, if any
   * @author Timz3rr
   */
  class InvalidData(message: String? = null, cause: Throwable? = null) :
      AppException(message, cause)

  /**
   * Any other failure, for which the user has nothing specific to fix.
   *
   * @param message details about the failure, meant for logs, if any
   * @param cause the original failure, if any
   * @author Timz3rr
   */
  class Unknown(message: String? = null, cause: Throwable? = null) : AppException(message, cause)
}
