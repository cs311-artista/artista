// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.model.artwork

import com.artista.artista.model.artist.Artist
import com.artista.artista.model.map.Location
import java.util.Date

/**
 * An intermediary to provide a populated instance of an [ArtworkRepositoryLocal] for demos and
 * testing
 *
 * @author 5kyPhy
 */
object ArtworkRepositoryProvider {
  private val _repository = ArtworkRepositoryLocal()

  var repository = _repository

  /**
   * This function is meant to populate the API side of the faked ArtworkRepository in order to
   * facilitate test/demo setup
   *
   * @param mocks a [List] of [Artwork] to put into the faked API. A premade list of mock data is
   *   set as default ([mockArtworks]).
   * @author 5kyPhy
   */
  fun populateAPI(mocks: List<Artwork> = mockArtworks) {
    mocks.forEach { artwork -> repository.putArtworkInRepositoryUnsavedSection(artwork) }
  }

  /**
   * This function is meant to populate the SavedArtwork side of the faked ArtworkRepository in
   * order ot facilitate test/demo setup
   *
   * @param mocks a [List] of [Artwork] to put into the faked cache. A premade list of mock data is
   *   set as default (first 3 elements of [mockArtworks]).
   * @author 5kyPhy
   */
  suspend fun populateSaved(mocks: List<Artwork> = mockArtworks.subList(0, 3)) {
    mocks.forEach { artwork -> repository.saveArtwork(artwork) }
  }
}

private val mockPabloPicasso = Artist("mock-artist-001", "Pablo Picasso")
private val mockVincentVanGogh = Artist("mock-artist-002", "Vincent van Gogh")
private val mockPierreAugusteRenoir = Artist("mock-artist-003", "Pierre-Auguste Renoir")
private val mockClaudeMonet = Artist("mock-artist-004", "Claude Monet")
private val mockGustavKlimt = Artist("mock-artist-005", "Gustav Klimt")
private val mockHenriMatisse = Artist("mock-artist-006", "Henri Matisse")
private val mockAugusteRodin = Artist("mock-artist-007", "Auguste Rodin")
private val mockEdwardHopper = Artist("mock-artist-008", "Edward Hopper")
private val mockMayaOConnell = Artist("mock-artist-009", "Maya O'Connell")
private val mockLuciaFernandez = Artist("mock-artist-010", "Lucía Fernández")
private val mockJordanLee = Artist("mock-artist-011", "Jordan Lee")
private val mockAikoTanaka = Artist("mock-artist-012", "Aiko Tanaka")
private val mockSamRivera = Artist("mock-artist-013", "Sam Rivera")
private val mockNoahSmith = Artist("mock-artist-014", "Noah Smith")

/**
 * Provides representative artist suggestions for local development and previews.
 *
 * @return representative artists in display order
 * @author Timz3rr, patrickmcdan
 */
val mockPopularArtists: List<Artist> =
    listOf(
        mockPabloPicasso,
        mockVincentVanGogh,
        mockPierreAugusteRenoir,
        mockClaudeMonet,
        mockGustavKlimt,
        mockHenriMatisse,
    )

/**
 * Provides representative artwork data for local development and previews.
 *
 * @author Copilot
 */
val mockArtworks: List<Artwork> =
    listOf(
        Artwork(
            uid = "mock-001",
            name = "The Thinker",
            artist = mockAugusteRodin,
            description = "A bronze study of contemplation.",
            conceptionDate = Date(0L),
            museum = "Musée Rodin",
            location = Location(2.3158, 48.8554, "Paris"),
            dimension = Dimension(98.0, 189.0),
        ),
        Artwork(
            uid = "mock-002",
            name = "Nighthawks",
            artist = mockEdwardHopper,
            description = null,
            conceptionDate = Date(946684800000L),
            museum = "Art Institute of Chicago",
            location = Location(-87.6237, 41.8796, "Chicago"),
            dimension = null,
        ),
        Artwork(
            uid = "mock-003",
            name = "L'étoile filante",
            artist = mockMayaOConnell,
            description = "Light, shadow & movement in one composition.",
            conceptionDate = null,
            museum = null,
            location = null,
            dimension = Dimension(42.5, 42.5),
        ),
        Artwork(
            uid = "mock-004",
            name = "Untitled — Blue No. 7",
            artist = null,
            description = "The artist is intentionally unknown.",
            conceptionDate = Date(315532800000L),
            museum = "Gallery \"North\"",
            location = Location(13.4050, 52.5200, null),
            dimension = Dimension(120.0, 80.0),
        ),
        Artwork(
            uid = "mock-005",
            name = "¿Dónde está el sol?",
            artist = mockLuciaFernandez,
            description = "A question in colour: red, yellow, and blue.",
            conceptionDate = Date(631152000000L),
            museum = null,
            location = Location(-3.7038, 40.4168, "Madrid"),
            dimension = null,
        ),
        Artwork(
            uid = "mock-006",
            name = "Still Life (Coffee & Rain)",
            artist = mockJordanLee,
            description = "",
            conceptionDate = null,
            museum = "The Corner / Studio 2",
            location = Location(-0.1276, 51.5072, "London"),
            dimension = Dimension(60.0, 45.0),
        ),
        Artwork(
            uid = "mock-007",
            name = "星の記憶",
            artist = mockAikoTanaka,
            description = "A memory of stars beyond the city.",
            conceptionDate = Date(1072915200000L),
            museum = "Kita-ku Arts Centre",
            location = null,
            dimension = Dimension(30.0, 30.0),
        ),
        Artwork(
            uid = "mock-008",
            name = "A/B: Fragments [I]",
            artist = mockSamRivera,
            description = null,
            conceptionDate = Date(1262304000000L),
            museum = "Museum of Modern Art",
            location = Location(-73.9776, 40.7614, "New York"),
            dimension = null,
        ),
        Artwork(
            uid = "mock-009",
            name = "Café No. 5",
            artist = null,
            description = "A small work with a large title!",
            conceptionDate = null,
            museum = null,
            location = null,
            dimension = null,
        ),
        Artwork(
            uid = "mock-010",
            name = "The Last Line: \"Begin Again\"",
            artist = mockNoahSmith,
            description = "A deliberately long title with punctuation — and a dash.",
            conceptionDate = Date(1640995200000L),
            museum = "Harbour Gallery",
            location = Location(151.2093, -33.8688, "Sydney"),
            dimension = Dimension(200.0, 100.0),
        ),
    )
