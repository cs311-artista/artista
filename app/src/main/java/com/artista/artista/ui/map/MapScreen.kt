// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.map

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.artista.artista.R
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkRepositoryProvider
import com.artista.artista.model.error.AppException
import com.artista.artista.model.map.Coordinate
import com.artista.artista.model.map.UserLocation
import com.artista.artista.model.walking.WalkingRouteRepositoryRemote
import com.artista.artista.resources.C
import com.artista.artista.ui.location.UserLocationPermission
import com.artista.artista.ui.location.hasUserLocationPermissions
import com.artista.artista.ui.navigation.BottomNavigationMenu
import com.artista.artista.ui.navigation.Tab
import com.artista.artista.ui.theme.ArtistaColors
import com.artista.artista.ui.theme.ArtistaTheme
import com.artista.artista.ui.theme.Dimensions
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

/**
 * Contains test tags used by the map screen.
 *
 * @author Felix Burchardt, patrickmcdan
 */
object MapScreenTestTags {
  /** Tag identifying the Google Map surface. */
  const val MAP = "map_surface"

  /** Tag identifying the marker for the user's current location. */
  const val USER_LOCATION_MARKER = "map_user_location_marker"

  /** Tag identifying markers for artworks loaded from the remote repository. */
  const val REMOTE_ARTWORK_MARKER = "map_remote_artwork_marker"

  /** Tag identifying markers for artworks saved locally by the user. */
  const val SAVED_ARTWORK_MARKER = "map_saved_artwork_marker"

  /** Tag identifying the walking route summary card. */
  const val ROUTE_SUMMARY_CARD = "map_route_summary_card"

  /** Tag identifying the walking route summary text. */
  const val ROUTE_SUMMARY = "map_route_summary"

  /** Tag identifying the walking route close button. */
  const val ROUTE_CLOSE_BUTTON = "map_route_close_button"

  /** Tag identifying the walking route loading state. */
  const val ROUTE_LOADING = "map_route_loading"

  /** Tag identifying the no-route informational state. */
  const val ROUTE_NO_ROUTE = "map_route_no_route"

  /** Tag identifying the unavailable-location state. */
  const val ROUTE_LOCATION_UNAVAILABLE = "map_route_location_unavailable"

  /** Tag identifying the walking route error state. */
  const val ROUTE_ERROR = "map_route_error"

  /** Tag identifying the walking route retry button. */
  const val ROUTE_RETRY_BUTTON = "map_route_retry_button"
}

/**
 * Identifies the artwork source represented by a map marker.
 *
 * @property color ARGB color used to render the marker icon.
 * @author 5kyPhy
 */
enum class MarkerType(val color: Int) {
  SavedArtwork(ArtistaColors.SavedArtworkMarker.toArgb()),
  RemoteArtwork(ArtistaColors.RemoteArtworkMarker.toArgb()),
}

/**
 * Returns the test tag associated with an artwork marker type.
 *
 * @param markerType source represented by the marker.
 * @return stable marker tag used by map tests and Google Maps marker metadata.
 * @author Copilot
 */
internal fun markerTag(markerType: MarkerType): String =
    when (markerType) {
      MarkerType.SavedArtwork -> MapScreenTestTags.SAVED_ARTWORK_MARKER
      MarkerType.RemoteArtwork -> MapScreenTestTags.REMOTE_ARTWORK_MARKER
    }

/**
 * Removes remote artworks that are already represented by saved markers.
 *
 * @param savedArtworks artworks that must use the saved marker style
 * @param remoteArtworks artworks returned by the remote repository
 * @return remote artworks that do not have a saved counterpart
 * @author Copilot
 */
internal fun remoteArtworksNotSaved(
    savedArtworks: List<Artwork>,
    remoteArtworks: List<Artwork>,
): List<Artwork> {
  val savedArtworkIds = savedArtworks.map { it.uid }.toSet()
  return remoteArtworks.filterNot { it.uid in savedArtworkIds }
}

internal const val USER_LOCATION_ZOOM = 15f

/**
 * Displays a map centered on the user's current position.
 *
 * @param mapViewModel ViewModel containing the latest user location.
 * @author 5kyPhy, patrickmcdan
 */
