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
   * Returns the [Artwork] specified by [uid]
   *
   * @param uid unique identifier of the artwork
   * @return the [Artwork] requested via the [uid]
   * @throws NoSuchElementException if no [Artwork] matching [uid] was found
   * @throws IllegalArgumentException if the [uid] is invalid
   * @author 5kyPhy
   */
  suspend fun getArtwork(uid: ArtworkId): Artwork

  /**
   * Searches Wikidata for artworks matching the provided text.
   *
   * @param query the user's search text, before Wikidata-specific query construction
   * @return matching artworks mapped from valid Wikidata results
   * @author IJJA3141
   */
  suspend fun searchArtworks(query: String): List<Artwork>
}
