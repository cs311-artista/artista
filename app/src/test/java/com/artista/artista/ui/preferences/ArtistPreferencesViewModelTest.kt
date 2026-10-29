package com.artista.artista.ui.preferences

import com.artista.artista.MainDispatcherRule
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artwork.ArtworkRepositoryLocal
import com.artista.artista.model.artwork.WikiDataRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
/**
 * Tests the state exposed by [ArtistPreferencesViewModel].
 *
 * @author Timz3rr, patrickmcdan
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArtistPreferencesViewModelTest {

  private lateinit var viewModel: ArtistPreferencesViewModel

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Before
  fun setUp() {
    val repository = ArtworkRepositoryLocal()
    viewModel = ArtistPreferencesViewModel(repository)
  }

  /** Verifies that the popular artists of the mockup are exposed, in their display order. */
  @Test
  fun initialState_exposesPopularArtists() = runTest {
    advanceUntilIdle()

    assertEquals(
        listOf(PICASSO, VAN_GOGH, RENOIR, MONET, KLIMT, MATISSE),
        viewModel.uiState.value.popularArtists,
    )
  }

  /** Verifies that no artist is selected before the user interacts with the screen. */
  @Test
  fun initialState_hasNoSelectedArtist() {
    assertTrue(viewModel.uiState.value.selectedArtists.isEmpty())
  }

  /** Verifies that toggling an unselected artist selects it. */
  @Test
  fun toggleArtist_selectsUnselectedArtist() {
    viewModel.toggleArtist(PICASSO.uid)

    assertEquals(setOf(PICASSO.uid), viewModel.uiState.value.selectedArtists)
  }

  /** Verifies that toggling an artist twice brings it back to its initial unselected state. */
  @Test
  fun toggleArtist_twice_leavesArtistUnselected() {
    viewModel.toggleArtist(PICASSO.uid)
    viewModel.toggleArtist(PICASSO.uid)

    assertTrue(viewModel.uiState.value.selectedArtists.isEmpty())
  }

  /** Verifies that toggling an artist leaves the selection of the other artists unchanged. */
  @Test
  fun toggleArtist_keepsOtherSelections() {
    viewModel.toggleArtist(PICASSO.uid)
    viewModel.toggleArtist(MONET.uid)

    assertEquals(setOf(PICASSO.uid, MONET.uid), viewModel.uiState.value.selectedArtists)

    viewModel.toggleArtist(PICASSO.uid)

    assertEquals(setOf(MONET.uid), viewModel.uiState.value.selectedArtists)
  }

    assertEquals(setOf(MONET), viewModel.uiState.value.selectedArtists)
  }

  /** Verifies that a failing popular artists load does not crash and leaves the list empty. */
  @Test
  fun init_whenLoadingPopularArtistsFails_exposesNoArtists() = runTest {
    val repository =
        object : WikiDataRepository by ArtworkRepositoryLocal() {
          override suspend fun getPopularArtists(): List<Artist> = error("Popular artists failure")
        }
    val viewModel = ArtistPreferencesViewModel(repository)
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.popularArtists.isEmpty())
  }

  private companion object {
    val PICASSO = Artist("mock-artist-001", "Pablo Picasso")
    val VAN_GOGH = Artist("mock-artist-002", "Vincent van Gogh")
    val RENOIR = Artist("mock-artist-003", "Pierre-Auguste Renoir")
    val MONET = Artist("mock-artist-004", "Claude Monet")
    val KLIMT = Artist("mock-artist-005", "Gustav Klimt")
    val MATISSE = Artist("mock-artist-006", "Henri Matisse")
  }
}