@Composable
fun MapScreen(
    mapViewModel: MapViewModel,
    onNavigationBarTabSelected: (Tab) -> Unit = {},
    onArtworkMarkerClick: (Artwork) -> Unit = {},
    hasLocationPermissions: (Context) -> Boolean = ::hasUserLocationPermissions,
) {
  val uiState by mapViewModel.uiStateFlow.collectAsStateWithLifecycle()
  val routeUiState by mapViewModel.routeUiStateFlow.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val locationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
  val permissions = REQUIRED_LOCATION_PERMISSIONS.toTypedArray()
  var hasLocationPermission by remember { mutableStateOf(hasAllLocationPermissions(context)) }
  val lifecycleOwner = LocalLifecycleOwner.current
  fun refreshLocationPermission() {
    hasLocationPermission = hasAllLocationPermissions(context)
  }

  val permissionLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshLocationPermission()
        if (!hasLocationPermission) {
          mapViewModel.onLocationPermissionDenied()
        }
      }
  val userLocation = uiState.userLocation
  val latestRouteUiState by rememberUpdatedState(routeUiState)
  val cameraPositionState = rememberCameraPositionState { position = cameraPositionFor(null) }
  val markerState = remember {
    MarkerState(position = LatLng(C.Constants.DEFAULT_MAP_LAT, C.Constants.DEFAULT_MAP_LONG))
  }

  // Request location permission once when screen opens.
  LaunchedEffect(Unit) {
    if (!hasLocationPermission) {
      permissionLauncher.launch(permissions)
    }
  }

  // Refresh permission state when returning from system settings.
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        refreshLocationPermission()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  // Acquire location only after permission is confirmed.
  LaunchedEffect(hasLocationPermission) {
    if (hasLocationPermission) {
      mapViewModel.acquireLocation(locationClient)
    } else {
      mapViewModel.setLocationPermissionError()
    }
  }

  // Show location errors after permission or acquisition failures.
  val errorMessageResId = mapViewModel.uiState.errorMsg
  val errorMessage = errorMessageResId?.let { stringResource(it) }

  UserLocationPermission(
      onPermissionResult = { granted ->
        mapViewModel.handleLocationPermissionResult(granted)
        if (granted && mapViewModel.uiState.userLocation == null) {
          mapViewModel.acquireLocation(context)
        } else if (!granted) {
          mapViewModel.setLocationPermissionError(AppException.UserLocationError())
        }
      },
      hasPermissions = hasLocationPermissions,
  )

  LaunchedEffect(errorMessage) {
    errorMessage?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
  }

  val userLocation = uiState.userLocation
  val cameraPositionState = rememberCameraPositionState { position = cameraPositionFor(null) }
  val markerState = remember {
    MarkerState(position = LatLng(C.Constants.DEFAULT_MAP_LAT, C.Constants.DEFAULT_MAP_LONG))
  }

  // Recenter map and move marker when user location changes.
  // Only recenter when no route is currently displayed, so the user can explore the map without
  // being interrupted by the camera moving back to their location.
  LaunchedEffect(userLocation) {
    userLocation?.let {
      markerState.position = LatLng(it.latitude, it.longitude)
      if (latestRouteUiState !is RouteUiState.Success) {
        cameraPositionState.position = cameraPositionFor(it)
      }
    }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    var mapLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(mapLoaded, routeUiState) {
      val success = routeUiState as? RouteUiState.Success
      if (mapLoaded && success != null) {
        routeBounds(success.route.path, uiState.userLocation, success.destination)?.let { bounds ->
          cameraPositionState.animate(
              CameraUpdateFactory.newLatLngBounds(bounds, Dimensions.MAP_ROUTE_CAMERA_PADDING)
          )
        }
      }
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize().testTag(MapScreenTestTags.MAP),
        cameraPositionState = cameraPositionState,
        onMapLoaded = { mapLoaded = true },
    ) {
      uiState.userLocation?.let {
        Marker(
            tag = MapScreenTestTags.USER_LOCATION_MARKER,
            state = markerState,
            title = stringResource(R.string.map_location_marker_text),
        )
      }
      uiState.savedArtworks.forEach { artwork ->
        DisplayArtworkMarker(artwork, onArtworkMarkerClick, MarkerType.SavedArtwork)
      }
      val remoteArtworks = remoteArtworksNotSaved(uiState.savedArtworks, uiState.remoteArtworks)
      remoteArtworks.forEach { artwork ->
        DisplayArtworkMarker(artwork, onArtworkMarkerClick, MarkerType.RemoteArtwork)
      }

      val success = routeUiState as? RouteUiState.Success
      success?.let { routeState ->
        if (routeState.route.path.isNotEmpty()) {
          Polyline(
              points = routeState.route.path.map { LatLng(it.latitude, it.longitude) },
              color = ArtistaColors.Primary,
              width = Dimensions.POLY_LINE_WIDTH,
          )
        }
        // Ensure the destination artwork is displayed if it's not already shown
        val displayedArtworkIds = (uiState.savedArtworks + remoteArtworks).map { it.uid }.toSet()
        if (
            routeState.destination.uid !in displayedArtworkIds &&
                routeState.destination.location != null
        ) {
          DisplayArtworkMarker(
              routeState.destination,
              onArtworkMarkerClick,
              MarkerType.RemoteArtwork,
          )
        }
      }
    }

    RouteOverlay(
        routeUiState = routeUiState,
        onClearRoute = mapViewModel::clearRoute,
        onRetryRoute = mapViewModel::retryRoute,
        modifier =
            Modifier.align(Alignment.TopCenter)
                .padding(
                    horizontal = Dimensions.mapRouteOverlayHorizontalPadding,
                    vertical = Dimensions.mapRouteOverlayTopPadding,
                ),
    )

    BottomNavigationMenu(
        modifier = Modifier.align(Alignment.BottomCenter),
        selectedTab = Tab.Map,
        onTabSelected = { tab -> onNavigationBarTabSelected(tab) },
    )
  }
}

