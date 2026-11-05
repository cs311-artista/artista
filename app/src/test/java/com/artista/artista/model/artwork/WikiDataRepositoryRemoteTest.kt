// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.model.artwork

import com.artista.artista.model.error.AppException
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

/**
 * Unit tests for [WikiDataRepositoryRemote].
 *
 * @author IJJA3141
 */
class WikiDataRepositoryRemoteTest {
  class fakeParser : JsonResponseParser() {
    override fun parseArtworkResults(responseBody: String): List<Artwork> {
      println("Response body: $responseBody")
      return listOf()
    }
  }

  private class SuccessfulParser : JsonResponseParser() {
    override fun parseArtworkResults(responseBody: String): List<Artwork> =
        listOf(Artwork("Q1", "Artwork", null, null, null, null, null, null))
  }

  private class EmptyParser : JsonResponseParser() {
    override fun parseArtworkResults(responseBody: String): List<Artwork> = emptyList()
  }

  @Ignore(
      "This test performs real network requests to the Wikidata SPARQL endpoint. It is intended to verify the correctness of the SPARQL query and the ability to fetch data from the remote API."
  )
  @Test
  fun runRealRequestForArtwork() = runTest {
    val repository = WikiDataRepositoryRemote(jsonResponseParser = fakeParser())

    try {
      repository.getArtwork("Q12418") // Mona Lisa
    } catch (e: NotImplementedError) {
      // Handle NotImplementedError if needed
    } catch (e: Exception) {
      println(e)
    }
  }

  @Ignore(
      "This test performs real network requests to the Wikidata SPARQL endpoint. It is intended to verify the correctness of the SPARQL query and the ability to fetch data from the remote API."
  )
  @Test
  fun runRealRequestForQuery() = runTest {
    val repository = WikiDataRepositoryRemote(jsonResponseParser = fakeParser())

    try {
      repository.searchArtworks("Mona Lisa")
    } catch (e: NotImplementedError) {
      // Handle NotImplementedError if needed
    } catch (e: Exception) {
      println(e)
    }
  }

  @Ignore(
      "This test performs real network requests to the Wikidata SPARQL endpoint. It is intended to verify the correctness of the SPARQL query and the ability to fetch data from the remote API."
  )
  @Test
  fun runRealRequestForLocation() = runTest {
    val repository = WikiDataRepositoryRemote(jsonResponseParser = fakeParser())
    val area = SearchArea(latitude = 46.5, longitude = 6.6, radiusKm = 12.0)

    try {
      repository.getArtworksNearLocation(area)
    } catch (e: NotImplementedError) {
      // Handle NotImplementedError if needed
    } catch (e: Exception) {
      println(e)
    }
  }

  @Test
  fun wikidataEnums_haveValidUniqueIdentifiers() {
    val itemIds = ArtworkType.entries.map { it.qid }
    val propertyIds = WikiDataProperty.entries.map { it.id }

    itemIds.forEach { assertTrue("$it is not an item ID", it.matches(Regex("Q\\d+"))) }
    propertyIds.forEach { assertTrue("$it is not a property ID", it.matches(Regex("P\\d+"))) }
    assertEquals("Artwork type IDs must be unique", itemIds.size, itemIds.toSet().size)
    assertEquals("Property IDs must be unique", propertyIds.size, propertyIds.toSet().size)
    assertEquals("P31", WikiDataProperty.INSTANCE_OF.toString())
  }

  @Test
  fun getArtwork_whenUidIsNotAnExactItemId_throwsInvalidInputWithoutRequest() = runTest {
    val client = testClient()
    val repository = WikiDataRepositoryRemote(client)

    listOf(
            "",
            " ",
            "Q123\n",
            " Q123",
            "Q123 ",
            "Q-1",
            "Q1.5",
            "QQ1",
            "Q 1",
            "P31",
            "wd:Q123",
            "Q\u0661\u0662\u0663",
        )
        .forEach { uid ->
          assertThrowsSuspend<AppException.InvalidInput> { repository.getArtwork(uid) }
        }

    assertNull("invalid IDs must not trigger a request", client.lastRequest)
  }

  @Test
  fun getArtwork_sendsExpectedQueryAndHeaders() = runTest {
    val client = testClient()
    val repository = WikiDataRepositoryRemote(client)

    assertThrowsSuspend<AppException.NotFound> { repository.getArtwork("Q12418") }

    val request = requireNotNull(client.lastRequest)
    val sparql = requireNotNull(request.url.queryParameter("query"))
    assertEquals("GET", request.method)
    assertEquals("json", request.url.queryParameter("format"))
    assertEquals("application/sparql-results+json", request.header("Accept"))
    assertTrue(request.header("User-Agent")!!.startsWith("Artista/1.0"))
    assertTrue(sparql.contains("VALUES ?item { wd:Q12418 }"))
    assertTrue(sparql.contains("FILTER(LANG(?itemLabel) = \"en\")"))
    assertTrue(sparql.contains("BIND(COALESCE(?ownCoordinates, ?locationSideCoordinates)"))
    assertFalse(sparql.contains("?pinOwner"))
    assertFalse(sparql.contains("?pinOwnerLabel"))
    assertTrue("getArtwork should limit duplicate rows", sparql.trimEnd().endsWith("LIMIT 1"))
  }

