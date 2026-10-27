package com.artista.artista.ui.preferences

import com.artista.artista.model.artwork.ArtworkRepositoryLocal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
/**
 * Tests the state exposed by [ArtistPreferencesViewModel].
 *
 * @author Timz3rr
 */
class ArtistPreferencesViewModelTest {

  private lateinit var viewModel: ArtistPreferencesViewModel

  @Before
  fun setUp() {
    val repository = ArtworkRepositoryLocal()
    viewModel = ArtistPreferencesViewModel(repository)
  }

  /** Verifies that the popular artists of the mockup are exposed, in their display order. */
  @Test
  fun initialState_exposesPopularArtists() {
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
    viewModel.toggleArtist(PICASSO)

    assertEquals(setOf(PICASSO), viewModel.uiState.value.selectedArtists)
  }

  /** Verifies that toggling an artist twice brings it back to its initial unselected state. */
  @Test
  fun toggleArtist_twice_leavesArtistUnselected() {
    viewModel.toggleArtist(PICASSO)
    viewModel.toggleArtist(PICASSO)

    assertTrue(viewModel.uiState.value.selectedArtists.isEmpty())
  }

  /** Verifies that toggling an artist leaves the selection of the other artists unchanged. */
  @Test
  fun toggleArtist_keepsOtherSelections() {
    viewModel.toggleArtist(PICASSO)
    viewModel.toggleArtist(MONET)

    assertEquals(setOf(PICASSO, MONET), viewModel.uiState.value.selectedArtists)

    viewModel.toggleArtist(PICASSO)

    assertEquals(setOf(MONET), viewModel.uiState.value.selectedArtists)
  }

  private companion object {
    const val PICASSO = "Pablo Picasso"
    const val VAN_GOGH = "Vincent van Gogh"
    const val RENOIR = "Pierre-Auguste Renoir"
    const val MONET = "Claude Monet"
    const val KLIMT = "Gustav Klimt"
    const val MATISSE = "Henri Matisse"
  }
}
