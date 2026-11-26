// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.map

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.SavedArtworksRepository
import com.artista.artista.model.artwork.SearchArea
import com.artista.artista.model.artwork.WikiDataRepository
import com.artista.artista.model.error.AppException
import com.artista.artista.model.location.LocationProvider
import com.artista.artista.model.map.Coordinate
import com.artista.artista.model.map.UserLocation
import com.artista.artista.model.walking.OFF_ROUTE_THRESHOLD_METERS
import com.artista.artista.model.walking.OFF_ROUTE_UPDATES_REQUIRED
import com.artista.artista.model.walking.RouteProgress
import com.artista.artista.model.walking.WalkingRoute
import com.artista.artista.model.walking.WalkingRouteRepository
import com.artista.artista.model.walking.advanceProgress
import com.artista.artista.model.walking.pathLengthMeters
import com.artista.artista.model.walking.trimPath
import com.artista.artista.ui.error.launchCatching
import com.artista.artista.ui.error.toMessageRes
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Minimum time between two route recalculation requests. */
const val RECALCULATE_COOLDOWN_MS = 15_000L

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
 * The part of the active walking route that is still ahead of the user.
 *
 * @property path remaining route geometry, starting at the user's projected position
 * @property remainingDistanceMeters length of [path]
 * @property remainingDurationSeconds walking time left, proportional to the remaining distance
 * @author patrickmcdan
 */
data class RouteProgressUiState(
    val path: List<Coordinate>,
    val remainingDistanceMeters: Double,
    val remainingDurationSeconds: Double,
)

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
 * State of the "Recalculate route" action for the active route.
 *
 * @property isAvailable whether the user is off-route, so the button is shown
 * @property isLoading whether a recalculation request is in progress
 * @property isCoolingDown whether the minimum delay since the last request has not elapsed yet
 * @property error failure of the last recalculation, shown next to the still-visible route
 * @author patrickmcdan
 */
data class RouteRecalculationUiState(
    val isAvailable: Boolean = false,
    val isLoading: Boolean = false,
    val isCoolingDown: Boolean = false,
    val error: RouteUiState.Error? = null,
) {
  /** Whether a new recalculation may be started now. */
  val canRequest: Boolean
    get() = isAvailable && !isLoading && !isCoolingDown
}

/**
 * Provides the user location to the map screen.
 *
 * @param wikiDataRepository repository used to load nearby artworks
 * @param savedArtworksRepository repository used to load saved artworks
 * @param locationProvider optional test seam; when null, the production fused-location client is
 *   used
 * @author Felix Burchardt, patrickmcdan
 */
