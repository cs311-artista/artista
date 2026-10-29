package com.artista.artista.ui.authentication

// Co-authored-by: DeepSeek Flash
/**
 * In-memory [GoogleCredentialProvider] for unit tests.
 *
 * Lets tests drive the credential seam directly — returning a token or simulating a failure — with
 * no dependency on Credential Manager.
 *
 * @param idToken the token a successful [getIdToken] returns
 * @param error when set, [getIdToken] returns a failure carrying this instead of a token
 * @author timo-by
 */
class FakeGoogleCredentialProvider(
    var idToken: String = "fake-id-token",
    var error: Throwable? = null,
) : GoogleCredentialProvider {

  /**
   * Number of times [getIdToken] has been called.
   *
   * @author timo-by
   */
  var callCount: Int = 0
    private set

  /**
   * When set, [clearSession] returns a failure carrying this instead of clearing the session.
   *
   * @author timo-by
   */
  var clearSessionError: Throwable? = null

  /**
   * Number of times [clearSession] has been called.
   *
   * @author timo-by
   */
  var clearSessionCallCount: Int = 0
    private set

  override suspend fun getIdToken(): Result<String> {
    callCount++
    error?.let {
      return Result.failure(it)
    }
    return Result.success(idToken)
  }

  override suspend fun clearSession(): Result<Unit> {
    clearSessionCallCount++
    clearSessionError?.let {
      return Result.failure(it)
    }
    return Result.success(Unit)
  }
}
