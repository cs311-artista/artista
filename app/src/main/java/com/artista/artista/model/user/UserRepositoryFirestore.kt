// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.model.user

import com.artista.artista.model.error.AppException
import com.artista.artista.model.error.translateFirestoreErrors
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/** The Firestore collection containing application user profiles. */
const val USER_COLLECTION_PATH = "Users"

/**
 * Firestore-backed implementation of the user repository.
 *
 * Every operation runs through [translateFirestoreErrors], so Firestore failures reach callers as
 * [AppException] and ViewModels never depend on Firebase.
 *
 * @param db the Firestore instance used to persist user profiles
 * @param collectionPath the Firestore collection containing the user profiles
 * @author krfpn
 * @author Copilot (223556219+Copilot@users.noreply.github.com)
 */
class UserRepositoryFirestore(
    private val db: FirebaseFirestore,
    private val collectionPath: String = USER_COLLECTION_PATH,
) : UserRepository {
  /**
   * Creates a user profile using the user's authentication UID as the document ID.
   *
   * @param user the user profile to store
   * @throws AppException.AlreadyExists if a profile already uses this UID
   * @throws AppException if Firestore cannot write the profile
   */
  override suspend fun createUser(user: User): Unit = translateFirestoreErrors {
    // Using the authentication UID as the document ID keeps Auth and Firestore identities aligned.
    val documentReference = db.collection(collectionPath).document(user.uid)
    val document = documentReference.get().await()
    if (document.exists()) {
      throw AppException.AlreadyExists("User '${user.uid}' already exists")
    }

    // Only create the profile after confirming that the UID is not already stored.
    documentReference.set(user).await()
  }

  /**
   * Retrieves a user profile by its unique identifier.
   *
   * @param userId the unique identifier of the user to retrieve
   * @return the stored user profile
   * @throws AppException.NotFound if the document does not exist
   * @throws AppException.InvalidData if the document is malformed
   * @throws AppException if Firestore cannot read the document
   */
  override suspend fun getUser(userId: String): User = translateFirestoreErrors {
    // Awaiting the Task keeps Firestore asynchronous without blocking the calling coroutine.
    val document = db.collection(collectionPath).document(userId).get().await()

    if (!document.exists()) {
      throw AppException.NotFound("User '$userId' was not found")
    }

    documentToUser(document)
  }

  /**
   * Updates an existing user profile.
   *
   * @param userId the unique identifier of the user to update
   * @param newValue the replacement user profile
   * @throws AppException.InvalidInput if [userId] differs from the UID of [newValue]
   * @throws AppException.NotFound if the user does not exist
   * @throws AppException if Firestore cannot update the profile
   */
  override suspend fun updateUser(userId: String, newValue: User): Unit = translateFirestoreErrors {
    if (userId != newValue.uid) {
      throw AppException.InvalidInput("User ID does not match the replacement profile")
    }

    // Check existence first because set() would otherwise create a missing user document.
    val document = db.collection(collectionPath).document(userId).get().await()
    if (!document.exists()) {
      throw AppException.NotFound("User '$userId' was not found")
    }

    db.collection(collectionPath).document(userId).set(newValue).await()
  }

  /**
   * Deletes a user profile.
   *
   * @param userId the unique identifier of the user to delete
   * @throws AppException.NotFound if the user does not exist
   * @throws AppException if Firestore cannot delete the profile
   */
  override suspend fun deleteUser(userId: String): Unit = translateFirestoreErrors {
    // Check existence first because Firestore delete() succeeds for a missing document.
    val document = db.collection(collectionPath).document(userId).get().await()
    if (!document.exists()) {
      throw AppException.NotFound("User '$userId' was not found")
    }
    // Reuse the reference from the verified snapshot instead of creating a new reference.
    document.reference.delete().await()
  }

  /**
   * Converts a Firestore document into a `User`.
   *
   * @param document the Firestore document to convert
   * @return the `user` represented by the document
   * @throws AppException.InvalidData if required fields are missing or have invalid types
   */
  private fun documentToUser(document: DocumentSnapshot): User {
    // The UID is required because it is the stable identity shared with Firebase Authentication.
    val uid =
        document.getString("uid")
            ?: throw AppException.InvalidData("User document '${document.id}' is missing its UID")

    // Preferences are stored as a nested map, so validate that structure before reading its fields.
    val preferenceData =
        document.get("preference") as? Map<*, *>
            ?: throw AppException.InvalidData(
                "User document '${document.id}' is missing its preferences"
            )

    // Validate every stored preference item before exposing it as a typed domain list.
    fun readStringList(fieldName: String): List<String> {
      // An absent preference means that the user has not selected any values for that category.
      val value = preferenceData[fieldName] ?: return emptyList()
      val values =
          value as? List<*>
              ?: throw AppException.InvalidData(
                  "User document '${document.id}' has an invalid '$fieldName' preference"
              )
      return values.map { item ->
        // Reject malformed values instead of allowing untyped Firestore data into the domain model.
        item as? String
            ?: throw AppException.InvalidData(
                "User document '${document.id}' has a non-string '$fieldName' preference"
            )
      }
    }

    return User(
        uid = uid,
        // A username is optional in the domain model, so a missing field remains null.
        userName = document.getString("userName"),
        preference =
            UserPreference(
                artists = readStringList("artists"),
                type = readStringList("type"),
                timePeriod = readStringList("timePeriod"),
            ),
    )
  }
}
