// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.map

import android.location.Location
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkId
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

/**
 * Provides a controllable location source to the map ViewModel.
 *
 * Production callers should leave this provider null so the ViewModel uses the fused-location
 * client. Tests can inject a provider to avoid depending on device location services.
 *
 * @author 5kyPhy
 */
fun interface LocationProvider {

  /**
   * Reads the current device location.
   *
   * @param onLocation callback receiving the location, or null when unavailable
   */
  fun getCurrentLocation(onLocation: (Location?) -> Unit)
}

/**
 * Stores the location state displayed by the map.
 *
 * @property userLocation Latest known user location, or null while location is unavailable.
 * @property savedArtworks List of artworks saved by the user.
 * @property remoteArtworks List of artworks fetched from remote sources.
 * @property hasLocationPermission Indicates whether the app has location permissions.
 * @property errorMsg Resource ID for the error message, or null when no error exists.
 * @author Felix Burchardt
 */
data class MapViewUiState(
    val userLocation: UserLocation? = null,
    val savedArtworks: List<Artwork> = listOf(),
    val remoteArtworks: List<Artwork> = listOf(),
    val hasLocationPermission: Boolean = false,
    @param:StringRes val errorMsg: Int? = null,
)

/**
 * Provides the user location to the map screen.
 *
 * @param wikiDataRepository repository used to load nearby artworks
 * @param savedArtworksRepository repository used to load saved artworks
 * @param locationProvider optional test seam; when null, the production fused-location client is
 *   used
 * @param artworkId optional artwork identifier associated with the map
 * @author 5kyPhy
 */
class MapViewModel(
    private val wikiDataRepository: WikiDataRepository,
    private val savedArtworksRepository: SavedArtworksRepository,
    // Null selects the production fused-location client; tests inject a deterministic provider.
    private val locationProvider: LocationProvider? = null,
    private val artworkId: ArtworkId? = null,
) : ViewModel() {

  private val _uiState = MutableStateFlow(MapViewUiState())

  /** Exposes the latest map state for reactive UI rendering. */
  val uiStateFlow: StateFlow<MapViewUiState> = _uiState.asStateFlow()

  /** Returns the current map state for synchronous consumers such as tests. */
  val uiState: MapViewUiState
    get() = _uiState.value

  init {
    viewModelScope.launchCatching(
        tag = TAG,
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
   * Handles permission result callback from the UI layer. Call this when activity result returns to
   * update ViewModel state.
   */
  fun handleLocationPermissionResult(granted: Boolean) {
    viewModelScope.launchCatching(
        tag = TAG,
        onError = ::setLocationPermissionError,
    ) {
      _uiState.update { it.copy(hasLocationPermission = granted) }
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
   * @author 5kyPhy
   */
  private fun loadRemoteArtworks(location: UserLocation) {
    viewModelScope.launchCatching(
        tag = TAG,
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
   * @param error location failure classified by the AppException framework.
   * @author 5kyPhy
   */
  fun setLocationError(error: AppException) {
    _uiState.update { it.copy(errorMsg = error.toMessageRes()) }
  }

  /**
   * Records that location permission is unavailable.
   *
   * @author 5kyPhy
   */
  fun setLocationPermissionError(error: AppException) {
    _uiState.update { it.copy(errorMsg = error.toMessageRes()) }
  }

  /**
   * Acquires the current device location and updates the map state.
   *
   * @param locationClient Provider used to access the device location.
   * @author 5kyPhy
   */
  fun acquireLocation(
      locationClient: FusedLocationProviderClient,
  ) {
    locationProvider?.let { provider ->
      provider.getCurrentLocation { location ->
        if (location != null) {
          updateUserLocation(UserLocation(location.latitude, location.longitude))
        } else {
          setLocationError(AppException.UserLocationError("Location was unavailable."))
        }
      }
      return
    }

    try {
      locationClient
          .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
          .addOnSuccessListener(DIRECT_EXECUTOR) { location ->
            if (location != null) {
              updateUserLocation(UserLocation(location.latitude, location.longitude))
            } else {
              setLocationError(AppException.UserLocationError("Location was unavailable."))
            }
          }
          .addOnFailureListener(DIRECT_EXECUTOR) { e ->
            setLocationError(AppException.UserLocationError(cause = e))
          }
    } catch (_: SecurityException) {
      setLocationPermissionError(AppException.UserLocationError())
    }
  }

  companion object {
    private const val ARTWORK_SEARCH_RADIUS_KM = 50.0
    private var TAG = MapViewModel::class.java.simpleName

    /**
     * Creates a factory for a map ViewModel with its repository dependencies.
     *
     * @param wikiDataRepository repository used for artwork data
     * @param savedArtworksRepository repository used for saved artworks
     * @return factory that creates [MapViewModel] instances
     * @author 5kyPhy
     */
    fun provideFactory(
        wikiDataRepository: WikiDataRepository,
        savedArtworksRepository: SavedArtworksRepository,
        artworkId: ArtworkId? = null,
    ): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MapViewModel::class.java)) {
              return MapViewModel(
                  wikiDataRepository,
                  savedArtworksRepository,
                  locationProvider = null,
                  artworkId = artworkId,
              )
                  as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
          }
        }

    val DIRECT_EXECUTOR = Executor { command -> command.run() }
  }
}
