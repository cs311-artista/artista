package com.artista.artista.model.authentication

import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over the authentication backend (Firebase Authentication with the Google provider).
 *
 * ViewModels depend on this interface only, never on a concrete Firebase implementation, so the
 * sign-in and sign-out logic stays unit-testable with a fake (see `FakeAuthRepository`).
 *
 * @author timo-by
 */
interface AuthRepository {

  /**
   * Observable authentication state.
   *
   * Emits the currently signed-in user, or `null` when nobody is signed in. The stream reflects
   * sign-in and sign-out as they happen, without requiring a manual refresh.
   *
   * @author timo-by
   */
  val authState: Flow<AuthUser?>

  /**
   * Signs a user in from a Google ID token.
   *
   * The token is obtained by the UI through Credential Manager; exchanging it for a Firebase
   * credential stays inside the implementation.
   *
   * @param idToken the Google ID token returned by Credential Manager
   * @return success with the signed-in user, or failure carrying the cause
   * @author timo-by
   */
  suspend fun signIn(idToken: String): Result<AuthUser>

  /**
   * Signs the currently authenticated user out.
   *
   * @return success, or failure carrying the cause
   * @author timo-by
   */
  suspend fun signOut(): Result<Unit>
}
