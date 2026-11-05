// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.model.artwork

import com.artista.artista.model.artist.Artist
import com.artista.artista.model.error.AppException
import com.artista.artista.model.error.translateFirestoreErrors
import com.artista.artista.model.map.Location
import com.artista.artista.model.user.USER_COLLECTION_PATH
import com.artista.artista.resources.C
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import java.util.Date
import kotlinx.coroutines.tasks.await

/**
 * Stores saved artworks in the current user's Firestore subcollection.
 *
 * Firestore's default local cache keeps these documents available offline and synchronizes writes
 * when connectivity returns.
 *
 * @param db Firestore database used to store saved artworks
 * @param auth Firebase Authentication used to identify the current user
 * @author hixeum
 */
class SavedArtworksRepositoryFirestore(
    private val db: FirebaseFirestore = Firebase.firestore,
    private val auth: FirebaseAuth = Firebase.auth,
) : SavedArtworksRepository {

  /**
   * Checks whether the current user has saved the requested artwork.
   *
   * @param uid identifier of the artwork to check
   * @return whether the artwork is saved
   * @throws AppException.InvalidInput if [uid] is blank
   * @throws AppException.Unauthorized if no user is authenticated
   * @throws AppException if Firestore cannot complete the read
   */
  override suspend fun isArtworkSaved(uid: ArtworkId): Boolean = translateFirestoreErrors {
    requireArtworkId(uid)
    savedArtworkDocument(currentUserId(), uid).get().await().exists()
  }

  /**
   * Saves or replaces an artwork for the current user.
   *
   * @param artwork artwork to persist
   * @throws AppException.InvalidInput if [artwork]'s identifier is blank
   * @throws AppException.Unauthorized if no user is authenticated
   * @throws AppException if Firestore cannot complete the write
   */
  override suspend fun saveArtwork(artwork: Artwork): Unit = translateFirestoreErrors {
    requireArtworkId(artwork.uid)
    savedArtworkDocument(currentUserId(), artwork.uid).set(artwork.toFirestoreData()).await()
  }

  /**
   * Removes an artwork saved by the current user.
   *
   * @param uid identifier of the artwork to remove
   * @throws AppException.InvalidInput if [uid] is blank
   * @throws AppException.NotFound if the artwork is not saved
   * @throws AppException.Unauthorized if no user is authenticated
   * @throws AppException if Firestore cannot complete the delete
   */
  override suspend fun removeArtworkFromSaved(uid: ArtworkId): Unit = translateFirestoreErrors {
    requireArtworkId(uid)
    val document = savedArtworkDocument(currentUserId(), uid)
    if (!document.get().await().exists()) {
      throw AppException.NotFound("Saved artwork '$uid' was not found")
    }
    document.delete().await()
  }

  /**
   * Loads and validates every saved artwork belonging to the current user.
   *
   * @return saved artworks for the authenticated user
   * @throws AppException.Unauthorized if no user is authenticated
   * @throws AppException.InvalidData if a saved artwork document is malformed
   * @throws AppException if Firestore cannot complete the query
   */
  override suspend fun getSavedArtworks(): List<Artwork> = translateFirestoreErrors {
    savedArtworksCollection(currentUserId()).get().await().documents.map(::documentToArtwork)
  }

  /** Returns the authenticated user's UID or rejects access when signed out. */
  private fun currentUserId(): String =
      auth.currentUser?.uid?.takeIf(String::isNotBlank)
          ?: throw AppException.Unauthorized(
              "A signed-in user is required to access saved artworks"
          )

  /** Rejects an artwork identifier that cannot be used as a Firestore document ID. */
  private fun requireArtworkId(uid: ArtworkId) {
    if (uid.isBlank()) throw AppException.InvalidInput("Artwork uid is blank")
  }

  /** Returns the current user's saved-artworks collection. */
  private fun savedArtworksCollection(userId: String) =
      db.collection(USER_COLLECTION_PATH)
          .document(userId)
          .collection(C.Constants.SAVED_ARTWORKS_COLLECTION_PATH)

  /** Returns a saved-artwork document for the specified user and artwork. */
  private fun savedArtworkDocument(userId: String, artworkId: ArtworkId) =
      savedArtworksCollection(userId).document(artworkId)

  /** Converts the domain artwork to Firestore-compatible maps and scalar values. */
  private fun Artwork.toFirestoreData(): Map<String, Any?> =
      mapOf(
          "uid" to uid,
          "name" to name,
          "artist" to artist?.let { mapOf("uid" to it.uid, "name" to it.name) },
          "description" to description,
          "conceptionDate" to conceptionDate,
          "museum" to museum,
          "location" to
              location?.let {
                mapOf("longitude" to it.longitude, "latitude" to it.latitude, "name" to it.name)
              },
          "dimension" to dimension?.let { mapOf("width" to it.width, "height" to it.height) },
      )

  /** Converts a validated Firestore document to an artwork domain value. */
  private fun documentToArtwork(document: DocumentSnapshot): Artwork {
    val uid = document.stringField("uid")
    if (uid.isBlank() || uid != document.id) {
      throw AppException.InvalidData(
          "Saved artwork document '${document.id}' has a missing or mismatched UID"
      )
    }

    return Artwork(
        uid = uid,
        name = document.optionalStringField("name"),
        artist = document.optionalMapField("artist")?.toArtist(document.id),
        description = document.optionalStringField("description"),
        conceptionDate = document.optionalDateField("conceptionDate"),
        museum = document.optionalStringField("museum"),
        location = document.optionalMapField("location")?.toLocation(document.id),
        dimension = document.optionalMapField("dimension")?.toDimension(document.id),
    )
  }

  /** Reads a required string field from a saved-artwork document. */
  private fun DocumentSnapshot.stringField(fieldName: String): String =
      get(fieldName) as? String ?: throw invalidField(fieldName)

  /** Reads an optional string field, rejecting values stored with another type. */
  private fun DocumentSnapshot.optionalStringField(fieldName: String): String? {
    val value = get(fieldName) ?: return null
    return value as? String ?: throw invalidField(fieldName)
  }

  /** Reads an optional Firestore timestamp or Java date. */
  private fun DocumentSnapshot.optionalDateField(fieldName: String): Date? {
    val value = get(fieldName) ?: return null
    return when (value) {
      is Date -> value
      is Timestamp -> value.toDate()
      else -> throw invalidField(fieldName)
    }
  }

  /** Reads an optional nested map, rejecting values stored with another type. */
  private fun DocumentSnapshot.optionalMapField(fieldName: String): Map<*, *>? {
    val value = get(fieldName) ?: return null
    return value as? Map<*, *> ?: throw invalidField(fieldName)
  }

  /** Converts a nested Firestore map to an artist after validating its required fields. */
  private fun Map<*, *>.toArtist(documentId: String): Artist {
    val uid = this["uid"] as? String ?: throw invalidNestedField(documentId, "artist.uid")
    val name = this["name"] as? String ?: throw invalidNestedField(documentId, "artist.name")
    if (uid.isBlank()) throw invalidNestedField(documentId, "artist.uid")
    return Artist(uid = uid, name = name)
  }

  /** Converts a nested Firestore map to a location after validating its coordinates. */
  private fun Map<*, *>.toLocation(documentId: String): Location =
      Location(
          longitude = requiredNumber("longitude", documentId, "location").toDouble(),
          latitude = requiredNumber("latitude", documentId, "location").toDouble(),
          name =
              when (val value = this["name"]) {
                null -> null
                is String -> value
                else -> throw invalidNestedField(documentId, "location.name")
              },
      )

  /** Converts a nested Firestore map to dimensions after validating both measurements. */
  private fun Map<*, *>.toDimension(documentId: String): Dimension =
      Dimension(
          width = requiredNumber("width", documentId, "dimension").toDouble(),
          height = requiredNumber("height", documentId, "dimension").toDouble(),
      )

  /** Reads a required numeric value from a nested Firestore map. */
  private fun Map<*, *>.requiredNumber(
      fieldName: String,
      documentId: String,
      parentField: String,
  ): Number =
      this[fieldName] as? Number ?: throw invalidNestedField(documentId, "$parentField.$fieldName")

  /** Creates an invalid-data error for a malformed top-level field. */
  private fun invalidField(fieldName: String) =
      AppException.InvalidData("Saved artwork has an invalid '$fieldName' field")

  /** Creates an invalid-data error for a malformed nested field. */
  private fun invalidNestedField(documentId: String, fieldName: String) =
      AppException.InvalidData(
          "Saved artwork document '$documentId' has an invalid '$fieldName' field"
      )
}
