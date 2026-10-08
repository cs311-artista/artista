// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.artista.artista.ui.navigation.BottomNavigationMenu
import com.artista.artista.ui.navigation.Tab
import com.artista.artista.ui.theme.ArtistaTheme
import com.google.android.gms.location.LocationServices
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
}

private val defaultMapLocation = LatLng(DEFAULT_MAP_LATITUDE, DEFAULT_MAP_LONGITUDE)
internal const val DEFAULT_MAP_LATITUDE = 46.5197
internal const val DEFAULT_MAP_LONGITUDE = 6.6323
internal const val DEFAULT_MAP_ZOOM = 12f
internal const val USER_LOCATION_ZOOM = 15f
internal val REQUIRED_LOCATION_PERMISSIONS =
    setOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

/**
 * Displays a map centered on the user's current position.
 *
 * @param mapViewModel ViewModel containing the latest user location.
 * @author Felix Burchardt
 */
@Composable
fun MapScreen(
    mapViewModel: MapViewModel = viewModel(),
    onNavigationBarTabSelected: (Tab) -> Unit = {},
) {
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
          mapViewModel.setLocationPermissionError()
        }
      }
  val userLocation = mapViewModel.uiState.userLocation
  val cameraPositionState = rememberCameraPositionState { position = cameraPositionFor(null) }
  val markerState = remember { MarkerState(position = defaultMapLocation) }

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
      mapViewModel.acquireLocation(locationClient, hasLocationPermission)
    }
  }

  // Show location errors after permission or acquisition failures.
  val errorMessageResId = mapViewModel.uiState.errorMsg
  val errorMessage = errorMessageResId?.let { stringResource(it) }
  LaunchedEffect(errorMessage) {
    errorMessage?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
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
      userLocation?.let {
        Marker(
            state = markerState,
            title = "Your location",
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

private fun hasAllLocationPermissions(context: android.content.Context): Boolean =
    REQUIRED_LOCATION_PERMISSIONS.all {
      ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

/**
 * Builds the initial or user-centered camera position.
 *
 * @param userLocation Current user location, or null before location is available.
 * @return Camera position centered on the user or the default map location.
 * @author Felix Burchardt
 */
internal fun cameraPositionFor(userLocation: UserLocation?): CameraPosition {
  val target = userLocation?.let { LatLng(it.latitude, it.longitude) } ?: defaultMapLocation
  val zoom = if (userLocation == null) DEFAULT_MAP_ZOOM else USER_LOCATION_ZOOM
  return CameraPosition.fromLatLngZoom(target, zoom)
}

/**
 * Previews the map screen layout.
 *
 * @author Felix Burchardt
 */
@Preview(widthDp = 1080, heightDp = 2424)
@Composable
fun MapScreenPreview() {
  ArtistaTheme() { MapScreen() }
}
