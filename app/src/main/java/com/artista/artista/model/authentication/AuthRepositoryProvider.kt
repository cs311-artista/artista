package com.artista.artista.model.authentication

import com.google.firebase.Firebase
import com.google.firebase.auth.auth

// Co-authored-by: DeepSeek Flash
/**
 * Composition root for the authentication repository.
 *
 * Keeps the Firebase dependency in the model layer so the UI never imports Firebase to obtain an
 * [AuthRepository], mirroring [com.artista.artista.model.user.UserRepositoryProvider].
 *
 * @author timo-by
 */
object AuthRepositoryProvider {

  private val firebaseRepository: AuthRepository by lazy { FirebaseAuthRepository(Firebase.auth) }

  private var overrideRepository: AuthRepository? = null

  /**
   * The repository used by the application.
   *
   * Settable so a test can point the application at a fake. The Firebase-backed implementation is
   * created lazily, because [Firebase.auth] needs the default [com.google.firebase.FirebaseApp] to
   * be initialized first.
   *
   * @author timo-by
   */
  var repository: AuthRepository
    get() = overrideRepository ?: firebaseRepository.also { overrideRepository = it }
    set(value) {
      overrideRepository = value
    }
}
