package com.artista.artista.model.artwork

import com.artista.artista.model.error.AppException
import kotlin.math.pow
import kotlin.math.round

/**
 * A matching Artwork returned as a recommendation for search.
 *
 * @property qid item identifier, such as `Q5593`
 * @property label artist's display name
 * @property description optional description of the matching item
 * @author IJJA3141
 */
data class ArtworkCandidate(
    val qid: ArtworkId,
    val label: String,
    val description: String?,
)

/**
 * Geographic search bounds used to query artworks near a point.
 *
 * The center is deliberately imprecise: [latitude] and [longitude] are snapped to a grid of 0.01
 * degrees (about 1.1 km north-south and 0.8 km east-west or less at European latitudes). Two
 * positions inside the same grid cell therefore produce equal [SearchArea] instances, which lets
 * callers use the area as a cache key and still get a hit after the user moves a short distance.
 * The snapped center can differ from the requested one by at most about 0.8 km.
 *
 * Construct it with `SearchArea(latitude, longitude, radiusKm)`. The rounding happens there, and
 * the primary constructor is private so an unsnapped instance cannot exist.
 *
 * @property latitude snapped latitude in degrees, between -90 and 90
 * @property longitude snapped longitude in degrees, between -180 and 180
 * @property radiusKm search radius in kilometres, between 0 and 50; not rounded
 * @author IJJA3141
 */
@ConsistentCopyVisibility
data class SearchArea
private constructor(val latitude: Double, val longitude: Double, val radiusKm: Double) {
  init {
    require(latitude in -90.0..90.0 && longitude in -180.0..180.0) { "invalid coordinates" }
    require(radiusKm in 0.0..MAX_RADIUS_KM) { "radius must be within 0..$MAX_RADIUS_KM km" }
  }

  companion object {
    const val MAX_RADIUS_KM = 50.0

    /** Number of decimal places kept for latitude and longitude (2 is about 1 km). */
    const val COORDINATE_DECIMALS = 2

    private val SCALE = 10.0.pow(COORDINATE_DECIMALS) // must equal 10^COORDINATE_DECIMALS

    /**
     * Creates an area centered on the given point, snapped to the coordinate grid.
     *
     * @param latitude requested latitude in degrees
     * @param longitude requested longitude in degrees
     * @param radiusKm search radius in kilometres
     * @throws IllegalArgumentException if a value is out of range or not a number
     */
    operator fun invoke(latitude: Double, longitude: Double, radiusKm: Double = 5.0): SearchArea =
        SearchArea(snap(latitude), snap(longitude), radiusKm)

    // Dividing by SCALE gives the nearest double to the decimal value, so 46.5197 becomes 46.52
    // and not 46.519999999999996. Adding 0.0 turns -0.0 into 0.0, because data class equality
    // would otherwise treat the two as different cache keys.
    private fun snap(value: Double): Double = round(value * SCALE) / SCALE + 0.0
  }
}

/**
 * Repository for retrieving artwork data from remote source.
 *
 * Failures are reported as [AppException]. Besides the cases documented on each function, any
 * operation can fail with another [AppException] type when the backend cannot be reached or refuses
 * the request.
 *
 * @author IJJA3141
 */
interface RemoteArtworkRepository {
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
   * Returns suggestion for [query].
   *
   * @param query search string
   * @param typeValues list of artwork types to filter
   * @param limit max return
   * @return candidates to the suggestion
   * @author IJJA3141
   */
  suspend fun getArtworkCandidates(
      query: String,
      typeValues: List<ArtworkType>,
      limit: Int = 5,
  ): List<ArtworkCandidate>

  /**
   * Searches remote source for artworks matching the provided text.
   *
   * Implementations may restrict matching to English labels to keep results stable across locales,
   * while still allowing additional filters to be added later.
   *
   * @param query the title fragment to search for in source
   * @param typeValues artwork classes to include in the search
   * @param limit maximum number of matches returned
   * @return matching artworks mapped from valid source results
   * @author IJJA3141
   */
  suspend fun getArtworksFromQuery(
      searchQuery: String,
      typeValues: List<ArtworkType> = ArtworkType.entries,
      limit: Int = 50,
  ): List<Artwork>

  /**
   * Returns the [Artwork] associated with the provided identifier.
   *
   * Wikidata may return duplicate rows for the same item when multiple labels or measurements are
   * associated with the same record. Implementations may therefore normalize the result by keeping
   * a single representative artwork for the requested [qid].
   *
   * @param qid unique identifier of the artwork
   * @return the [Artwork] requested via the [qid]
   * @throws AppException.NotFound if no [Artwork] matching [qid] was found
   * @throws AppException.InvalidInput if the [qid] is blank or otherwise invalid
   * @author IJJA3141
   */
  suspend fun getArtwork(qid: ArtworkId): Artwork?
}
