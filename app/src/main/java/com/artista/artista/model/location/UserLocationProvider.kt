package com.artista.artista.model.location

import com.artista.artista.model.error.AppException
import com.artista.artista.model.map.UserLocation
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.concurrent.CancellationException
import kotlinx.coroutines.tasks.await

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
  override suspend fun getUserLocation(): UserLocation? {
    val cts = CancellationTokenSource()
    try {
      val location =
          locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
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
}
