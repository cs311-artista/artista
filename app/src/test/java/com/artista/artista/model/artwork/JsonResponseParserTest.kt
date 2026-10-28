// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.model.artwork

import java.time.Instant
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests parsing a real Wikidata SPARQL response.
 *
 * @author 5kyPhy
 */
@RunWith(RobolectricTestRunner::class)
class JsonResponseParserTest {

  private val parser = JsonResponseParser()

  @Test
  fun parseArtworkResults_parsesCapturedWikidataResponse() {
    val resource =
        requireNotNull(javaClass.getResource("/wikidata_response.json")) {
          "wikidata_response.json test resource is missing"
        }
    val artworks = parser.parseArtworkResults(resource.readText())

    assertEquals(1, artworks.size)
    val artwork = artworks.single()
    assertEquals("Q12418", artwork.uid)
    assertEquals("Mona Lisa", artwork.name)
    assertEquals("Leonardo da Vinci", artwork.artistName)
    assertEquals("oil painting by Leonardo da Vinci in the Louvre, Paris", artwork.description)
    assertEquals(Date.from(Instant.parse("1503-01-01T00:00:00Z")), artwork.conceptionDate)
    assertEquals("Department of Paintings of the Louvre", artwork.museum)
    assertEquals("Salle des États, Louvre", artwork.location?.name)
    assertEquals(0.0, artwork.location?.longitude ?: 0.0, 0.01)
    assertEquals(0.0, artwork.location?.latitude ?: 0.0, 0.01)
    assertEquals(53.4, artwork.dimension?.width ?: 0.0, 0.01)
    assertEquals(79.4, artwork.dimension?.height ?: 0.0, 0.01)
  }
}
