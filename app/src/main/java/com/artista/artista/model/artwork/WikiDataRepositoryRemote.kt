// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.model.artwork

import com.artista.artista.model.artist.Artist
import com.artista.artista.model.error.AppException
import java.text.Normalizer
import java.util.LinkedHashMap
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * User agent sent with every request to Wikimedia services (Wikidata and Commons). Wikimedia's
 * user-agent policy rejects generic clients, so requests identify the app and link to its project.
 */
const val WIKIMEDIA_USER_AGENT = "Artista/1.0 (https://github.com/cs311-artista/artista)"

/**
 * Thread-safe, bounded cache for parsed Wikidata query results.
 *
 * @param maxEntries maximum number of query results to retain
 * @param ttlNanos duration for which a result remains valid
 * @param clock monotonic time source used to determine result age
 * @author IJJA3141
 */
internal class ArtworkQueryCache(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val ttlNanos: Long = DEFAULT_TTL_NANOS,
    private val clock: () -> Long = System::nanoTime,
) {
  private data class Entry(val artworks: List<Artwork>, val cachedAtNanos: Long)

  private val entries = LinkedHashMap<String, Entry>(16, 0.75f, true)

  init {
    require(maxEntries > 0) { "maxEntries must be positive" }
    require(ttlNanos > 0) { "ttlNanos must be positive" }
  }

  @Synchronized
  fun get(query: String): List<Artwork>? {
    val entry = entries[query] ?: return null
    if (clock() - entry.cachedAtNanos >= ttlNanos) {
      entries.remove(query)
      return null
    }
    return entry.artworks.toList()
  }

  @Synchronized
  fun findArtwork(uid: ArtworkId): Artwork? {
    val now = clock()
    val iterator = entries.entries.iterator()
    var matchedQuery: String? = null
    var matchedArtwork: Artwork? = null
    while (iterator.hasNext()) {
      val (query, entry) = iterator.next()
      if (now - entry.cachedAtNanos >= ttlNanos) {
        iterator.remove()
      } else {
        entry.artworks
            .firstOrNull { it.uid == uid }
            ?.let {
              matchedQuery = query
              matchedArtwork = it
            }
      }
    }
    matchedQuery?.let { entries[it] }
    return matchedArtwork
  }

  @Synchronized
  fun put(query: String, artworks: List<Artwork>) {
    entries[query] = Entry(artworks.toList(), clock())
    while (entries.size > maxEntries) {
      entries.remove(entries.keys.first())
    }
  }

  private companion object {
    const val DEFAULT_MAX_ENTRIES = 100
    const val DEFAULT_TTL_NANOS = 5 * 60 * 1_000_000_000L
  }
}

