// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.map

import android.location.Location
import androidx.annotation.StringRes
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.artista.artista.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.concurrent.Executor

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
    @param:StringRes val errorMsg: Int? = null,
)

/**
 * Provides the user location to the map screen.
 *
 * @author Felix Burchardt
 */
class MapViewModel(
    private val locationProvider: LocationProvider? = null,
) : ViewModel() {

  val uiState: MapViewUiState
    get() = _uiState.value

  private val _uiState = mutableStateOf(MapViewUiState())

  /**
   * Updates the location shown on the map.
   *
   * @param location Latest location reported by the device.
   * @author Felix Burchardt
   */
  fun updateUserLocation(location: UserLocation) {
    _uiState.value = uiState.copy(userLocation = location, errorMsg = null)
  }

  /**
   * Records an error encountered while reading the device location.
   *
   * @param messageResId Resource ID for the error message.
   * @author Felix Burchardt
   */
  fun setLocationError(@StringRes messageResId: Int) {
    _uiState.value = uiState.copy(errorMsg = messageResId)
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
   * @param hasLocationPermission Whether runtime location permission is granted.
   * @author @Felix Burchardt
   */
  fun acquireLocation(
      locationClient: FusedLocationProviderClient,
      hasLocationPermission: Boolean,
  ) {
    if (!hasLocationPermission) {
      setLocationPermissionError()
      return
    }

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

  private companion object {
    val DIRECT_EXECUTOR = Executor { command -> command.run() }
  }
}

/**
 * Geographic coordinates used by the map screen.
 *
 * @property latitude Latitude in degrees.
 * @property longitude Longitude in degrees.
 * @author Felix Burchardt
 */
data class UserLocation(
    val latitude: Double,
    val longitude: Double,
)
