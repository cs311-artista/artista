package com.artista.artista.model.authentication

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import java.io.IOException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

// Co-authored-by: DeepSeek Flash
/**
 * Firebase Authentication implementation of [AuthRepository], using the Google provider.
 *
 * Firebase persists the session on the device and restores it when the process starts, so
 * [authState] is built on [FirebaseAuth.addAuthStateListener] rather than on a value read once at
 * construction: the listener is invoked with the restored user and again on every session change.
 * Until that first callback the repository reports nothing, which is what lets the UI tell "signed
 * out" apart from "not known yet".
 *
 * @param auth the Firebase Authentication entry point; injected so callers can point it at the
 *   Authentication emulator in tests
 * @author timo-by
 */
class FirebaseAuthRepository(private val auth: FirebaseAuth) : AuthRepository {

  /**
   * Emits the restored session as soon as it is available, then every subsequent change.
   *
   * @author timo-by
   */
  override val authState: Flow<AuthUser?> = callbackFlow {
    // The listener fires on registration with the locally restored user, and then on every session
    // change, so the flow never needs a manual refresh.
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
      trySend(firebaseAuth.currentUser?.toAuthUser())
    }
    auth.addAuthStateListener(listener)
    // Leaking the listener would keep the flow's channel alive and keep delivering updates to a
    // collector that is gone.
    awaitClose { auth.removeAuthStateListener(listener) }
  }
      // Only the latest session matters, so a collector that falls behind is given the current one
      // rather than a backlog of stale ones.
      .conflate()
      // Firebase re-notifies listeners for the same user (for example on a token refresh), and the
      // UI has nothing to do for an unchanged session.
      .distinctUntilChanged()

  /**
   * Exchanges a Google ID token for a Firebase session and persists it on the device.
   *
   * @param idToken the Google ID token returned by Credential Manager
   * @return success with the signed-in user, or failure carrying an [AuthException]
   * @author timo-by
   */
  override suspend fun signIn(idToken: String): Result<AuthUser> =
      runAuthCatching(Throwable::toFirebaseAuthError) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        // A successful sign-in task always carries a user; when it does not, the exchange yielded
        // no session, so there is nothing to sign in with.
        auth.signInWithCredential(credential).await().user?.toAuthUser()
            ?: throw AuthException(AuthError.Unknown)
      }

  /**
   * Clears the persisted session, so the next launch starts signed out.
   *
   * @return success, or failure carrying an [AuthException]
   * @author timo-by
   */
  override suspend fun signOut(): Result<Unit> =
      runAuthCatching(Throwable::toFirebaseAuthError) { auth.signOut() }

  /**
   * Adapts a Firebase account to the domain's thin [AuthUser] abstraction, reading only the two
   * fields the domain exposes.
   *
   * @return the domain representation of this account
   * @author timo-by
   */
  private fun FirebaseUser.toAuthUser(): AuthUser = authUserOf(uid = uid, displayName = displayName)
}

/**
 * Builds the domain representation of an authenticated account from its raw Firebase fields.
 *
 * Takes raw fields rather than a `FirebaseUser`, which can neither be constructed nor mocked on the
 * JVM: that is what makes the displayed name verifiable by `./gradlew check` instead of only
 * against the emulator. A blank name is reported as `null`, so a caller defaulting a profile's
 * username to it never stores a name that renders as empty.
 *
 * @param uid the stable identifier Firebase assigns to the account
 * @param displayName the name the account's provider reports, or `null` when it reports none
 * @return the domain representation of the account
 * @author timo-by
 */
internal fun authUserOf(uid: String, displayName: String?): AuthUser =
    FirebaseAuthUser(
        uid = uid,
        // Google trims nothing, so a name made of spaces would be stored as an empty username by
        // a caller defaulting the profile to it.
        displayName = displayName?.trim()?.takeIf { it.isNotEmpty() },
    )

/**
 * Classifies a Firebase failure.
 *
 * Only the outcomes a user can act on are named; every other `FirebaseAuthException` subclass (too
 * many requests, account already in use) is matched by the fallback and collapses to
 * [AuthError.Unknown]. That is why no arm on the parent type exists: it would change no outcome.
 *
 * Declared at file level so it can be unit-tested without the emulator.
 *
 * @return the domain outcome matching this failure
 * @author timo-by
 */
internal fun Throwable.toFirebaseAuthError(): AuthError =
    when (this) {
      is FirebaseAuthInvalidCredentialsException,
      is FirebaseAuthInvalidUserException -> AuthError.InvalidCredentials

      is FirebaseNetworkException,
      is IOException -> AuthError.Network

      else -> AuthError.Unknown
    }

/**
 * [AuthUser] backed by a Firebase Authentication account.
 *
 * @param uid the stable identifier Firebase assigns to the account
 * @param displayName the name the account's provider reports, or `null` when it reports none
 * @author timo-by
 */
data class FirebaseAuthUser(
    override val uid: String,
    override val displayName: String? = null,
) : AuthUser
