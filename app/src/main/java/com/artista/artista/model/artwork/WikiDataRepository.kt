// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.model.artwork

import com.artista.artista.model.artist.Artist
import com.artista.artista.model.error.AppException

/**
 * Wikidata artwork classes used when filtering search results.
 *
 * Each entry carries the Wikidata item identifier used in SPARQL queries.
 *
 * @property qid unique Wikidata QID for the artwork class
 * @author IJJA3141
 */
enum class ArtworkType(val qid: String) {
  PAINTING("Q3305213"),
  DRAWING("Q93184"),
  SCULPTURE("Q860861"),
  PRINT("Q11060274"),
  PHOTOGRAPH("Q125191"),
  MURAL("Q219423"),
  OIL_PAINTING("Q174705"),
  WATERCOLOR_PAINTING("Q18761202"),
  MOSAIC("Q133067");

  override fun toString(): String = qid
}

/**
 * Geographic search bounds used to query artworks near a point.
 *
 * @property latitude latitude in degrees, between -90 and 90
 * @property longitude longitude in degrees, between -180 and 180
 * @property radiusKm search radius in kilometres, between 0 and 50
 * @author IJJA3141
 */
data class SearchArea(val latitude: Double, val longitude: Double, val radiusKm: Double = 5.0) {
  init {
    require(latitude in -90.0..90.0 && longitude in -180.0..180.0) { "invalid coordinates" }
    require(radiusKm in 0.0..MAX_RADIUS_KM) { "radius must be within 0..$MAX_RADIUS_KM km" }
  }

  companion object {
    const val MAX_RADIUS_KM = 50.0
  }
}

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
   * Returns artworks around a specific geographic search area.
   *
   * @param searchArea geographic bounds used to locate matching artworks
   * @param typeValues artwork classes to include in the query
   * @param limit maximum number of artworks returned by the SPARQL query
   * @return artworks matching the search area and type filters
   * @author patrickmcdan, IJJA3141
   */
  suspend fun getArtworksNearLocation(
      searchArea: SearchArea,
      typeValues: List<ArtworkType> = ArtworkType.entries,
      limit: Int = 50,
  ): List<Artwork>

  /**
   * Returns the artists suggested by the application.
   *
   * @return popular artists in display order
   * @author patrickmcdan
   */
  @Deprecated("This function will be moved outside of WikiDataRepository since the popularity isn't a Wikidata metric.")
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
   * @param artworkTitle the title fragment to search for in Wikidata
   * @param typeValues artwork classes to include in the search
   * @param limit maximum number of matches returned
   * @return matching artworks mapped from valid Wikidata results
   * @author IJJA3141
   */
  suspend fun searchArtworks(
      artworkTitle: String,
      typeValues: List<ArtworkType> = ArtworkType.entries,
      limit: Int = 50,
  ): List<Artwork>
}
