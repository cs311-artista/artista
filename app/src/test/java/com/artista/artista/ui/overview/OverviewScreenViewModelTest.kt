// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.overview

import com.artista.artista.MainDispatcherRule
import com.artista.artista.R
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.mockArtworks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Tests the state exposed by [OverviewViewModel].
 *
 * @author patrickmcdan
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OverviewScreenViewModelTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  /**
   * Verifies that the ViewModel loads artworks from its replaceable data source.
   *
   * @author patrickmcdan
   */
  @Test
  fun overviewViewModel_loadsArtworksFromDataSource() = runTest {
    val expectedArtworks =
        listOf(
            artwork(name = "The Starry Night", artistName = "Vincent van Gogh"),
            artwork(name = "Girl with a Pearl Earring", artistName = "Johannes Vermeer"),
        )
    val viewModel =
        OverviewViewModel(
            artworkProvider = { expectedArtworks },
            ioDispatcher = mainDispatcherRule.testDispatcher,
        )

    advanceUntilIdle()

    assertEquals(expectedArtworks, viewModel.uiState.value.artworks)
  }

  /**
   * Verifies that the default data source provides deterministic placeholder content.
   *
   * @author patrickmcdan
   */
  @Test
  fun overviewViewModel_defaultDataSourceProvidesPlaceholderArtworks() = runTest {
    val viewModel = OverviewViewModel(ioDispatcher = mainDispatcherRule.testDispatcher)

    advanceUntilIdle()

    assertEquals(mockArtworks, viewModel.uiState.value.artworks)
  }

  /**
   * Verifies that a provider failure exposes a load error and no stale artwork list.
   *
   * @author patrickmcdan
   */
  @Test
  fun overviewViewModel_whenLoadingFails_exposesLoadError() = runTest {
    val viewModel =
        OverviewViewModel(
            artworkProvider = { error("Artwork provider failure") },
            ioDispatcher = mainDispatcherRule.testDispatcher,
        )

    advanceUntilIdle()

    assertEquals(R.string.overview_load_error, viewModel.uiState.value.errorMessageRes)
    assertTrue(viewModel.uiState.value.artworks.isEmpty())
  }

  /**
   * Verifies that clearing the error removes it from the UI state.
   *
   * @author patrickmcdan
   */
  @Test
  fun clearErrorMessage_removesTheCurrentError() = runTest {
    val viewModel =
        OverviewViewModel(
            artworkProvider = { error("Artwork provider failure") },
            ioDispatcher = mainDispatcherRule.testDispatcher,
        )

    advanceUntilIdle()
    viewModel.clearErrorMessage()

    assertEquals(null, viewModel.uiState.value.errorMessageRes)
  }

  private fun artwork(name: String, artistName: String): Artwork =
      Artwork(
          uid = "$name-$artistName",
          name = name,
          artistName = artistName,
          description = null,
          conceptionDate = null,
          museum = null,
          location = null,
          dimension = null,
      )
}
