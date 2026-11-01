// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.model.artwork

import com.artista.artista.model.artist.Artist
import com.artista.artista.model.error.AppException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Wikidata property identifiers used by the repository queries.
 *
 * Each value maps to a WikiData P-number used in SPARQL filters and OPTIONAL clauses.
 *
 * @property id unique Wikidata property identifier
 * @author IJJA3141
 */
enum class WikiDataProperty(val id: String) {
  INSTANCE_OF("P31"),
  SUBCLASS_OF("P279"),
  TITLE("P1476"),
  ARTIST("P170"),
  IMAGE("P18"),
  INCEPTION_DATE("P571"),
  COLLECTION("P195"),
  LOCATION("P276"),
  COORDINATES("P625"),
  WIDTH("P2049"),
  HEIGHT("P2048");

  override fun toString(): String = id
}

/**
 * Remote implementation of [WikiDataRepository] backed by the Wikidata SPARQL API.
 *
 * Search requests first find a limited set of matching paintings in a subquery, then fetch their
 * optional details. The class hierarchy path starts from the fixed painting class to avoid scanning
 * all items for their instance-of statements.
 *
 * @param client HTTP client used to execute requests; inject a test client to intercept requests
 * @param endpoint Wikidata SPARQL endpoint URL
 * @param jsonResponseParser JSON to artista.Artwork parser
 * @author IJJA3141
 */
