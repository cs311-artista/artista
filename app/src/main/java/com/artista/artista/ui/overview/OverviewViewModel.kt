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
import com.artista.artista.model.error.AppException
import com.artista.artista.model.location.LocationProvider
import com.artista.artista.model.map.Coordinate
import com.artista.artista.model.map.UserLocation
import com.artista.artista.model.walking.distanceMeters
import com.artista.artista.ui.error.launchCatching
import com.artista.artista.ui.error.toMessageRes
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * How the Overview screen lays out nearby artworks.
 *
 * @author hixeum
 */
enum class OverviewDisplayMode {
  /** Two-column grid of image tiles. */
  GRID,

  /** Single column of detailed cards. */
  LIST,
}

/**
 * Immutable state displayed by the Overview screen.
 *
 * @property artworks artwork entries currently available to display
 * @property savedArtworkIds identifiers of artworks saved by the user
 * @property closeSavedArtworks saved artworks within
 *   [OverviewViewModel.CLOSE_SAVED_ARTWORK_RADIUS_METERS] of the user, closest first and matching
 *   [activeFilter]
 * @property displayMode whether nearby artworks are shown as a grid or as a list
 * @property activeFilter criteria currently applied to the artwork entries
 * @property artistOptions distinct artists from the unfiltered list
 * @property museumOptions distinct museums from the unfiltered list
 * @property locationOptions distinct location names from the unfiltered list
 * @property hasLocationPermission whether the user granted the location permissions
 * @property userLocation last known location of the user, or null before it is acquired
 * @property errorMessageRes localized error resource, or null when there is no error
 * @property saveErrorMessageRes localized save-operation error resource, or null when there is no
 *   error
 * @author patrickmcdan, hixeum
 */
