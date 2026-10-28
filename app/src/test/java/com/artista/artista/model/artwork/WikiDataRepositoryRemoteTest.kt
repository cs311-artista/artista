// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.model.artwork

import com.artista.artista.model.error.AppException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [WikiDataRepositoryRemote].
 *
 * @author IJJA3141
 */
class WikiDataRepositoryRemoteTest {

  /**
   * /!\ This is not a unit test /!\
   *
   * This test performs real network requests to the Wikidata SPARQL endpoint. It is intended to
   * verify the correctness of the SPARQL query and the ability to fetch data from the remote API.
   */
  // @Test
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

  /**
   * /!\ This is not a unit test /!\
   *
   * This test performs real network requests to the Wikidata SPARQL endpoint. It is intended to
   * verify the correctness of the SPARQL query and the ability to fetch data from the remote API.
   */
  // @Test
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
  fun buildRequest_escapesSearchTermAndUsesEnglishLabels() {
    val repository = WikiDataRepositoryRemote()

    val query = repository.buildRequest("Mona \"Lisa\"")

    assertTrue(query.contains("wd:Q3305213"))
    assertTrue(query.contains("FILTER(LANG(?itemLabel) = \"en\")"))
    assertTrue(query.contains("mona \\\"lisa\\\""))
  }

  @Test
  fun getArtwork_whenUidIsBlank_throwsInvalidInput() = runTest {
    val repository = WikiDataRepositoryRemote()

    assertThrowsSuspend<AppException.InvalidInput> { repository.getArtwork("   ") }
    assertThrowsSuspend<AppException.InvalidInput> { repository.getArtwork("") }
  }

  @Test
  fun searchArtworks_whenQueryIsBlank_returnsEmptyList() = runTest {
    val repository = WikiDataRepositoryRemote()

    assertTrue(repository.searchArtworks("   ").isEmpty())
    assertTrue(repository.searchArtworks("").isEmpty())
  }

  private suspend inline fun <reified T : Throwable> assertThrowsSuspend(
      crossinline block: suspend () -> Unit
  ) {
    try {
      block()
      assertTrue("Expected ${T::class.simpleName} to be thrown", false)
    } catch (exception: Throwable) {
      assertTrue(
          "Expected ${T::class.simpleName}, got ${exception::class.simpleName}",
          exception is T,
      )
    }
  }
}
