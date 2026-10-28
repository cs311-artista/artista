// Co-authored-by: Gemini & Copilot
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.model.artwork

import com.artista.artista.model.error.AppException

/**
 * Contract defining persistent storage operations for artworks saved by the user.
 *
 * ViewModels interact with this abstraction rather than concrete implementations to maintain clean
 * separation between UI state management and persistence.
 *
 * Failures are reported as [AppException]. Besides the cases documented on each function, any
 * operation can fail with another [AppException] type when the backend cannot be reached or refuses
 * the request.
 *
 * @author hixeum, IJJA3141, 5kyPhy
 */
interface SavedArtworksRepository {

  /**
   * Checks whether an artwork with the specified identifier is currently bookmarked or saved.
   *
   * @param uid unique identifier of the artwork
   * @return true if the artwork is already saved, false otherwise
   * @throws AppException.InvalidInput if [uid] is blank
   * @author hixeum,
   */
  suspend fun isArtworkSaved(uid: ArtworkId): Boolean

  /**
   * Persists an artwork to storage, updating any existing record with the same identifier.
   *
   * @param artwork the complete artwork entity to persist
   * @throws AppException.InvalidInput if the artwork uid is blank
   * @author hixeum,
   */
  suspend fun saveArtwork(artwork: Artwork)

  /**
   * Deletes an artwork from storage using its unique identifier.
   *
   * @param uid unique identifier of the artwork to remove
   * @throws AppException.InvalidInput if [uid] is blank
   * @throws AppException.NotFound if no artwork matches [uid]
   * @author hixeum,
   */
  suspend fun removeArtworkFromSaved(uid: ArtworkId)

  /**
   * Retrieves artworks saved by the current user.
   *
   * @return the current user's saved artworks
   * @author IJJA3141
   */
  suspend fun getSavedArtworks(): List<Artwork>
}
