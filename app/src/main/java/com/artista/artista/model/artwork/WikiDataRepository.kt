// Co-authored-by: Copilot
package com.artista.artista.model.artwork

/**
 * Retrieves artwork search results from Wikidata.
 *
 * Implementations translate the search text into Wikidata requests and map valid results to
 * [Artwork] values.
 *
 * @author IJJA3141
 */
interface WikiDataRepository {
  /**
   * Searches Wikidata for artworks matching the provided text.
   *
   * @param query the user's search text, before Wikidata-specific query construction
   * @return matching artworks mapped from valid Wikidata results
   * @author IJJA3141
   */
  suspend fun searchArtworks(query: String): List<Artwork>
}
