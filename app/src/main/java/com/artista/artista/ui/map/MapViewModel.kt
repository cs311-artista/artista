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
import com.artista.artista.model.map.Coordinate
import com.artista.artista.model.map.UserLocation
import com.artista.artista.model.walking.WalkingRoute
import com.artista.artista.model.walking.WalkingRouteRepository
import com.artista.artista.ui.error.launchCatching
import com.artista.artista.ui.error.toMessageRes
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.concurrent.Executor
import kotlinx.coroutines.Job
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
 * Describes the outcome of a walking route request.
 *
 * @author patrickmcdan
 */
sealed interface RouteUiState {
  /** No route request is currently active. */
  data object Idle : RouteUiState

  /** A route request is in progress. */
  data object Loading : RouteUiState

  /**
   * A walking route was loaded successfully.
   *
   * @property route route geometry and travel metrics
   * @property destination artwork reached by the route
   * @author patrickmcdan
   */
  data class Success(val route: WalkingRoute, val destination: Artwork) : RouteUiState

  /** No route could be found to the requested artwork. */
  data object NoRoute : RouteUiState

  /** The user's current location is unavailable. */
  data object LocationUnavailable : RouteUiState

  /**
   * A route request failed.
   *
   * @property kind category of failure exposed to the UI
   * @property messageRes resource ID for the error message to display
   * @author patrickmcdan
   */
  data class Error(val kind: RouteErrorKind, @StringRes val messageRes: Int) : RouteUiState
}

/**
 * Categories of route failures that have different UI and retry behavior.
 *
 * @author patrickmcdan
 */
enum class RouteErrorKind(
    /**
     * Whether retrying the route request is useful for this error.
     *
     * @author patrickmcdan
     */
    val canRetry: Boolean
) {
  /** The route service could not be reached. */
  NETWORK(canRetry = true),

  /** The route service's short-lived rate limit was reached. */
  TEMPORARY_RATE_LIMIT(canRetry = true),

  /** The route service's daily rate limit was reached. */
  DAILY_RATE_LIMIT(canRetry = false),

  /** An unexpected route failure occurred. */
  GENERIC(canRetry = true),
}

/**
 * Provides the user location to the map screen.
 *
 * @author Felix Burchardt, patrickmcdan
 */
