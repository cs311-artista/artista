package com.artista.artista.model.location

import com.artista.artista.model.map.UserLocation
import com.artista.artista.resources.C

/**
 * A fake implementation of [LocationProvider] for testing purposes.
 *
 * @param errorType the type of error to throw, if any.
 * @param providedLocation the location to provide, if any.
 * @author 5kyPhy
 */
class FakeLocationProvider(
    val errorType: Throwable? = null,
    val providedLocation: UserLocation? = null,
) : LocationProvider {
  /**
   * Returns the [UserLocation] associated to [C.Constants.DEFAULT_MAP_LAT] and
   * [C.Constants.DEFAULT_MAP_LONG], null if failing was set or throws the provided error.
   */
  override suspend fun getUserLocation(): UserLocation? {
    errorType?.let { throw it }
    return providedLocation
        ?: UserLocation(C.Constants.DEFAULT_MAP_LAT, C.Constants.DEFAULT_MAP_LONG)
  }
}