/**
 * Computes bounds containing the route, current user location and destination.
 *
 * @param path route coordinates
 * @param userLocation current user location, when available
 * @param destination route destination artwork
 * @return bounds containing all available points, or null when no points exist
 * @author patrickmcdan
 */
internal fun routeBounds(
    path: List<Coordinate>,
    userLocation: UserLocation?,
    destination: Artwork,
): LatLngBounds? {
  val points = buildList {
    addAll(path.map { LatLng(it.latitude, it.longitude) })
    userLocation?.let { add(LatLng(it.latitude, it.longitude)) }
    destination.location?.let { add(LatLng(it.latitude, it.longitude)) }
  }
  if (points.isEmpty()) return null
  return LatLngBounds.builder().apply { points.forEach(::include) }.build()
}

/**
 * Displays the route summary or the current route request state.
 *
 * @param routeUiState current walking route state
 * @param onClearRoute called when the route is dismissed
 * @param onRetryRoute called when a retry is requested
 * @param modifier modifier applied to the overlay
 * @author patrickmcdan
 */
@Composable
internal fun RouteOverlay(
    routeUiState: RouteUiState,
    onClearRoute: () -> Unit,
    onRetryRoute: () -> Unit,
    modifier: Modifier = Modifier,
) {
  when (routeUiState) {
    RouteUiState.Idle -> Unit
    RouteUiState.Loading -> {
      RouteMessageCard(
          modifier = modifier.testTag(MapScreenTestTags.ROUTE_LOADING),
          message = stringResource(R.string.map_route_loading),
          showProgress = true,
      )
    }
    is RouteUiState.Success -> {
      Card(
          modifier = modifier.fillMaxWidth().testTag(MapScreenTestTags.ROUTE_SUMMARY_CARD),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      ) {
        Row(
            modifier = Modifier.padding(Dimensions.mapRouteOverlayContentPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
                text =
                    routeUiState.destination.name ?: stringResource(R.string.artwork_unknown_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(Dimensions.mapRouteOverlaySpacing))
            Text(
                modifier = Modifier.testTag(MapScreenTestTags.ROUTE_SUMMARY),
                text =
                    formatWalkingRouteSummary(
                        routeUiState.route.duration,
                        routeUiState.route.distanceMeters,
                    ),
                style = MaterialTheme.typography.bodyMedium,
            )
          }
          IconButton(
              modifier = Modifier.testTag(MapScreenTestTags.ROUTE_CLOSE_BUTTON),
              onClick = onClearRoute,
          ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.map_route_close),
            )
          }
        }
      }
    }
    RouteUiState.NoRoute -> {
      RouteMessageCard(
          modifier = modifier.testTag(MapScreenTestTags.ROUTE_NO_ROUTE),
          message = stringResource(R.string.map_route_no_route),
          onDismiss = onClearRoute,
          informational = true,
      )
    }
    RouteUiState.LocationUnavailable -> {
      RouteMessageCard(
          modifier = modifier.testTag(MapScreenTestTags.ROUTE_LOCATION_UNAVAILABLE),
          message = stringResource(R.string.map_location_unavailable_error),
          onDismiss = onClearRoute,
      )
    }
    is RouteUiState.Error -> {
      RouteMessageCard(
          modifier = modifier.testTag(MapScreenTestTags.ROUTE_ERROR),
          message = stringResource(routeUiState.messageRes),
          onDismiss = onClearRoute,
          onRetry = if (routeUiState.kind.canRetry) onRetryRoute else null,
      )
    }
  }
}

