// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.model.artwork

import com.artista.artista.model.error.AppException
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
  fun getArtwork_whenUidIsUnknown_throwsNotFound() = runTest {
    val repository = ArtworkRepositoryLocal()

    assertThrowsSuspend<AppException.NotFound> { repository.getArtwork("unknown") }
  }

  @Test
  fun getArtwork_whenUidIsBlank_throwsInvalidInput() = runTest {
    val repository = ArtworkRepositoryLocal()

    assertThrowsSuspend<AppException.InvalidInput> { repository.getArtwork(" ") }
    assertThrowsSuspend<AppException.InvalidInput> { repository.getArtwork("") }
    assertThrowsSuspend<AppException.InvalidInput> { repository.getArtwork("\n") }
  }

  // putArtworkInRepositoryUnsavedSection is a helper to fill the repository for demos and tests,
  // not part of the repository interface. A duplicate uid there is a programming mistake, never an
  // error shown to the user, so it keeps IllegalArgumentException instead of an AppException.
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
  fun searchArtworks_skipsArtworksWithoutAName() = runTest {
    val repository = ArtworkRepositoryLocal()
    val namedArtwork = artwork("starry-night", "The Starry Night")
    val unnamedArtwork = artwork("untitled", null, "Starry Painter")
    repository.putArtworkInRepositoryUnsavedSection(namedArtwork)
    repository.putArtworkInRepositoryUnsavedSection(unnamedArtwork)

    val results = repository.searchArtworks("starry")

    assertEquals(listOf(namedArtwork), results)
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
  fun isArtworkSaved_whenUidIsBlank_throwsInvalidInput() = runTest {
    val repository = ArtworkRepositoryLocal()

    assertThrowsSuspend<AppException.InvalidInput> { repository.isArtworkSaved("") }
    assertThrowsSuspend<AppException.InvalidInput> { repository.isArtworkSaved(" ") }
    assertThrowsSuspend<AppException.InvalidInput> { repository.isArtworkSaved("\n") }
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
  fun removeArtworkFromSaved_whenUidIsUnknown_throwsNotFound() = runTest {
    val repository = ArtworkRepositoryLocal()

    assertThrowsSuspend<AppException.NotFound> { repository.removeArtworkFromSaved("unknown") }
  }

  @Test
  fun removeArtworkFromSaved_whenUidIsBlank_throwsInvalidInput() = runTest {
    val repository = ArtworkRepositoryLocal()

    assertThrowsSuspend<AppException.InvalidInput> { repository.removeArtworkFromSaved("") }
    assertThrowsSuspend<AppException.InvalidInput> { repository.removeArtworkFromSaved(" ") }
    assertThrowsSuspend<AppException.InvalidInput> { repository.removeArtworkFromSaved("\n") }
  }

  /** Verifies that the nearby query returns the populated artworks in insertion order. */
  @Test
  fun getArtworksNearLocation_returnsPopulatedArtworksInInsertionOrder() = runTest {
    val repository = ArtworkRepositoryLocal()
    mockArtworks.forEach { repository.putArtworkInRepositoryUnsavedSection(it) }

    assertEquals(mockArtworks, repository.getArtworksNearLocation(null))
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

  private fun artwork(uid: String, name: String?, artistName: String? = null) =
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
