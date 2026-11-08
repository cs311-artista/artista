// Co-authored-by: Gemini <gemini@google.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.artwork

import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkId
import com.artista.artista.model.artwork.ArtworkType
import com.artista.artista.model.artwork.SavedArtworksRepository
import com.artista.artista.model.artwork.SearchArea
import com.artista.artista.model.artwork.WikiDataRepository
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
  private lateinit var fakeWikiDataRepository: FakeWikiDataRepository
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
    fakeRepository = FakeArtworkRepository()
    fakeWikiDataRepository = FakeWikiDataRepository(sampleArtwork)
    viewModel = ArtworkScreenViewModel(sampleArtwork.uid, fakeWikiDataRepository, fakeRepository)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun setArtwork_updatesArtworkAndIsSavedFromRepository() = runTest {
    fakeRepository.saveArtwork(sampleArtwork)

    viewModel.setArtwork(sampleArtwork.uid)

    val state = viewModel.uiState.value
    assertEquals(sampleArtwork, state.artwork)
    assertTrue(state.isSaved)
  }

  @Test
  fun setArtwork_whenNotSavedInRepository_setsIsSavedToFalse() = runTest {
    viewModel.setArtwork(sampleArtwork.uid)

    val state = viewModel.uiState.value
    assertEquals(sampleArtwork, state.artwork)
    assertFalse(state.isSaved)
  }

  @Test
  fun toggleSaveArtwork_whenArtworkNotSaved_savesArtworkInRepositoryAndUpdatesState() = runTest {
    viewModel.setArtwork(sampleArtwork.uid)
    viewModel.toggleSaveArtwork()

    assertTrue(viewModel.uiState.value.isSaved)
    assertTrue(fakeRepository.isArtworkSaved(sampleArtwork.uid))
  }

  @Test
  fun toggleSaveArtwork_whenArtworkAlreadySaved_removesArtworkFromRepositoryAndUpdatesState() =
      runTest {
        fakeRepository.saveArtwork(sampleArtwork)
        viewModel.setArtwork(sampleArtwork.uid)

        viewModel.toggleSaveArtwork()

        assertFalse(viewModel.uiState.value.isSaved)
        assertFalse(fakeRepository.isArtworkSaved(sampleArtwork.uid))
      }

  @Test
  fun setArtwork_whenArtworkHasNoName_tracksSavedStateByUid() = runTest {
    val untitledArtwork = sampleArtwork.copy(name = null)
    fakeRepository.saveArtwork(untitledArtwork)

    fakeWikiDataRepository.artwork = untitledArtwork
    viewModel.setArtwork(untitledArtwork.uid)
    assertTrue(viewModel.uiState.value.isSaved)

    viewModel.toggleSaveArtwork()
    assertFalse(viewModel.uiState.value.isSaved)
    assertFalse(fakeRepository.isArtworkSaved(untitledArtwork.uid))
  }

  @Test
  fun toggleSaveArtwork_whenCurrentArtworkIsNull_doesNothing() {
    fakeWikiDataRepository.shouldThrow = true
    viewModel.setArtwork(sampleArtwork.uid)

    viewModel.toggleSaveArtwork()

    assertFalse(viewModel.uiState.value.isSaved)
  }

  @Test
  fun toggleSaveArtwork_whenRepositoryEmptyAndThrowsException_revertsState() = runTest {
    viewModel.setArtwork(sampleArtwork.uid)
    fakeRepository.shouldThrowError = true

    viewModel.toggleSaveArtwork()

    assertFalse(viewModel.uiState.value.isSaved)
  }

  /** Fake implementation of [SavedArtworksRepository] for testing [ArtworkScreenViewModel]. */
  class FakeArtworkRepository : SavedArtworksRepository {
    private val savedArtworks = mutableListOf<Artwork>()
    var shouldThrowError = false

    override suspend fun isArtworkSaved(uid: ArtworkId): Boolean {
      if (shouldThrowError) throw RuntimeException("Simulated error")
      return savedArtworks.any { it.uid == uid }
    }

    override suspend fun saveArtwork(artwork: Artwork) {
      if (shouldThrowError) throw RuntimeException("Simulated database failure")
      savedArtworks.removeAll { it.uid == artwork.uid }
      savedArtworks.add(artwork)
    }

    override suspend fun removeArtworkFromSaved(uid: ArtworkId) {
      if (shouldThrowError) throw RuntimeException("Simulated database failure")
      savedArtworks.removeAll { it.uid == uid }
    }

    override suspend fun getSavedArtworks(): List<Artwork> = savedArtworks.toList()
  }

  private class FakeWikiDataRepository(var artwork: Artwork) : WikiDataRepository {
    var shouldThrow = false

    override suspend fun getArtworksNearLocation(
        searchArea: SearchArea,
        typeValues: List<ArtworkType>,
        limit: Int,
    ): List<Artwork> = emptyList()

    @Deprecated("Only required by the repository interface.")
    override suspend fun getPopularArtists(): List<Artist> = emptyList()

    override suspend fun getArtwork(uid: ArtworkId): Artwork {
      if (shouldThrow) throw RuntimeException("Simulated loading failure")
      return artwork
    }

    override suspend fun searchArtworks(
        artworkTitle: String,
        typeValues: List<ArtworkType>,
        limit: Int,
    ): List<Artwork> = emptyList()
  }
}
