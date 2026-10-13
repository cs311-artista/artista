// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.model.artwork

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ArtworkRepositoryLocal].
 *
 * @author 5kyPhy
 */
class ArtworkRepositoryLocalTest {

  @Test
  fun getArtwork_returnsArtworkWithMatchingUid() = runTest {
    val repository = ArtworkRepositoryLocal()
    val artwork = artwork("starry-night", "The Starry Night")
    repository.putArtworkInRepositoryUnsavedSection(artwork)

    assertEquals(artwork, repository.getArtwork("starry-night"))
  }

  @Test
  fun getArtwork_whenUidIsUnknown_throwsNoSuchElementException() = runTest {
    val repository = ArtworkRepositoryLocal()

    assertThrowsSuspend<NoSuchElementException> { repository.getArtwork("unknown") }
  }

  @Test
  fun getArtwork_whenUidIsBlank_throwsIllegalArgumentException() = runTest {
    val repository = ArtworkRepositoryLocal()

    assertThrowsSuspend<IllegalArgumentException> { repository.getArtwork(" ") }
    assertThrowsSuspend<IllegalArgumentException> { repository.getArtwork("") }
    assertThrowsSuspend<IllegalArgumentException> { repository.getArtwork("\n") }
  }

  @Test
  fun putArtworkInRepositoryUnsavedSection_withExistingUid_throwIllegalArgumentException() =
      runTest {
        val repository = ArtworkRepositoryLocal()
        val original = artwork("same-id", "Original")
        val replacement = artwork("same-id", "Replacement")
        repository.putArtworkInRepositoryUnsavedSection(original)

        assertThrowsSuspend<IllegalArgumentException> {
          repository.putArtworkInRepositoryUnsavedSection(replacement)
        }
      }

  @Test
  fun searchArtworks_returnsArtworksWhoseNamesContainQueryIgnoringCase() = runTest {
    val repository = ArtworkRepositoryLocal()
    val matchingArtwork = artwork("starry-night", "The Starry Night")
    val artistOnlyMatch = artwork("other-artwork", "Other Artwork", "The Starry Night")
    repository.putArtworkInRepositoryUnsavedSection(matchingArtwork)
    repository.putArtworkInRepositoryUnsavedSection(artistOnlyMatch)

    val results = repository.searchArtworks("STARRY")

    assertEquals(listOf(matchingArtwork), results)
  }

  @Test
  fun searchArtworks_withEmptyQuery_returnsEmptyList() = runTest {
    val repository = ArtworkRepositoryLocal()
    val artworks = listOf(artwork("first", "First"), artwork("second", "Second"))
    artworks.forEach(repository::putArtworkInRepositoryUnsavedSection)

    assert(repository.searchArtworks("").toSet().isEmpty())
  }

  @Test
  fun savedArtworks_areEmptyInitially() = runTest {
    val repository = ArtworkRepositoryLocal()

    assertTrue(repository.getSavedArtworks().isEmpty())
  }

  @Test
  fun saveArtwork_makesArtworkSavedAndReturnsIt() = runTest {
    val repository = ArtworkRepositoryLocal()
    val artwork = artwork("saved", "Saved Artwork")

    repository.saveArtwork(artwork)

    assertTrue(repository.isArtworkSaved("saved"))
    assertEquals(listOf(artwork), repository.getSavedArtworks())
  }

  @Test
  fun saveArtwork_calledTwiceForSameUid_doesNotCreateDuplicate() = runTest {
    val repository = ArtworkRepositoryLocal()
    val artwork = artwork("saved", "Saved Artwork")
    repository.saveArtwork(artwork)

    repository.saveArtwork(artwork)

    assertEquals(listOf(artwork), repository.getSavedArtworks())
  }

  @Test
  fun isArtworkSaved_whenUidIsUnknown_returnsFalse() = runTest {
    val repository = ArtworkRepositoryLocal()

    assertFalse(repository.isArtworkSaved("unknown"))
  }

  @Test
  fun isArtworkSaved_whenUidIsBlank_throwsIllegalArgumentException() = runTest {
    val repository = ArtworkRepositoryLocal()

    assertThrowsSuspend<IllegalArgumentException> { repository.isArtworkSaved("") }
    assertThrowsSuspend<IllegalArgumentException> { repository.isArtworkSaved(" ") }
    assertThrowsSuspend<IllegalArgumentException> { repository.isArtworkSaved("\n") }
  }

  @Test
  fun removeArtworkFromSaved_removesExistingArtwork() = runTest {
    val repository = ArtworkRepositoryLocal()
    val artwork = artwork("saved", "Saved Artwork")
    repository.saveArtwork(artwork)

    repository.removeArtworkFromSaved("saved")

    assertFalse(repository.isArtworkSaved("saved"))
    assertTrue(repository.getSavedArtworks().isEmpty())
  }

  @Test
  fun removeArtworkFromSaved_whenUidIsUnknown_throwsNoSuchElementException() = runTest {
    val repository = ArtworkRepositoryLocal()

    assertThrowsSuspend<NoSuchElementException> { repository.removeArtworkFromSaved("unknown") }
  }

  @Test
  fun removeArtworkFromSaved_whenUidIsBlank_throwsIllegalArgumentException() = runTest {
    val repository = ArtworkRepositoryLocal()

    assertThrowsSuspend<IllegalArgumentException> { repository.removeArtworkFromSaved("") }
    assertThrowsSuspend<IllegalArgumentException> { repository.removeArtworkFromSaved(" ") }
    assertThrowsSuspend<IllegalArgumentException> { repository.removeArtworkFromSaved("\n") }
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

  private fun artwork(uid: String, name: String, artistName: String? = null) =
      Artwork(
          uid = uid,
          name = name,
          artistName = artistName,
          location = null,
          conceptionDate = null,
          dimension = null,
          description = null,
          museum = null,
      )
}