data class OverviewUiState(
    val artworks: List<Artwork> = emptyList(),
    val savedArtworkIds: Set<ArtworkId> = emptySet(),
    val closeSavedArtworks: List<Artwork> = emptyList(),
    val displayMode: OverviewDisplayMode = OverviewDisplayMode.GRID,
    val activeFilter: ArtworkFilter = ArtworkFilter(),
    val artistOptions: List<String> = emptyList(),
    val museumOptions: List<String> = emptyList(),
    val locationOptions: List<String> = emptyList(),
    val hasLocationPermission: Boolean = false,
    val userLocation: UserLocation? = null,
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
    private val locationProvider: LocationProvider,
) : ViewModel() {

  private val _uiState = MutableStateFlow(OverviewUiState())
  private var allArtworks: List<Artwork> = emptyList()
  private var savedArtworks: List<Artwork> = emptyList()

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
      val userLocation = locationProvider.getUserLocation()
      val searchArea = SearchArea(userLocation.latitude, userLocation.longitude, 50.0)

      allArtworks = artworkRepository.getArtworksNearLocation(searchArea, limit = 100)
      _uiState.update { current ->
        val activeFilter = current.activeFilter
        current.copy(
            artworks = allArtworks.applyFilter(activeFilter),
            userLocation = userLocation,
            closeSavedArtworks = closeSavedArtworks(userLocation, activeFilter),
            artistOptions = artistOptions(allArtworks),
            museumOptions = museumOptions(allArtworks),
            locationOptions = locationOptions(allArtworks),
            errorMessageRes = null,
        )
      }
    }
  }

  /**
   * Reloads saved artworks from the repository, updating the bookmark state and the saved artworks
   * close to the user.
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
      savedArtworks = savedArtworksRepository.getSavedArtworks()
      _uiState.update { state ->
        state.copy(
            savedArtworkIds = savedArtworks.map { it.uid }.toSet(),
            closeSavedArtworks = closeSavedArtworks(state.userLocation, state.activeFilter),
        )
      }
    }
  }

  /**
   * Saves or removes an artwork and updates the bookmark state shown in Overview, including the
   * saved artworks close to the user.
   *
   * The update is optimistic and is rolled back if the repository write fails.
   *
   * @param artwork artwork whose saved status should be toggled
   */
  fun toggleSavedArtwork(artwork: Artwork) {
    // Ignore repeated taps until the previous repository write for this artwork completes.
    if (!pendingSavedArtworkIds.add(artwork.uid)) return

    val wasSaved = artwork.uid in _uiState.value.savedArtworkIds

    fun updateSavedState(isSaved: Boolean, errorMessageRes: Int? = null) {
      // Kept in step with the bookmark so that the close saved artworks row reacts to the tap
      // immediately instead of waiting for the next refresh from the repository.
      savedArtworks =
          savedArtworks.filterNot { it.uid == artwork.uid } +
              if (isSaved) listOf(artwork) else emptyList()
      _uiState.update { state ->
        state.copy(
            savedArtworkIds =
                if (isSaved) state.savedArtworkIds + artwork.uid
                else state.savedArtworkIds - artwork.uid,
            closeSavedArtworks = closeSavedArtworks(state.userLocation, state.activeFilter),
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
   * Sets an error message to be displayed in the Overview screen.
   *
   * @param error the [AppException] that contains the error message resource
   * @author 5kyPhy
   */
  fun setErrorMessage(error: AppException) {
    _uiState.update { it.copy(errorMessageRes = error.toMessageRes()) }
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
    _uiState.update { state ->
      state.copy(
          activeFilter = filter,
          artworks = allArtworks.applyFilter(filter),
          closeSavedArtworks = closeSavedArtworks(state.userLocation, filter),
      )
    }
  }

  /**
   * Removes every filter criterion so all loaded artworks are displayed again.
   *
   * @author hixeum
   */
  fun clearFilter() = applyFilter(ArtworkFilter())

  /**
   * Switches nearby artworks between the grid and the list layouts.
   *
   * @author hixeum
   */
  fun toggleDisplayMode() {
    _uiState.update { state ->
      state.copy(
          displayMode =
              when (state.displayMode) {
                OverviewDisplayMode.GRID -> OverviewDisplayMode.LIST
                OverviewDisplayMode.LIST -> OverviewDisplayMode.GRID
              }
      )
    }
  }

  /**
   * Selects the saved artworks close enough to [userLocation] to be highlighted, closest first.
   *
   * @param userLocation location of the user, or null when it is not known yet
   * @param filter criteria the highlighted artworks must also match
   * @return the matching saved artworks, or an empty list when [userLocation] is unknown
   * @author hixeum
   */
  private fun closeSavedArtworks(
      userLocation: UserLocation?,
      filter: ArtworkFilter,
  ): List<Artwork> {
    if (userLocation == null) return emptyList()
    val user = Coordinate(userLocation.latitude, userLocation.longitude)
    return savedArtworks
        .mapNotNull { artwork ->
          val location = artwork.location ?: return@mapNotNull null
          val distance = distanceMeters(user, Coordinate(location.latitude, location.longitude))
          if (distance <= CLOSE_SAVED_ARTWORK_RADIUS_METERS) artwork to distance else null
        }
        .sortedBy { (_, distance) -> distance }
        .map { (artwork, _) -> artwork }
        .applyFilter(filter)
  }

  /**
   * Handles permission result callback from the UI layer. Call this when activity result returns to
   * update ViewModel state.
   *
   * @param granted whether the location permissions were granted by the user or not.
   * @author 5kyPhy
   */
  fun handleLocationPermissionResult(granted: Boolean) {
    viewModelScope.launchCatching(
        tag = TAG,
        onError = { error ->
          _uiState.update { it.copy(saveErrorMessageRes = error.toMessageRes()) }
        },
    ) {
      _uiState.update { it.copy(hasLocationPermission = granted) }
    }
  }

  companion object {
    private const val TAG = "OverviewViewModel"

    /** Maximum distance, in meters, for a saved artwork to count as close to the user. */
    const val CLOSE_SAVED_ARTWORK_RADIUS_METERS = 30_000.0

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
        locationClient: LocationProvider,
    ): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(OverviewViewModel::class.java)) {
              return OverviewViewModel(repository, savedArtworksRepository, locationClient) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
          }
        }
  }
}
