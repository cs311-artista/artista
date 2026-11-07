// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: DeepSeek Flash
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

  private companion object {
    const val MONA_LISA_IMAGE_URL =
        "https://commons.wikimedia.org/wiki/Special:FilePath/" +
            "Mona%20Lisa%2C%20by%20Leonardo%20da%20Vinci%2C%20from%20C2RMF%20retouched.jpg" +
            "?width=800"
  }

  /** Checks that every field of a complete Wikidata row, image included, is parsed. */
  @Test
  fun parseArtworkResults_parsesCapturedWikidataResponse() {
    val resource =
        requireNotNull(javaClass.getResource("/wikidata_responses/wikidata_response.json")) {
          "wikidata_response.json test resource is missing"
        }
    val artworks = parser.parseArtworkResults(resource.readText())

    assertEquals(1, artworks.size)
    val artwork = artworks.single()
    assertEquals("Q12418", artwork.uid)
    assertEquals("Mona Lisa", artwork.name)
    assertEquals("Leonardo da Vinci", artwork.artist?.name)
    assertEquals("uid-Leonardo da Vinci", artwork.artist?.uid)
    assertEquals("oil painting by Leonardo da Vinci in the Louvre, Paris", artwork.description)
    assertEquals(Date.from(Instant.parse("1503-01-01T00:00:00Z")), artwork.conceptionDate)
    assertEquals("Department of Paintings of the Louvre", artwork.museum)
    assertEquals("Salle des États, Louvre", artwork.location?.name)
    assertEquals(0.0, artwork.location?.longitude ?: 0.0, 0.01)
    assertEquals(0.0, artwork.location?.latitude ?: 0.0, 0.01)
    assertEquals(53.4, artwork.dimension?.width ?: 0.0, 0.01)
    assertEquals(79.4, artwork.dimension?.height ?: 0.0, 0.01)
    assertEquals(MONA_LISA_IMAGE_URL, artwork.imageUrl)
  }

  /**
   * Checks that a response with several rows yields one artwork per row, each with its own image: a
   * link for the artwork that has one, null for the one that has none.
   */
  @Test
  fun parseArtworkResults_parsesMultipleCapturedWikidataResponses() {
    val resource =
        requireNotNull(
            javaClass.getResource("/wikidata_responses/wikidata_response_multiple.json")
        ) {
          "wikidata_response_multiple.json test resource is missing"
        }
    val artworks = parser.parseArtworkResults(resource.readText())

    assertEquals(2, artworks.size)

    var artwork = artworks[0]
    assertEquals("Q12418", artwork.uid)
    assertEquals("Mona Lisa", artwork.name)
    assertEquals("Leonardo da Vinci", artwork.artist?.name)
    assertEquals("uid-Leonardo da Vinci", artwork.artist?.uid)
    assertEquals("oil painting by Leonardo da Vinci in the Louvre, Paris", artwork.description)
    assertEquals(Date.from(Instant.parse("1503-01-01T00:00:00Z")), artwork.conceptionDate)
    assertEquals("Department of Paintings of the Louvre", artwork.museum)
    assertEquals("Salle des États, Louvre", artwork.location?.name)
    assertEquals(0.0, artwork.location?.longitude ?: 0.0, 0.01)
    assertEquals(0.0, artwork.location?.latitude ?: 0.0, 0.01)
    assertEquals(53.4, artwork.dimension?.width ?: 0.0, 0.01)
    assertEquals(79.4, artwork.dimension?.height ?: 0.0, 0.01)
    assertEquals(MONA_LISA_IMAGE_URL, artwork.imageUrl)

    artwork = artworks[1]
    assertEquals("Q12419", artwork.uid)
    assertEquals("The Thinker", artwork.name)
    assertNull(artwork.artist)
    assertNull(artwork.description)
    assertNull(artwork.conceptionDate)
    assertEquals("Department of Sculptures of the Louvre", artwork.museum)
    assertNull(artwork.location)
    assertEquals(0.0, artwork.dimension?.width ?: 0.0, 0.01)
    assertEquals(0.0, artwork.dimension?.height ?: 0.0, 0.01)
    assertNull(artwork.imageUrl)
  }

  /**
   * Checks that two rows describing the same item yield a single artwork, the first of the two when
   * they are equally rich.
   *
   * Wikidata returns one row per value of the optional properties, so an item with two images comes
   * back twice. Left as is, the repeat reaches the artwork lists of Overview and Saved Artworks,
   * whose Compose key is the uid, and throws "Key ... was already used".
   */
  @Test
  fun parseArtworkResults_collapsesRepeatedRowsOfTheSameItem() {
    val response =
        """
        {
          "results": {
            "bindings": [
              {
                "item": { "value": "http://www.wikidata.org/entity/Q19933946" },
                "itemLabel": { "value": "Skulptur" },
                "image": {
                  "value": "http://commons.wikimedia.org/wiki/Special:FilePath/First.JPG"
                }
              },
              {
                "item": { "value": "http://www.wikidata.org/entity/Q19933946" },
                "itemLabel": { "value": "Skulptur" },
                "image": {
                  "value": "http://commons.wikimedia.org/wiki/Special:FilePath/Second.JPG"
                }
              }
            ]
          }
        }
        """
            .trimIndent()

    val artworks = parser.parseArtworkResults(response)

    assertEquals(1, artworks.size)
    assertEquals("Q19933946", artworks.single().uid)
    assertEquals(
        "https://commons.wikimedia.org/wiki/Special:FilePath/First.JPG?width=800",
        artworks.single().imageUrl,
    )
  }

  /**
   * Checks that the row carrying coordinates wins over one that only names the place.
   *
   * A row without coordinates still yields a location, at (0, 0), so keeping the wrong row would
   * replace a correct pin with one in the Gulf of Guinea.
   */
  @Test
  fun parseArtworkResults_prefersTheRowCarryingCoordinates() {
    val response =
        """
        {
          "results": {
            "bindings": [
              {
                "item": { "value": "http://www.wikidata.org/entity/Q12418" },
                "itemLabel": { "value": "Mona Lisa" },
                "locationLabel": { "value": "Louvre" }
              },
              {
                "item": { "value": "http://www.wikidata.org/entity/Q12418" },
                "itemLabel": { "value": "Mona Lisa" },
                "locationLabel": { "value": "Louvre" },
                "locationCoordinates": { "value": "Point(2.337 48.860)" }
              }
            ]
          }
        }
        """
            .trimIndent()

    val artworks = parser.parseArtworkResults(response)

    assertEquals(1, artworks.size)
    val location = artworks.single().location
    assertNotNull(location)
    assertEquals(2.337, location?.longitude ?: 0.0, 0.01)
    assertEquals(48.860, location?.latitude ?: 0.0, 0.01)
  }

  /** Checks that optional fields set to JSON null, image included, are parsed as null. */
  @Test
  fun parseArtworkResults_parsesCapturedWikidataResponseWithNullFields() {
    val resource =
        requireNotNull(
            javaClass.getResource("/wikidata_responses/wikidata_response_null_fields.json")
        ) {
          "wikidata_response_null_fields.json test resource is missing"
        }
    val artworks = parser.parseArtworkResults(resource.readText())

    assertEquals(1, artworks.size)
    val artwork = artworks.single()
    assertEquals("Q12419", artwork.uid)
    assertEquals("The Thinker", artwork.name)
    assertNull(artwork.artist)
    assertNull(artwork.description)
    assertNull(artwork.conceptionDate)
    assertEquals("Department of Sculptures of the Louvre", artwork.museum)
    assertNull(artwork.location)
    assertNull(artwork.dimension)
    assertNull(artwork.imageUrl)
  }

  /** Checks that optional fields set to an empty string, image included, are parsed as missing. */
  @Test
  fun parseArtworkResults_parsesCapturedWikidataResponseWithEmptyFields() {
    val resource =
        requireNotNull(
            javaClass.getResource("/wikidata_responses/wikidata_response_empty_fields.json")
        ) {
          "wikidata_response_empty_fields.json test resource is missing"
        }
    val artworks = parser.parseArtworkResults(resource.readText())

    assertEquals(1, artworks.size)
    val artwork = artworks.single()
    assertEquals("Q12419", artwork.uid)
    assertEquals("The Thinker", artwork.name)
    assertNull(artwork.artist)
    assertNull(artwork.description)
    assertNull(artwork.conceptionDate)
    assertEquals("Department of Sculptures of the Louvre", artwork.museum)
    assertNull(artwork.location)
    assertEquals(0.0, artwork.dimension?.width ?: 0.0, 0.01)
    assertEquals(0.0, artwork.dimension?.height ?: 0.0, 0.01)
    assertNull(artwork.imageUrl)
  }

  /**
   * Checks that an http image link becomes https, because Android blocks cleartext traffic by
   * default, and asks for a thumbnail instead of the full-resolution file.
   */
  @Test
  fun testParseImageUrl_upgradesHttpToHttpsAndRequestsThumbnail() {
    val imageUrl =
        invokePrivateMethod<String?>(
            parser,
            "parseImageUrl",
            "http://commons.wikimedia.org/wiki/Special:FilePath/Starry%20Night.jpg",
        )
    assertEquals(
        "https://commons.wikimedia.org/wiki/Special:FilePath/Starry%20Night.jpg?width=800",
        imageUrl,
    )
  }

  /** Checks that an image link already in https keeps its scheme and still gets the thumbnail. */
  @Test
  fun testParseImageUrl_keepsHttpsScheme() {
    val imageUrl =
        invokePrivateMethod<String?>(
            parser,
            "parseImageUrl",
            "https://commons.wikimedia.org/wiki/Special:FilePath/Starry%20Night.jpg",
        )
    assertEquals(
        "https://commons.wikimedia.org/wiki/Special:FilePath/Starry%20Night.jpg?width=800",
        imageUrl,
    )
  }

  /** Checks that a blank image link gives null, so the card shows its fallback instead. */
  @Test
  fun testParseImageUrl_blankValueReturnsNull() {
    val imageUrl = invokePrivateMethod<String?>(parser, "parseImageUrl", "  ")
    assertNull(imageUrl)
  }

  /** Checks that a value that is not an http(s) link gives null instead of a broken link. */
  @Test
  fun testParseImageUrl_invalidUrlReturnsNull() {
    val imageUrl = invokePrivateMethod<String?>(parser, "parseImageUrl", "not a url")
    assertNull(imageUrl)
  }

  /**
   * Checks that the width is merged with the query the link already has, and replaces a width
   * already present instead of sending it twice.
   */
  @Test
  fun testParseImageUrl_mergesWidthWithExistingQuery() {
    val imageUrl =
        invokePrivateMethod<String?>(
            parser,
            "parseImageUrl",
            "http://commons.wikimedia.org/wiki/Special:FilePath/Starry%20Night.jpg?lang=en&width=300",
        )
    assertEquals(
        "https://commons.wikimedia.org/wiki/Special:FilePath/Starry%20Night.jpg?lang=en&width=800",
        imageUrl,
    )
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
