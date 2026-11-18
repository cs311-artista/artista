// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.artista.artista.model.location

import android.os.Looper
import com.artista.artista.model.error.AppException
import com.artista.artista.model.map.UserLocation
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Provides the user's current location.
 *
 * @param locationClient the client used to fetch the user's location
 * @author 5kyPhy
 */
class UserLocationProvider(private val locationClient: FusedLocationProviderClient) :
    LocationProvider {
  companion object {
    private const val LOCATION_ACQUISITION_TIMEOUT = 10_000L
    private const val LOCATION_CACHE_MAX_AGE = 30_000L

    private const val UPDATE_INTERVAL_MS = 3_000L
    // Skips tiny GPS wobbles so a standing user does not trigger needless route recomputation.
    private const val MIN_UPDATE_DISTANCE_METERS = 5f
  }

  /**
   * Returns the user's current location.
   *
   * @return the user's current location
   * @throws [AppException.UserLocationError] if the location is unavailable
   * @throws [AppException.PermissionsError] if the user has not granted the necessary permissions
   * @author 5kyPhy
   */
  override suspend fun getUserLocation(): UserLocation {
    val cts = CancellationTokenSource()
    try {
      val request =
          CurrentLocationRequest.Builder()
              .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
              .setDurationMillis(LOCATION_ACQUISITION_TIMEOUT)
              .setMaxUpdateAgeMillis(LOCATION_CACHE_MAX_AGE)
              .build()
      val location =
          locationClient.getCurrentLocation(request, cts.token).await()
              ?: throw AppException.UserLocationError("Location was unavailable.")
      return UserLocation(location.latitude, location.longitude)
    } catch (e: AppException) {
      throw e
    } catch (e: CancellationException) {
      throw e
    } catch (e: SecurityException) {
      throw AppException.PermissionsError("Permissions are not granted.", e)
    } catch (e: Exception) {
      throw AppException.UserLocationError("Failed to get user location.", e)
    } finally {
      cts.cancel()
    }
  }

  override fun locationUpdates(): Flow<UserLocation> = callbackFlow {
    val request =
        LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                UPDATE_INTERVAL_MS,
            )
            .setMinUpdateDistanceMeters(MIN_UPDATE_DISTANCE_METERS)
            .build()

    val callback =
        object : LocationCallback() {
          override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { trySend(UserLocation(it.latitude, it.longitude)) }
          }
        }

    try {
      locationClient
          .requestLocationUpdates(request, callback, Looper.getMainLooper())
          .addOnFailureListener {
            close(
                AppException.UserLocationError(
                    "Failed to start location updates.",
                    it,
                ),
            )
          }
    } catch (e: SecurityException) {
      close(AppException.PermissionsError("Permissions are not granted.", e))
    }

    // Unregister the callback when collection is cancelled or the flow closes.
    awaitClose { locationClient.removeLocationUpdates(callback) }
  }
}
