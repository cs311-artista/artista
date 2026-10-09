// Co-authored-by: Copilot
package com.artista.artista.model.artwork

/**
 * Provides artwork search results to the rest of the application.
 *
 * @author IJJA3141
 */
interface ArtworkRepository {
  /**
   * Retrieves artworks saved by the current user.
   *
   * @return the current user's saved artworks
   * @author IJJA3141
   */
  suspend fun getSavedArtworks(): List<Artwork>
}
