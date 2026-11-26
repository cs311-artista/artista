// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.model.artwork

import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artist.ArtistCandidate
import com.artista.artista.model.artist.ArtistId
import com.artista.artista.model.artist.ArtistOccupation
import com.artista.artista.model.artist.RemoteArtistRepository
import com.artista.artista.model.error.AppException
import java.text.Normalizer
import java.util.Locale
import kotlin.collections.forEach
import kotlin.collections.ifEmpty
import kotlin.collections.map
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.text.startsWith
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException

/**
 * User agent sent with every request to Wikimedia services (Wikidata and Commons). Wikimedia's
 * user-agent policy rejects generic clients, so requests identify the app and link to its project.
 */
const val WIKIMEDIA_USER_AGENT = "Artista/1.0 (https://github.com/cs311-artista/artista)"

/**
 * Constants for Wikidata item identifiers.
 *
 * Each value maps to a WikiData P-number used in SPARQL filters and OPTIONAL clauses.
 *
 * @property qid unique Wikidata property identifier
 * @author IJJA3141
 */
enum class WikiDataProperty(val qid: String) {
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
  HEIGHT("P2048"),
  OCCUPATION("P106"),
  DATE_OF_BIRTH("P569"),
  DATE_OF_DEATH("P570"),
  PLACE_OF_BIRTH("P19"),
  PLACE_OF_DEATH("P20"),
  CITIZENSHIP("P27"),
  MOVEMENT("P135");

  override fun toString(): String = qid
}

enum class WikiDataItems(val qid: String) {
  HUMAN("Q5");

  override fun toString(): String = qid
}

/**
 * Implementation of [RemoteArtistRepository] and [RemoteArtworkRepository] backed by the Wikidata
 * SPARQL API.
 *
 * Search requests first find a limited set of matching paintings in a subquery, then fetch their
 * optional details. The class hierarchy path starts from the fixed painting class to avoid scanning
 * all items for their instance-of statements.
 *
 * @param client HTTP client used to execute requests; inject a test client to intercept requests
 * @param jsonResponseParser JSON to artista.Artwork parser
 * @author IJJA3141
 */
