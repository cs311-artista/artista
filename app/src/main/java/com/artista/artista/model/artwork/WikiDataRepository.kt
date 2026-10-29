// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.model.artwork

import com.artista.artista.model.artist.Artist
import com.artista.artista.model.error.AppException
import com.artista.artista.ui.map.UserLocation

/**
 * Repository for retrieving artwork metadata from Wikidata.
 *
 * Implementations translate user input into SPARQL requests, keep the result set anchored to
 * English-language labels for a consistent UI, and map valid rows into [Artwork] instances.
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
   * @return popular artists in display order
   * @author patrickmcdan
   */
  suspend fun getPopularArtists(): List<Artist>

  /**
   * Returns the [Artwork] associated with the provided Wikidata identifier.
   *
   * Wikidata may return duplicate rows for the same item when multiple labels or measurements are
   * associated with the same record. Implementations may therefore normalize the result by keeping
   * a single representative artwork for the requested [uid].
   *
   * @param uid unique identifier of the artwork, such as a Wikidata item ID
   * @return the [Artwork] requested via the [uid]
   * @throws AppException.NotFound if no [Artwork] matching [uid] was found
   * @throws AppException.InvalidInput if the [uid] is blank or otherwise invalid
   * @author IJJA3141
   */
  suspend fun getArtwork(uid: ArtworkId): Artwork

  /**
   * Searches Wikidata for artworks matching the provided text.
   *
   * Implementations may restrict matching to English labels to keep results stable across locales,
   * while still allowing additional filters to be added later.
   *
   * @param query the user's search text before the repository turns it into a Wikidata query
   * @return matching artworks mapped from valid Wikidata results
   * @author IJJA3141
   */
  suspend fun searchArtworks(query: String): List<Artwork>
}
