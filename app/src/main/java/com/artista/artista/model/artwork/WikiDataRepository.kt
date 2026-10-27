// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.model.artwork

import com.artista.artista.ui.map.UserLocation

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
   * Returns artworks available near the supplied location.
   *
   * The local implementation currently returns all populated artworks because no distance filtering
   * rule has been defined by the application.
   *
   * @param location reference location, or null when no location is available
   * @return artworks available to display
   * @author patrickmcdan
   */
  suspend fun getArtworksNearLocation(location: UserLocation? = null): List<Artwork>

  /**
   * Returns the artists suggested by the application.
   *
   * @return popular artist names in display order
   * @author patrickmcdan
   */
  suspend fun getPopularArtists(): List<String>

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
