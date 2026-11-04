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
  private lateinit var repository: ArtworkRepositoryLocal

  @Before
  fun setUp() {
    repository = ArtworkRepositoryLocal()
  }

  @After fun tearDown() {}

  @Test
  fun populateAPI_withCustomArtworks_addsArtworksToRepository() = runTest {
    val artworks = listOf(mockArtworks[0], mockArtworks[1])

    artworks.forEach(repository::putArtworkInRepositoryUnsavedSection)

    assertEquals(
        artworks,
        artworks.map { artwork -> repository.getArtwork(artwork.uid) },
    )
  }

  @Test
  fun populateAPI_withoutArguments_addsAllDefaultArtworks() = runTest {
    mockArtworks.forEach(repository::putArtworkInRepositoryUnsavedSection)

    assertEquals(
        mockArtworks,
        mockArtworks.map { artwork -> repository.getArtwork(artwork.uid) },
    )
  }

  @Test
  fun populateSaved_withCustomArtworks_savesOnlyThoseArtworks() = runTest {
    val artworks = listOf(mockArtworks[1], mockArtworks[3])

    artworks.forEach { repository.saveArtwork(it) }

    assertEquals(
        artworks.toSet(),
        repository.getSavedArtworks().toSet(),
    )
  }

  @Test
  fun populateSaved_withoutArguments_savesFirstThreeDefaultArtworks() = runTest {
    mockArtworks.take(3).forEach { repository.saveArtwork(it) }

    assertEquals(
        mockArtworks.take(3).toSet(),
        repository.getSavedArtworks().toSet(),
    )
  }

  @Test
  fun populateAPI_doesNotSaveArtworks_andPopulateSavedDoesNotAddAPIArtworks() = runTest {
    val artwork = mockArtworks.first()

    repository.putArtworkInRepositoryUnsavedSection(artwork)
    assertFalse(repository.isArtworkSaved(artwork.uid))

    repository.saveArtwork(artwork)
    assertTrue(repository.isArtworkSaved(artwork.uid))
    assertEquals(artwork, repository.getArtwork(artwork.uid))
  }
}
