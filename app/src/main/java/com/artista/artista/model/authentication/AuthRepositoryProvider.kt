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

  /**
   * Whether a repository is currently installed on top of the application's own.
   *
   * Readable so a test can check that [resetToApplicationRepository] cleared the override: reading
   * [repository] instead would build the Firebase implementation, which needs an initialised app.
   *
   * @author timo-by
   */
  internal val isOverridden: Boolean
    get() = overrideRepository != null

  /**
   * Puts the application's repository back, discarding one installed through [repository].
   *
   * What a test calls once it is done with a fake: the provider is process-wide, so a repository
   * left in place would be the one every later test - and every later screen - reads.
   *
   * @author timo-by
   */
  fun resetToApplicationRepository() {
    overrideRepository = null
  }
}
