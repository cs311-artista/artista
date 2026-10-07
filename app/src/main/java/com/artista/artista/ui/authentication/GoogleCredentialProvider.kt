package com.artista.artista.ui.authentication

/**
 * Abstraction over the platform credential provider (Credential Manager) that supplies a Google ID
 * token.
 *
 * The sign-in screen depends on this interface only, never on the concrete Credential Manager, so
 * the tap → sign-in flow stays unit-testable with a fake (see `FakeGoogleCredentialProvider`).
 *
 * @author timo-by
 */
interface GoogleCredentialProvider {

  /**
   * Requests a Google ID token from Credential Manager.
   *
   * Triggers the Google sign-in sheet; on success the returned token is exchanged for a Firebase
   * session by the authentication repository.
   *
   * @return success with the Google ID token, or failure carrying the cause (e.g. no credential
   *   available, or the user cancelled)
   * @author timo-by
   */
  suspend fun getIdToken(): Result<String>
}
