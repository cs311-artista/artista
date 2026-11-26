// Co-authored-by: DeepSeek Flash <noreply@deepseek.com>

// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.map

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.artista.artista.R
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkRepositoryProvider
import com.artista.artista.model.error.AppException
import com.artista.artista.model.location.UserLocationProvider
import com.artista.artista.model.map.Coordinate
import com.artista.artista.model.map.UserLocation
import com.artista.artista.model.walking.WalkingRouteRepositoryRemote
import com.artista.artista.resources.C
import com.artista.artista.ui.artwork.ArtworkImage
import com.artista.artista.ui.error.toMessageRes
import com.artista.artista.ui.location.UserLocationPermission
import com.artista.artista.ui.location.hasUserLocationPermissions
import com.artista.artista.ui.navigation.BottomNavigationMenu
import com.artista.artista.ui.navigation.Tab
import com.artista.artista.ui.shared.ArtworkMarker
import com.artista.artista.ui.shared.MarkerType
import com.artista.artista.ui.theme.ArtistaColors
import com.artista.artista.ui.theme.ArtistaTheme
import com.artista.artista.ui.theme.Dimensions
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

/**
 * Contains test tags used by the map screen.
 *
 * @author 5kyPhy, patrickmcdan
 */
object MapScreenTestTags {
  /** Tag identifying the Google Map surface. */
  const val MAP = "map_surface"

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
  /** Tag identifying the recalculate route button. */
  const val ROUTE_RECALCULATE_BUTTON = "map_route_recalculate_button"
  /** Tag identifying the recalculate route error state. */
  const val ROUTE_RECALCULATE_ERROR = "map_route_recalculate_error"

  /** Tag identifying the unavailable-location state. */
  const val ROUTE_LOCATION_UNAVAILABLE = "map_route_location_unavailable"

  /** Tag identifying the walking route error state. */
  const val ROUTE_ERROR = "map_route_error"

  /** Tag identifying the walking route retry button. */
  const val ROUTE_RETRY_BUTTON = "map_route_retry_button"

  /** Tag identifying the selected-artwork card. */
  const val ARTWORK_CARD = "map_artwork_card"

  /** Tag identifying the selected artwork's name in the card. */
  const val ARTWORK_CARD_NAME = "map_artwork_card_name"

  /** Tag identifying the selected artwork's image in the card. */
  const val ARTWORK_CARD_IMAGE = "map_artwork_card_image"

  /** Tag identifying the fallback shown while an artwork image loads or fails. */
  const val ARTWORK_CARD_IMAGE_FALLBACK = "map_artwork_card_image_fallback"

  /** Tag identifying the compact placeholder shown when an artwork has no image URL. */
  const val ARTWORK_CARD_IMAGE_PLACEHOLDER = "map_artwork_card_image_placeholder"

  /** Tag identifying the send action on the selected-artwork card. */
  const val ARTWORK_CARD_SEND = "map_artwork_card_send"

  /** Tag identifying the dismiss action on the selected-artwork card. */
  const val ARTWORK_CARD_DISMISS = "map_artwork_card_dismiss"

  /** Tag identifying the map's recenter action. */
  const val RECENTER_BUTTON = "map_recenter_button"

  /** Tag identifying the go to details action on the selected-artwork card. */
  const val ARTWORK_CARD_DETAILS = "map_artwork_details_button"
}

/**
 * Returns the test tag associated with an artwork marker type.
 *
 * @param markerType source represented by the marker.
 * @return stable marker tag used by map tests and Google Maps marker metadata.
 * @author 5kyPhy
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

/**
 * Displays a map centered on the user's current position.
 *
 * @param mapViewModel ViewModel containing the latest user location.
 * @param onNavigationBarTabSelected called when a navigation bar tab is selected
 * @param onDetailsButtonClicked called when the selected artwork's details action is pressed
 * @param hasLocationPermissions returns true when the user has granted location permissions. Made
 *   injectable for testing and should not be changed for production.
 * @author 5kyPhy, patrickmcdan
 */