  @Test
  fun getArtwork_whenServerReturnsError_throwsNetwork() = runTest {
    val repository = WikiDataRepositoryRemote(testClient(statusCode = 500))

    assertThrowsSuspend<AppException.Network> { repository.getArtwork("Q12418") }
  }

  @Test
  fun searchArtworks_reusesCachedResultsForTheSameQuery() = runTest {
    val client = testClient()
    val repository = WikiDataRepositoryRemote(client, jsonResponseParser = SuccessfulParser())

    val first = repository.searchArtworks("Mona Lisa")
    val second = repository.searchArtworks("Mona Lisa")

    assertEquals(first, second)
    assertEquals(1, client.requestCount)
  }

  @Test
  fun getArtwork_returnsMatchingArtworkFromCachedSearchResults() = runTest {
    val client = testClient()
    val repository = WikiDataRepositoryRemote(client, jsonResponseParser = SuccessfulParser())

    repository.searchArtworks("Artwork")
    val artwork = repository.getArtwork("Q1")

    assertEquals("Q1", artwork.uid)
    assertEquals("Artwork", artwork.name)
    assertEquals(1, client.requestCount)
  }

  @Test
  fun searchArtworks_doesNotReuseResultsForDifferentQueries() = runTest {
    val client = testClient()
    val repository = WikiDataRepositoryRemote(client, jsonResponseParser = SuccessfulParser())

    repository.searchArtworks("Mona Lisa")
    repository.searchArtworks("The Starry Night")

    assertEquals(2, client.requestCount)
  }

  @Test
  fun searchArtworks_cachesEmptyResults() = runTest {
    val client = testClient()
    val repository = WikiDataRepositoryRemote(client, jsonResponseParser = EmptyParser())

    assertTrue(repository.searchArtworks("No matching artwork").isEmpty())
    assertTrue(repository.searchArtworks("No matching artwork").isEmpty())

    assertEquals(1, client.requestCount)
  }

  @Test
  fun searchArtworks_doesNotCacheFailedRequests() = runTest {
    val client = testClient(statusCode = 500)
    val repository = WikiDataRepositoryRemote(client)

    repeat(2) {
      assertThrowsSuspend<AppException.Network> { repository.searchArtworks("Mona Lisa") }
    }

    assertEquals(2, client.requestCount)
  }

  @Test
  fun getArtwork_doesNotCacheMissingArtworks() = runTest {
    val client = testClient()
    val repository = WikiDataRepositoryRemote(client)

    repeat(2) { assertThrowsSuspend<AppException.NotFound> { repository.getArtwork("Q12418") } }

    assertEquals(2, client.requestCount)
  }

  @Test
  fun artworkQueryCache_expiresEntriesAfterTheTtl() {
    var nowNanos = 0L
    val cache = ArtworkQueryCache(maxEntries = 2, ttlNanos = 10, clock = { nowNanos })
    cache.put("query", emptyList())
    assertEquals(emptyList<Artwork>(), cache.get("query"))

    nowNanos = 10

    assertNull(cache.get("query"))
  }

  @Test
  fun artworkQueryCache_doesNotReturnMissingOrExpiredArtworks() {
    var nowNanos = 0L
    val cache = ArtworkQueryCache(ttlNanos = 10, clock = { nowNanos })
    val artwork = Artwork("Q1", "Artwork", null, null, null, null, null, null)
    cache.put("query", listOf(artwork))

    assertNull(cache.findArtwork("Q2"))

    nowNanos = 10

    assertNull(cache.findArtwork("Q1"))
  }

  @Test
  fun artworkQueryCache_rejectsNonPositiveCapacityAndTtl() {
    assertThrows<IllegalArgumentException> { ArtworkQueryCache(maxEntries = 0) }
    assertThrows<IllegalArgumentException> { ArtworkQueryCache(ttlNanos = 0) }
  }

  @Test
  fun artworkQueryCache_keepsItsOwnCopyOfArtworkLists() {
    val cache = ArtworkQueryCache()
    val source = mutableListOf(Artwork("Q1", "Artwork", null, null, null, null, null, null))
    cache.put("query", source)
    source.clear()

    assertEquals(1, cache.get("query")?.size)
  }