class MapViewModel(
    private val wikiDataRepository: WikiDataRepository,
    private val savedArtworksRepository: SavedArtworksRepository,
    private val walkingRouteRepository: WalkingRouteRepository,
    private val locationProvider: LocationProvider? = null,
) : ViewModel() {

  private val _uiState = MutableStateFlow(MapViewUiState())
  private val _routeUiState = MutableStateFlow<RouteUiState>(RouteUiState.Idle)
  private var routeRequestJob: Job? = null
  private var lastRouteArtwork: Artwork? = null
  private var pendingRouteArtwork: Artwork? = null
  private var locationFailed = false
  private var artworkLoadJob: Job? = null

  /** Exposes the latest map state for reactive UI rendering. */
  val uiStateFlow: StateFlow<MapViewUiState> = _uiState.asStateFlow()

  /** Exposes the current walking route request state. */
  val routeUiStateFlow: StateFlow<RouteUiState> = _routeUiState.asStateFlow()

  /** Returns the current map state for synchronous consumers such as tests. */
  val uiState: MapViewUiState
    get() = _uiState.value

  /** Returns the current walking route state for synchronous consumers such as tests. */
  val routeUiState: RouteUiState
    get() = _routeUiState.value

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
   * @author Felix Burchardt, patrickmcdan
   */
  fun updateUserLocation(location: UserLocation) {
    _uiState.update { it.copy(userLocation = location, errorMsg = null) }
    loadRemoteArtworks(location)
    locationFailed = false
    pendingRouteArtwork?.let { artwork ->
      pendingRouteArtwork = null
      startRouteRequest(artwork, location)
    }
  }

  /**
   * Loads artworks located near the user's current position.
   *
   * @param location center of the geographic artwork search
   * @author Felix Burchardt
   */
  private fun loadRemoteArtworks(location: UserLocation) {
    artworkLoadJob?.cancel()
    artworkLoadJob =
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
    locationFailed = true
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
   * Completes a pending route request when location permission is denied.
   *
   * @author patrickmcdan
   */
  fun onLocationPermissionDenied() {
    setLocationPermissionError()
    finishPendingRouteWithoutLocation()
  }

  /**
   * Loads a walking route from the user's current location to an artwork.
   *
   * @param artwork destination artwork
   * @author patrickmcdan
   */
  fun loadRouteTo(artwork: Artwork) {
    if (isRoutingTo(artwork)) return
    requestRoute(artwork)
  }

  private fun isRoutingTo(artwork: Artwork): Boolean =
      when (val state = _routeUiState.value) {
        is RouteUiState.Success -> state.destination.uid == artwork.uid
        RouteUiState.Loading -> lastRouteArtwork?.uid == artwork.uid
        else -> false
      }

  private fun requestRoute(artwork: Artwork) {
    lastRouteArtwork = artwork
    routeRequestJob?.cancel()
    pendingRouteArtwork = null
    _routeUiState.value = RouteUiState.Loading
    val userLocation = uiState.userLocation
    when {
      userLocation != null -> startRouteRequest(artwork, userLocation)
      locationFailed -> _routeUiState.value = RouteUiState.LocationUnavailable
      else -> pendingRouteArtwork = artwork
    }
  }

  /**
   * Re-requests the last artwork passed to [requestRoute], when one exists.
   *
   * @author patrickmcdan
   */
  fun retryRoute() {
    lastRouteArtwork?.let(::requestRoute)
  }

  /**
   * Cancels any active walking route request and clears its state.
   *
   * @author patrickmcdan
   */
  fun clearRoute() {
    routeRequestJob?.cancel()
    routeRequestJob = null
    pendingRouteArtwork = null
    lastRouteArtwork = null
    _routeUiState.value = RouteUiState.Idle
  }

  private fun finishPendingRouteWithoutLocation() {
    if (pendingRouteArtwork != null) {
      pendingRouteArtwork = null
      _routeUiState.value = RouteUiState.LocationUnavailable
    }
  }

  private fun startRouteRequest(artwork: Artwork, userLocation: UserLocation) {
    routeRequestJob =
        viewModelScope.launchCatching(
            tag = MapViewModel::class.java.simpleName,
            onError = ::setRouteError,
        ) {
          // Safety net only: entry points should never pass an artwork without a
          // location. If it happens anyway, "no route" is accurate and there is
          // nothing for the user to retry.
          val destination =
              artwork.location
                  ?: run {
                    _routeUiState.value = RouteUiState.NoRoute
                    return@launchCatching
                  }
          val route =
              walkingRouteRepository.getWalkingRoute(
                  origin = Coordinate(userLocation.latitude, userLocation.longitude),
                  destination = Coordinate(destination.latitude, destination.longitude),
              )
          _routeUiState.value = RouteUiState.Success(route, artwork)
        }
  }

  private fun setRouteError(error: AppException) {
    _routeUiState.value =
        when (error) {
          is AppException.NotFound -> RouteUiState.NoRoute
          is AppException.Network ->
              RouteUiState.Error(RouteErrorKind.NETWORK, messageRes = error.toMessageRes())
          is AppException.RateLimited ->
              RouteUiState.Error(
                  if (error.isTransient) RouteErrorKind.TEMPORARY_RATE_LIMIT
                  else RouteErrorKind.DAILY_RATE_LIMIT,
                  messageRes = error.toMessageRes(),
              )
          else -> RouteUiState.Error(RouteErrorKind.GENERIC, messageRes = error.toMessageRes())
        }
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
    locationFailed = false
    if (locationProvider != null) {
      locationProvider.getCurrentLocation { updateFromLocation(it) }
      return
    }
    acquireLocationFromGoogle(locationClient)
  }

  private fun updateFromLocation(location: Location?) {
    if (location != null) updateUserLocation(UserLocation(location.latitude, location.longitude))
    else {
      setLocationError(R.string.map_location_unavailable_error)
      finishPendingRouteWithoutLocation()
    }
  }

  private fun acquireLocationFromGoogle(locationClient: FusedLocationProviderClient) {
    try {
      locationClient
          .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
          .addOnSuccessListener(DIRECT_EXECUTOR) { updateFromLocation(it) }
          .addOnFailureListener(DIRECT_EXECUTOR) {
            setLocationError(R.string.map_location_acquisition_error)
            finishPendingRouteWithoutLocation()
          }
    } catch (_: SecurityException) {
      setLocationPermissionError()
      finishPendingRouteWithoutLocation()
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
        walkingRouteRepository: WalkingRouteRepository,
    ): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MapViewModel::class.java)) {
              return MapViewModel(
                  wikiDataRepository,
                  savedArtworksRepository,
                  walkingRouteRepository,
              )
                  as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
          }
        }

    val DIRECT_EXECUTOR = Executor { command -> command.run() }
  }
}
