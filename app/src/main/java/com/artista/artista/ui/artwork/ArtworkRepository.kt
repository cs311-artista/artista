// Co-authored-by: Gemini <gemini@google.com>
package com.artista.artista.model.artwork

/**
 * Contract defining persistent storage operations for artworks saved by the user.
 *
 * ViewModels interact with this abstraction rather than concrete implementations to maintain clean
 * separation between UI state management and persistence[cite: 1].
 *
 * @author hixeum
 */
interface ArtworkRepository {

  /**
   * Checks whether an artwork with the specified identifier is currently bookmarked or saved.
   *
   * @param artworkName unique name or title identifying the artwork
   * @return true if the artwork is already stored, false otherwise
   * @throws IllegalArgumentException if [artworkName] is blank
   */
  suspend fun isArtworkSaved(artworkName: String): Boolean

  /**
   * Persists an artwork to storage, updating any existing record with the same identifier.
   *
   * @param artwork the complete artwork entity to persist
   * @throws IllegalArgumentException if the artwork title is invalid
   */
  suspend fun saveArtwork(artwork: Artwork)

  /**
   * Deletes an artwork from storage using its unique identifier.
   *
   * @param artworkName unique name or title of the artwork to remove
   * @throws IllegalArgumentException if [artworkName] is blank
   * @throws NoSuchElementException if no artwork matches [artworkName]
   */
  suspend fun removeArtwork(artworkName: String)
}
