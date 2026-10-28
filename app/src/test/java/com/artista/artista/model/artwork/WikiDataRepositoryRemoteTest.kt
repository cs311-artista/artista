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
  @Ignore(
      "This test performs real network requests to the Wikidata SPARQL endpoint. It is intended to verify the correctness of the SPARQL query and the ability to fetch data from the remote API."
  )
  @Test
  fun runRealRequestForArtwork() = runTest {
    val repository = WikiDataRepositoryRemote()

    try {
      println(repository.getArtwork("Q12418")) // Mona Lisa
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
    val repository = WikiDataRepositoryRemote()

    try {
      println(repository.searchArtworks("Lise Sewing"))
    } catch (e: NotImplementedError) {
      // Handle NotImplementedError if needed
    } catch (e: Exception) {
      println(e)
    }
  }

  @Test
  fun wikiDataConstants_haveExpectedFormatAndAreUnique() {
    assertEquals("Q3305213", WikiDataItems.PAINTING_CLASS)
    assertTrue(WikiDataItems.PAINTING_CLASS.matches(Regex("Q\\d+")))

    val properties =
        listOf(
            WikiDataProperties.INSTANCE_OF,
            WikiDataProperties.SUBCLASS_OF,
            WikiDataProperties.TITLE,
            WikiDataProperties.ARTIST,
            WikiDataProperties.IMAGE,
            WikiDataProperties.INCEPTION_DATE,
            WikiDataProperties.COLLECTION,
            WikiDataProperties.LOCATION,
            WikiDataProperties.COORDINATES,
            WikiDataProperties.WIDTH,
            WikiDataProperties.HEIGHT,
        )

    properties.forEach { assertTrue("$it is not a property ID", it.matches(Regex("P\\d+"))) }
    assertEquals("Property IDs must be unique", properties.size, properties.toSet().size)
  }

  @Test
  fun buildRequest_escapesSearchTermAndUsesEnglishLabels() {
    val repository = WikiDataRepositoryRemote()

    val query = repository.buildRequest("Mona \"Lisa\"")

    assertTrue(query.contains("wd:Q3305213"))
    assertTrue(query.contains("FILTER(LANG(?itemLabel) = \"en\")"))
    assertTrue(query.contains("mona \\\"lisa\\\""))
  }

  @Test
  fun buildRequest_trimsSurroundingWhitespace() {
    val repository = WikiDataRepositoryRemote()

    val padded = repository.buildRequest("  \t Mona Lisa \n ")

    assertEquals(repository.buildRequest("Mona Lisa"), padded)
    assertTrue(padded.contains("\"mona lisa\""))
  }

  @Test
  fun buildRequest_escapesBackslashBeforeQuote() {
    val repository = WikiDataRepositoryRemote()

    // Input is: a\b"c
    val query = repository.buildRequest("a\\b\"c")

    // Expected literal is: "a\\b\"c"
    assertTrue(query.contains("""FILTER(CONTAINS(LCASE(STR(?itemLabel)), "a\\b\"c"))"""))
  }

  @Test
  fun buildRequest_keepsInjectionAttemptInsideStringLiteral() {
    val repository = WikiDataRepositoryRemote()

    val query = repository.buildRequest("x\" ) } UNION { ?s ?p ?o #")

    // The quote is escaped, so everything stays inside the CONTAINS string literal.
    assertTrue(
        query.contains("""FILTER(CONTAINS(LCASE(STR(?itemLabel)), "x\" ) } union { ?s ?p ?o #"))""")
    )
  }

  @Test
  fun buildRequest_hasExpectedStructure() {
    val repository = WikiDataRepositoryRemote()

    val query = repository.buildRequest("Mona Lisa")

    assertTrue(query.startsWith("SELECT"))
    assertEquals(query.trim(), query)
    assertTrue(query.contains("SELECT DISTINCT ?item ?itemLabel"))
    assertTrue(query.contains("LIMIT 50"))
    assertTrue(query.contains("wd:Q3305213 ^wdt:P279*/^wdt:P31 ?item"))
    assertTrue(query.contains("FILTER(CONTAINS(LCASE(STR(?itemLabel)), \"mona lisa\"))"))
    assertEquals(
        "Braces must be balanced",
        query.count { it == '{' },
        query.count { it == '}' },
    )
  }

  @Test
  fun getArtwork_whenUidIsNotAnExactItemId_throwsInvalidInputWithoutRequest() = runTest {
    val client = testClient()
    val repository = WikiDataRepositoryRemote(client)

    listOf(
            "Q123\n",
            " Q123",
            "Q123 ",
            "Q-1",
            "Q1.5",
            "QQ1",
            "Q 1",
            "P31",
            "wd:Q123",
            "Q\u0661\u0662\u0663", // Arabic-Indic digits must not count as \d
        )
        .forEach { uid ->
          assertThrowsSuspend<AppException.InvalidInput> { repository.getArtwork(uid) }
        }

    assertNull("no request should be registered", client.lastRequest)
  }

  @Test
  fun getArtwork_sendsRequestForRequestedItemWithExpectedHeaders() = runTest {
    val client = testClient()
    val repository = WikiDataRepositoryRemote(client)

    // Parsing is not implemented yet, so a successful HTTP round trip ends in NotImplementedError.
    assertThrowsSuspend<NotImplementedError> { repository.getArtwork("Q12418") }

    val request = requireNotNull(client.lastRequest)
    val sparql = requireNotNull(request.url.queryParameter("query"))
    assertEquals("GET", request.method)
    assertEquals("json", request.url.queryParameter("format"))
    assertEquals("application/sparql-results+json", request.header("Accept"))
    assertTrue(request.header("User-Agent")!!.startsWith("Artista/1.0"))
    assertTrue(sparql.contains("VALUES ?item { wd:Q12418 }"))
    assertTrue(sparql.contains("LANG(?itemLabel) = \"en\" || LANG(?itemLabel) = \"\""))
    assertFalse("getArtwork must not use the search subquery", sparql.contains("LIMIT"))
  }

  @Test
  fun getArtwork_whenServerReturnsError_throwsNetwork() = runTest {
    val repository = WikiDataRepositoryRemote(testClient(statusCode = 500))

    assertThrowsSuspend<AppException.Network> { repository.getArtwork("Q12418") }
  }

  @Test
  fun getArtwork_whenUidIsBlankOrMalformed_throwsInvalidInput() = runTest {
    val repository = WikiDataRepositoryRemote()

    listOf("", " ", "abc", "mona lisa", "Q", "Q12a", "Q123 } UNION { ?s ?p ?o", "q123").forEach {
        uid ->
      assertThrowsSuspend<AppException.InvalidInput> { repository.getArtwork(uid) }
    }
  }

  @Test
  fun searchArtworks_whenQueryIsBlank_returnsEmptyListWithoutRequest() = runTest {
    val client = testClient()
    val repository = WikiDataRepositoryRemote(client)

    assertTrue(repository.searchArtworks("   ").isEmpty())
    assertEquals(null, client.lastRequest)
  }

  @Test
  fun searchArtworks_sendsEncodedQueryAndExpectedHeaders() = runTest {
    val client = testClient()
    val repository = WikiDataRepositoryRemote(client)
    client.statusCode = 200

    assertThrowsSuspend<NotImplementedError> { repository.searchArtworks("Mona Lisa") }

    val request = requireNotNull(client.lastRequest)
    assertEquals("GET", request.method)
    assertEquals("json", request.url.queryParameter("format"))
    assertEquals(repository.buildRequest("Mona Lisa"), request.url.queryParameter("query"))
    assertEquals("application/sparql-results+json", request.header("Accept"))
    assertTrue(request.header("User-Agent")!!.startsWith("Artista/1.0"))
  }

  @Test
  fun searchArtworks_whenServerReturnsError_throwsNetwork() = runTest {
    val client = testClient(statusCode = 500)
    val repository = WikiDataRepositoryRemote(client)

    assertThrowsSuspend<AppException.Network> { repository.searchArtworks("Mona Lisa") }
  }

  @Test
  fun searchArtworks_whenRequestFails_throwsNetworkWithCause() = runTest {
    val client =
        OkHttpClient.Builder().addInterceptor { throw IOException("connection failed") }.build()
    val repository = WikiDataRepositoryRemote(client)

    try {
      repository.searchArtworks("Mona Lisa")
      throw AssertionError("Expected AppException.Network")
    } catch (exception: AppException.Network) {
      assertTrue(exception.cause != null)
    }
  }

  private fun testClient(
      statusCode: Int = 200,
      responseBody: String = """{"results":{"bindings":[]}}""",
  ): RecordingHttpClient {
    val client = RecordingHttpClient(statusCode, responseBody)
    return client
  }

  private class RecordingHttpClient(
      var statusCode: Int,
      var responseBody: String,
  ) : OkHttpClient() {
    var lastRequest: Request? = null
      private set

    private val delegate =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
              lastRequest = chain.request()
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
}
