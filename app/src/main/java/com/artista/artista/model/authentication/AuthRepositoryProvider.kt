package com.artista.artista.model.authentication

import com.google.firebase.Firebase
import com.google.firebase.auth.auth

// Co-authored-by: DeepSeek Flash
/**
 * Composition root for the authentication repository.
 *
 * Keeps the Firebase dependency in the model layer, mirroring the other repositories' providers, so
 * the UI never has to import Firebase to obtain an [AuthRepository].
 *
 * @author timo-by
 */
object AuthRepositoryProvider {

  private val firebaseRepository: AuthRepository by lazy { FirebaseAuthRepository(Firebase.auth) }

  private var overrideRepository: AuthRepository? = null

  /**
   * The repository used by the application.
   *
   * Settable so a test can point the application at a fake, mirroring
   * [com.artista.artista.model.user.UserRepositoryProvider]. The Firebase-backed implementation is
   * created lazily on first read, because [Firebase.auth] requires the default
   * [com.google.firebase.FirebaseApp] to be initialized first, which happens while the application
   * starts.
   *
   * @author timo-by
   */
  var repository: AuthRepository
    get() = overrideRepository ?: firebaseRepository.also { overrideRepository = it }
    set(value) {
      overrideRepository = value
    }
}