@Composable
fun MapScreen(
    mapViewModel: MapViewModel,
    onNavigationBarTabSelected: (Tab) -> Unit = {},
    onDetailsButtonClicked: (Artwork) -> Unit,
    hasLocationPermissions: (Context) -> Boolean = ::hasUserLocationPermissions,
) {
  val uiState by mapViewModel.uiStateFlow.collectAsStateWithLifecycle()
  val routeUiState by mapViewModel.routeUiStateFlow.collectAsStateWithLifecycle()
  val routeProgressUiState by mapViewModel.routeProgressUiStateFlow.collectAsStateWithLifecycle()
  val recalculationUiState by mapViewModel.recalculationUiStateFlow.collectAsStateWithLifecycle()
  val hasArrived by mapViewModel.hasArrived.collectAsStateWithLifecycle()
  val isRouteActive = routeUiState is RouteUiState.Success && !hasArrived
  val context = LocalContext.current
  val userLocation = uiState.userLocation
  val latestRouteUiState by rememberUpdatedState(routeUiState)
  val cameraPositionState = rememberCameraPositionState { position = cameraPositionFor(null) }

  var locationAcquisitionRequested by remember { mutableStateOf(false) }

  // Show location errors after permission or acquisition failures.
  val errorMessageResId = mapViewModel.uiState.errorMsg
  val errorMessage = errorMessageResId?.let { stringResource(it) }

  UserLocationPermission(
      onPermissionResult = { granted ->
        mapViewModel.handleLocationPermissionResult(granted)
        if (
            granted && (!locationAcquisitionRequested || mapViewModel.uiState.userLocation == null)
        ) {
          locationAcquisitionRequested = true
          mapViewModel.acquireLocation()
        } else if (!granted) {
          mapViewModel.setLocationPermissionError(AppException.UserLocationError())
        }
      },
      hasPermissions = hasLocationPermissions,
  )

  // Keyed on route and permission, so tracking runs only while a route is shown and the screen is
  // started, and is stopped on route close, backgrounding or leaving composition.
  LifecycleStartEffect(isRouteActive, hasLocationPermissions) {
    if (isRouteActive && hasLocationPermissions(context)) mapViewModel.startLocationUpdates()
    onStopOrDispose { mapViewModel.stopLocationUpdates() }
  }

  LaunchedEffect(errorMessage) {
    errorMessage?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
  }

  // Recenter map when user location changes.
  // Only recenter when no route is currently displayed, so the user can explore the map without
  // being interrupted by the camera moving back to their location.
  LaunchedEffect(userLocation) {
    userLocation?.let {
      if (latestRouteUiState !is RouteUiState.Success) {
        cameraPositionState.position = cameraPositionFor(it)
      }
    }
  }

  var mapSize by remember { mutableStateOf(IntSize.Zero) }

  Box(modifier = Modifier.fillMaxSize().onSizeChanged { mapSize = it }) {
    var mapLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(mapLoaded, mapSize, routeUiState) {
      val success = routeUiState as? RouteUiState.Success
      // Google Maps throws if the view is smaller than twice the padding, which happens
      // when the route arrives before the map has been laid out.
      if (!mapLoaded || success == null || mapSize.width <= 0 || mapSize.height <= 0) {
        return@LaunchedEffect
      }
      val bounds =
          routeBounds(success.route.path, uiState.userLocation, success.destination)
              ?: return@LaunchedEffect
      val padding =
          minOf(Dimensions.MAP_ROUTE_CAMERA_PADDING, minOf(mapSize.width, mapSize.height) / 4)
      cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, padding))
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize().testTag(MapScreenTestTags.MAP),
        cameraPositionState = cameraPositionState,
        onMapLoaded = { mapLoaded = true },
        onMapClick = { mapViewModel.dismissSelectedArtwork() },
        properties = MapProperties(isMyLocationEnabled = uiState.hasLocationPermission),
        uiSettings =
            MapUiSettings(
                mapToolbarEnabled = false,
                // The app draws its own recenter control; Google's sits under the artwork card.
                myLocationButtonEnabled = false,
            ),
        contentPadding =
            PaddingValues(
                bottom =
                    Dimensions.bottomMapToolBarPadding +
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                top = Dimensions.topMapToolBarPadding,
            ),
    ) {
      uiState.savedArtworks.forEach { artwork ->
        DisplayArtworkMarker(artwork, mapViewModel::selectArtwork, MarkerType.SavedArtwork)
      }
      val remoteArtworks = remoteArtworksNotSaved(uiState.savedArtworks, uiState.remoteArtworks)
      remoteArtworks.forEach { artwork ->
        DisplayArtworkMarker(artwork, mapViewModel::selectArtwork, MarkerType.RemoteArtwork)
      }

      val success = routeUiState as? RouteUiState.Success
      success?.let { routeState ->
        // Fall back to the full path until the first progress is computed.
        val path = routeProgressUiState?.path ?: routeState.route.path
        if (path.isNotEmpty() && !hasArrived) {
          Polyline(
              points = path.map { LatLng(it.latitude, it.longitude) },
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
              mapViewModel::selectArtwork,
              MarkerType.RemoteArtwork,
          )
        }
      }
    }

    if (userLocation != null) {
      MapRecenterButton(
          onClick = { cameraPositionState.position = cameraPositionFor(userLocation) },
          modifier =
              Modifier.align(Alignment.BottomEnd)
                  .padding(
                      end = Dimensions.mapRecenterButtonEndPadding,
                      bottom = Dimensions.mapRecenterButtonBottomPadding,
                  ),
      )
    }

    RouteOverlay(
        routeUiState = routeUiState,
        routeProgressUiState = routeProgressUiState,
        selectedArtwork = uiState.selectedArtwork,
        onNavigate = mapViewModel::navigateToSelectedArtwork,
        recalculationUiState = recalculationUiState,
        onClearRoute = mapViewModel::clearRoute,
        onRetryRoute = mapViewModel::retryRoute,
        onRecalculateRoute = mapViewModel::recalculateRoute,
        hasArrived = hasArrived,
        onDetailsButtonClicked = onDetailsButtonClicked,
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
 * Displays the selected artwork card or the current route request state.
 *
 * @param routeUiState current walking route state
 * @param routeProgressUiState remaining part of the route; when null the full route values are
 *   shown
 * @param selectedArtwork artwork whose card is shown while no route is active, and whose name is
 *   shown while a route is loading
 * @param onNavigate called when the selected artwork's send action is pressed
 * @param onClearRoute called when the card or route is dismissed
 * @param onRetryRoute called when a retry is requested
 * @param onRecalculateRoute called when a new route from the user's location is requested, from the
 *   "Recalculate route" button or the retry action of a failed recalculation
 * @param recalculationUiState state of the route recalculation: whether the button is offered, its
 *   loading and cooldown states, and the error of a failed request
 * @param hasArrived whether the user is within the arrival threshold of the destination
 * @param onDetailsButtonClicked called when the selected artwork's details action is pressed
 * @param modifier modifier applied to the overlay
 * @author patrickmcdan
 */
@Composable
internal fun RouteOverlay(
    routeUiState: RouteUiState,
    routeProgressUiState: RouteProgressUiState? = null,
    selectedArtwork: Artwork? = null,
    onNavigate: () -> Unit = {},
    onClearRoute: () -> Unit,
    onRetryRoute: () -> Unit,
    onRecalculateRoute: () -> Unit,
    recalculationUiState: RouteRecalculationUiState,
    hasArrived: Boolean,
    onDetailsButtonClicked: (Artwork) -> Unit,
    modifier: Modifier = Modifier,
) {
  when (routeUiState) {
    RouteUiState.Idle -> {
      selectedArtwork?.let { artwork ->
        ArtworkCard(
            name = artwork.name ?: stringResource(R.string.artwork_unknown_title),
            imageUrl = artwork.imageUrl,
            showImage = true,
            showSend = true,
            showDetails = true,
            onNavigate = onNavigate,
            onDismiss = onClearRoute,
            onDetails = { onDetailsButtonClicked(artwork) },
            cardTestTag = MapScreenTestTags.ARTWORK_CARD,
            modifier = modifier,
        )
      }
    }
    RouteUiState.Loading -> {
      ArtworkCard(
          name = selectedArtwork?.let { it.name ?: stringResource(R.string.artwork_unknown_title) },
          imageUrl = null,
          showImage = false,
          showSend = false,
          showDetails = selectedArtwork?.let { true } ?: false,
          onNavigate = onNavigate,
          onDismiss = onClearRoute,
          onDetails = { selectedArtwork?.let { onDetailsButtonClicked(it) } },
          cardTestTag = MapScreenTestTags.ARTWORK_CARD,
          modifier = modifier,
          secondLine = { ArtworkCardLoadingLine() },
      )
    }
    is RouteUiState.Success -> {
      Column(
          modifier = modifier,
          verticalArrangement = Arrangement.spacedBy(Dimensions.mapRouteOverlaySpacing),
      ) {
        ArtworkCard(
            name = routeUiState.destination.name ?: stringResource(R.string.artwork_unknown_title),
            imageUrl = null,
            showImage = false,
            showSend = false,
            showDismiss = !hasArrived,
            showDetails = selectedArtwork?.let { true } ?: false,
            onNavigate = onNavigate,
            onDismiss = onClearRoute,
            onDetails = { selectedArtwork?.let { onDetailsButtonClicked(it) } },
            cardTestTag = MapScreenTestTags.ROUTE_SUMMARY_CARD,
            secondLine = {
              Text(
                  modifier = Modifier.testTag(MapScreenTestTags.ROUTE_SUMMARY),
                  text =
                      when {
                        hasArrived -> stringResource(R.string.map_route_arrived)
                        routeProgressUiState != null ->
                            formatWalkingRouteSummary(
                                routeProgressUiState.remainingDurationSeconds.seconds,
                                routeProgressUiState.remainingDistanceMeters.roundToInt(),
                            )
                        else ->
                            formatWalkingRouteSummary(
                                routeUiState.route.duration,
                                routeUiState.route.distanceMeters,
                            )
                      },
                  style = MaterialTheme.typography.bodyMedium,
                  textAlign = TextAlign.Center,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
              )
            },
        )
        if (hasArrived) {
          Button(
              modifier = Modifier.fillMaxWidth().testTag(MapScreenTestTags.ROUTE_CLOSE_BUTTON),
              onClick = onClearRoute,
          ) {
            Text(text = stringResource(R.string.map_route_arrived_close))
          }
        }
        if (recalculationUiState.isAvailable && recalculationUiState.error == null) {
          Button(
              modifier =
                  Modifier.fillMaxWidth().testTag(MapScreenTestTags.ROUTE_RECALCULATE_BUTTON),
              enabled = recalculationUiState.canRequest,
              onClick = onRecalculateRoute,
          ) {
            if (recalculationUiState.isLoading) {
              CircularProgressIndicator(
                  modifier = Modifier.size(Dimensions.iconSizeMedium),
                  color = LocalContentColor.current,
              )
              Spacer(modifier = Modifier.size(Dimensions.mapRouteOverlaySpacing))
            }
            Text(text = stringResource(R.string.map_route_recalculate))
          }
        }
        recalculationUiState.error?.let { error ->
          RouteMessageCard(
              modifier = Modifier.testTag(MapScreenTestTags.ROUTE_RECALCULATE_ERROR),
              message = stringResource(error.messageRes),
              onRetry = if (error.kind.canRetry) onRecalculateRoute else null,
              retryEnabled = recalculationUiState.canRequest,
          )
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
          message = stringResource(AppException.UserLocationError().toMessageRes()),
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

/**
 * Displays the selected artwork, its route state and the actions that operate on it.
 *
 * The header and the image are two rounded surfaces with a small gap, so they read as one card
 * without sharing a border. The header is compact until a second line appears, which keeps the
 * selected state as thin as the search bar; routing makes it taller. Equal-width slots on both
 * sides of the name keep the text centred whether the send action is present or not.
 *
 * @param name artwork name, or null while a route is loading without a selected artwork
 * @param imageUrl artwork image, or null when the artwork has no image
 * @param showImage whether the image surface is shown below the header
 * @param showSend whether the send action is part of the header
 * @param showDismiss whether the close action is part of the header; the arrival state hides it
 *   because it offers an explicit close button under the card instead
 * @param showDetails whether the details action is part of the header
 * @param onNavigate called when the send action is pressed
 * @param onDismiss called when the close action is pressed
 * @param onDetails called when the details action is pressed
 * @param cardTestTag stable tag identifying the header in this state
 * @param modifier modifier applied to the column containing both surfaces
 * @param secondLine optional line under the name, such as the loading indicator or the ETA
 * @author timo-by, 5kyPhy
 */
@Composable
private fun ArtworkCard(
    name: String?,
    imageUrl: String?,
    showImage: Boolean,
    showSend: Boolean,
    showDismiss: Boolean = true,
    showDetails: Boolean,
    onNavigate: () -> Unit,
    onDismiss: () -> Unit,
    onDetails: () -> Unit,
    cardTestTag: String,
    modifier: Modifier = Modifier,
    secondLine: (@Composable () -> Unit)? = null,
) {
  val displayName = name ?: stringResource(R.string.artwork_unknown_title)
  val verticalPadding =
      if (secondLine == null) Dimensions.mapArtworkCardCompactVerticalPadding
      else Dimensions.mapRouteOverlayContentPadding
  Column(modifier = modifier.fillMaxWidth()) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag(cardTestTag),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
      Row(
          modifier =
              Modifier.padding(
                  horizontal = Dimensions.mapRouteOverlayContentPadding,
                  vertical = verticalPadding,
              ),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        if (showSend) {
          IconButton(
              modifier = Modifier.testTag(MapScreenTestTags.ARTWORK_CARD_SEND),
              onClick = onNavigate,
          ) {
            Icon(
                imageVector = Icons.Outlined.NearMe,
                contentDescription = stringResource(R.string.map_artwork_navigate),
            )
          }
        }
        if (showDetails) {
          IconButton(
              modifier = Modifier.testTag(MapScreenTestTags.ARTWORK_CARD_DETAILS),
              onClick = onDetails,
          ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = stringResource(R.string.map_artwork_details),
            )
          }
        }
        if (!(showSend || showImage)) {
          Spacer(modifier = Modifier.size(Dimensions.standardTouchTargetSize))
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          if (name != null) {
            Text(
                modifier = Modifier.testTag(MapScreenTestTags.ARTWORK_CARD_NAME),
                text = name,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (secondLine != null) {
              Spacer(modifier = Modifier.height(Dimensions.mapRouteOverlaySpacing))
            }
          }
          secondLine?.invoke()
        }
        if (showDismiss) {
          IconButton(
              modifier = Modifier.testTag(MapScreenTestTags.ARTWORK_CARD_DISMISS),
              onClick = onDismiss,
          ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.map_artwork_dismiss),
            )
          }
        }
      }
    }
    if (showImage) {
      Spacer(modifier = Modifier.height(Dimensions.mapArtworkCardImageSpacing))
      if (imageUrl == null) {
        ArtworkCardEmptyImage()
      } else {
        Card(
            modifier = Modifier.fillMaxWidth().height(Dimensions.mapArtworkCardImageHeight),
            colors =
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
          ArtworkImage(
              imageUrl = imageUrl,
              contentDescription =
                  stringResource(R.string.artwork_card_image_description, displayName),
              contentScale = ContentScale.Crop,
              imageTestTag = MapScreenTestTags.ARTWORK_CARD_IMAGE,
              fallbackTestTag = MapScreenTestTags.ARTWORK_CARD_IMAGE_FALLBACK,
              modifier = Modifier.fillMaxSize(),
          )
        }
      }
    }
  }
}

/**
 * Displays the compact placeholder shown when an artwork has no image URL.
 *
 * A missing image must not take the 160 dp banner's space, but an empty card would read as a
 * failure. The icon and label make the empty state deliberate.
 */
@Composable
private fun ArtworkCardEmptyImage() {
  Card(
      modifier =
          Modifier.fillMaxWidth()
              .height(Dimensions.mapArtworkCardEmptyImageHeight)
              .testTag(MapScreenTestTags.ARTWORK_CARD_IMAGE_PLACEHOLDER),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
  ) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
          imageVector = Icons.Outlined.Image,
          contentDescription = null,
          modifier = Modifier.size(Dimensions.iconSizeMedium),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.width(Dimensions.mapRouteOverlaySpacing))
      Text(
          text = stringResource(R.string.map_artwork_no_image),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/** Displays the small progress indicator and text shown while a walking route is requested. */
@Composable
private fun ArtworkCardLoadingLine() {
  Row(
      modifier = Modifier.testTag(MapScreenTestTags.ROUTE_LOADING),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    CircularProgressIndicator(modifier = Modifier.size(Dimensions.iconSizeMedium))
    Spacer(modifier = Modifier.size(Dimensions.mapRouteOverlaySpacing))
    Text(
        text = stringResource(R.string.map_route_loading),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun RouteMessageCard(
    message: String,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    informational: Boolean = false,
    retryEnabled: Boolean = true,
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
            enabled = retryEnabled,
        ) {
          Text(text = stringResource(R.string.try_again))
        }
      }
      onDismiss?.let {
        IconButton(
            modifier = Modifier.testTag(MapScreenTestTags.ROUTE_CLOSE_BUTTON),
            onClick = it,
        ) {
          Icon(
              imageVector = Icons.Default.Close,
              contentDescription = stringResource(R.string.map_artwork_dismiss),
          )
        }
      }
    }
  }
}

/**
 * Draws the map control that recenters the camera on the user's location.
 *
 * The control is only drawn once a location is known; the caller positions it above Google's zoom
 * controls so the two never overlap.
 *
 * @param onClick called when the user taps the control
 * @param modifier modifier applied to the control, including its placement on the map
 * @author timo-by
 */
@Composable
internal fun MapRecenterButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
  SmallFloatingActionButton(
      onClick = onClick,
      modifier = modifier.testTag(MapScreenTestTags.RECENTER_BUTTON),
  ) {
    Icon(
        imageVector = Icons.Outlined.MyLocation,
        contentDescription = stringResource(R.string.map_recenter),
    )
  }
}

/**
 * Displays a map marker for an artwork.
 *
 * Tapping the marker selects the artwork; the app card replaces the Google Maps info window.
 *
 * @param artwork artwork represented by the marker.
 * @param onArtworkSelected called when the marker is tapped.
 * @param markerType source represented by the marker.
 * @author 5kyPhy
 */
@Composable
private fun DisplayArtworkMarker(
    artwork: Artwork,
    onArtworkSelected: (Artwork) -> Unit,
    markerType: MarkerType,
) {
  artwork.location?.let {
    val artworkMarkerState =
        remember(key1 = artwork.uid) {
          MarkerState(position = LatLng(artwork.location.latitude, artwork.location.longitude))
        }
    val icon = remember(markerType) { ArtworkMarker.artworkMarkerIcon(markerType) }
    Marker(
        tag = markerTag(markerType),
        state = artworkMarkerState,
        onClick = { _ ->
          onArtworkSelected(artwork)
          true
        },
        icon = icon,
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
  val zoom =
      if (userLocation == null) C.Constants.DEFAULT_MAP_ZOOM else C.Constants.USER_LOCATION_ZOOM
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
  val locationClient = LocationServices.getFusedLocationProviderClient(LocalContext.current)
  ArtistaTheme {
    MapScreen(
        mapViewModel =
            viewModel(
                factory =
                    MapViewModel.provideFactory(
                        wikiDataRepository = ArtworkRepositoryProvider.mockRepository,
                        savedArtworksRepository = ArtworkRepositoryProvider.mockRepository,
                        walkingRouteRepository = WalkingRouteRepositoryRemote(),
                        locationProvider = UserLocationProvider(locationClient),
                    )
            ),
        onDetailsButtonClicked = {},
    )
  }
}
