// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.model.artwork

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ArtworkRepositoryProvider].
 *
 * @author Copilot, Timz3rr
 */
class ArtworkRepositoryProviderTest {
  private val repository: ArtworkRepositoryLocal
    get() = ArtworkRepositoryProvider.mockRepository

  @Before fun setUp() = repository.clear()

  @After fun tearDown() = repository.clear()

  @Test
  fun populateAPI_withCustomArtworks_addsArtworksToRepository() = runTest {
    val artworks = listOf(mockArtworks[0], mockArtworks[1])

    ArtworkRepositoryProvider.populateAPI(artworks)

    assertEquals(
        artworks,
        artworks.map { artwork -> repository.getArtwork(artwork.uid) },
    )
  }

  @Test
  fun populateAPI_withoutArguments_addsAllDefaultArtworks() = runTest {
    ArtworkRepositoryProvider.populateAPI()

    assertEquals(
        mockArtworks,
        mockArtworks.map { artwork -> repository.getArtwork(artwork.uid) },
    )
  }

  @Test
  fun populateSaved_withCustomArtworks_savesOnlyThoseArtworks() = runTest {
    val artworks = listOf(mockArtworks[1], mockArtworks[3])

    ArtworkRepositoryProvider.populateSaved(artworks)

    assertEquals(
        artworks.toSet(),
        repository.getSavedArtworks().toSet(),
    )
  }

  @Test
  fun populateSaved_withoutArguments_savesFirstThreeDefaultArtworks() = runTest {
    ArtworkRepositoryProvider.populateSaved()

    assertEquals(
        mockArtworks.take(3).toSet(),
        repository.getSavedArtworks().toSet(),
    )
  }

  @Test
  fun populateAPI_doesNotSaveArtworks_andPopulateSavedDoesNotAddAPIArtworks() = runTest {
    val artwork = mockArtworks.first()

    ArtworkRepositoryProvider.populateAPI(listOf(artwork))
    assertFalse(repository.isArtworkSaved(artwork.uid))

    ArtworkRepositoryProvider.populateSaved(listOf(artwork))
    assertTrue(repository.isArtworkSaved(artwork.uid))
    assertEquals(artwork, repository.getArtwork(artwork.uid))
  }

  /** Checks that every mock popular artist has a Wikimedia Commons picture link. */
  @Test
  fun mockPopularArtists_allHaveWikimediaCommonsPicture() {
    mockPopularArtists.forEach { artist ->
      val imageUrl = artist.imageUrl
      assertFalse("${artist.name} has no picture", imageUrl.isNullOrBlank())
      assertTrue(
          "${artist.name} has an unexpected picture link: $imageUrl",
          imageUrl!!.startsWith("https://commons.wikimedia.org/"),
      )
    }
  }

  /** Checks that adding pictures kept the mock popular artists' identifiers and order. */
  @Test
  fun mockPopularArtists_keepIdentifiersInOrder() {
    assertEquals(
        listOf(
            "mock-artist-001",
            "mock-artist-002",
            "mock-artist-003",
            "mock-artist-004",
            "mock-artist-005",
            "mock-artist-006",
        ),
        mockPopularArtists.map { it.uid },
    )
  }
}
