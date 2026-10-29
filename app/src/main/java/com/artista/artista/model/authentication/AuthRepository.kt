package com.artista.artista.model.authentication

import kotlinx.coroutines.flow.Flow

// Co-authored-by: DeepSeek Flash
/**
 * Abstraction over the authentication backend (Firebase Authentication with the Google provider).
 *
 * ViewModels depend on this interface only, so the sign-in and sign-out logic stays unit-testable
 * with a fake (see `FakeAuthRepository`).
 *
 * @author timo-by
 */
interface AuthRepository {

  /**
   * Observable authentication state.
   *
   * Emits the signed-in user, or `null` when nobody is signed in, and emits nothing until the
   * backend has reported the session: a collector can tell "signed out" apart from "not known yet".
   *
   * It lives here rather than on the ViewModel because a session can change without one asking, for
   * example when Firebase revokes it.
   *
   * @author timo-by
   */
  val authState: Flow<AuthUser?>

  /**
   * Signs a user in from a Google ID token obtained by the UI through Credential Manager.
   *
   * @param idToken the Google ID token returned by Credential Manager
   * @return success with the signed-in user, or failure carrying an [AuthException]
   * @author timo-by
   */
  suspend fun signIn(idToken: String): Result<AuthUser>

  /**
   * Signs the currently authenticated user out.
   *
   * @return success, or failure carrying an [AuthException]
   * @author timo-by
   */
  suspend fun signOut(): Result<Unit>
}
