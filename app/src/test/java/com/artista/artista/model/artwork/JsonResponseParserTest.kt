// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.model.artwork

import com.artista.artista.model.map.Location
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests parsing a captured Wikidata SPARQL response. The parsed wikidata_response.json was captured
 * through a call from the WikiDataRepositoryRemote, and therefore represents real-world data,
 * without having to make API calls each time we test.
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

  @Test
  fun parseArtworkResults_parsesMultipleCapturedWikidataResponses() {
    val resource =
        requireNotNull(javaClass.getResource("/wikidata_response_multiple.json")) {
          "wikidata_response_multiple.json test resource is missing"
        }
    val artworks = parser.parseArtworkResults(resource.readText())

    assertEquals(2, artworks.size)

    var artwork = artworks[0]
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

    artwork = artworks[1]
    assertEquals("Q12419", artwork.uid)
    assertEquals("The Thinker", artwork.name)
    assertNull(artwork.artistName)
    assertNull(artwork.description)
    assertNull(artwork.conceptionDate)
    assertEquals("Department of Sculptures of the Louvre", artwork.museum)
    assertNull(artwork.location)
    assertEquals(0.0, artwork.dimension?.width ?: 0.0, 0.01)
    assertEquals(0.0, artwork.dimension?.height ?: 0.0, 0.01)
  }

  @Test
  fun parseArtworkResults_parsesCapturedWikidataResponseWithNullFields() {
    val resource =
        requireNotNull(javaClass.getResource("/wikidata_response_null_fields.json")) {
          "wikidata_response_null_fields.json test resource is missing"
        }
    val artworks = parser.parseArtworkResults(resource.readText())

    assertEquals(1, artworks.size)
    val artwork = artworks.single()
    assertEquals("Q12419", artwork.uid)
    assertEquals("The Thinker", artwork.name)
    assertNull(artwork.artistName)
    assertNull(artwork.description)
    assertNull(artwork.conceptionDate)
    assertEquals("Department of Sculptures of the Louvre", artwork.museum)
    assertNull(artwork.location)
    assertNull(artwork.dimension)
  }

  @Test
  fun parseArtworkResults_parsesCapturedWikidataResponseWithEmptyFields() {
    val resource =
        requireNotNull(javaClass.getResource("/wikidata_response_empty_fields.json")) {
          "wikidata_response_empty_fields.json test resource is missing"
        }
    val artworks = parser.parseArtworkResults(resource.readText())

    assertEquals(1, artworks.size)
    val artwork = artworks.single()
    assertEquals("Q12419", artwork.uid)
    assertEquals("The Thinker", artwork.name)
    assertNull(artwork.artistName)
    assertNull(artwork.description)
    assertNull(artwork.conceptionDate)
    assertEquals("Department of Sculptures of the Louvre", artwork.museum)
    assertNull(artwork.location)
    assertEquals(0.0, artwork.dimension?.width ?: 0.0, 0.01)
    assertEquals(0.0, artwork.dimension?.height ?: 0.0, 0.01)
  }

  @Test
  fun testParseDate_withInstantParse() {
    val date = invokePrivateMethod<Date>(parser, "parseDate", "2023-01-01T00:00:00Z")
    assertNotNull(date)
    assertEquals(Date.from(Instant.parse("2023-01-01T00:00:00Z")), date)
  }

  @Test
  fun testParseDate_withLocalDateParse() {
    val date = invokePrivateMethod<Date>(parser, "parseDate", "2023-01-01")
    assertNotNull(date)
    assertEquals(
        Date.from(LocalDate.parse("2023-01-01").atStartOfDay(ZoneOffset.UTC).toInstant()),
        date,
    )
  }

  @Test
  fun testParseLocation_withCoordinates() {
    val location =
        invokePrivateMethod<Location?>(parser, "parseLocation", "Point(1.0 2.0)", "Test Location")
    assertNotNull(location)
    assertEquals(1.0, location!!.longitude, 0.0)
    assertEquals(2.0, location.latitude, 0.0)
    assertEquals("Test Location", location.name)
  }

  @Test
  fun testParseLocation_withLabelNoCoordinates() {
    val location = invokePrivateMethod<Location?>(parser, "parseLocation", null, "Test Location")
    assertNotNull(location)
    assertEquals(0.0, location!!.longitude, 0.0)
    assertEquals(0.0, location.latitude, 0.0)
    assertEquals("Test Location", location.name)
  }

  @Test
  fun testParseLocation_noCoordinatesNoLabel() {
    val location = invokePrivateMethod<Location?>(parser, "parseLocation", null, null)
    assertNull(location)
  }

  /**
   * This function allows for the calling of the private functions inside JsonResponseParser, in
   * order to be able to test them. It works through reflection of those functions.
   */
  private inline fun <reified T> invokePrivateMethod(
      obj: Any,
      methodName: String,
      vararg args: Any?,
  ): T {
    val method =
        JsonResponseParser::class
            .java
            .getDeclaredMethod(
                methodName,
                *args.map { it?.javaClass ?: String::class.java }.toTypedArray(),
            )
    method.isAccessible = true
    return method.invoke(obj, *args) as T
  }
}
