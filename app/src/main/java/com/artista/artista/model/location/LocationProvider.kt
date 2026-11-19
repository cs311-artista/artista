// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.artista.artista.model.location

import com.artista.artista.model.error.AppException
import com.artista.artista.model.map.UserLocation
import kotlinx.coroutines.flow.Flow

interface LocationProvider {

  /**
   * Returns the user's current location.
   *
   * @return the user's current location
   * @throws [AppException.UserLocationError] if the location is unavailable
   * @throws [AppException.PermissionsError] if the user has not granted the necessary permissions
   * @author 5kyPhy
   */
  suspend fun getUserLocation(): UserLocation

  /**
   * Streams the user's location as it changes.
   *
   * Collecting the flow starts location updates and cancelling the collection stops them, so
   * callers control the GPS lifecycle through coroutine cancellation.
   *
   * @return a flow of the user's locations
   * @throws [AppException.UserLocationError] (thrown on collection) if updates cannot be started
   * @throws [AppException.PermissionsError] (thrown on collection) if the user has not granted the
   *   necessary permissions
   * @author patrickmcdan
   */
  fun locationUpdates(): Flow<UserLocation>
}
