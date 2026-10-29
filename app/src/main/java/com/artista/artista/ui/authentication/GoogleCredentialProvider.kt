package com.artista.artista.ui.authentication

// Co-authored-by: DeepSeek Flash
/**
 * Abstraction over the platform credential provider (Credential Manager) that supplies a Google ID
 * token and forgets the session it keeps for this application.
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

  /**
   * Clears the session that Credential Manager and its providers keep for this application.
   *
   * Needed on sign-out because Firebase only forgets its own session: the credential providers keep
   * theirs, and that is the state the next request is answered from. Left behind, the account that
   * just signed out is still the active credential, which is the session a user who signed out has
   * implicitly asked to end.
   *
   * A failure here is reported but must not fail the sign-out itself: by the time it is called the
   * Firebase session is already gone, and telling the user that signing out failed would be wrong.
   *
   * @return success, or failure carrying an
   *   [com.artista.artista.model.authentication.AuthException] describing why the state was kept
   * @author timo-by
   */
  suspend fun clearSession(): Result<Unit>
}
