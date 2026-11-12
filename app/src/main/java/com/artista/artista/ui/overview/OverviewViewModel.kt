// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.overview

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkFilter
import com.artista.artista.model.artwork.ArtworkId
import com.artista.artista.model.artwork.SavedArtworksRepository
import com.artista.artista.model.artwork.SearchArea
import com.artista.artista.model.artwork.WikiDataRepository
import com.artista.artista.model.artwork.applyFilter
import com.artista.artista.model.artwork.artistOptions
import com.artista.artista.model.artwork.locationOptions
import com.artista.artista.model.artwork.museumOptions
import com.artista.artista.ui.error.launchCatching
import com.artista.artista.ui.error.toMessageRes
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Immutable state displayed by the Overview screen.
 *
 * @property artworks artwork entries currently available to display
 * @property savedArtworkIds identifiers of artworks saved by the user
 * @property activeFilter criteria currently applied to the artwork entries
 * @property artistOptions distinct artists from the unfiltered list
 * @property museumOptions distinct museums from the unfiltered list
 * @property locationOptions distinct location names from the unfiltered list
 * @property errorMessageRes localized error resource, or null when there is no error
 * @property saveErrorMessageRes localized save-operation error resource, or null when there is no
 *   error
 * @author patrickmcdan
 */
data class OverviewUiState(
    val artworks: List<Artwork> = emptyList(),
    val savedArtworkIds: Set<ArtworkId> = emptySet(),
    val activeFilter: ArtworkFilter = ArtworkFilter(),
    val artistOptions: List<String> = emptyList(),
    val museumOptions: List<String> = emptyList(),
    val locationOptions: List<String> = emptyList(),
    @param:StringRes val errorMessageRes: Int? = null,
    @param:StringRes val saveErrorMessageRes: Int? = null,
)

/**
 * Provides the artwork entries displayed by the Overview screen.
 *
 * The repository is injected so data access remains outside the UI layer.
 *
 * @param artworkRepository repository used to load the artwork entries
 * @param savedArtworksRepository repository used to load and update saved artworks
 * @author patrickmcdan, hixeum
 */
class OverviewViewModel(
    private val artworkRepository: WikiDataRepository,
    private val savedArtworksRepository: SavedArtworksRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(OverviewUiState())
  private var allArtworks: List<Artwork> = emptyList()

  val uiState: StateFlow<OverviewUiState> = _uiState.asStateFlow()

  private val pendingSavedArtworkIds = mutableSetOf<ArtworkId>()

  init {
    refreshUiState()
    refreshSavedArtworkIds()
  }

  /**
   * Refreshes the Overview state by loading artworks from the repository.
   *
   * @author patrickmcdan
   */
  fun refreshUiState() {
    viewModelScope.launchCatching(
        tag = TAG,
        onError = { error -> _uiState.update { it.copy(errorMessageRes = error.toMessageRes()) } },
    ) {
      val lausanneCenter =
          SearchArea(
              latitude = 46.5197,
              longitude = 6.6323,
              radiusKm = 5.0,
          )
      allArtworks = artworkRepository.getArtworksNearLocation(lausanneCenter)
      _uiState.update { current ->
        val activeFilter = current.activeFilter
        current.copy(
            artworks = allArtworks.applyFilter(activeFilter),
            artistOptions = artistOptions(allArtworks),
            museumOptions = museumOptions(allArtworks),
            locationOptions = locationOptions(allArtworks),
            errorMessageRes = null,
        )
      }
    }
  }

  /**
   * Reloads saved artwork identifiers from the repository.
   *
   * Overview can remain on the navigation back stack while another screen changes saved artworks.
   * Calling this when Overview resumes keeps its bookmark state synchronized with those changes.
   */
  fun refreshSavedArtworkIds() {
    viewModelScope.launchCatching(
        tag = TAG,
        onError = { error ->
          _uiState.update { it.copy(saveErrorMessageRes = error.toMessageRes()) }
        },
    ) {
      val savedArtworkIds = savedArtworksRepository.getSavedArtworks().map { it.uid }.toSet()
      _uiState.update { it.copy(savedArtworkIds = savedArtworkIds) }
    }
  }

  /**
   * Saves or removes an artwork and updates the bookmark state shown in Overview.
   *
   * @param artwork artwork whose saved status should be toggled
   */
  fun toggleSavedArtwork(artwork: Artwork) {
    // Ignore repeated taps until the previous repository write for this artwork completes.
    if (!pendingSavedArtworkIds.add(artwork.uid)) return

    val wasSaved = artwork.uid in _uiState.value.savedArtworkIds

    fun updateSavedState(isSaved: Boolean, errorMessageRes: Int? = null) {
      _uiState.update { state ->
        state.copy(
            savedArtworkIds =
                if (isSaved) state.savedArtworkIds + artwork.uid
                else state.savedArtworkIds - artwork.uid,
            saveErrorMessageRes = errorMessageRes,
        )
      }
    }

    updateSavedState(isSaved = !wasSaved)

    val toggleJob: Job =
        viewModelScope.launchCatching(
            tag = TAG,
            onError = { error ->
              updateSavedState(isSaved = wasSaved, errorMessageRes = error.toMessageRes())
            },
        ) {
          if (wasSaved) {
            savedArtworksRepository.removeArtworkFromSaved(artwork.uid)
          } else {
            savedArtworksRepository.saveArtwork(artwork)
          }
        }
    toggleJob.invokeOnCompletion { pendingSavedArtworkIds.remove(artwork.uid) }
  }

  /**
   * Clears the current Overview error message.
   *
   * @author patrickmcdan
   */
  fun clearErrorMessage() {
    _uiState.value = _uiState.value.copy(errorMessageRes = null)
  }

  /** Clears the current saved-artwork error message. */
  fun clearSaveErrorMessage() {
    _uiState.update { it.copy(saveErrorMessageRes = null) }
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
    private const val TAG = "OverviewViewModel"

    /**
     * Creates a factory for an overview ViewModel with its repository dependency.
     *
     * @param repository repository used to load nearby artworks
     * @param savedArtworksRepository repository used to access saved artworks
     * @return factory that creates [OverviewViewModel] instances
     * @author patrickmcdan
     */
    fun provideFactory(
        repository: WikiDataRepository,
        savedArtworksRepository: SavedArtworksRepository,
    ): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(OverviewViewModel::class.java)) {
              return OverviewViewModel(repository, savedArtworksRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
          }
        }
  }
}
