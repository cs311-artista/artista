package com.artista.artista.model.location

import com.artista.artista.model.error.AppException
import com.artista.artista.model.map.UserLocation
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.concurrent.Executor

/**
 * Provides the user's current location.
 *
 * @param locationClient the client used to fetch the user's location
 * @author 5kyPhy
 */
class UserLocationProvider(val locationClient: FusedLocationProviderClient) : LocationProvider {

  /**
   * Returns the user's current location, or null if it is unavailable.
   *
   * @return the user's current location, or null if it is unavailable
   * @throws [AppException.UserLocationError] if the location is unavailable
   * @throws [AppException.PermissionsError] if the user has not granted the necessary permissions
   * @author 5kyPhy
   */
  override fun getUserLocation(): UserLocation? {
    try {
      locationClient
          .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
          .addOnSuccessListener(DIRECT_EXECUTOR) { location ->
            if (location != null) {
              return@addOnSuccessListener
            } else {
              throw AppException.UserLocationError("Location was unavailable.")
            }
          }
          .addOnFailureListener(DIRECT_EXECUTOR) { e ->
            throw AppException.UserLocationError(cause = e)
          }
    } catch (e: SecurityException) {
      throw AppException.PermissionsError(cause = e)
    }
    return null
  }

  companion object {
    val DIRECT_EXECUTOR = Executor { command -> command.run() }
  }
}
