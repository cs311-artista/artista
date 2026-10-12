// Co-authored-by: Gemini & Copilot
package com.artista.artista.model.artwork

/**
 * Contract defining persistent storage operations for artworks saved by the user.
 *
 * ViewModels interact with this abstraction rather than concrete implementations to maintain clean
 * separation between UI state management and persistence
 *
 * @author hixeum, IJJA3141, 5kyPhy
 */
interface SavedArtworksRepository {

  /**
   * Checks whether an artwork with the specified identifier is currently bookmarked or saved.
   *
   * @param uid unique identifier of the artwork
   * @return true if the artwork is already saved, false otherwise
   * @throws IllegalArgumentException if [uid] is blank
   * @author hixeum,
   */
  suspend fun isArtworkSaved(uid: ArtworkId): Boolean

  /**
   * Persists an artwork to storage, updating any existing record with the same identifier.
   *
   * @param artwork the complete artwork entity to persist
   * @throws IllegalArgumentException if the artwork title is invalid
   * @author hixeum,
   */
  suspend fun saveArtwork(artwork: Artwork)

  /**
   * Deletes an artwork from storage using its unique identifier.
   *
   * @param uid unique identifier of the artwork to remove
   * @throws IllegalArgumentException if [uid] is blank
   * @throws NoSuchElementException if no artwork matches [uid]
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
