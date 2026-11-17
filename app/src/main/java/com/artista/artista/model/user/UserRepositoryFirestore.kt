// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: DeepSeek Flash <noreply@deepseek.com>
package com.artista.artista.model.user

import com.artista.artista.model.error.AppException
import com.artista.artista.model.error.translateFirestoreErrors
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
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
   * Saves only the username of a user profile, creating the profile when it does not exist yet.
   *
   * @param userId the unique identifier of the user to rename
   * @param userName the new username
   * @throws AppException if Firestore refuses or cannot complete the write
   * @author Timz3rr
   */
  override suspend fun updateUsername(userId: UserId, userName: String): Unit =
      translateFirestoreErrors {
        // No read first: offline, reading a profile missing from the local cache fails, while a
        // write alone is queued and sent once the connection returns. The merge keeps the other
        // fields, such as the preferences.
        // The UID is written on every rename because, when the profile is missing from the local
        // cache, Firestore's local copy holds only the fields written here, and getUser needs the
        // UID to read it. The security rules also require it if this write creates the profile,
        // for example after its creation at sign-in failed.
        db.collection(collectionPath)
            .document(userId)
            .set(mapOf("uid" to userId, "userName" to userName), SetOptions.merge())
            .await()
      }

  /**
   * Updates only the picture URL of an existing user profile.
   *
   * Firestore writes the single field named, so this write cannot revert a name or a preference
   * saved by another screen while it is in flight, and those saves cannot revert the picture.
   *
   * The document is read first, as [updateUser] and [deleteUser] do, because the security rule of a
   * user compares `request.resource.data.uid`: on a document that does not exist, a one-field
   * update leaves that key out, so the rule denies the write and Firestore reports
   * PERMISSION_DENIED. The caller would then receive [AppException.Unauthorized] instead of the
   * [AppException.NotFound] it needs to tell "this user has no profile" from "this user could not
   * be read".
   *
   * @param userId the unique identifier of the user to update
   * @param photoUrl the URL of the picture to store
   * @throws AppException.NotFound if the user does not exist
   * @throws AppException if Firestore cannot update the profile
   * @author timo-by
   */
  override suspend fun updatePhotoUrl(userId: String, photoUrl: String): Unit =
      translateFirestoreErrors {
        // Check existence first because update() on a missing document is denied by the rules
        // rather than reported as missing.
        val document = db.collection(collectionPath).document(userId).get().await()
        if (!document.exists()) {
          throw AppException.NotFound("User '$userId' was not found")
        }
        document.reference.update("photoUrl", photoUrl).await()
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
   * @throws AppException.InvalidData if required fields are missing or fields have invalid types
   */
  private fun documentToUser(document: DocumentSnapshot): User {
    // The UID is required because it is the stable identity shared with Firebase Authentication.
    val uid =
        document.getString("uid")
            ?: throw AppException.InvalidData("User document '${document.id}' is missing its UID")

    // A missing preference field means no preference chosen yet: a profile created by
    // updateUsername, or Firestore's local copy of a rename queued offline, only holds the UID and
    // the name. Preferences are stored as a nested map, so any other present value is malformed.
    val preferenceData =
        when (val value = document.get("preference")) {
          null -> emptyMap<String, Any>()
          is Map<*, *> -> value
          else ->
              throw AppException.InvalidData(
                  "User document '${document.id}' has invalid preferences"
              )
        }

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

    /**
     * Reads the picture URL stored on the document.
     *
     * The field is read by hand: getString() casts blindly, and its failure would escape as a plain
     * RuntimeException, which translateFirestoreErrors does not translate, since it only handles
     * FirebaseFirestoreException.
     *
     * @return the picture URL of the document, or `null` when it has none
     * @author timo-by
     */
    fun readPhotoUrl(): String? =
        when (val value = document.get("photoUrl")) {
          // Documents written before the picture existed have no such field, and must keep loading:
          // no migration is needed.
          null -> null
          // A blank URL is no picture, so it is exposed as none rather than as an empty address.
          is String -> value.takeIf { it.isNotBlank() }
          // Anything else carries nothing displayable. The picture is cosmetic, while the name
          // and the preferences are not, and the app could not repair the value: the refresh
          // that would overwrite it reads the profile first. An unreadable picture is read as
          // no picture, not as a reason to hide the whole profile.
          else -> null
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
        photoUrl = readPhotoUrl(),
    )
  }
}
