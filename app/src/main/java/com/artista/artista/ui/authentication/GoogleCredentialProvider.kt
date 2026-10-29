package com.artista.artista.ui.authentication

// Co-authored-by: DeepSeek Flash
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
   * Triggers the Google account chooser; on success the returned token is exchanged for a Firebase
   * session by the authentication repository.
   *
   * @return success with the Google ID token, or failure carrying an
   *   [com.artista.artista.model.authentication.AuthException] that describes what went wrong (the
   *   user cancelled, no account is available, ...)
   * @author timo-by
   */
  suspend fun getIdToken(): Result<String>
}
