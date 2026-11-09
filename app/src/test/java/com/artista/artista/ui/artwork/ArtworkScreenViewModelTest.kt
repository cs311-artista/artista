// Co-authored-by: Gemini <gemini@google.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.artwork

import com.artista.artista.R
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkId
import com.artista.artista.model.artwork.ArtworkRepositoryProvider
import com.artista.artista.model.artwork.SavedArtworksRepository
import com.artista.artista.model.artwork.WikiDataRepository
import com.artista.artista.model.error.AppException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ArtworkScreenViewModel].
 *
 * @author hixeum
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArtworkScreenViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private val savedRepository = ArtworkRepositoryProvider.mockRepository
  private val wikiDataRepository = ArtworkRepositoryProvider.mockRepository
  private lateinit var viewModel: ArtworkScreenViewModel

  private val sampleArtwork =
      Artwork(
          uid = "monaId",
          name = "Mona Lisa",
          artist = Artist("test-da-vinci", "Leonardo da Vinci"),
          description = "Portrait of Lisa Gherardini",
          conceptionDate = null,
          museum = "Louvre",
          location = null,
          dimension = null,
      )

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    savedRepository.clear()
    wikiDataRepository.clear()
    ArtworkRepositoryProvider.populateAPI(listOf(sampleArtwork))
    viewModel = ArtworkScreenViewModel(sampleArtwork.uid, wikiDataRepository, savedRepository)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun setArtwork_updatesArtworkAndIsSavedFromRepository() = runTest {
    savedRepository.saveArtwork(sampleArtwork)

    viewModel.setArtwork(sampleArtwork.uid)
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(sampleArtwork, state.artwork)
    assertTrue(state.isSaved)
  }

  @Test
  fun setArtwork_exposesLoadingUntilArtworkIsFetched() = runTest {
    val artworkResult = CompletableDeferred<Artwork>()
    val delayedRepository =
        object : WikiDataRepository by wikiDataRepository {
          override suspend fun getArtwork(uid: ArtworkId): Artwork = artworkResult.await()
        }
    viewModel = ArtworkScreenViewModel(sampleArtwork.uid, delayedRepository, savedRepository)

    assertTrue(viewModel.uiState.value.isLoading)
    assertEquals(null, viewModel.uiState.value.artwork)

    artworkResult.complete(sampleArtwork)
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(sampleArtwork, viewModel.uiState.value.artwork)
    assertFalse(viewModel.uiState.value.hasLoadFailed)
  }

  @Test
  fun setArtwork_whenLoadingFails_clearsLoadingAndExposesLoadFailure() = runTest {
    val artworkResult = CompletableDeferred<Artwork>()
    val delayedRepository =
        object : WikiDataRepository by wikiDataRepository {
          override suspend fun getArtwork(uid: ArtworkId): Artwork = artworkResult.await()
        }
    viewModel = ArtworkScreenViewModel(sampleArtwork.uid, delayedRepository, savedRepository)

    assertTrue(viewModel.uiState.value.isLoading)
    artworkResult.completeExceptionally(AppException.Network())
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isLoading)
    assertTrue(viewModel.uiState.value.hasLoadFailed)
    assertEquals(R.string.error_network, viewModel.uiState.value.loadErrorMessageResId)
    assertEquals(null, viewModel.uiState.value.artwork)
  }

  @Test
  fun retryArtwork_restartsLoadingAndDisplaysTheArtworkAfterRetry() = runTest {
    val firstResult = CompletableDeferred<Artwork>()
    val secondResult = CompletableDeferred<Artwork>()
    var requestCount = 0
    val delayedRepository =
        object : WikiDataRepository by wikiDataRepository {
          override suspend fun getArtwork(uid: ArtworkId): Artwork {
            requestCount++
            return if (requestCount == 1) firstResult.await() else secondResult.await()
          }
        }
    viewModel = ArtworkScreenViewModel(sampleArtwork.uid, delayedRepository, savedRepository)
    firstResult.completeExceptionally(AppException.Network())
    advanceUntilIdle()

    viewModel.retryArtwork()
    assertTrue(viewModel.uiState.value.isLoading)
    assertFalse(viewModel.uiState.value.hasLoadFailed)

    secondResult.complete(sampleArtwork)
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(sampleArtwork, viewModel.uiState.value.artwork)
    assertFalse(viewModel.uiState.value.hasLoadFailed)
  }

  @Test
  fun setArtwork_whenNotSavedInRepository_setsIsSavedToFalse() = runTest {
    viewModel.setArtwork(sampleArtwork.uid)
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(sampleArtwork, state.artwork)
    assertFalse(state.isSaved)
  }

  @Test
  fun toggleSaveArtwork_whenArtworkNotSaved_savesArtworkInRepositoryAndUpdatesState() = runTest {
    viewModel.setArtwork(sampleArtwork.uid)
    advanceUntilIdle()
    viewModel.toggleSaveArtwork()
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.isSaved)
    assertTrue(savedRepository.isArtworkSaved(sampleArtwork.uid))
  }

  @Test
  fun toggleSaveArtwork_whenArtworkAlreadySaved_removesArtworkFromRepositoryAndUpdatesState() =
      runTest {
        savedRepository.saveArtwork(sampleArtwork)
        viewModel.setArtwork(sampleArtwork.uid)
        advanceUntilIdle()

        viewModel.toggleSaveArtwork()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaved)
        assertFalse(savedRepository.isArtworkSaved(sampleArtwork.uid))
      }

  @Test
  fun setArtwork_whenArtworkHasNoName_tracksSavedStateByUid() = runTest {
    val untitledArtwork = sampleArtwork.copy(name = null)
    savedRepository.saveArtwork(untitledArtwork)

    viewModel.setArtwork(untitledArtwork.uid)
    advanceUntilIdle()
    assertTrue(viewModel.uiState.value.isSaved)

    viewModel.toggleSaveArtwork()
    advanceUntilIdle()
    assertFalse(viewModel.uiState.value.isSaved)
    assertFalse(savedRepository.isArtworkSaved(untitledArtwork.uid))
  }

  @Test
  fun toggleSaveArtwork_whenCurrentArtworkIsNull_doesNothing() {
    viewModel.toggleSaveArtwork()

    assertFalse(viewModel.uiState.value.isSaved)
  }

  @Test
  fun setArtwork_whenRepositoryThrowsAppException_showsMappedError() = runTest {
    val invalidArtwork = sampleArtwork.copy(uid = "")

    viewModel.setArtwork(invalidArtwork.uid)
    advanceUntilIdle()

    assertEquals(null, viewModel.uiState.value.artwork)
    assertFalse(viewModel.uiState.value.isSaved)
    assertEquals(R.string.error_invalid_input, viewModel.uiState.value.errorMessageResId)
  }

  @Test
  fun toggleSaveArtwork_whileSaveIsPending_ignoresFurtherToggles() = runTest {
    viewModel.setArtwork(sampleArtwork.uid)
    advanceUntilIdle()

    viewModel.toggleSaveArtwork()
    assertTrue(viewModel.uiState.value.isSaveInProgress)
    assertTrue(viewModel.uiState.value.isSaved)

    viewModel.toggleSaveArtwork()

    assertTrue(viewModel.uiState.value.isSaveInProgress)
    assertTrue(viewModel.uiState.value.isSaved)
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isSaveInProgress)
    assertTrue(viewModel.uiState.value.isSaved)
    assertTrue(savedRepository.isArtworkSaved(sampleArtwork.uid))
  }

  @Test
  fun toggleSaveArtwork_whenRepositoryThrowsAppException_revertsStateAndShowsMappedError() =
      runTest {
        val failingSavedRepository =
            object : SavedArtworksRepository {
              override suspend fun isArtworkSaved(uid: ArtworkId): Boolean = false

              override suspend fun saveArtwork(artwork: Artwork) {
                throw AppException.InvalidInput("Simulated invalid artwork")
              }

              override suspend fun removeArtworkFromSaved(uid: ArtworkId) = Unit

              override suspend fun getSavedArtworks(): List<Artwork> = emptyList()
            }
        viewModel =
            ArtworkScreenViewModel(
                sampleArtwork.uid,
                wikiDataRepository,
                failingSavedRepository,
            )

        advanceUntilIdle()
        viewModel.clearErrorMessage()

        viewModel.toggleSaveArtwork()
        assertTrue(viewModel.uiState.value.isSaveInProgress)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSaved)
        assertFalse(viewModel.uiState.value.isSaveInProgress)
        assertEquals(R.string.error_invalid_input, viewModel.uiState.value.errorMessageResId)
      }
}