  @Test
  fun artworkQueryCache_evictsLeastRecentlyUsedEntryWhenFull() {
    val cache = ArtworkQueryCache(maxEntries = 2)
    val artwork = Artwork("Q1", "Artwork", null, null, null, null, null, null)
    cache.put("first", listOf(artwork))
    cache.put("second", emptyList())
    assertEquals(artwork, cache.findArtwork("Q1"))
    cache.put("third", emptyList())

    assertNull(cache.get("second"))
    assertEquals(listOf(artwork), cache.get("first"))
    assertEquals(emptyList<Artwork>(), cache.get("third"))
  }

  @Test
  fun getArtworksNearLocation_buildsQueryForAreaAndTypes() = runTest {
    val client = testClient()
    val repository = WikiDataRepositoryRemote(client)

    assertThrowsSuspend<AppException.NotFound> {
      repository.getArtworksNearLocation(
          searchArea = SearchArea(latitude = 46.5, longitude = 6.6, radiusKm = 12.0),
          typeValues = listOf(ArtworkType.PAINTING, ArtworkType.DRAWING),
          limit = 20,
      )
    }

    val query = requireNotNull(client.lastRequest).url.queryParameter("query")
    assertTrue(query!!.contains("Point(6.6 46.5)"))
    assertTrue(query.contains("wikibase:radius \"12.0\""))
    assertTrue(query.contains("VALUES ?type { wd:Q3305213 wd:Q93184 }"))
    assertTrue(query.contains("?item wdt:P276 ?location"))
    assertTrue(query.contains("LIMIT 20"))
    assertTrue("the outer query should also be limited", query.trimEnd().endsWith("LIMIT 20"))
  }

  @Test
  fun getArtworksNearLocation_rejectsEmptyOrDuplicateTypesAndInvalidLimitsWithoutRequest() =
      runTest {
        val client = testClient()
        val repository = WikiDataRepositoryRemote(client)
        val area = SearchArea(latitude = 46.5, longitude = 6.6)

        assertThrowsSuspend<AppException.InvalidInput> {
          repository.getArtworksNearLocation(area, emptyList(), 50)
        }
        assertThrowsSuspend<AppException.InvalidInput> {
          repository.getArtworksNearLocation(
              area,
              listOf(ArtworkType.PAINTING, ArtworkType.PAINTING),
              50,
          )
        }
        listOf(0, -1, WikiDataRepositoryRemote.MAX_LIMIT + 1).forEach { limit ->
          assertThrowsSuspend<AppException.InvalidInput> {
            repository.getArtworksNearLocation(area, listOf(ArtworkType.PAINTING), limit)
          }
        }

        assertNull("invalid inputs must not trigger a request", client.lastRequest)
      }

  @Test
  fun searchArea_rejectsInvalidCoordinatesAndRadius() = runTest {
    assertThrowsSuspend<IllegalArgumentException> { SearchArea(latitude = 91.0, longitude = 6.6) }
    assertThrowsSuspend<IllegalArgumentException> {
      SearchArea(latitude = 46.5, longitude = -181.0)
    }
    assertThrowsSuspend<IllegalArgumentException> {
      SearchArea(latitude = 46.5, longitude = 6.6, radiusKm = -1.0)
    }
    assertThrowsSuspend<IllegalArgumentException> {
      SearchArea(latitude = 46.5, longitude = 6.6, radiusKm = SearchArea.MAX_RADIUS_KM + 1.0)
    }
    assertThrowsSuspend<IllegalArgumentException> {
      SearchArea(latitude = Double.NaN, longitude = 6.6)
    }
  }

  @Test
  fun searchArtworks_whenQueryIsBlank_throwsInvalidInputWithoutRequest() = runTest {
    val client = testClient()
    val repository = WikiDataRepositoryRemote(client)

    assertThrowsSuspend<AppException.InvalidInput> {
      repository.searchArtworks("   ", listOf(ArtworkType.PAINTING), 50)
    }
    assertNull(client.lastRequest)
  }

  @Test
  fun searchArtworks_buildsQueryWithRequestedTypesLimitAndEnglishLabels() = runTest {
    val client = testClient()
    val repository = WikiDataRepositoryRemote(client)

    assertThrowsSuspend<AppException.NotFound> {
      repository.searchArtworks(
          artworkTitle = "Mona Lisa",
          typeValues = listOf(ArtworkType.PAINTING, ArtworkType.SCULPTURE),
          limit = 25,
      )
    }

    val request = requireNotNull(client.lastRequest)
    val sparql = requireNotNull(request.url.queryParameter("query"))
    assertEquals("GET", request.method)
    assertEquals("json", request.url.queryParameter("format"))
    assertEquals("application/sparql-results+json", request.header("Accept"))
    assertTrue(request.header("User-Agent")!!.startsWith("Artista/1.0"))
    assertTrue(
        sparql.contains("mwapi:gsrsearch \"haswbstatement:P31=Q3305213|P31=Q860861 mona lisa*\"")
    )
    assertTrue(sparql.contains("FILTER(LANG(?itemLabel) = \"en\")"))
    assertTrue(sparql.trimEnd().endsWith("LIMIT 25"))
  }

