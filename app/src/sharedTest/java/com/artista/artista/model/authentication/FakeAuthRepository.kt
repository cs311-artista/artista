// Co-authored-by: DeepSeek Flash <noreply@deepseek.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.model.authentication

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

/**
 * In-memory [AuthRepository] for unit and instrumented tests.
 *
 * Lets tests drive the authentication state directly and simulate failures, with no Firebase
 * dependency. It lives in the shared test sources so that both suites use this single copy.
 *
 * @param initialUser the user the repository starts signed in as, or `null` for signed out
 * @author timo-by
 */
class FakeAuthRepository(initialUser: AuthUser? = null) : AuthRepository {

  private val state = MutableStateFlow(initialUser)

  /**
   * Re-notifications of the current state, which [state] would drop as an unchanged value.
   *
   * See [reemitAuthState].
   */
  private val reemissions = MutableSharedFlow<AuthUser?>(extraBufferCapacity = 64)

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

  /**
   * Number of times [signIn] has been called.
   *
   * @author timo-by
   */
  var signInCallCount: Int = 0
    private set

  /**
   * Number of times [signOut] has been called.
   *
   * @author timo-by
   */
  var signOutCallCount: Int = 0
    private set

  /**
   * The last ID token passed to [signIn], or `null` if it was never called.
   *
   * @author timo-by
   */
  var lastIdToken: String? = null
    private set

  /**
   * When set, [authState] fails with this instead of emitting the authentication state.
   *
   * @author timo-by
   */
  var authStateError: Throwable? = null

  /**
   * When true, [authState] never reports a state, as while a persisted session is being restored.
   *
   * @author timo-by
   */
  var authStateIsPending: Boolean = false

  /**
   * Number of collectors currently subscribed to [authState].
   *
   * A collector that stops is the only observable sign that the ViewModel holding it was cleared,
   * so a test uses this to check that a screen's ViewModel died with the screen rather than being
   * left alive: nothing else would show it, the ViewModel being unreachable by then.
   */
  var activeAuthStateCollectors: Int = 0
    private set

  override val authState: Flow<AuthUser?> = flow {
    authStateError?.let { throw it }
    if (authStateIsPending) awaitCancellation()
    // The re-emissions are merged in so that a test can notify the same user twice. Unlike
    // [FirebaseAuthRepository], this flow neither conflates nor drops unchanged values: that is
    // what lets a test express a notification the production flow filters out.
    emitAll(merge(state, reemissions))
  }
      .onStart { activeAuthStateCollectors++ }
      .onCompletion { activeAuthStateCollectors-- }

  override suspend fun signIn(idToken: String): Result<AuthUser> {
    signInCallCount++
    lastIdToken = idToken
    signInError?.let {
      return Result.failure(it)
    }
    state.value = user
    return Result.success(user)
  }

  override suspend fun signOut(): Result<Unit> {
    signOutCallCount++
    signOutError?.let {
      return Result.failure(it)
    }
    state.value = null
    return Result.success(Unit)
  }

  /**
   * Emits a new authentication state as if it came from the backend, e.g. an externally revoked
   * session. Does not count as a [signIn] or [signOut] call.
   *
   * @param user the new authentication state
   * @author timo-by
   */
  fun setAuthState(user: AuthUser?) {
    state.value = user
  }

  /**
   * Reports the current authentication state again, as a backend listener may notify an unchanged
   * user, for example on a token refresh.
   *
   * A state holder drops a value equal to the one it already holds, so without this a test could
   * not tell "the same user arrived again" apart from "nothing happened".
   *
   * Production does not reach a collector with an unchanged user:
   * [FirebaseAuthRepository.authState] drops those with `distinctUntilChanged()`. What this
   * expresses is therefore the invariant the ViewModel has to hold on its own - one profile
   * initialization per session - rather than an event the app really receives.
   *
   * @author timo-by
   */
  fun reemitAuthState() {
    reemissions.tryEmit(state.value)
  }
}

/**
 * Trivial [AuthUser] implementation used by tests.
 *
 * @param uid the unique identifier to expose
 * @param photoUrl the picture URL the fake provider reports, `null` by default
 * @author timo-by
 */
data class FakeAuthUser(
    override val uid: String,
    override val photoUrl: String? = null,
) : AuthUser
