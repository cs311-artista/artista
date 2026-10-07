package com.artista.artista.model.user

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.tasks.await

/** The Firestore collection containing application user profiles. */
const val USER_COLLECTION_PATH = "Users"

/**
 * Firestore-backed implementation of the user repository.
 *
 * @param db the Firestore instance used to persist user profiles
 * @author Copilot (223556219+Copilot@users.noreply.github.com)
 */
class UserRepositoryFirestore(private val db: FirebaseFirestore) : UserRepository {
  /**
   * Creates a user profile using the user's authentication UID as the document ID.
   *
   * @param user the user profile to store
   * @throws Exception if Firestore cannot write the profile
   */
  override suspend fun createUser(user: User) {
    // Using the authentication UID as the document ID keeps Auth and Firestore identities aligned.
    db.collection(USER_COLLECTION_PATH).document(user.uid).set(user).await()
  }

  /**
   * Retrieves a user profile by its unique identifier.
   *
   * @param userId the unique identifier of the user to retrieve
   * @return the stored user profile
   * @throws Exception if the document does not exist, is malformed, or cannot be read
   */
  override suspend fun getUser(userId: String): User {
    // Awaiting the Task keeps Firestore asynchronous without blocking the calling coroutine.
    val document = db.collection(USER_COLLECTION_PATH).document(userId).get().await()

    if (!document.exists()) {
      throw NoSuchElementException("User '$userId' was not found")
    }

    return document.toObject<User>()
        ?: throw IllegalStateException("User '$userId' contains invalid data")
  }

  /**
   * Updates an existing user profile.
   *
   * @param userId the unique identifier of the user to update
   * @param newValue the replacement user profile
   * @throws Exception if the user does not exist, the identifiers differ, or Firestore cannot
   *   update it
   */
  override suspend fun updateUser(userId: String, newValue: User) {
    if (userId != newValue.uid) {
      throw IllegalArgumentException("User ID does not match the replacement profile")
    }

    // Check existence first because set() would otherwise create a missing user document.
    val document = db.collection(USER_COLLECTION_PATH).document(userId).get().await()
    if (!document.exists()) {
      throw NoSuchElementException("User '$userId' was not found")
    }

    db.collection(USER_COLLECTION_PATH).document(userId).set(newValue).await()
  }

  /**
   * Deletes a user profile.
   *
   * @param userId the unique identifier of the user to delete
   * @throws Exception if Firestore cannot delete the profile
   */
  override suspend fun deleteUser(userId: String) {
    db.collection(USER_COLLECTION_PATH).document(userId).delete().await()
  }
}