@Composable
private fun RouteMessageCard(
    message: String,
    modifier: Modifier = Modifier,
    showProgress: Boolean = false,
    onDismiss: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    informational: Boolean = false,
) {
  Card(
      modifier = modifier.fillMaxWidth(),
      colors =
          CardDefaults.cardColors(
              containerColor =
                  if (informational) MaterialTheme.colorScheme.secondaryContainer
                  else MaterialTheme.colorScheme.errorContainer
          ),
  ) {
    Row(
        modifier = Modifier.padding(Dimensions.mapRouteOverlayContentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      if (showProgress) {
        CircularProgressIndicator(modifier = Modifier.size(Dimensions.iconSizeMedium))
        Spacer(modifier = Modifier.size(Dimensions.mapRouteOverlaySpacing))
      }
      Text(
          modifier = Modifier.weight(1f),
          text = message,
          color =
              if (informational) MaterialTheme.colorScheme.onSecondaryContainer
              else MaterialTheme.colorScheme.onErrorContainer,
          style = MaterialTheme.typography.bodyLarge,
      )
      onRetry?.let {
        Button(
            modifier = Modifier.testTag(MapScreenTestTags.ROUTE_RETRY_BUTTON),
            onClick = it,
        ) {
          Text(text = stringResource(R.string.map_route_retry))
        }
      }
      onDismiss?.let {
        IconButton(
            modifier = Modifier.testTag(MapScreenTestTags.ROUTE_CLOSE_BUTTON),
            onClick = it,
        ) {
          Icon(
              imageVector = Icons.Default.Close,
              contentDescription = stringResource(R.string.map_route_close),
          )
        }
      }
    }
  }
}

@Composable
private fun DisplayArtworkMarker(
    artwork: Artwork,
    onArtworkMarkerClick: (Artwork) -> Unit,
    markerType: MarkerType,
) {
  artwork.location?.let {
    val artworkMarkerState =
        remember(key1 = artwork.uid) {
          MarkerState(position = LatLng(artwork.location.latitude, artwork.location.longitude))
        }
    Marker(
        tag = markerTag(markerType),
        state = artworkMarkerState,
        title = artwork.name,
        snippet = stringResource(R.string.map_marker_navigate_to),
        onInfoWindowClick = { onArtworkMarkerClick(artwork) },
        icon = artworkMarkerIcon(markerType.color),
    )
  }
}

/**
 * Creates a map-compatible pin icon using the supplied centralized artwork color.
 *
 * Google Maps requires a [com.google.android.gms.maps.model.BitmapDescriptor] for marker icons, so
 * the Compose color cannot be passed directly to [Marker].
 *
 * @param color ARGB color used for the pin body.
 * @return bitmap descriptor containing a colored pin with a white center.
 * @author 5kyPhy
 */
private fun artworkMarkerIcon(color: Int) =
    BitmapDescriptorFactory.fromBitmap(artworkMarkerBitmap(color))

/**
 * Renders the colored pin bitmap used by an artwork map marker.
 *
 * @param color ARGB color used for the pin body.
 * @return bitmap containing a colored pin with a white center.
 * @author Copilot
 */
private fun artworkMarkerBitmap(color: Int): Bitmap =
    createBitmap(48, 64).also { bitmap ->
      Canvas(bitmap).apply {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        // Draw the pin manually so its color comes from the shared Compose palette.
        val path =
            Path().apply {
              moveTo(24f, 64f)
              cubicTo(20f, 56f, 4f, 42f, 4f, 24f)
              cubicTo(4f, 11f, 13f, 2f, 24f, 2f)
              cubicTo(35f, 2f, 44f, 11f, 44f, 24f)
              cubicTo(44f, 42f, 28f, 56f, 24f, 64f)
              close()
            }
        drawPath(path, paint)
        drawCircle(
            24f,
            24f,
            8f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = android.graphics.Color.WHITE },
        )
      }
    }

/**
 * Builds the initial or user-centered camera position.
 *
 * @param userLocation Current user location, or null before location is available.
 * @return Camera position centered on the user or the default map location.
 * @author Felix Burchardt
 */
internal fun cameraPositionFor(userLocation: UserLocation?): CameraPosition {
  val target =
      userLocation?.let { LatLng(it.latitude, it.longitude) }
          ?: LatLng(C.Constants.DEFAULT_MAP_LAT, C.Constants.DEFAULT_MAP_LONG)
  val zoom = if (userLocation == null) C.Constants.DEFAULT_MAP_ZOOM else USER_LOCATION_ZOOM
  return CameraPosition.fromLatLngZoom(target, zoom)
}

/**
 * Previews the map screen layout.
 *
 * @author Felix Burchardt
 */
@Preview(widthDp = 1080, heightDp = 2424)
@Composable
internal fun MapScreenPreview() {
  ArtistaTheme {
    MapScreen(
        mapViewModel =
            viewModel(
                factory =
                    MapViewModel.provideFactory(
                        wikiDataRepository = ArtworkRepositoryProvider.mockRepository,
                        savedArtworksRepository = ArtworkRepositoryProvider.mockRepository,
                        walkingRouteRepository = WalkingRouteRepositoryRemote(),
                    )
            )
    )
  }
}
