// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.graphics.createBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkRepositoryProvider
import com.artista.artista.model.error.AppException
import com.artista.artista.model.map.UserLocation
import com.artista.artista.resources.C
import com.artista.artista.ui.location.UserLocationPermission
import com.artista.artista.ui.navigation.BottomNavigationMenu
import com.artista.artista.ui.navigation.Tab
import com.artista.artista.ui.theme.ArtistaColors
import com.artista.artista.ui.theme.ArtistaTheme
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

/**
 * Contains test tags used by the map screen.
 *
 * @author Felix Burchardt
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
 * @author 5kyPhy
 */
@Composable
fun MapScreen(
    mapViewModel: MapViewModel,
    onNavigationBarTabSelected: (Tab) -> Unit = {},
    onArtworkMarkerClick: (Artwork) -> Unit = {},
) {
  val uiState by mapViewModel.uiStateFlow.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val locationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
  val errorMessageResId = mapViewModel.uiState.errorMsg
  val errorMessage = errorMessageResId?.let { stringResource(it) }

  UserLocationPermission { granted ->
    mapViewModel.handleLocationPermissionResult(granted)
    if (granted && mapViewModel.uiState.userLocation == null) {
      mapViewModel.acquireLocation(locationClient)
    } else if (!granted) {
      mapViewModel.setLocationPermissionError(AppException.UserLocationError())
    }
  }

  LaunchedEffect(errorMessage) {
    errorMessage?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
  }

  val userLocation = uiState.userLocation
  val cameraPositionState = rememberCameraPositionState { position = cameraPositionFor(null) }
  val markerState = remember {
    MarkerState(position = LatLng(C.Constants.DEFAULT_MAP_LAT, C.Constants.DEFAULT_MAP_LONG))
  }

  // Recenter map and move marker when user location changes.
  LaunchedEffect(userLocation) {
    userLocation?.let {
      cameraPositionState.position = cameraPositionFor(it)
      markerState.position = LatLng(it.latitude, it.longitude)
    }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    GoogleMap(
        modifier = Modifier.fillMaxSize().testTag(MapScreenTestTags.MAP),
        cameraPositionState = cameraPositionState,
    ) {
      uiState.savedArtworks.forEach { artwork ->
        DisplayArtworkMarker(artwork, onArtworkMarkerClick, MarkerType.SavedArtwork)
      }
      remoteArtworksNotSaved(uiState.savedArtworks, uiState.remoteArtworks).forEach { artwork ->
        DisplayArtworkMarker(
            artwork,
            onArtworkMarkerClick,
            MarkerType.RemoteArtwork,
        )
      }
    }

    BottomNavigationMenu(
        modifier = Modifier.align(Alignment.BottomCenter),
        selectedTab = Tab.Map,
        onTabSelected = { tab -> onNavigationBarTabSelected(tab) },
    )
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
                    )
            )
    )
  }
}