/**
 * Constants for Wikidata item identifiers.
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
  private val artworkQueryCache = ArtworkQueryCache()

  @Deprecated(
      "This function will be moved outside of WikiDataRepository since the popularity isn't a Wikidata metric."
  )
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
   * /!\ Current implementation of the parser needs to be updates not all information retrieved from
   * Wikidata will be present in the artwork.
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

    // if artwork is cached return cached artwork (even if it was from another query)
    artworkQueryCache.findArtwork(uid)?.let {
      return it
    }

    val query = buildArtworkByIdQuery(uid)

    // Reuse the shared request, parsing, and caching path so later lookups can use this result.
    val artworks = fetchArtworks(query, cacheEmptyResults = false)
    return artworks.firstOrNull() ?: throw AppException.NotFound("No artwork found for uid $uid")
  }

  /**
   * Returns artworks around a geographic search area filtered by artwork type.
   *
   * /!\ Current implementation of the parser needs to be updates not all information retrieved from
   * Wikidata will be present in the artwork.
   *
   * @param searchArea geographic bounds used to locate matching artworks
   * @param typeValues artwork classes to include in the query
   * @param limit maximum number of results returned by the query
   * @return matching artworks mapped from valid Wikidata results, at most [limit] and unique by uid
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
    return fetchArtworks(query, limit = limit)
  }

  /**
   * Searches for artworks by label using the English-language Wikidata data set.
   *
   * /!\ Current implementation of the parser needs to be updates not all information retrieved from
   * Wikidata will be present in the artwork.
   *
   * The search is intentionally limited to English strings to keep the user-facing result set
   * stable and avoid mixing localized labels from other languages.
   *
   * Results are ordered by relevance: artworks with an image first, then by how well the title and
   * artist match the search terms (accent/case-insensitive, word-prefix matching). Ordering is
   * applied only to the (at most [limit]) items selected by the Wikidata search; it cannot surface
   * a better match that the remote search did not return.
   *
   * @param artworkTitle text to search for in the artwork title
   * @return the artworks whose English title contains the search term, ordered by relevance, unique
   *   by uid and at most [limit]
   * @throws AppException.InvalidInput if the title is blank or contains no supported characters
   * @author IJJA3141
   */
  override suspend fun searchArtworks(
      artworkTitle: String,
      typeValues: List<ArtworkType>,
      limit: Int,
  ): List<Artwork> {
    val normalizedTitle = normalizeSearchText(artworkTitle)

    validateTypeValues(typeValues)
    validateLimit(limit)

    val query = buildArtworkSearchQuery(prefixTerm(normalizedTitle), typeValues, limit)
    return fetchArtworks(query, searchTermForOrdering = normalizedTitle, limit = limit)
  }

  companion object {
    val ITEM_ID_REGEX = Regex("Q\\d+")
    val COMBINING_MARKS = Regex("\\p{M}+")
    private val WHITESPACE = Regex("\\s+")
    private val UNSUPPORTED_SEARCH_CHARACTERS = Regex("[^\\p{L}\\p{M}\\p{N}\\s]+")

    const val MAX_LIMIT = 200
    const val ROW_FAN_OUT = 10 // generous: observed fan-out is ~1-2 rows per artwork
    const val MAX_ROWS = 2_000 // absolute ceiling against runaway queries

    internal fun rowGuard(limit: Int): Int = minOf(limit * ROW_FAN_OUT, MAX_ROWS)

    // Relevance scoring: lower score = more relevant. Artist matches weigh more than title matches
    // so that searching a title is not outranked by an artist whose name merely resembles a term.
    private const val MIN_TOKEN_LENGTH = 2
    private const val TITLE_TOKEN_BONUS = 10
    private const val ARTIST_TOKEN_BONUS = 20
    private const val EXACT_TITLE_BONUS = 100
    private const val TITLE_PREFIX_BONUS = 30
    private const val NO_TITLE_PENALTY = 150
    private const val NO_ARTIST_PENALTY = 100
    private const val NO_LOCATION_PENALTY = 5

    const val SELECT_LINE =
        "SELECT ?item ?itemLabel ?artistLabel ?image ?inception ?collectionLabel ?locationLabel ?width ?height ?description ?locationCoordinates ?coordinateSource"

    val DETAILS =
        """
      OPTIONAL { ?item wdt:${WikiDataProperty.COORDINATES} ?ownCoordinates . }
      OPTIONAL {
        ?item wdt:${WikiDataProperty.LOCATION} ?location .
        OPTIONAL { ?location rdfs:label ?locationLabel . FILTER(LANG(?locationLabel) = "en") }
        OPTIONAL { ?location wdt:${WikiDataProperty.COORDINATES} ?locationSideCoordinates . }
      }
      OPTIONAL { ?item rdfs:label ?itemLabel . FILTER(LANG(?itemLabel) = "en") }
      OPTIONAL { ?item wdt:${WikiDataProperty.ARTIST} ?artist . ?artist rdfs:label ?artistLabel . FILTER(LANG(?artistLabel) = "en") }
      OPTIONAL { ?item wdt:${WikiDataProperty.IMAGE} ?image . }
      OPTIONAL { ?item wdt:${WikiDataProperty.INCEPTION_DATE} ?inception . }
      OPTIONAL { ?item wdt:${WikiDataProperty.COLLECTION} ?collection . ?collection rdfs:label ?collectionLabel . FILTER(LANG(?collectionLabel) = "en") }
      OPTIONAL { ?item wdt:${WikiDataProperty.WIDTH} ?width . }
      OPTIONAL { ?item wdt:${WikiDataProperty.HEIGHT} ?height . }
      OPTIONAL { ?item schema:description ?description . FILTER(LANG(?description) = "en") }
      BIND(COALESCE(?ownCoordinates, ?locationSideCoordinates) AS ?locationCoordinates)
      BIND(IF(BOUND(?ownCoordinates), "item",
           IF(BOUND(?locationSideCoordinates), "location", ?unbound)) AS ?coordinateSource)
      """
            .trimIndent()
  }

  private fun validateLimit(limit: Int) {
    if (limit !in 1..MAX_LIMIT) {
      throw AppException.InvalidInput("limit must be between 1 and $MAX_LIMIT")
    }
  }

  /**
   * Runs [query] (or serves it from cache) and post-processes the rows.
   *
   * The outer SPARQL LIMIT is [${rowGuard(limit)}] so that the DETAILS fan-out rows (multiple
   * labels, widths, collections) are not truncated before parsing. Rows are therefore deduplicated
   * by uid, optionally ordered by relevance, and capped to [limit] here. The processed list is what
   * gets cached.
   */
  private suspend fun fetchArtworks(
      query: String,
      cacheEmptyResults: Boolean = true,
      searchTermForOrdering: String? = null,
      limit: Int? = null,
  ): List<Artwork> {
    artworkQueryCache.get(query)?.let {
      return it
    }

    var artworks =
        jsonResponseParser.parseArtworkResults(executeSparqlRequest(query)).distinctBy { it.uid }
    if (searchTermForOrdering != null) {
      artworks = artworks.orderBySearchRelevance(searchTermForOrdering)
    }
    if (limit != null) {
      artworks = artworks.take(limit)
    }

    if (cacheEmptyResults || artworks.isNotEmpty()) {
      artworkQueryCache.put(query, artworks)
    }

    return artworks
  }

  /**
   * Orders artworks by relevance to [searchTerm], which must already be normalized (see
   * [normalizeSearchText]). Artworks with an image come first, then lower scores, and ties keep
   * their original order (the sort is stable).
   */
  private fun List<Artwork>.orderBySearchRelevance(searchTerm: String): List<Artwork> {
    val foldedTerm = fold(searchTerm)
    val allTokens = foldedTerm.split(WHITESPACE).filter { it.isNotEmpty() }
    // Ignore very short tokens ("a", "of") which would match nearly everything, unless the query
    // consists only of such tokens.
    val tokens = allTokens.filter { it.length >= MIN_TOKEN_LENGTH }.ifEmpty { allTokens }
    // Word-prefix match: the token must start at a word boundary.
    val tokenRegexes = tokens.map {
      Regex("(?<![\\p{L}\\p{N}])${Regex.escape(it)}", RegexOption.IGNORE_CASE)
    }

    fun score(artwork: Artwork): Int {
      var score = 0

      val title = artwork.name?.let(::fold)
      if (title == null) {
        score += NO_TITLE_PENALTY
      } else {
        tokenRegexes.forEach { if (it.containsMatchIn(title)) score -= TITLE_TOKEN_BONUS }
        if (title == foldedTerm) score -= EXACT_TITLE_BONUS
        else if (title.startsWith(foldedTerm)) score -= TITLE_PREFIX_BONUS
      }

      val artistName = artwork.artist?.name?.let(::fold)
      if (artistName == null) {
        score += NO_ARTIST_PENALTY
      } else {
        tokenRegexes.forEach { if (it.containsMatchIn(artistName)) score -= ARTIST_TOKEN_BONUS }
      }

      if (artwork.location == null) score += NO_LOCATION_PENALTY

      return score
    }

    // Score each artwork once instead of on every comparison.
    return map { artwork -> Triple(artwork.imageUrl.isNullOrBlank(), score(artwork), artwork) }
        .sortedWith(compareBy<Triple<Boolean, Int, Artwork>>({ it.first }, { it.second }))
        .map { it.third }
  }

  private fun validateTypeValues(typeValues: List<ArtworkType>) {
    if (typeValues.isEmpty()) {
      throw AppException.InvalidInput("typeValues cannot be empty")
    }
    if (typeValues.distinct().size != typeValues.size) {
      throw AppException.InvalidInput("typeValues contains duplicates")
    }
  }

  // Removes unsupported characters and collapses whitespace. The result is plain text (no wildcards
  // or boolean syntax), so it is safe to use for relevance ordering; use [prefixTerm] to turn it
  // into a Wikidata search expression.
  private fun normalizeSearchText(searchTerm: String): String {
    // Keep Unicode letters, combining marks, numbers, and whitespace; remove other characters.
    val sanitizedSearchTerm = UNSUPPORTED_SEARCH_CHARACTERS.replace(searchTerm, "")
    if (sanitizedSearchTerm.isBlank()) {
      throw AppException.InvalidInput("artworkTitle is blank or contains no supported characters")
    }

    // Split on whitespace and rejoin with single spaces to normalize the query.
    val normalizedSearchTerm =
        sanitizedSearchTerm.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
    return normalizedSearchTerm
  }

  // Lowercases and strips diacritics so comparisons are case/accent-insensitive.
  private fun fold(value: String): String =
      Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
          .replace(COMBINING_MARKS, "")

  // Appends * to the complete search query (and also matches the accent-folded form).
  private fun prefixTerm(query: String): String {
    val lower = query.lowercase(Locale.ROOT)
    val folded = fold(query)
    fun wild(value: String) = if (value.length >= 2) "$value*" else value
    return if (folded == lower) wild(lower) else "(${wild(folded)} OR ${wild(lower)})"
  }

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
  LIMIT ${rowGuard(limit)}
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
    LIMIT 1
    """
          .trimIndent()

  private fun buildArtworkSearchQuery(
      searchExpression: String,
      typeValues: List<ArtworkType>,
      limit: Int,
  ): String {
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
              mwapi:gsrsearch "haswbstatement:$wikidataTypeFilter $searchExpression" ;
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
      LIMIT ${rowGuard(limit)}
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
                .header("User-Agent", WIKIMEDIA_USER_AGENT)
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
