// Co-authored-by: Gemini <gemini@google.com>
package com.artista.artista.ui.artwork

import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

  private val testDispatcher = UnconfinedTestDispatcher()
  private lateinit var fakeRepository: FakeArtworkRepository
  private lateinit var viewModel: ArtworkScreenViewModel

  private val sampleArtwork =
      Artwork(
          name = "Mona Lisa",
          artistName = "Leonardo da Vinci",
          description = "Portrait of Lisa Gherardini",
          conceptionDate = null,
          museum = "Louvre",
          location = null,
          dimension = null,
      )

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    fakeRepository = FakeArtworkRepository()
    viewModel = ArtworkScreenViewModel(savedArtworkRepository = fakeRepository)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun setArtwork_updatesArtworkAndIsSavedFromRepository() = runTest {
    fakeRepository.saveArtwork(sampleArtwork)

    viewModel.setArtwork(sampleArtwork)

    val state = viewModel.uiState.value
    assertEquals(sampleArtwork, state.artwork)
    assertTrue(state.isSaved)
  }

  @Test
  fun setArtwork_whenNotSavedInRepository_setsIsSavedToFalse() = runTest {
    viewModel.setArtwork(sampleArtwork)

    val state = viewModel.uiState.value
    assertEquals(sampleArtwork, state.artwork)
    assertFalse(state.isSaved)
  }

  @Test
  fun toggleSaveArtwork_whenArtworkNotSaved_savesArtworkInRepositoryAndUpdatesState() = runTest {
    viewModel.setArtwork(sampleArtwork)
    viewModel.toggleSaveArtwork()

    assertTrue(viewModel.uiState.value.isSaved)
    assertTrue(fakeRepository.isArtworkSaved(sampleArtwork.name))
  }

  @Test
  fun toggleSaveArtwork_whenArtworkAlreadySaved_removesArtworkFromRepositoryAndUpdatesState() =
      runTest {
        fakeRepository.saveArtwork(sampleArtwork)
        viewModel.setArtwork(sampleArtwork)

        viewModel.toggleSaveArtwork()

        assertFalse(viewModel.uiState.value.isSaved)
        assertFalse(fakeRepository.isArtworkSaved(sampleArtwork.name))
      }

  @Test
  fun toggleSaveArtwork_whenCurrentArtworkIsNull_doesNothing() {
    viewModel.toggleSaveArtwork()

    assertFalse(viewModel.uiState.value.isSaved)
  }

  @Test
  fun toggleSaveArtwork_whenRepositoryEmptyAndThrowsException_revertsState() = runTest {
    viewModel.setArtwork(sampleArtwork)
    fakeRepository.shouldThrowError = true

    viewModel.toggleSaveArtwork()

    assertFalse(viewModel.uiState.value.isSaved)
  }

  /** Fake implementation of [ArtworkRepository] for testing [ArtworkScreenViewModel]. */
  class FakeArtworkRepository : ArtworkRepository {
    private val savedArtworks = mutableListOf<Artwork>()
    var shouldThrowError = false

    override suspend fun isArtworkSaved(artworkName: String): Boolean {
      if (shouldThrowError) throw RuntimeException("Simulated error")
      return savedArtworks.any { it.name == artworkName }
    }

    override suspend fun saveArtwork(artwork: Artwork) {
      if (shouldThrowError) throw RuntimeException("Simulated database failure")
      savedArtworks.removeAll { it.name == artwork.name }
      savedArtworks.add(artwork)
    }

    override suspend fun removeArtwork(artworkName: String) {
      if (shouldThrowError) throw RuntimeException("Simulated database failure")
      savedArtworks.removeAll { it.name == artworkName }
    }
  }
}