  @Test
  fun searchArtworks_removesUnsupportedCharactersBeforeBuildingQuery() = runTest {
    val client = testClient()
    val repository = WikiDataRepositoryRemote(client)

    assertThrowsSuspend<AppException.NotFound> {
      repository.searchArtworks("a\\b\"c", listOf(ArtworkType.PAINTING), 10)
    }

    val query = requireNotNull(client.lastRequest).url.queryParameter("query")
    assertTrue(query!!.contains("mwapi:gsrsearch \"haswbstatement:P31=Q3305213 abc*\""))
    assertFalse(query.contains("a\\b"))
  }

  @Test
  fun searchArtworks_whenInputContainsOnlyUnsupportedCharacters_throwsInvalidInputWithoutRequest() =
      runTest {
        val client = testClient()
        val repository = WikiDataRepositoryRemote(client)

        assertThrowsSuspend<AppException.InvalidInput> {
          repository.searchArtworks("!@#$", listOf(ArtworkType.PAINTING), 50)
        }
        assertNull(client.lastRequest)
      }

  @Test
  fun searchArtworks_rejectsEmptyOrDuplicateTypesAndInvalidLimitsWithoutRequest() = runTest {
    val client = testClient()
    val repository = WikiDataRepositoryRemote(client)

    assertThrowsSuspend<AppException.InvalidInput> {
      repository.searchArtworks("Mona Lisa", emptyList(), 50)
    }
    assertThrowsSuspend<AppException.InvalidInput> {
      repository.searchArtworks(
          "Mona Lisa",
          listOf(ArtworkType.PAINTING, ArtworkType.PAINTING),
          50,
      )
    }
    listOf(0, -1, WikiDataRepositoryRemote.MAX_LIMIT + 1).forEach { limit ->
      assertThrowsSuspend<AppException.InvalidInput> {
        repository.searchArtworks("Mona Lisa", listOf(ArtworkType.PAINTING), limit)
      }
    }

    assertNull("invalid inputs must not trigger a request", client.lastRequest)
  }

  @Test
  fun searchArtworks_whenRequestFails_throwsNetworkWithCause() = runTest {
    val client =
        OkHttpClient.Builder().addInterceptor { throw IOException("connection failed") }.build()
    val repository = WikiDataRepositoryRemote(client)

    try {
      repository.searchArtworks("Mona Lisa", listOf(ArtworkType.PAINTING), 50)
      throw AssertionError("Expected AppException.Network")
    } catch (exception: AppException.Network) {
      assertTrue(exception.cause != null)
    }
  }

  private fun testClient(
      statusCode: Int = 200,
      responseBody: String = """{"results":{"bindings":[]}}""",
  ): RecordingHttpClient = RecordingHttpClient(statusCode, responseBody)

  private class RecordingHttpClient(
      var statusCode: Int,
      var responseBody: String,
  ) : OkHttpClient() {
    var lastRequest: Request? = null
      private set

    var requestCount: Int = 0
      private set

    private val delegate =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
              lastRequest = chain.request()
              requestCount++
              Response.Builder()
                  .request(chain.request())
                  .protocol(Protocol.HTTP_1_1)
                  .code(statusCode)
                  .message(if (statusCode in 200..299) "OK" else "Error")
                  .body(responseBody.toResponseBody())
                  .build()
            }
            .build()

    override fun newCall(request: Request) = delegate.newCall(request)
  }

  private suspend inline fun <reified T : Throwable> assertThrowsSuspend(
      crossinline block: suspend () -> Unit
  ) {
    try {
      block()
    } catch (exception: Throwable) {
      if (exception is T) return
      throw AssertionError(
          "Expected ${T::class.simpleName}, got ${exception::class.simpleName}",
          exception,
      )
    }
    throw AssertionError("Expected ${T::class.simpleName} to be thrown")
  }

  private inline fun <reified T : Throwable> assertThrows(block: () -> Unit) {
    try {
      block()
    } catch (exception: Throwable) {
      if (exception is T) return
      throw AssertionError(
          "Expected ${T::class.simpleName}, got ${exception::class.simpleName}",
          exception,
      )
    }
    throw AssertionError("Expected ${T::class.simpleName} to be thrown")
  }
}
