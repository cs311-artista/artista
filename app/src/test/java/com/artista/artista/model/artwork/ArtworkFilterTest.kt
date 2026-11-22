// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.model.artwork

import com.artista.artista.model.artist.Artist
import com.artista.artista.model.map.Location
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests reusable artwork filtering and option helpers.
 *
 * @author patrickmcdan, hixeum
 */
class ArtworkFilterTest {

  @Test
  fun emptyFilter_returnsAllArtworks() {
    assertEquals(artworks, artworks.applyFilter(ArtworkFilter()))
    assertTrue(!ArtworkFilter().isActive())
  }

  @Test
  fun exactSetCriteria_matchArtistMuseumAndLocation() {
    val filter =
        ArtworkFilter(
            artists = setOf("Vincent van Gogh"),
            museums = setOf("Museum of Modern Art"),
            locations = setOf("New York"),
        )

    assertEquals(listOf(artworks[0]), artworks.applyFilter(filter))
  }

  @Test
  fun activeLocation_excludesNullLocationNames() {
    val artworkWithoutLocation = artworks[0].copy(location = null)

    assertEquals(
        emptyList<Artwork>(),
        listOf(artworkWithoutLocation).applyFilter(ArtworkFilter(locations = setOf("New York"))),
    )
  }

  @Test
  fun yearBounds_areInclusive() {
    val filter = ArtworkFilter(fromYear = 1665, toYear = 1889)

    assertEquals(artworks, artworks.applyFilter(filter))
  }

  /** Verifies that a BCE artwork is not matched by a CE range with the same absolute year. */
  @Test
  fun yearBounds_doNotMatchBceDatesAsCe() {
    val bce =
        artworks[0].copy(
            conceptionDate =
                Date.from(LocalDate.of(-499, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant())
        )

    assertEquals(
        emptyList<Artwork>(),
        listOf(bce).applyFilter(ArtworkFilter(fromYear = 400, toYear = 600)),
    )
  }

  @Test
  fun activeYearBound_excludesUndeterminableYears() {
    val artworkWithoutDate = artworks[0].copy(conceptionDate = null)

    assertEquals(
        emptyList<Artwork>(),
        listOf(artworkWithoutDate).applyFilter(ArtworkFilter(fromYear = 1800)),
    )
  }

  /** Checks that an artwork matches when any one of its types is selected. */
  @Test
  fun typeCriterion_matchesArtworksHavingAnySelectedType() {
    val oilPainting =
        artworks[0].copy(types = setOf(ArtworkType.PAINTING, ArtworkType.OIL_PAINTING))
    val sculpture = artworks[1].copy(types = setOf(ArtworkType.SCULPTURE))

    assertEquals(
        listOf(oilPainting),
        listOf(oilPainting, sculpture)
            .applyFilter(ArtworkFilter(types = setOf(ArtworkType.OIL_PAINTING))),
    )
    assertEquals(
        listOf(oilPainting, sculpture),
        listOf(oilPainting, sculpture)
            .applyFilter(ArtworkFilter(types = setOf(ArtworkType.PAINTING, ArtworkType.SCULPTURE))),
    )
  }

  /** Checks that an artwork with no known type, e.g. saved before types existed, is excluded. */
  @Test
  fun activeTypeCriterion_excludesArtworksWithoutTypes() {
    val artworkWithoutTypes = artworks[0].copy(types = emptySet())

    assertEquals(
        emptyList<Artwork>(),
        listOf(artworkWithoutTypes).applyFilter(ArtworkFilter(types = setOf(ArtworkType.PAINTING))),
    )
  }

  /** Checks that the type criterion combines with the others instead of replacing them. */
  @Test
  fun typeCriterion_combinesWithOtherCriteria() {
    val paintings = artworks.map { it.copy(types = setOf(ArtworkType.PAINTING)) }

    assertEquals(
        listOf(paintings[1]),
        paintings.applyFilter(
            ArtworkFilter(artists = setOf("Johannes Vermeer"), types = setOf(ArtworkType.PAINTING))
        ),
    )
  }

  @Test
  fun typeCriterion_makesFilterActive() {
    assertTrue(ArtworkFilter(types = setOf(ArtworkType.MOSAIC)).isActive())
    assertFalse(ArtworkFilter(types = emptySet()).isActive())
  }

  @Test
  fun options_areDistinctAndSorted() {
    val duplicate = artworks[0].copy(uid = "duplicate")
    val source = listOf(artworks[1], duplicate, artworks[0])

    assertEquals(listOf("Vincent van Gogh", "Johannes Vermeer").sorted(), artistOptions(source))
    assertEquals(listOf("Museum of Modern Art", "Rijksmuseum").sorted(), museumOptions(source))
    assertEquals(listOf("Amsterdam", "New York").sorted(), locationOptions(source))
  }

  @Test
  fun suggestions_prioritizePrefixes_andExcludeSelected() {
    val options = listOf("Modern Art", "Museum", "Rijksmuseum", "Art Museum")

    assertEquals(
        listOf("Art Museum", "Modern Art"),
        suggestions("art", options, selected = setOf("Rijksmuseum")),
    )
  }

  private companion object {
    val artworks =
        listOf(
            artwork(
                "starry-night",
                "The Starry Night",
                "Vincent van Gogh",
                1889,
                "Museum of Modern Art",
                "New York",
            ),
            artwork(
                "pearl-earring",
                "Girl with a Pearl Earring",
                "Johannes Vermeer",
                1665,
                "Rijksmuseum",
                "Amsterdam",
            ),
        )

    fun artwork(
        uid: String,
        name: String,
        artist: String,
        year: Int,
        museum: String,
        location: String,
    ): Artwork =
        Artwork(
            uid = uid,
            name = name,
            artist = Artist(uid = artist, name = artist),
            description = null,
            conceptionDate =
                Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                    .apply {
                      clear()
                      set(Calendar.YEAR, year)
                    }
                    .time,
            museum = museum,
            location = Location(longitude = 0.0, latitude = 0.0, name = location),
            dimension = null,
        )
  }
}
