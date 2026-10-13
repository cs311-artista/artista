package com.artista.artista.model.user

import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

/**
 * An intermediary to provide implementations of the [UserRepository] interface. Allows for
 * switching between live and in-memory database and easy population of in-memory database.
 *
 * @author 5kyPhy
 */
object UserRepositoryProvider {
  private val _repositoryLocal: UserRepository = UserRepositoryLocal()
  private val _repositoryFirestore: UserRepositoryFirestore by lazy {
    UserRepositoryFirestore(Firebase.firestore)
  }

  /**
   * Repository currently used by the application.
   *
   * @return the selected repository, defaulting to the lazy Firestore implementation
   */
  var repository: UserRepository
    get() = _repository ?: _repositoryFirestore.also { _repository = it }
    set(value) {
      _repository = value
    }

  private var _repository: UserRepository? = null

  /**
   * Function used to tell the RepositoryProvider to use an in-memory implementation of the
   * UserRepository interface, instead of the Firestore-based implementation.
   *
   * @author 5kyPhy
   */
  fun useFakeRepository() {
    repository = _repositoryLocal
  }

  /**
   * Function allowing easy, batched addition of mock user. Only populated the in-memory
   * implementation of the [UserRepository] as a safeguard.
   *
   * @param mocks a [List] of [User]s used to populate the in-memory [UserRepository]. A premade
   *   list of mock data is set as default ([mockUsers]).
   * @author 5kyPhy
   */
  suspend fun populateFakeRepository(mocks: List<User> = mockUsers) {
    mocks.forEach { mock -> _repositoryLocal.createUser(mock) }
  }
}

val mockUsers: List<User> =
    listOf(
        User(
            uid = "Johns_ID",
            userName = "John Doe",
            preference = UserPreference(),
        ),
        User(
            uid = "Unnamed_ID",
            userName = null,
            preference =
                UserPreference(
                    artists =
                        listOf(
                            "VanGogh",
                            "Pollock",
                            "DaVinci",
                        ),
                ),
        ),
        User(
            uid = "Sams_ID",
            userName = "Samantha Johnson",
            preference = UserPreference(type = listOf("")),
        ),
    )