class WikiDataRepositoryRemote(
    private val client: OkHttpClient = OkHttpClient(),
    private val endpoint: HttpUrl = "https://query.wikidata.org/sparql".toHttpUrl(),
    private val jsonResponseParser: JsonResponseParser = JsonResponseParser(),
) : WikiDataRepository {

  override suspend fun getPopularArtists(): List<Artist> {
    throw NotImplementedError("Not yet implemented")
  }

  /**
   * Retrieves a single artwork by its Wikidata identifier.
   *
   * WikiData can return duplicate rows for the same artwork when multiple labels or measurements
   * are present. The first parsed row is used because the repository contract expects a single
   * artwork instance for a given identifier.
   *
   * @param uid Wikidata item identifier for the artwork, such as Q12345
   * @return the artwork matching the identifier
   * @throws AppException.InvalidInput if the identifier is blank
   * @throws AppException.NotFound if no artwork matches the identifier
   * @author IJJA3141
   */
  override suspend fun getArtwork(uid: ArtworkId): Artwork {
    if (uid.isBlank()) throw AppException.InvalidInput("uid is blank")
    if (!uid.matches(ITEM_ID_REGEX)) {
      throw AppException.InvalidInput("uid is not a valid Wikidata item ID: $uid")
    }

    val query = buildArtworkByIdQuery(uid)
    val artworks = jsonResponseParser.parseArtworkResults(executeSparqlRequest(query))
    return artworks.firstOrNull() ?: throw AppException.NotFound("No artwork found for uid $uid")
  }

  /**
   * Returns artworks around a geographic search area filtered by artwork type.
   *
   * @param searchArea geographic bounds used to locate matching artworks
   * @param typeValues artwork classes to include in the query
   * @param limit maximum number of results returned by the query
   * @return matching artworks mapped from valid Wikidata results
   * @author IJJA3141
   */
  override suspend fun getArtworksNearLocation(
      searchArea: SearchArea,
      typeValues: List<ArtworkType>,
      limit: Int,
  ): List<Artwork> {
    validateTypeValues(typeValues)
    validateLimit(limit)

    val query = buildArtworkNearLocationQuery(searchArea, typeValues, limit)
    return jsonResponseParser.parseArtworkResults(executeSparqlRequest(query))
  }

  /**
   * Searches for artworks by label using the English-language Wikidata data set.
   *
   * The search is intentionally limited to English strings to keep the user-facing result set
   * stable and avoid mixing localized labels from other languages.
   *
   * @param artworkTitle text to search for in the artwork title
   * @return the artworks whose English title contains the search term
   * @author IJJA3141
   */
  override suspend fun searchArtworks(
      artworkTitle: String,
      typeValues: List<ArtworkType>,
      limit: Int,
  ): List<Artwork> {
    val sanitizedTitle = artworkTitle.trim()
    if (sanitizedTitle.isEmpty()) return emptyList()

    validateTypeValues(typeValues)
    validateLimit(limit)

    val query = buildArtworkSearchQuery(sanitizedTitle, typeValues, limit)
    return jsonResponseParser.parseArtworkResults(executeSparqlRequest(query))
  }

  companion object {
    val ITEM_ID_REGEX = Regex("Q\\d+")
    const val MAX_LIMIT = 200

    const val SELECT_LINE =
        "SELECT ?item ?itemLabel ?artistLabel ?image ?inception ?collectionLabel ?locationLabel ?locationCoordinates ?width ?height ?description"

    val DETAILS =
        """
      OPTIONAL { ?item wdt:${WikiDataProperty.ARTIST} ?artist . ?artist rdfs:label ?artistLabel . FILTER(LANG(?artistLabel) = "en") }
      OPTIONAL { ?item wdt:${WikiDataProperty.IMAGE} ?image . }
      OPTIONAL { ?item wdt:${WikiDataProperty.INCEPTION_DATE} ?inception . }
      OPTIONAL { ?item wdt:${WikiDataProperty.COLLECTION} ?collection . ?collection rdfs:label ?collectionLabel . FILTER(LANG(?collectionLabel) = "en") }
      OPTIONAL { ?item wdt:${WikiDataProperty.LOCATION} ?location . ?location rdfs:label ?locationLabel . FILTER(LANG(?locationLabel) = "en") }
      OPTIONAL { ?item wdt:${WikiDataProperty.LOCATION} ?locationCoordinates . }
      OPTIONAL { ?item wdt:${WikiDataProperty.WIDTH} ?width . }
      OPTIONAL { ?item wdt:${WikiDataProperty.HEIGHT} ?height . }
      OPTIONAL { ?item schema:description ?description . FILTER(LANG(?description) = "en") }
      """
            .trimIndent()
  }

  private fun validateLimit(limit: Int) {
    if (limit !in 1..MAX_LIMIT) {
      throw AppException.InvalidInput("limit must be between 1 and $MAX_LIMIT")
    }
  }

  private fun validateTypeValues(typeValues: List<ArtworkType>) {
    if (typeValues.isEmpty()) {
      throw AppException.InvalidInput("typeValues cannot be empty")
    }
    if (typeValues.distinct().size != typeValues.size) {
      throw AppException.InvalidInput("typeValues contains duplicates")
    }
  }

  private fun sanitizeSearchText(value: String): String =
      value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ").trim()

  private fun List<ArtworkType>.toSparqlValueList(): String =
      joinToString(separator = " ") { "wd:${it.qid}" }

  private fun buildArtworkNearLocationQuery(
      searchArea: SearchArea,
      typeValues: List<ArtworkType>,
      limit: Int,
  ): String {
    return """
      $SELECT_LINE
      WHERE {
        {
          SELECT DISTINCT ?item
          WHERE {
            SERVICE wikibase:around {
              ?location wdt:${WikiDataProperty.COORDINATES} ?locationCoordinates .
              bd:serviceParam wikibase:center "Point(${searchArea.longitude} ${searchArea.latitude})"^^geo:wktLiteral .
              bd:serviceParam wikibase:radius "${searchArea.radiusKm}" .
            }
            ?item wdt:${WikiDataProperty.LOCATION} ?location .
            VALUES ?type { ${typeValues.toSparqlValueList()} }
            ?item wdt:${WikiDataProperty.INSTANCE_OF} ?type .
          }
          LIMIT $limit
        }
        $DETAILS
      }
    """
        .trimIndent()
  }

  private fun buildArtworkByIdQuery(uid: ArtworkId): String =
      """
      $SELECT_LINE
      WHERE {
        VALUES ?item { wd:$uid }
        $DETAILS
      }
      """
          .trimIndent()

  private fun buildArtworkSearchQuery(
      artworkTitle: String,
      typeValues: List<ArtworkType>,
      limit: Int,
  ): String {
    val safeTitle = sanitizeSearchText(artworkTitle)
    val wikidataTypeFilter =
        typeValues.joinToString(separator = "|") { "${WikiDataProperty.INSTANCE_OF}=${it.qid}" }

    return """
      $SELECT_LINE
      WHERE {
        {
          SELECT DISTINCT ?item ?itemLabel
          WHERE {
            SERVICE wikibase:mwapi {
              bd:serviceParam wikibase:endpoint "www.wikidata.org" ;
                              wikibase:api "Generator" ;
                              mwapi:generator "search" ;
                              mwapi:gsrsearch "haswbstatement:$wikidataTypeFilter $safeTitle" ;
                              mwapi:gsrlimit "$limit" .
              ?item wikibase:apiOutputItem mwapi:title .
            }
            ?item rdfs:label ?itemLabel .
            FILTER(LANG(?itemLabel) = "en")
          }
          LIMIT $limit
        }
        $DETAILS
      }
    """
        .trimIndent()
  }

  internal suspend fun executeSparqlRequest(sparqlQuery: String): String =
      withContext(Dispatchers.IO) {
        val url =
            endpoint
                .newBuilder()
                .addQueryParameter("format", "json")
                .addQueryParameter("query", sparqlQuery)
                .build()
        val request =
            Request.Builder()
                .url(url)
                .header("Accept", "application/sparql-results+json")
                .header("User-Agent", "Artista/1.0 (https://github.com/cs311-artista/artista)")
                .get()
                .build()

        try {
          client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
              throw AppException.Network(
                  "Wikidata request failed with status ${response.code}: ${response.message}"
              )
            }
            response.body?.string()
                ?: throw AppException.InvalidData("Wikidata response body was empty")
          }
        } catch (exception: AppException) {
          throw exception
        } catch (exception: CancellationException) {
          throw exception
        } catch (exception: Exception) {
          throw AppException.Network("Unable to read Wikidata response", exception)
        }
      }
}
