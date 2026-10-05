package com.artista.artista.model.authentication

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory [AuthRepository] for unit tests.
 *
 * Lets tests drive the authentication state directly and simulate failures, with no Firebase
 * dependency.
 *
 * @param initialUser the user the repository starts signed in as, or `null` for signed out
 * @author timo-by
 */
class FakeAuthRepository(initialUser: AuthUser? = null) : AuthRepository {

  private val state = MutableStateFlow(initialUser)

  /**
   * The user that a successful [signIn] produces.
   *
   * @author timo-by
   */
  var user: AuthUser = FakeAuthUser(uid = "fake-uid")

  /**
   * When set, [signIn] returns a failure carrying this instead of signing in.
   *
   * @author timo-by
   */
  var signInError: Throwable? = null

  /**
   * When set, [signOut] returns a failure carrying this instead of signing out.
   *
   * @author timo-by
   */
  var signOutError: Throwable? = null

  override val authState: Flow<AuthUser?> = state.asStateFlow()

  override suspend fun signIn(idToken: String): Result<AuthUser> {
    signInError?.let {
      return Result.failure(it)
    }
    state.value = user
    return Result.success(user)
  }

  override suspend fun signOut(): Result<Unit> {
    signOutError?.let {
      return Result.failure(it)
    }
    state.value = null
    return Result.success(Unit)
  }
}

/**
 * Trivial [AuthUser] implementation used by tests.
 *
 * @param uid the unique identifier to expose
 * @author timo-by
 */
data class FakeAuthUser(override val uid: String) : AuthUser
