// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
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
 * @author Copilot
 */
class ArtworkRepositoryProviderTest {

  @Before
  fun setUp() {
    ArtworkRepositoryProvider.repository = ArtworkRepositoryLocal()
  }

  @After fun tearDown() {}

  @Test
  fun populateAPI_withCustomArtworks_addsArtworksToRepository() = runTest {
    val artworks = listOf(mockArtworks[0], mockArtworks[1])

    ArtworkRepositoryProvider.populateAPI(artworks)

    assertEquals(
        artworks,
        artworks.map { artwork -> ArtworkRepositoryProvider.repository.getArtwork(artwork.uid) },
    )
  }

  @Test
  fun populateAPI_withoutArguments_addsAllDefaultArtworks() = runTest {
    ArtworkRepositoryProvider.populateAPI()

    assertEquals(
        mockArtworks,
        mockArtworks.map { artwork ->
          ArtworkRepositoryProvider.repository.getArtwork(artwork.uid)
        },
    )
  }

  @Test
  fun populateSaved_withCustomArtworks_savesOnlyThoseArtworks() = runTest {
    val artworks = listOf(mockArtworks[1], mockArtworks[3])

    ArtworkRepositoryProvider.populateSaved(artworks)

    assertEquals(
        artworks.toSet(),
        ArtworkRepositoryProvider.repository.getSavedArtworks().toSet(),
    )
  }

  @Test
  fun populateSaved_withoutArguments_savesFirstThreeDefaultArtworks() = runTest {
    ArtworkRepositoryProvider.populateSaved()

    assertEquals(
        mockArtworks.take(3).toSet(),
        ArtworkRepositoryProvider.repository.getSavedArtworks().toSet(),
    )
  }

  @Test
  fun populateAPI_doesNotSaveArtworks_andPopulateSavedDoesNotAddAPIArtworks() = runTest {
    val artwork = mockArtworks.first()

    ArtworkRepositoryProvider.populateAPI(listOf(artwork))
    assertFalse(ArtworkRepositoryProvider.repository.isArtworkSaved(artwork.uid))

    ArtworkRepositoryProvider.populateSaved(listOf(artwork))
    assertTrue(ArtworkRepositoryProvider.repository.isArtworkSaved(artwork.uid))
    assertEquals(artwork, ArtworkRepositoryProvider.repository.getArtwork(artwork.uid))
  }
}
