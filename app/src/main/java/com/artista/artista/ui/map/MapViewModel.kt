// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.map

import android.location.Location
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.artista.artista.R
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.SavedArtworksRepository
import com.artista.artista.model.artwork.SearchArea
import com.artista.artista.model.artwork.WikiDataRepository
import com.artista.artista.model.error.AppException
import com.artista.artista.model.map.UserLocation
import com.artista.artista.ui.error.launchCatching
import com.artista.artista.ui.error.toMessageRes
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Provides the current device location to the map ViewModel. */
fun interface LocationProvider {

  /**
   * Reads the current device location.
   *
   * @param onLocation Callback receiving the location, or null when unavailable.
   */
  fun getCurrentLocation(onLocation: (Location?) -> Unit)
}

/**
 * Stores the location state displayed by the map.
 *
 * @property userLocation Latest known user location, or null while location is unavailable.
 * @property errorMsg Resource ID for the error message, or null when no error exists.
 * @author Felix Burchardt
 */
data class MapViewUiState(
    val userLocation: UserLocation? = null,
    val savedArtworks: List<Artwork> = listOf(),
    val remoteArtworks: List<Artwork> = listOf(),
    @param:StringRes val errorMsg: Int? = null,
)

/**
 * Provides the user location to the map screen.
 *
 * @author Felix Burchardt
 */
class MapViewModel(
    private val wikiDataRepository: WikiDataRepository,
    private val savedArtworksRepository: SavedArtworksRepository,
    private val locationProvider: LocationProvider? = null,
) : ViewModel() {

  private val _uiState = MutableStateFlow(MapViewUiState())

  /** Exposes the latest map state for reactive UI rendering. */
  val uiStateFlow: StateFlow<MapViewUiState> = _uiState.asStateFlow()

  /** Returns the current map state for synchronous consumers such as tests. */
  val uiState: MapViewUiState
    get() = _uiState.value

  init {
    viewModelScope.launchCatching(
        tag = MapViewModel::class.java.simpleName,
        onError = ::setSavedArtworksError,
    ) {
      val savedArtworks = savedArtworksRepository.getSavedArtworks()
      _uiState.update { state ->
        state.copy(
            savedArtworks = savedArtworks,
        )
      }
    }
  }

  /**
   * Updates the location shown on the map.
   *
   * @param location Latest location reported by the device.
   * @author Felix Burchardt
   */
  fun updateUserLocation(location: UserLocation) {
    _uiState.update { it.copy(userLocation = location, errorMsg = null) }
    loadRemoteArtworks(location)
  }

  /**
   * Loads artworks located near the user's current position.
   *
   * @param location center of the geographic artwork search
   * @author Copilot
   */
  private fun loadRemoteArtworks(location: UserLocation) {
    viewModelScope.launchCatching(
        tag = MapViewModel::class.java.simpleName,
        onError = ::setRepositoryError,
    ) {
      val artworks =
          wikiDataRepository.getArtworksNearLocation(
              searchArea =
                  SearchArea(
                      latitude = location.latitude,
                      longitude = location.longitude,
                      radiusKm = ARTWORK_SEARCH_RADIUS_KM,
                  )
          )
      _uiState.update { it.copy(remoteArtworks = artworks) }
    }
  }

  private fun setRepositoryError(error: AppException) {
    _uiState.update { it.copy(errorMsg = error.toMessageRes()) }
  }

  private fun setSavedArtworksError(error: AppException) {
    if (error is AppException.Unauthorized) {
      _uiState.update { it.copy(savedArtworks = emptyList()) }
    } else {
      setRepositoryError(error)
    }
  }

  /**
   * Records an error encountered while reading the device location.
   *
   * @param messageResId Resource ID for the error message.
   * @author Felix Burchardt
   */
  fun setLocationError(@StringRes messageResId: Int) {
    _uiState.update { it.copy(errorMsg = messageResId) }
  }

  /**
   * Records that location permission is unavailable.
   *
   * @author @Felix Burchardt
   */
  fun setLocationPermissionError() {
    setLocationError(R.string.map_location_permission_error)
  }

  /**
   * Acquires the current device location and updates the map state.
   *
   * @param locationClient Provider used to access the device location.
   * @author @Felix Burchardt
   */
  fun acquireLocation(
      locationClient: FusedLocationProviderClient,
  ) {
    if (locationProvider != null) {
      locationProvider.getCurrentLocation { updateFromLocation(it) }
      return
    }
    acquireLocationFromGoogle(locationClient)
  }

  private fun updateFromLocation(location: Location?) {
    if (location != null) updateUserLocation(UserLocation(location.latitude, location.longitude))
    else setLocationError(R.string.map_location_unavailable_error)
  }

  private fun acquireLocationFromGoogle(locationClient: FusedLocationProviderClient) {
    try {
      locationClient
          .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
          .addOnSuccessListener(DIRECT_EXECUTOR) { updateFromLocation(it) }
          .addOnFailureListener(DIRECT_EXECUTOR) {
            setLocationError(R.string.map_location_acquisition_error)
          }
    } catch (_: SecurityException) {
      setLocationPermissionError()
    }
  }

  companion object {
    private const val ARTWORK_SEARCH_RADIUS_KM = 50.0

    /**
     * Creates a factory for a map ViewModel with its repository dependencies.
     *
     * @param wikiDataRepository repository used for artwork data
     * @param savedArtworksRepository repository used for saved artworks
     * @return factory that creates [MapViewModel] instances
     * @author Felix Burchardt
     */
    fun provideFactory(
        wikiDataRepository: WikiDataRepository,
        savedArtworksRepository: SavedArtworksRepository,
    ): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MapViewModel::class.java)) {
              return MapViewModel(wikiDataRepository, savedArtworksRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
          }
        }

    val DIRECT_EXECUTOR = Executor { command -> command.run() }
  }
}
