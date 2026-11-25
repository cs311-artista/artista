// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.artwork

import com.artista.artista.MainDispatcherRule
import com.artista.artista.R
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkFilter
import com.artista.artista.model.artwork.ArtworkId
import com.artista.artista.model.artwork.SavedArtworksRepository
import com.artista.artista.model.error.AppException
import com.artista.artista.model.map.Location
import java.time.Instant
import java.util.Date
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [SavedArtworksViewModel].
 *
 * @author krfpn, hixeum
 */
class SavedArtworksViewModelTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val repository = FakeSavedArtworksRepository()
  private val viewModel = SavedArtworksViewModel(repository)

  private val daVinci = Artist(uid = "davinci", name = "Leonardo da Vinci")
  private val picasso = Artist(uid = "picasso", name = "Pablo Picasso")

  private val monaLisa =
      Artwork(
          uid = "monaLisa",
          name = "Mona Lisa",
          artist = daVinci,
          description = "Portrait",
          conceptionDate = Date.from(Instant.parse("1503-01-01T00:00:00Z")),
          museum = "Louvre",
          location = Location(latitude = 48.86, longitude = 2.33, name = "Paris"),
          dimension = null,
      )

  private val guernica =
      Artwork(
          uid = "guernica",
          name = "Guernica",
          artist = picasso,
          description = "Mural",
          conceptionDate = Date.from(Instant.parse("1937-01-01T00:00:00Z")),
          museum = "Reina Sofia",
          location = Location(latitude = 40.4, longitude = -3.69, name = "Madrid"),
          dimension = null,
      )

  private val olderArtwork = artwork("olderId")
  private val newerArtwork = artwork("newerId")

  /** Verifies that a load failing with an [AppException] stores that type's message. */
  @Test
  fun refreshSavedArtworks_onAppException_usesItsMessage() {
    repository.loadFailure = AppException.Network()

    viewModel.refreshSavedArtworks()
    runPendingCoroutines()

    assertEquals(R.string.error_network, viewModel.uiState.value.loadErrorMessageResId)
  }

  /** Verifies that a load failing with any other exception stores the unknown error message. */
  @Test
  fun refreshSavedArtworks_onUnexpectedException_usesUnknownMessage() {
    repository.loadFailure = IllegalStateException("Simulated unexpected failure")

    viewModel.refreshSavedArtworks()
    runPendingCoroutines()

    assertEquals(R.string.error_unknown, viewModel.uiState.value.loadErrorMessageResId)
  }

  /** Verifies that a slower older load cannot replace the list delivered by a newer one. */
  @Test
  fun refreshSavedArtworks_whenReplaced_olderLoadDoesNotOverwriteList() {
    val olderLoad = CompletableDeferred<List<Artwork>>()
    val newerLoad = CompletableDeferred<List<Artwork>>()
    repository.pendingLoads += listOf(olderLoad, newerLoad)

    viewModel.refreshSavedArtworks()
    runPendingCoroutines()
    // Queues the second load, which cancels the first.
    viewModel.refreshSavedArtworks()
    runPendingCoroutines()

    // Answers in the wrong order: without the cancellation, the older list would be kept.
    newerLoad.complete(listOf(newerArtwork))
    runPendingCoroutines()
    olderLoad.complete(listOf(olderArtwork))
    // Would let the first load finish if it was still running.
    runPendingCoroutines()

    assertEquals(listOf(newerArtwork), viewModel.uiState.value.artworks)
  }

  /** Verifies that applying a filter updates the active filter and filters the artwork list. */
  @Test
  fun applyFilter_updatesActiveFilterAndFiltersArtworksList() {
    repository.savedList = listOf(monaLisa, guernica)
    viewModel.refreshSavedArtworks()
    runPendingCoroutines()

    assertEquals(2, viewModel.uiState.value.artworks.size)

    val filter = ArtworkFilter(artists = setOf("Leonardo da Vinci"))
    viewModel.applyFilter(filter)

    assertEquals(filter, viewModel.uiState.value.activeFilter)
    assertEquals(listOf(monaLisa), viewModel.uiState.value.artworks)
  }

  /** Verifies that refreshing reapplies the active filter to the newly loaded artworks. */
  @Test
  fun refreshSavedArtworks_withActiveFilterReappliesFilterToLoadedArtworks() {
    repository.savedList = listOf(monaLisa)
    viewModel.refreshSavedArtworks()
    runPendingCoroutines()

    val filter = ArtworkFilter(artists = setOf("Pablo Picasso"))
    viewModel.applyFilter(filter)
    assertTrue(viewModel.uiState.value.artworks.isEmpty())

    repository.savedList = listOf(monaLisa, guernica)
    viewModel.refreshSavedArtworks()
    runPendingCoroutines()

    assertEquals(filter, viewModel.uiState.value.activeFilter)
    assertEquals(listOf(guernica), viewModel.uiState.value.artworks)
  }

  /** Verifies that options lists (artists, museums, locations) are populated on refresh. */
  @Test
  fun refreshSavedArtworks_populatesArtistMuseumAndLocationOptions() {
    repository.savedList = listOf(monaLisa, guernica)
    viewModel.refreshSavedArtworks()
    runPendingCoroutines()

    assertEquals(
        listOf("Leonardo da Vinci", "Pablo Picasso"),
        viewModel.uiState.value.artistOptions,
    )
    assertEquals(listOf("Louvre", "Reina Sofia"), viewModel.uiState.value.museumOptions)
    assertEquals(listOf("Madrid", "Paris"), viewModel.uiState.value.locationOptions)
  }

  /** Verifies that clearing an active filter restores the full artwork list. */
  @Test
  fun clearFilter_resetsActiveFilterAndRestoresFullList() {
    repository.savedList = listOf(monaLisa, guernica)
    viewModel.refreshSavedArtworks()
    runPendingCoroutines()

    viewModel.applyFilter(ArtworkFilter(artists = setOf("Pablo Picasso")))
    assertEquals(listOf(guernica), viewModel.uiState.value.artworks)

    viewModel.clearFilter()
    assertEquals(ArtworkFilter(), viewModel.uiState.value.activeFilter)
    assertEquals(listOf(monaLisa, guernica), viewModel.uiState.value.artworks)
  }

  /**
   * Verifies that unsaving an artwork under an active filter updates the filtered list and options.
   */
  @Test
  fun onArtworkUnsave_withActiveFilter_updatesFilteredListAndOptions() {
    repository.savedList = listOf(monaLisa, guernica)
    viewModel.refreshSavedArtworks()
    runPendingCoroutines()

    viewModel.applyFilter(ArtworkFilter(museums = setOf("Louvre")))
    assertEquals(listOf(monaLisa), viewModel.uiState.value.artworks)

    viewModel.onArtworkUnsave(monaLisa)
    runPendingCoroutines()

    assertTrue(viewModel.uiState.value.artworks.isEmpty())
    assertEquals(listOf("Pablo Picasso"), viewModel.uiState.value.artistOptions)
    assertEquals(listOf("Reina Sofia"), viewModel.uiState.value.museumOptions)
  }

  /** Verifies that an unsave failure restores the artwork in its original unfiltered position. */
  @Test
  fun onArtworkUnsave_onFailure_restoresArtworkAtOriginalPosition() {
    repository.savedList = listOf(monaLisa, guernica)
    viewModel.refreshSavedArtworks()
    runPendingCoroutines()

    viewModel.applyFilter(ArtworkFilter(artists = setOf("Pablo Picasso")))
    assertEquals(listOf(guernica), viewModel.uiState.value.artworks)

    repository.removeFailure = AppException.Network()
    viewModel.onArtworkUnsave(guernica)
    runPendingCoroutines()

    assertEquals(R.string.error_network, viewModel.uiState.value.unsaveErrorMessageResId)
    assertEquals(
        listOf("Leonardo da Vinci", "Pablo Picasso"),
        viewModel.uiState.value.artistOptions,
    )

    viewModel.clearFilter()

    assertEquals(listOf(monaLisa, guernica), viewModel.uiState.value.artworks)
  }

  /** Verifies that an unsave failing with an [AppException] stores that type's message. */
  @Test
  fun onArtworkUnsave_onAppException_usesItsMessage() {
    repository.removeFailure = AppException.Network()

    viewModel.onArtworkUnsave(olderArtwork)
    runPendingCoroutines()

    assertEquals(R.string.error_network, viewModel.uiState.value.unsaveErrorMessageResId)
  }

  /** Verifies that clearing the load error removes its message. */
  @Test
  fun clearLoadError_clearsMessage() {
    repository.loadFailure = AppException.Network()
    viewModel.refreshSavedArtworks()
    runPendingCoroutines()
    // Confirms there is a message to clear.
    assertNotNull(viewModel.uiState.value.loadErrorMessageResId)

    viewModel.clearLoadError()

    assertNull(viewModel.uiState.value.loadErrorMessageResId)
  }

  /** Verifies that clearing the unsave error removes its message. */
  @Test
  fun clearUnsaveError_clearsMessage() {
    repository.removeFailure = AppException.Network()
    viewModel.onArtworkUnsave(olderArtwork)
    runPendingCoroutines()
    // Confirms there is a message to clear.
    assertNotNull(viewModel.uiState.value.unsaveErrorMessageResId)

    viewModel.clearUnsaveError()

    assertNull(viewModel.uiState.value.unsaveErrorMessageResId)
  }

  /** Runs the coroutines the ViewModel launched, which the test dispatcher holds until asked. */
  private fun runPendingCoroutines() {
    mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
  }

  /**
   * Creates an artwork that only differs by its identifier.
   *
   * @param uid unique identifier of the artwork
   * @return the artwork
   */
  private fun artwork(uid: ArtworkId): Artwork =
      Artwork(
          uid = uid,
          name = "Artwork $uid",
          artist = null,
          description = null,
          conceptionDate = null,
          museum = null,
          location = null,
          dimension = null,
      )

  /** In-memory test double implementing [SavedArtworksRepository]. */
  private class FakeSavedArtworksRepository : SavedArtworksRepository {
    /** When set, [getSavedArtworks] throws it. */
    var loadFailure: Exception? = null

    /** When set, [removeArtworkFromSaved] throws it. */
    var removeFailure: Exception? = null

    /** Saved artworks list returned when [pendingLoads] is empty. */
    var savedList: List<Artwork> = emptyList()

    /** Answers given to successive loads, each one suspending until the test completes it. */
    val pendingLoads = ArrayDeque<CompletableDeferred<List<Artwork>>>()

    override suspend fun isArtworkSaved(uid: ArtworkId): Boolean = false

    override suspend fun saveArtwork(artwork: Artwork) = Unit

    override suspend fun removeArtworkFromSaved(uid: ArtworkId) {
      removeFailure?.let { throw it }
    }

    override suspend fun getSavedArtworks(): List<Artwork> {
      loadFailure?.let { throw it }
      return pendingLoads.removeFirstOrNull()?.await() ?: savedList
    }

    override suspend fun getSavedArtwork(uid: ArtworkId): Artwork? = savedList.firstOrNull {
      it.uid == uid
    }

    override suspend fun getCachedSavedArtwork(uid: ArtworkId): Artwork? = getSavedArtwork(uid)
  }
}
