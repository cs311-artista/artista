// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.artwork

import com.artista.artista.MainDispatcherRule
import com.artista.artista.R
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkId
import com.artista.artista.model.artwork.SavedArtworksRepository
import com.artista.artista.model.error.AppException
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [SavedArtworksViewModel].
 *
 * @author Kaio R. Freitas Pereira Nascimento (krfpn)
 */
class SavedArtworksViewModelTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val repository = FakeSavedArtworksRepository()
  private val viewModel = SavedArtworksViewModel(repository)

  private val olderArtwork = artwork("olderId")
  private val newerArtwork = artwork("newerId")

  /** Verifies that a load failing with an [AppException] stores that type's message. */
  @Test
  fun refreshSavedArtworks_onAppException_usesItsMessage() {
    // Makes the next load fail with a network error.
    repository.loadFailure = AppException.Network()

    // Only queues the load; it does not run yet.
    viewModel.refreshSavedArtworks()
    // Runs the queued load, which fails and stores the message.
    runPendingCoroutines()

    // The stored message must match the error type.
    assertEquals(R.string.error_network, viewModel.uiState.value.loadErrorMessageResId)
  }

  /** Verifies that a load failing with any other exception stores the unknown error message. */
  @Test
  fun refreshSavedArtworks_onUnexpectedException_usesUnknownMessage() {
    // Makes the next load fail with a non-AppException error.
    repository.loadFailure = IllegalStateException("Simulated unexpected failure")

    // Only queues the load; it does not run yet.
    viewModel.refreshSavedArtworks()
    // Runs the queued load, which fails and stores the message.
    runPendingCoroutines()

    // Any other exception must give the unknown message.
    assertEquals(R.string.error_unknown, viewModel.uiState.value.loadErrorMessageResId)
  }

  /** Verifies that a slower older load cannot replace the list delivered by a newer one. */
  @Test
  fun refreshSavedArtworks_whenReplaced_olderLoadDoesNotOverwriteList() {
    // Answer of the first load, completed by hand.
    val olderLoad = CompletableDeferred<List<Artwork>>()
    // Answer of the second load, completed by hand.
    val newerLoad = CompletableDeferred<List<Artwork>>()
    // Makes each load pause until its answer is completed.
    repository.pendingLoads += listOf(olderLoad, newerLoad)

    // Queues the first load.
    viewModel.refreshSavedArtworks()
    // Runs it until it pauses on its answer.
    runPendingCoroutines()
    // Queues the second load, which cancels the first.
    viewModel.refreshSavedArtworks()
    // Runs it until it pauses on its answer.
    runPendingCoroutines()

    // Answers in the wrong order: without the cancellation, the older list would be kept.
    newerLoad.complete(listOf(newerArtwork))
    // Lets the second load finish.
    runPendingCoroutines()
    // Answers the canceled first load last.
    olderLoad.complete(listOf(olderArtwork))
    // Would let the first load finish if it was still running.
    runPendingCoroutines()

    // The newer list must still be displayed.
    assertEquals(listOf(newerArtwork), viewModel.uiState.value.artworks)
  }

  /** Verifies that an unsave failing with an [AppException] stores that type's message. */
  @Test
  fun onArtworkUnsave_onAppException_usesItsMessage() {
    // Makes the next removal fail with a network error.
    repository.removeFailure = AppException.Network()

    // Only queues the removal; it does not run yet.
    viewModel.onArtworkUnsave(olderArtwork)
    // Runs the queued removal, which fails and stores the message.
    runPendingCoroutines()

    // The stored message must match the error type.
    assertEquals(R.string.error_network, viewModel.uiState.value.unsaveErrorMessageResId)
  }

  /** Verifies that clearing the load error removes its message. */
  @Test
  fun clearLoadError_clearsMessage() {
    // Makes the next load fail with a network error.
    repository.loadFailure = AppException.Network()
    // Only queues the load; it does not run yet.
    viewModel.refreshSavedArtworks()
    // Runs the queued load, which fails and stores the message.
    runPendingCoroutines()
    // Confirms there is a message to clear.
    assertNotNull(viewModel.uiState.value.loadErrorMessageResId)

    // Clears the message, as the screen does after the toast.
    viewModel.clearLoadError()

    // The message must be gone.
    assertNull(viewModel.uiState.value.loadErrorMessageResId)
  }

  /** Verifies that clearing the unsave error removes its message. */
  @Test
  fun clearUnsaveError_clearsMessage() {
    // Makes the next removal fail with a network error.
    repository.removeFailure = AppException.Network()
    // Only queues the removal; it does not run yet.
    viewModel.onArtworkUnsave(olderArtwork)
    // Runs the queued removal, which fails and stores the message.
    runPendingCoroutines()
    // Confirms there is a message to clear.
    assertNotNull(viewModel.uiState.value.unsaveErrorMessageResId)

    // Clears the message, as the screen does after the toast.
    viewModel.clearUnsaveError()

    // The message must be gone.
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

    /** Answers given to successive loads, each one suspending until the test completes it. */
    val pendingLoads = ArrayDeque<CompletableDeferred<List<Artwork>>>()

    override suspend fun isArtworkSaved(uid: ArtworkId): Boolean = false

    override suspend fun saveArtwork(artwork: Artwork) = Unit

    override suspend fun removeArtworkFromSaved(uid: ArtworkId) {
      removeFailure?.let { throw it }
    }

    override suspend fun getSavedArtworks(): List<Artwork> {
      loadFailure?.let { throw it }
      return pendingLoads.removeFirstOrNull()?.await() ?: emptyList()
    }
  }
}
