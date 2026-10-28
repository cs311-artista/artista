// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.model.artwork

import android.util.Log
import com.artista.artista.model.error.AppException
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Constants for Wikidata item identifiers.
 *
 * Comments represent WikiData nomenclature for the corresponding item.
 *
 * @author IJJA3141
 */
object WikiDataItems {
  const val PAINTING_CLASS = "Q3305213" // painting
}

/**
 * Constants for Wikidata property identifiers.
 *
 * Comments represent WikiData nomenclature for the corresponding property.
 *
 * @author IJJA3141
 */
object WikiDataProperties {
  const val INSTANCE_OF = "P31"
  const val SUBCLASS_OF = "P279"
  const val TITLE = "P1476"
  const val ARTIST = "P170" // creator
  const val IMAGE = "P18"
  const val INCEPTION_DATE = "P571" // date of foundation or creation
  const val COLLECTION = "P195"
  const val LOCATION = "P276" // located in
  const val COORDINATES = "P625"
  const val WIDTH = "P2049"
  const val HEIGHT = "P2048"
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
 * @author IJJA3141
 */
class WikiDataRepositoryRemote(
    private val client: OkHttpClient = OkHttpClient(),
    private val endpoint: HttpUrl = "https://query.wikidata.org/sparql".toHttpUrl(),
) : WikiDataRepository {

  private companion object {
    /** Wikidata item IDs are "Q" followed by one or more digits, e.g. Q12345. */
    val ITEM_ID_REGEX = Regex("Q\\d+")
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

    val query =
        """
        SELECT ?item ?itemLabel ?artistLabel ?image ?inception ?collectionLabel ?locationLabel ?locationCoordinates ?width ?height ?description
        WHERE {
          VALUES ?item { wd:$uid }
          OPTIONAL { ?item rdfs:label ?itemLabel . FILTER(LANG(?itemLabel) = "en" || LANG(?itemLabel) = "") }
          OPTIONAL { ?item wdt:${WikiDataProperties.ARTIST} ?artist . ?artist rdfs:label ?artistLabel . FILTER(LANG(?artistLabel) = "en") }
          OPTIONAL { ?item wdt:${WikiDataProperties.IMAGE} ?image . }
          OPTIONAL { ?item wdt:${WikiDataProperties.INCEPTION_DATE} ?inception . }
          OPTIONAL { ?item wdt:${WikiDataProperties.COLLECTION} ?collection . ?collection rdfs:label ?collectionLabel . FILTER(LANG(?collectionLabel) = "en") }
          OPTIONAL { ?item wdt:${WikiDataProperties.LOCATION} ?location . ?location rdfs:label ?locationLabel . FILTER(LANG(?locationLabel) = "en") }
          OPTIONAL { ?item wdt:${WikiDataProperties.LOCATION} ?locationCoordinates . }
          OPTIONAL { ?item wdt:${WikiDataProperties.WIDTH} ?width . }
          OPTIONAL { ?item wdt:${WikiDataProperties.HEIGHT} ?height . }
          OPTIONAL { ?item schema:description ?description . FILTER(LANG(?description) = "en") }
        }
        """
            .trimIndent()

    val response = executeSparqlRequest(query)
    val artworks = parseArtworkResults(response)

    return artworks.firstOrNull() ?: throw AppException.NotFound("No artwork found for uid $uid")
  }

  /**
   * Searches for artworks by label using the English-language Wikidata data set.
   *
   * The search is intentionally limited to English strings to keep the user-facing result set
   * stable and avoid mixing localized labels from other languages.
   *
   * @param query text to search for in the artwork title
   * @return the artworks whose English title contains the search term
   * @author IJJA3141
   */
  override suspend fun searchArtworks(query: String): List<Artwork> {
    if (query.isBlank()) return emptyList()
    val request = buildRequest(query)
    val response = executeSparqlRequest(request)
    return parseArtworkResults(response)
  }

  /**
   * Builds a SPARQL query for label-based artwork search.
   *
   * Only English labels are considered for now because the app currently presents artwork titles in
   * a single language; this keeps the query predictable while leaving room to extend the filters
   * later. Only paintings are used for now.
   *
   * @param searchTerm user-entered text used to match artwork titles
   * @return SPARQL query string for the Wikidata endpoint
   * @author IJJA3141
   */
  internal fun buildRequest(searchTerm: String): String {
    val normalized = searchTerm.trim()
    val escapedTerm = normalized.lowercase(Locale.ROOT).replace("\\", "\\\\").replace("\"", "\\\"")

    return """
      SELECT ?item ?itemLabel ?artistLabel ?image ?inception ?collectionLabel ?locationLabel ?locationCoordinates ?width ?height ?description
      WHERE {
        {
          SELECT DISTINCT ?item ?itemLabel
          WHERE {
            wd:${WikiDataItems.PAINTING_CLASS} ^wdt:${WikiDataProperties.SUBCLASS_OF}*/^wdt:${WikiDataProperties.INSTANCE_OF} ?item .
            ?item rdfs:label ?itemLabel .
            FILTER(LANG(?itemLabel) = "en")
            FILTER(CONTAINS(LCASE(STR(?itemLabel)), "$escapedTerm"))
          }
          LIMIT 50
        }
        OPTIONAL { ?item wdt:${WikiDataProperties.ARTIST} ?artist . ?artist rdfs:label ?artistLabel . FILTER(LANG(?artistLabel) = "en") }
        OPTIONAL { ?item wdt:${WikiDataProperties.IMAGE} ?image . }
        OPTIONAL { ?item wdt:${WikiDataProperties.INCEPTION_DATE} ?inception . }
        OPTIONAL { ?item wdt:${WikiDataProperties.COLLECTION} ?collection . ?collection rdfs:label ?collectionLabel . FILTER(LANG(?collectionLabel) = "en") }
        OPTIONAL { ?item wdt:${WikiDataProperties.LOCATION} ?location . ?location rdfs:label ?locationLabel . FILTER(LANG(?locationLabel) = "en") }
        OPTIONAL { ?item wdt:${WikiDataProperties.LOCATION} ?locationCoordinates . }
        OPTIONAL { ?item wdt:${WikiDataProperties.WIDTH} ?width . }
        OPTIONAL { ?item wdt:${WikiDataProperties.HEIGHT} ?height . }
        OPTIONAL { ?item schema:description ?description . FILTER(LANG(?description) = "en") }
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

  /**
   * Parses a SPARQL JSON response into artwork domain objects.
   *
   * This method is intentionally left unimplemented for now; the repository currently delegates the
   * full response parsing to the next implementation step once the API contract is finalized.
   *
   * @param responseBody raw SPARQL JSON body returned by the endpoint
   * @return the artworks encoded in the response
   * @throws NotImplementedError until the parsing logic is implemented
   * @author IJJA3141
   */
  internal fun parseArtworkResults(responseBody: String): List<Artwork> {
    Log.d("Artwork", responseBody) // for test purposes, to see the response from Wikidata
    // will be removed when the parsing is implemented

    throw NotImplementedError("Parsing of Wikidata response is not implemented yet")
  }
}
