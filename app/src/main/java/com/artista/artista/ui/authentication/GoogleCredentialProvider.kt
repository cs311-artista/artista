package com.artista.artista.ui.authentication

import com.artista.artista.model.error.AppException

// Co-authored-by: DeepSeek Flash
/**
 * Reported when the user dismissed the Google account chooser.
 *
 * Dismissing is an outcome of the interaction, not a failure: the user changed their mind, so the
 * screen has nothing to report. It is deliberately not an [AppException], because that is the
 * vocabulary a caller shows to the user.
 *
 * @author timo-by
 */
class SignInCancelledException : Exception("The user dismissed the Google sign-in chooser")

/**
 * Reported when Credential Manager has no Google account to offer, so there is nothing to sign in
 * with.
 *
 * Like [SignInCancelledException], this is an outcome of the interaction rather than a failure of
 * the provider, and the screen answers it with its own message instead of a generic error.
 *
 * @author timo-by
 */
class NoGoogleAccountException : Exception("No Google account is usable on this device")

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
   * @return success with the Google ID token, or failure carrying an [AppException] when the
   *   request failed, [SignInCancelledException] when the user dismissed the chooser, or
   *   [NoGoogleAccountException] when there is no account to offer
   * @author timo-by
   */
  suspend fun getIdToken(): Result<String>
}
