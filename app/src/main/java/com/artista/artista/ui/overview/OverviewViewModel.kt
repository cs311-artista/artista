// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.overview

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.artista.artista.R
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.mockArtworks
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Immutable state displayed by the Overview screen.
 *
 * @property artworks artwork entries currently available to display
 * @property errorMessageRes localized error resource, or null when there is no error
 * @author patrickmcdan
 */
data class OverviewUiState(
    val artworks: List<Artwork> = emptyList(),
    @param:StringRes val errorMessageRes: Int? = null,
)

/**
 * Provides the artwork entries displayed by the Overview screen.
 *
 * The provider is injected so the placeholder source can be replaced by an artwork repository
 * without changing the screen's state contract.
 *
 * @param artworkProvider source used to load the artwork entries
 * @param ioDispatcher dispatcher used for artwork loading
 * @author patrickmcdan
 */
class OverviewViewModel(
    private val artworkProvider: suspend () -> List<Artwork> = { mockArtworks },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

  private val _uiState = MutableStateFlow(OverviewUiState())

  val uiState: StateFlow<OverviewUiState> = _uiState.asStateFlow()

  init {
    refreshUiState()
  }

  /**
   * Refreshes the Overview state by loading artworks from the provider.
   *
   * @author patrickmcdan
   */
  fun refreshUiState() {
    viewModelScope.launch(ioDispatcher) {
      try {
        _uiState.value = OverviewUiState(artworks = artworkProvider())
      } catch (_: Exception) {
        // Error classification is not defined yet, so keep this failure handling generic.
        _uiState.value = _uiState.value.copy(errorMessageRes = R.string.overview_load_error)
      }
    }
  }

  /**
   * Clears the current Overview error message.
   *
   * @author patrickmcdan
   */
  fun clearErrorMessage() {
    _uiState.value = _uiState.value.copy(errorMessageRes = null)
  }
}
