// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.model.artwork

import com.artista.artista.model.error.AppException
import com.artista.artista.ui.map.UserLocation

/**
 * Retrieves artwork search results from Wikidata.
 *
 * Implementations translate the search text into Wikidata requests and map valid results to
 * [Artwork] values.
 *
 * Failures are reported as [AppException]. Besides the cases documented on each function, any
 * operation can fail with another [AppException] type when the backend cannot be reached or refuses
 * the request.
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
   * @throws AppException.NotFound if no [Artwork] matching [uid] was found
   * @throws AppException.InvalidInput if the [uid] is invalid
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
