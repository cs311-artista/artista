// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.model.artwork

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
 * Remote implementation of [WikiDataRepository] backed by the Wikidata SPARQL API.
 *
 * Search requests first find a limited set of matching paintings in a subquery, then fetch their
 * optional details. The class hierarchy path starts from the fixed painting class to avoid scanning
 * all items for their instance-of statements.
 *
 * @param client HTTP client used to execute requests; inject a test client to intercept requests
 * @param endpoint Wikidata SPARQL endpoint URL
 * @author Copilot
 */
class WikiDataRepositoryRemote(
    private val client: OkHttpClient = OkHttpClient(),
    private val endpoint: HttpUrl = "https://query.wikidata.org/sparql".toHttpUrl(),
) : WikiDataRepository {

  override suspend fun getArtwork(uid: ArtworkId): Artwork {
    if (uid.isBlank()) throw AppException.InvalidInput("uid is blank")

    val query =
        """
        SELECT ?item ?itemLabel ?artistLabel ?image ?inception ?collectionLabel ?locationLabel ?locationCoordinates ?width ?height ?description
        WHERE {
          VALUES ?item { wd:$uid }
          OPTIONAL { ?item rdfs:label ?itemLabel . FILTER(LANG(?itemLabel) = "en" || LANG(?itemLabel) = "") }
          OPTIONAL { ?item wdt:P170 ?artist . ?artist rdfs:label ?artistLabel . FILTER(LANG(?artistLabel) = "en") }
          OPTIONAL { ?item wdt:P18 ?image . }
          OPTIONAL { ?item wdt:P571 ?inception . }
          OPTIONAL { ?item wdt:P195 ?collection . ?collection rdfs:label ?collectionLabel . FILTER(LANG(?collectionLabel) = "en") }
          OPTIONAL { ?item wdt:P276 ?location . ?location rdfs:label ?locationLabel . FILTER(LANG(?locationLabel) = "en") }
          OPTIONAL { ?item wdt:P625 ?locationCoordinates . }
          OPTIONAL { ?item wdt:P2049 ?width . }
          OPTIONAL { ?item wdt:P2048 ?height . }
          OPTIONAL { ?item schema:description ?description . FILTER(LANG(?description) = "en") }
        }
        """.trimIndent()

    val response = executeSparqlRequest(query)
    val artworks = parseArtworkResults(response)

    // it might append that the api replies with the same uid multiple times, so we just return the first one
    // this happens when the artwork has multiple labels or size (it is weird that WikiData stores the same artwork multiple times)
    return artworks.firstOrNull() ?: throw AppException.NotFound("No artwork found for uid $uid")
  }

    // only searches in English for now.
  override suspend fun searchArtworks(query: String): List<Artwork> {
    if (query.isBlank()) return emptyList()
    val request = buildRequest(query)
    val response = executeSparqlRequest(request)
    return parseArtworkResults(response)
  }

  // For now only filters by label, but we can add more filters later (like artist, collection, etc.)
  internal fun buildRequest(searchTerm: String): String {
    val normalized = searchTerm.trim()
    val escapedTerm =
        normalized
            .lowercase(Locale.ROOT)
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")

    return """
      SELECT ?item ?itemLabel ?artistLabel ?image ?inception ?collectionLabel ?locationLabel ?locationCoordinates ?width ?height ?description
      WHERE {
        {
          SELECT DISTINCT ?item ?itemLabel
          WHERE {
            wd:Q3305213 ^wdt:P279*/^wdt:P31 ?item .
            ?item rdfs:label ?itemLabel .
            FILTER(LANG(?itemLabel) = "en")
            FILTER(CONTAINS(LCASE(STR(?itemLabel)), "$escapedTerm"))
          }
          LIMIT 50
        }
        OPTIONAL { ?item wdt:P170 ?artist . ?artist rdfs:label ?artistLabel . FILTER(LANG(?artistLabel) = "en") }
        OPTIONAL { ?item wdt:P18 ?image . }
        OPTIONAL { ?item wdt:P571 ?inception . }
        OPTIONAL { ?item wdt:P195 ?collection . ?collection rdfs:label ?collectionLabel . FILTER(LANG(?collectionLabel) = "en") }
        OPTIONAL { ?item wdt:P276 ?location . ?location rdfs:label ?locationLabel . FILTER(LANG(?locationLabel) = "en") }
        OPTIONAL { ?item wdt:P625 ?locationCoordinates . }
        OPTIONAL { ?item wdt:P2049 ?width . }
        OPTIONAL { ?item wdt:P2048 ?height . }
        OPTIONAL { ?item schema:description ?description . FILTER(LANG(?description) = "en") }
      }
      """.trimIndent()
  }

  private suspend fun executeSparqlRequest(sparqlQuery: String): String =
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
                .header("User-Agent", "Artista/1.0")
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

  internal fun parseArtworkResults(responseBody: String): List<Artwork> {
    throw NotImplementedError("Parsing of Wikidata response is not implemented yet")
  }
}