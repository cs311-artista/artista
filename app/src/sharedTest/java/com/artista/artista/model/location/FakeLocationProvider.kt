// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.artista.artista.model.location

import com.artista.artista.model.map.UserLocation
import com.artista.artista.resources.C
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

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
   * [C.Constants.DEFAULT_MAP_LONG] or throws the provided error.
   */
  override suspend fun getUserLocation(): UserLocation {
    errorType?.let { throw it }
    return providedLocation
        ?: UserLocation(C.Constants.DEFAULT_MAP_LAT, C.Constants.DEFAULT_MAP_LONG)
  }

  /** Locations emitted here reach whoever is collecting [locationUpdates]. */
  val updates = MutableSharedFlow<UserLocation>(extraBufferCapacity = 16)

  /** Number of collectors currently subscribed to [locationUpdates], to check start and stop. */
  var activeCollectors = 0
    private set

  override fun locationUpdates(): Flow<UserLocation> = flow {
    errorType?.let { throw it }
    emitAll(updates)
  }
      .onStart { activeCollectors++ }
      .onCompletion { activeCollectors-- }
}
