package com.artista.artista.model.location

import com.artista.artista.model.error.AppException
import com.artista.artista.model.map.UserLocation

interface LocationProvider {

  /**
   * Returns the user's current location, or null if it is unavailable.
   *
   * @return the user's current location, or null if it is unavailable
   * @throws [AppException.UserLocationError] if the location is unavailable
   * @throws [AppException.PermissionsError] if the user has not granted the necessary permissions
   * @author 5kyPhy
   */
  fun getUserLocation(): UserLocation?
}
