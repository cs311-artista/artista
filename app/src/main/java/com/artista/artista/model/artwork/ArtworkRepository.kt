// Co-authored-by: Gemini & Copilot
package com.artista.artista.model.artwork

/**
 * Contract defining persistent storage operations for artworks saved by the user.
 *
 * ViewModels interact with this abstraction rather than concrete implementations to maintain clean
 * separation between UI state management and persistence
 *
 * @author hixeum, IJJA3141
 */
interface ArtworkRepository {

  /**
   * Checks whether an artwork with the specified identifier is currently bookmarked or saved.
   *
   * @param artworkName unique name or title identifying the artwork
   * @return true if the artwork is already stored, false otherwise
   * @throws IllegalArgumentException if [artworkName] is blank
   * @author hixeum,
   */
  suspend fun isArtworkSaved(artworkName: String): Boolean

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
   * @param artworkName unique name or title of the artwork to remove
   * @throws IllegalArgumentException if [artworkName] is blank
   * @throws NoSuchElementException if no artwork matches [artworkName]
   * @author hixeum,
   */
  suspend fun removeArtwork(artworkName: String)

  /**
   * Retrieves artworks saved by the current user.
   *
   * @return the current user's saved artworks
   * @author IJJA3141
   */
  suspend fun getSavedArtworks(): List<Artwork>
}
