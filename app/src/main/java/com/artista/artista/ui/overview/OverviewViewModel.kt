// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.overview

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.artista.artista.R
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.SearchArea
import com.artista.artista.model.artwork.ArtworkFilter
import com.artista.artista.model.artwork.SearchArea
import com.artista.artista.model.artwork.WikiDataRepository
import com.artista.artista.model.artwork.applyFilter
import com.artista.artista.model.artwork.artistOptions
import com.artista.artista.model.artwork.locationOptions
import com.artista.artista.model.artwork.museumOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Immutable state displayed by the Overview screen.
 *
 * @property artworks artwork entries currently available to display
 * @property activeFilter criteria currently applied to the artwork entries
 * @property artistOptions distinct artists from the unfiltered list
 * @property museumOptions distinct museums from the unfiltered list
 * @property locationOptions distinct location names from the unfiltered list
 * @property errorMessageRes localized error resource, or null when there is no error
 * @author patrickmcdan
 */
data class OverviewUiState(
    val artworks: List<Artwork> = emptyList(),
    val activeFilter: ArtworkFilter = ArtworkFilter(),
    val artistOptions: List<String> = emptyList(),
    val museumOptions: List<String> = emptyList(),
    val locationOptions: List<String> = emptyList(),
    @param:StringRes val errorMessageRes: Int? = null,
)

/**
 * Provides the artwork entries displayed by the Overview screen.
 *
 * The repository is injected so data access remains outside the UI layer.
 *
 * @param artworkRepository repository used to load the artwork entries
 * @author patrickmcdan
 */
class OverviewViewModel(
    private val artworkRepository: WikiDataRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(OverviewUiState())
  private var allArtworks: List<Artwork> = emptyList()

  val uiState: StateFlow<OverviewUiState> = _uiState.asStateFlow()

  init {
    refreshUiState()
  }

  /**
   * Refreshes the Overview state by loading artworks from the repository.
   *
   * @author patrickmcdan
   */
  fun refreshUiState() {
    viewModelScope.launch {
      try {
        val lausanneCenter =
          SearchArea(
            latitude = 46.5197,
            longitude = 6.6323,
            radiusKm = 5.0,
          )
        allArtworks = artworkRepository.getArtworksNearLocation(lausanneCenter)
        val activeFilter = _uiState.value.activeFilter
        _uiState.value =
            _uiState.value.copy(
                artworks = allArtworks.applyFilter(activeFilter),
                artistOptions = artistOptions(allArtworks),
                museumOptions = museumOptions(allArtworks),
                locationOptions = locationOptions(allArtworks),
                errorMessageRes = null,
            )
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

  /**
   * Applies criteria to the artworks currently loaded by the Overview screen.
   *
   * @param filter criteria to apply
   * @author patrickmcdan
   */
  fun applyFilter(filter: ArtworkFilter) {
    _uiState.value =
        _uiState.value.copy(
            activeFilter = filter,
            artworks = allArtworks.applyFilter(filter),
        )
  }

  companion object {
    /**
     * Creates a factory for an overview ViewModel with its repository dependency.
     *
     * @param repository repository used to load nearby artworks
     * @return factory that creates [OverviewViewModel] instances
     * @author patrickmcdan
     */
    fun provideFactory(repository: WikiDataRepository): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(OverviewViewModel::class.java)) {
              return OverviewViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
          }
        }
  }
}
