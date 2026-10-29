package com.artista.artista.model.authentication

import com.artista.artista.model.error.AppException
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
 * The session is persisted on the device by Firebase Authentication itself: the signed-in user is
 * cached in local storage and restored when the application process starts again. That is why
 * [authState] is built on [FirebaseAuth.addAuthStateListener] rather than on a value read once at
 * construction: the listener is invoked with the restored user, so a returning user is already
 * signed in before the first frame is drawn, and it is invoked again whenever the session changes
 * (sign-in, sign-out, or a session revoked by Firebase). Until that first callback the repository
 * reports nothing, which is what lets the UI tell "signed out" apart from "not known yet".
 *
 * Failures are reported as [AppException], the vocabulary every repository in the app translates
 * its backend failures into.
 *
 * No explicit persistence call is made, because the Android SDK has no way to disable on-device
 * persistence and does not need to be told to enable it.
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
   * @return success with the signed-in user, or failure carrying an [AppException]
   * @author timo-by
   */
  override suspend fun signIn(idToken: String): Result<AuthUser> =
      runAuthCatching(Throwable::toAppException) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        // Awaiting the Task keeps the Firebase call asynchronous without blocking a thread. A
        // successful sign-in task always carries a user; when it somehow does not, the exchange
        // yielded no session, so there is nothing to sign in with.
        auth.signInWithCredential(credential).await().user?.toAuthUser()
            ?: throw AppException.Unknown()
      }

  /**
   * Clears the persisted session, so the next launch starts signed out.
   *
   * @return success, or failure carrying an [AppException]
   * @author timo-by
   */
  override suspend fun signOut(): Result<Unit> =
      runAuthCatching(Throwable::toAppException) { auth.signOut() }

  /**
   * Adapts a Firebase user to the domain's thin [AuthUser] abstraction.
   *
   * @return the domain representation of this Firebase user
   * @author timo-by
   */
  private fun FirebaseUser.toAuthUser(): AuthUser = FirebaseAuthUser(uid)
}

/**
 * Translates a Firebase Authentication failure into the matching [AppException].
 *
 * Only the outcomes a user can act on differently are named. Every other `FirebaseAuthException` -
 * throttling, an account already linked to another credential - is matched by the fallback, which
 * is why no arm on that parent type exists: it would change no outcome, and the user has nothing
 * different to do. The original exception is kept as the cause so logs still show its code.
 *
 * Declared at file level rather than as a private member so it can be unit-tested without the
 * Authentication emulator that [FirebaseAuthRepository] itself needs.
 *
 * @return the [AppException] matching this failure
 * @author timo-by
 */
internal fun Throwable.toAppException(): AppException =
    when (this) {
      is FirebaseAuthInvalidCredentialsException,
      is FirebaseAuthInvalidUserException -> AppException.Unauthorized(message, this)

      is FirebaseNetworkException,
      is IOException -> AppException.Network(message, this)

      else -> AppException.Unknown(message, this)
    }

/**
 * [AuthUser] backed by a Firebase Authentication account.
 *
 * @param uid the stable identifier Firebase assigns to the account
 * @author timo-by
 */
data class FirebaseAuthUser(override val uid: String) : AuthUser