class MapViewModel(
    private val wikiDataRepository: WikiDataRepository,
    private val savedArtworksRepository: SavedArtworksRepository,
    private val walkingRouteRepository: WalkingRouteRepository,
    private val locationProvider: LocationProvider,
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

  private val _routeProgressUiState = MutableStateFlow<RouteProgressUiState?>(null)
  private var routeProgress: RouteProgress? = null
  private var locationUpdatesJob: Job? = null

  private var offRouteUpdates = 0
  private var recalculationJob: Job? = null
  private var cooldownJob: Job? = null
  private val _recalculationUiState = MutableStateFlow(RouteRecalculationUiState())

  /** Exposes the remaining part of the active route, or null when no route is shown. */
  val routeProgressUiStateFlow: StateFlow<RouteProgressUiState?> =
      _routeProgressUiState.asStateFlow()

  /** State of the "Recalculate route" action. */
  val recalculationUiStateFlow: StateFlow<RouteRecalculationUiState> =
      _recalculationUiState.asStateFlow()

  /** Returns the remaining route for synchronous consumers such as tests. */
  val routeProgressUiState: RouteProgressUiState?
    get() = _routeProgressUiState.value

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
   * Trims the active route to the user's location, without calling the routing API again.
   *
   * @param location the user's latest location
   */
  private fun updateRouteProgress(location: UserLocation) {
    val route = (_routeUiState.value as? RouteUiState.Success)?.route ?: return
    val path = route.path
    val progress =
        advanceProgress(path, Coordinate(location.latitude, location.longitude), routeProgress)
            ?: return
    routeProgress = progress
    val trimmed = trimPath(path, progress)
    val remaining = pathLengthMeters(trimmed)
    // Scale against the polyline length so distance and duration stay consistent with each other.
    val total = pathLengthMeters(path)
    val ratio = if (total > 0.0) (remaining / total).coerceIn(0.0, 1.0) else 0.0
    _routeProgressUiState.value =
        RouteProgressUiState(
            trimmed,
            remaining,
            route.duration.toDouble(DurationUnit.SECONDS) * ratio,
        )
  }

  private fun resetRouteProgress() {
    routeProgress = null
    _routeProgressUiState.value = null
    resetOffRoute()
  }

  /**
   * Starts continuous location updates, forwarding each one to [applyLocation].
   *
   * Calling it while updates are already running does nothing, so repeated lifecycle events cannot
   * create several collectors.
   *
   * @author patrickmcdan
   */
  fun startLocationUpdates() {
    if (locationUpdatesJob?.isActive == true) return
    locationUpdatesJob =
        viewModelScope.launchCatching(tag = TAG, onError = ::setLocationError) {
          locationProvider.locationUpdates().collect { applyLocation(it) }
        }
  }

  /**
   * Stops continuous location updates.
   *
   * @author patrickmcdan
   */
  fun stopLocationUpdates() {
    locationUpdatesJob?.cancel()
    locationUpdatesJob = null
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
        onError = ::setLocationPermissionError,
    ) {
      _uiState.update { it.copy(hasLocationPermission = granted) }
    }
  }

  /**
   * Updates the location shown on the map also searching for nearby artworks.
   *
   * @param location Latest location reported by the device.
   * @author Felix Burchardt, patrickmcdan
   */
  fun updateUserLocation(location: UserLocation) {
    applyLocation(location)
    loadRemoteArtworks(location)
  }

  /**
   * Stores [location] and refreshes what depends on it (route progress, pending route), without
   * searching for artworks. Live updates use it directly: the artworks around the start of the
   * route are already loaded, and one Wikidata request per fix hits the rate limit.
   */
  private fun applyLocation(location: UserLocation) {
    _uiState.update { it.copy(userLocation = location, errorMsg = null) }
    locationFailed = false
    updateRouteProgress(location)
    trackOffRoute()
    pendingRouteArtwork?.let { artwork ->
      pendingRouteArtwork = null
      startRouteRequest(artwork, location)
    }
  }

  /** Counts consecutive off-route fixes; a single fix back on the route starts over. */
  private fun trackOffRoute() {
    val offRouteMeters = routeProgress?.offRouteMeters
    if (offRouteMeters == null || offRouteMeters <= OFF_ROUTE_THRESHOLD_METERS) {
      resetOffRoute()
      return
    }
    offRouteUpdates++
    if (offRouteUpdates >= OFF_ROUTE_UPDATES_REQUIRED) {
      _recalculationUiState.update { it.copy(isAvailable = true) }
    }
  }

  /**
   * Hides the button and drops a stale failure. An in-flight recalculation is not cancelled: its
   * request is already sent, so its response still replaces the route, and the cooldown keeps
   * running. Closing the route or requesting another one cancels it, see [resetRecalculation].
   */
  private fun resetOffRoute() {
    offRouteUpdates = 0
    _recalculationUiState.update { it.copy(isAvailable = false, error = null) }
  }

  /** Cancels any recalculation and forgets its cooldown, for when the route itself goes away. */
  private fun resetRecalculation() {
    recalculationJob?.cancel()
    recalculationJob = null
    cooldownJob?.cancel()
    cooldownJob = null
    offRouteUpdates = 0
    _recalculationUiState.value = RouteRecalculationUiState()
  }

  /**
   * Requests a new walking route from the user's current location to the active route's
   * destination. The current route stays displayed until the new one arrives, and a failure leaves
   * it untouched. Does nothing unless the user is off-route and no request or cooldown is running.
   *
   * @author patrickmcdan
   */
  fun recalculateRoute() {
    if (!_recalculationUiState.value.canRequest) return
    val success = _routeUiState.value as? RouteUiState.Success ?: return
    val origin = uiState.userLocation ?: return
    val target = success.destination.location ?: return
    startRecalculationCooldown()
    _recalculationUiState.update { it.copy(isLoading = true, isCoolingDown = true, error = null) }
    recalculationJob =
        viewModelScope.launchCatching(tag = TAG, onError = ::setRecalculationError) {
          val route =
              walkingRouteRepository.getWalkingRoute(
                  origin = Coordinate(origin.latitude, origin.longitude),
                  destination = Coordinate(target.latitude, target.longitude),
              )
          _routeUiState.value = RouteUiState.Success(route, success.destination)
          // Progress belongs to the old path: start over on the new one.
          resetRouteProgress()
          _recalculationUiState.update { it.copy(isLoading = false) }
          uiState.userLocation?.let { updateRouteProgress(it) }
        }
  }

  private fun startRecalculationCooldown() {
    cooldownJob?.cancel()
    cooldownJob =
        viewModelScope.launchCatching(
            tag = TAG,
            // Fail open: a broken timer must not leave the button disabled for good.
            onError = { _recalculationUiState.update { it.copy(isCoolingDown = false) } },
        ) {
          delay(RECALCULATE_COOLDOWN_MS.milliseconds)
          _recalculationUiState.update { it.copy(isCoolingDown = false) }
        }
  }

  private fun setRecalculationError(error: AppException) {
    // "No route" is not a RouteUiState.Error, but here it must still be shown as one.
    val routeError =
        routeStateFor(error) as? RouteUiState.Error
            ?: RouteUiState.Error(RouteErrorKind.GENERIC, error.toMessageRes())
    // If the user is back on the route meanwhile, the failure is no longer relevant.
    _recalculationUiState.update {
      it.copy(isLoading = false, error = if (it.isAvailable) routeError else null)
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
   * @param error location failure classified by the AppException framework.
   * @author 5kyPhy, patrickmcdan
   */
  fun setLocationError(error: AppException) {
    locationFailed = true
    _uiState.update { it.copy(errorMsg = error.toMessageRes()) }
    if (pendingRouteArtwork != null) {
      pendingRouteArtwork = null
      _routeUiState.value = RouteUiState.LocationUnavailable
    }
  }

  /**
   * Records that location permission is unavailable.
   *
   * @author 5kyPhy, patrickmcdan
   */
  fun setLocationPermissionError(error: AppException) {
    setLocationError(error)
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
    resetRouteProgress()
    resetRecalculation()
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
    resetRouteProgress()
    resetRecalculation()
    stopLocationUpdates()
    _routeUiState.value = RouteUiState.Idle
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
          uiState.userLocation?.let { updateRouteProgress(it) }
        }
  }

  private fun routeStateFor(error: AppException): RouteUiState =
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

  private fun setRouteError(error: AppException) {
    _routeUiState.value = routeStateFor(error)
  }

  /**
   * Acquires the current device location and updates the map state.
   *
   * @author 5kyPhy
   */
  fun acquireLocation() {
    locationFailed = false
    viewModelScope.launchCatching(
        tag = TAG,
        onError = ::setLocationError,
    ) {
      val location = locationProvider.getUserLocation()
      updateUserLocation(UserLocation(location.latitude, location.longitude))
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
        walkingRouteRepository: WalkingRouteRepository,
        locationProvider: LocationProvider,
    ): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MapViewModel::class.java)) {
              return MapViewModel(
                  wikiDataRepository,
                  savedArtworksRepository,
                  walkingRouteRepository,
                  locationProvider,
              )
                  as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
          }
        }
  }
}