class WikiDataRepositoryRemote(
    private val client: OkHttpClient =
        OkHttpClient().newBuilder().callTimeout(30, java.util.concurrent.TimeUnit.SECONDS).build(),
    private val jsonResponseParser: JsonResponseParser = JsonResponseParser(),
    private val caches: WikiDataCaches = WikiDataCaches(),
) : WikiDataRepository {
  private val guard: ThrottleGuard = ThrottleGuard()

  // RemoteArtworkRepository
  override suspend fun getArtworksNearLocation(
      searchArea: SearchArea,
      typeValues: List<ArtworkType>,
      limit: Int,
  ): List<Artwork> {
    validateTypeValues(typeValues)
    validateLimit(limit)

    val key = SearchAreaKey(searchArea, typeValues.toSet(), limit)

    return caches.nearLocationArtworks.getOrLoad(key) {
      val query = buildArtworkNearLocationQuery(searchArea, typeValues, limit)
      val body = executeRequest(buildSparqlRequest(query))
      val artworks = jsonResponseParser.parseArtworkResults(body).toList()
      caches.rememberArtworks(artworks)
      artworks
    }
  }

  override suspend fun getArtworkCandidates(
      query: String,
      typeValues: List<ArtworkType>,
      limit: Int,
  ): List<ArtworkCandidate> {
    val text = query.trim()
    if (text.isEmpty()) return emptyList()

    validateTypeValues(typeValues)
    validateLimit(limit)

    val key = ArtworkQueryKey(cacheText(text), typeValues.toSet(), limit)

    return caches.artworkCandidates.getOrLoad(key) {
      val url = buildMediaWikiQuery(text, max(limit * 5, 50))
      val body = executeRequest(url, accept = "application/json")
      val unfilteredCandidates = jsonResponseParser.parseArtworkCandidates(body)
      filterArtworkCandidate(unfilteredCandidates, typeValues).take(limit)
    }
  }

  override suspend fun getArtworksFromQuery(
      searchQuery: String,
      typeValues: List<ArtworkType>,
      limit: Int,
  ): List<Artwork> {
    val normalizedQuery = normalizeSearchText(searchQuery)
    validateTypeValues(typeValues)
    validateLimit(limit)

    val key = ArtworkQueryKey(cacheText(normalizedQuery), typeValues.toSet(), limit)

    return caches.searchedArtworks.getOrLoad(key) {
      val prefixedTerm = prefixTerm(normalizedQuery)
      val query = buildArtworkSearchQuery(prefixedTerm, typeValues, limit)
      val request = buildSparqlRequest(query)
      val body = executeRequest(request)
      val ordered =
          jsonResponseParser
              .parseArtworkResults(body)
              .distinctBy { it.uid }
              .orderBySearchRelevance(normalizedQuery)
              .take(limit)
              .toList()
      caches.rememberArtworks(ordered)
      ordered
    }
  }

  override suspend fun getArtwork(qid: ArtworkId): Artwork? {
    if (qid.isBlank()) throw AppException.InvalidInput("uid is blank")
    if (!qid.matches(ITEM_ID_REGEX)) {
      throw AppException.InvalidInput("uid is not a valid Wikidata item ID: $qid")
    }

    caches.artworksById.get(qid)?.let {
      return it
    }

    val query =
        """$ARTWORK_SELECT_LINE
                     WHERE {
                        VALUES ?item { wd:$qid }
                        $ARTWORK_DETAILS
                     }
                     LIMIT 1"""
            .trimIndent()

    val request = buildSparqlRequest(query)
    val body = executeRequest(request)
    val artwork =
        jsonResponseParser.parseArtworkResults(body).firstOrNull()
            ?: throw AppException.NotFound("No artwork found for uid $qid") // not cached
    caches.artworksById.put(qid, artwork)
    return artwork
  }

  // RemoteArtistRepository
  override suspend fun getArtistCandidates(query: String, limit: Int): List<ArtistCandidate> {
    val text = query.trim()
    if (text.isEmpty()) return emptyList()

    validateLimit(limit)

    val key = ArtistQueryKey(cacheText(text), limit)

    return caches.artistCandidates.getOrLoad(key) {
      val url = buildMediaWikiQuery(text, max(limit * 5, 50))
      val body = executeRequest(url, accept = "application/json")
      val unfilteredCandidates = jsonResponseParser.parseArtistCandidates(body)
      filterArtistCandidate(unfilteredCandidates).take(limit)
    }
  }

  override suspend fun getArtistsFromQuery(
      query: String,
      limit: Int,
  ): List<Artist> {
    val normalizedQuery = normalizeSearchText(query)
    validateLimit(limit)

    val key = ArtistQueryKey(cacheText(query), limit)

    return caches.searchedArtists.getOrLoad(key) {
      val query = buildArtistQuery(normalizedQuery, limit)
      val request = buildSparqlRequest(query)
      val body = executeRequest(request)
      val artists = jsonResponseParser.parseArtistsResults(body).orderByNameMatch(normalizedQuery)
      caches.rememberArtist(artists)
      artists
    }
  }

  override suspend fun getArtist(qid: ArtistId): Artist? {
    if (!qid.matches(ITEM_ID_REGEX)) throw AppException.InvalidInput("uid is invalid")

    caches.artistsById.get(qid)?.let {
      return it
    }

    val query =
        """
        $ARTIST_SELECT_LINE
        WHERE {
            VALUES ?artist { wd:$qid }
            $ARTIST_DETAILS
        }
        GROUP BY ?artist
        """
            .trimIndent()

    val request = buildSparqlRequest(query)
    val body = executeRequest(request)
    val artist = jsonResponseParser.parseArtistResults(body)
    artist?.let { caches.artistsById.put(qid, it) }

    return artist
  }

  // private members
  companion object {

    private const val MIN_TOKEN_LENGTH = 2

    // Relevance scoring: higher token bonus = more relevant
    // Bonus (more bonus better)
    private const val TITLE_TOKEN_BONUS = 10
    private const val ARTIST_TOKEN_BONUS = 20
    private const val EXACT_TITLE_BONUS = 100
    private const val TITLE_PREFIX_BONUS = 30

    // Malus (less malus better)
    private const val NO_TITLE_PENALTY = 150
    private const val NO_ARTIST_PENALTY = 100
    private const val NO_LOCATION_PENALTY = 5
    private const val MEDIAWIKI_API_URL = "https://www.wikidata.org/w/api.php"
    private const val WIKIDATA_SPARQL_URL = "https://query.wikidata.org/sparql"

    const val MAX_LIMIT = 200
    val ITEM_ID_REGEX = Regex("Q\\d+")
    val COMBINING_MARKS = Regex("\\p{M}+")
    private val WHITESPACE = Regex("\\s+")

    private val UNSUPPORTED_SEARCH_CHARACTERS = Regex("[^\\p{L}\\p{M}\\p{N}\\s]+")

    // artist
    private val ARTIST_SELECT_LINE =
        """
        SELECT ?artist
                  (SAMPLE(?labelEn) AS ?artistLabel)
                  (SAMPLE(?descEn) AS ?description)
                  (SAMPLE(?img) AS ?image)
                  (SAMPLE(?born) AS ?birthDate)
                  (SAMPLE(?died) AS ?deathDate)
                  (SAMPLE(?birthPlaceEn) AS ?birthPlaceLabel)
                  (SAMPLE(?deathPlaceEn) AS ?deathPlaceLabel)
                  (GROUP_CONCAT(DISTINCT ?occupationEn; separator=" | ") AS ?occupations)
                  (GROUP_CONCAT(DISTINCT ?citizenshipEn; separator=" | ") AS ?citizenships)
                  (GROUP_CONCAT(DISTINCT ?movementEn; separator=" | ") AS ?movements)
        """
            .trimIndent()

    private val ARTIST_DETAILS =
        """
          OPTIONAL { ?artist rdfs:label ?labelEn . FILTER(LANG(?labelEn) = "en") }
          OPTIONAL { ?artist schema:description ?descEn . FILTER(LANG(?descEn) = "en") }
          OPTIONAL { ?artist wdt:${WikiDataProperty.IMAGE} ?img . }
          OPTIONAL { ?artist wdt:${WikiDataProperty.DATE_OF_BIRTH} ?born . }
          OPTIONAL { ?artist wdt:${WikiDataProperty.DATE_OF_DEATH} ?died . }
          OPTIONAL { ?artist wdt:${WikiDataProperty.PLACE_OF_BIRTH} ?birthPlace . ?birthPlace rdfs:label ?birthPlaceEn . FILTER(LANG(?birthPlaceEn) = "en") }
          OPTIONAL { ?artist wdt:${WikiDataProperty.PLACE_OF_DEATH} ?deathPlace . ?deathPlace rdfs:label ?deathPlaceEn . FILTER(LANG(?deathPlaceEn) = "en") }
          OPTIONAL { ?artist wdt:${WikiDataProperty.OCCUPATION} ?o . ?o rdfs:label ?occupationEn . FILTER(LANG(?occupationEn) = "en") }
          OPTIONAL { ?artist wdt:${WikiDataProperty.CITIZENSHIP} ?c . ?c rdfs:label ?citizenshipEn . FILTER(LANG(?citizenshipEn) = "en") }
          OPTIONAL { ?artist wdt:${WikiDataProperty.MOVEMENT} ?m . ?m rdfs:label ?movementEn . FILTER(LANG(?movementEn) = "en") }
      """
            .trimIndent()

    const val ARTWORK_SELECT_LINE =
        "SELECT ?item ?itemLabel ?artistLabel ?image ?inception ?collectionLabel ?locationLabel ?width ?height ?description ?locationCoordinates ?coordinateSource"

    val ARTWORK_DETAILS =
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

    const val ROW_FAN_OUT = 10 // generous: observed fan-out is ~1-2 rows per artwork
    const val MAX_ROWS = 2_000 // absolute ceiling against runaway queries

    internal fun rowGuard(limit: Int): Int = minOf(limit * ROW_FAN_OUT, MAX_ROWS)
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

  /**
   * Orders artist by relevance to [searchTerm], which must already be normalized (see
   * [normalizeSearchText]).
   */
  private fun List<Artist>.orderByNameMatch(searchTerm: String): List<Artist> {
    val tokens = fold(searchTerm).split(WHITESPACE).filter { it.isNotEmpty() }
    val (named, others) =
        partition { artist ->
          val name = fold(artist.name)
          tokens.all { it in name }
        }
    return named + others
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

  private fun buildArtworkSearchQuery(
      searchExpression: String,
      typeValues: List<ArtworkType>,
      limit: Int,
  ): String {
    val wikidataTypeFilter =
        typeValues.joinToString(separator = "|") {
          "${com.artista.artista.model.artwork.WikiDataProperty.INSTANCE_OF}=${it.qid}"
        }

    return """
      $ARTWORK_SELECT_LINE
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
        $ARTWORK_DETAILS
      }
      LIMIT ${WikiDataRepositoryRemote.rowGuard(limit)}
    """
        .trimIndent()
  }

  private fun buildArtworkNearLocationQuery(
      searchArea: SearchArea,
      typeValues: List<ArtworkType>,
      limit: Int,
  ): String =
      """
        $ARTWORK_SELECT_LINE
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
    $ARTWORK_DETAILS
  }
  LIMIT ${rowGuard(limit)}
    """
          .trimIndent()

  private fun buildArtistQuery(query: String, limit: Int) =
      """
    $ARTIST_SELECT_LINE
    WHERE {
      {
        SELECT DISTINCT ?artist ?artistLabel
        WHERE {
          SERVICE wikibase:mwapi {
            bd:serviceParam wikibase:endpoint "www.wikidata.org" ;
            wikibase:api "Generator" ;
            mwapi:generator "search" ;
            mwapi:gsrsearch "haswbstatement:${WikiDataProperty.INSTANCE_OF}=${WikiDataItems.HUMAN} $query" ;
            mwapi:gsrlimit "$limit" .
            ?artist wikibase:apiOutputItem mwapi:title .
          }
          ?artist rdfs:label ?artistLabel .
          FILTER(LANG(?artistLabel) = "en")
          FILTER EXISTS {
                  ?artist wdt:${WikiDataProperty.OCCUPATION} ?occupation .
                  VALUES ?occupation { ${
              ArtistOccupation.entries.map { it.qid }.joinToString(" ") { "wd:$it" }
          } }
      }
      FILTER EXISTS { ?work wdt:${WikiDataProperty.ARTIST} ?artist . }
        }
        LIMIT $limit
      }
      $ARTIST_DETAILS
    }
    GROUP BY ?artist
    LIMIT ${rowGuard(limit)}"""
          .trimIndent()

  internal fun buildMediaWikiQuery(query: String, limit: Int): HttpUrl =
      MEDIAWIKI_API_URL.toHttpUrl()
          .newBuilder()
          .addQueryParameter("action", "wbsearchentities")
          .addQueryParameter("search", query)
          .addQueryParameter("language", "en")
          .addQueryParameter("uselang", "en")
          .addQueryParameter("type", "item")
          .addQueryParameter("limit", limit.toString())
          .addQueryParameter("format", "json")
          .build()

  private suspend fun filterArtistCandidate(
      candidates: List<ArtistCandidate>,
  ): List<ArtistCandidate> {
    if (candidates.isEmpty()) return emptyList()

    val query =
        """
    SELECT DISTINCT ?artist WHERE {
      VALUES ?artist { ${candidates.map { it.qid }.joinToString(" ") { "wd:$it" }} }
      ?artist wdt:${WikiDataProperty.INSTANCE_OF} wd:${WikiDataItems.HUMAN} .
      FILTER EXISTS {
              ?artist wdt:${WikiDataProperty.OCCUPATION} ?occupation .
              VALUES ?occupation { ${ArtistOccupation.entries.map { it.qid }.joinToString(" ") { "wd:$it" }} }
      }
    }
  """
            .trimIndent()

    val url = buildSparqlRequest(query)
    val body = executeRequest(url)
    val qids = jsonResponseParser.parseFilteredArtists(body)

    return candidates.filter { it.qid in qids }
  }

  private suspend fun filterArtworkCandidate(
      candidates: List<ArtworkCandidate>,
      typeValues: List<ArtworkType>,
  ): List<ArtworkCandidate> {
    if (candidates.isEmpty()) return emptyList()

    if (!candidates.all { it.qid.matches(ITEM_ID_REGEX) })
        throw AppException.InvalidInput("invalid candidate id")

    val query =
        """
      SELECT ?artwork WHERE {
        VALUES ?artwork { ${candidates.map{it.qid}.joinToString(" ") { "wd:$it" }} }
        FILTER EXISTS {
          ?artwork wdt:${WikiDataProperty.INSTANCE_OF} ?type .
          VALUES ?type { ${typeValues.toSparqlValueList()} }
        }
      }
  """
            .trimIndent()
    val url = buildSparqlRequest(query)
    val body = executeRequest(url)
    val qids = jsonResponseParser.parseFilteredArtworks(body)

    return candidates.filter { it.qid in qids }
  }

  private fun buildSparqlRequest(sparqlQuery: String): HttpUrl =
      WIKIDATA_SPARQL_URL.toHttpUrl()
          .newBuilder()
          .addQueryParameter("format", "json")
          .addQueryParameter("query", sparqlQuery)
          .build()

  private suspend fun executeRequest(
      url: HttpUrl,
      accept: String = "application/sparql-results+json",
  ): String {
    guard.check()

    return withContext(Dispatchers.IO) {
      val request =
          Request.Builder()
              .url(url)
              .header("Accept", accept)
              .header("User-Agent", WIKIMEDIA_USER_AGENT)
              .get()
              .build()

      try {
        client.newCall(request).await().use { response ->
          if (!response.isSuccessful) {
            guard.record(response.code, response.header("Retry-After")?.toLongOrNull())

            throw AppException.Network(
                "Wikidata GET request failed with status ${response.code}: ${response.message}"
            )
          }
          response.body?.string()
              ?: throw AppException.InvalidData("Wikidata GET response body was empty")
        }
      } catch (exception: AppException) {
        throw exception
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: Exception) {
        throw AppException.Network("Unable to read Wikidata GET response", exception)
      }
    }
  }
}

/**
 * Stops the app from sending requests to a rate-limited service for a while after the service asked
 * it to slow down.
 *
 * Wikimedia services answer 429 (Too Many Requests) or 503 (Service Unavailable), usually with a
 * `Retry-After` header, when a client sends too much. Clients that ignore this and keep sending
 * requests can be banned for a long time, so after such a response the guard rejects further calls
 * locally, with no network traffic, until the requested delay has passed.
 *
 * Usage: call [check] before every request, and [record] with the status code and the parsed
 * `Retry-After` value of every response, successful or not.
 *
 * Behavior:
 * - Only 429 and 503 start a block. Every other status, including 500, is ignored, and none of them
 *   lifts a block that is already active.
 * - A block lasts `Retry-After` seconds, or [DEFAULT_BLOCK_SECONDS] when the header is missing or
 *   is not a number of seconds (for example an HTTP date). The value is clamped to
 *   [MIN_BLOCK_SECONDS]..[MAX_BLOCK_SECONDS], so a bad header can neither disable the guard nor
 *   block the app for days.
 * - A later response never shortens an active block. A longer one extends it.
 * - Time comes from a monotonic clock, so changing the device date or time has no effect.
 * - It is thread safe.
 *
 * Each Wikimedia service enforces its own limits, so use one instance per service (for example one
 * for the SPARQL endpoint and one for the MediaWiki API) so that a block on one does not stop calls
 * to the other.
 *
 * @param clock monotonic time source in nanoseconds, replaceable in tests
 */
class ThrottleGuard(private val clock: () -> Long = System::nanoTime) {
  // Guarded by `this`. Null until the first block: the origin of a monotonic clock is arbitrary
  // (it can even be negative), so no Long constant can stand for "never blocked".
  private var blockedUntilNanos: Long? = null

  /**
   * Fails fast while a block is active.
   *
   * @throws AppException.Network if the service asked the app to wait and the delay has not passed
   */
  @Synchronized
  fun check() {
    val until = blockedUntilNanos ?: return
    // Compared by difference so the check stays correct if the clock value ever wraps around.
    if (until - clock() > 0) {
      throw AppException.Network("Wikidata is rate limiting this app, try again shortly")
    }
    blockedUntilNanos = null
  }

  /**
   * Records the outcome of a request and starts or extends a block if the service asked for one.
   *
   * @param code HTTP status code of the response
   * @param retryAfterSeconds value of the `Retry-After` header in seconds, or null if the header is
   *   missing or could not be read as a number of seconds
   */
  @Synchronized
  fun record(code: Int, retryAfterSeconds: Long?) {
    if (code != HTTP_TOO_MANY_REQUESTS && code != HTTP_SERVICE_UNAVAILABLE) return

    val seconds =
        (retryAfterSeconds ?: DEFAULT_BLOCK_SECONDS).coerceIn(MIN_BLOCK_SECONDS, MAX_BLOCK_SECONDS)
    val until = clock() + seconds * NANOS_PER_SECOND

    val current = blockedUntilNanos
    if (current == null || until - current > 0) blockedUntilNanos = until
  }

  companion object {
    /** Block length used when the response has no usable `Retry-After`. */
    internal const val DEFAULT_BLOCK_SECONDS = 60L

    /** Shortest block, so `Retry-After: 0` or a negative value still pauses briefly. */
    internal const val MIN_BLOCK_SECONDS = 1L

    /** Longest block, so an absurd `Retry-After` cannot lock the app out for days. */
    internal const val MAX_BLOCK_SECONDS = 3_600L

    private const val NANOS_PER_SECOND = 1_000_000_000L
    private const val HTTP_TOO_MANY_REQUESTS = 429
    private const val HTTP_SERVICE_UNAVAILABLE = 503
  }
}

/**
 * Executes this call without blocking a thread and cancels the underlying HTTP request when the
 * calling coroutine is cancelled.
 */
internal suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
  continuation.invokeOnCancellation { cancel() }
  enqueue(
      object : Callback {
        override fun onResponse(call: Call, response: Response) {
          // The coroutine may have been cancelled while the response was in flight.
          if (continuation.isCancelled) response.close() else continuation.resume(response)
        }

        override fun onFailure(call: Call, e: IOException) {
          if (!continuation.isCancelled) continuation.resumeWithException(e)
        }
      }
  )
}
