// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.model.artwork

import com.artista.artista.model.artist.Artist
import com.artista.artista.model.map.Location
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests reusable artwork filtering and option helpers.
 *
 * @author patrickmcdan
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

  @Test
  fun activeYearBound_excludesUndeterminableYears() {
    val artworkWithoutDate = artworks[0].copy(conceptionDate = null)

    assertEquals(
        emptyList<Artwork>(),
        listOf(artworkWithoutDate).applyFilter(ArtworkFilter(fromYear = 1800)),
    )
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
                Calendar.getInstance()
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
