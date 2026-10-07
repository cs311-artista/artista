// Co-authored-by: Copilot
package com.artista.artista.model.artwork

/**
 * Provides artwork search results to the rest of the application.
 *
 * @author IJJA3141
 */
interface ArtworkRepository {
  /**
   * Searches for artworks matching a user-provided query.
   *
   * Implementations delegate source-specific request construction and return mapped domain
   * artworks.
   *
   * @param query the user's search text
   * @return matching artworks
   * @author IJJA3141
   */
  suspend fun searchArtworks(query: String): List<Artwork>
}
