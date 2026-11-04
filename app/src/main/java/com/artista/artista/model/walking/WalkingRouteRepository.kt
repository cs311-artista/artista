// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.model.walking

import com.artista.artista.model.error.AppException
import com.artista.artista.model.map.Coordinate
import kotlin.time.Duration

/**
 * Retrieves walking routes between two coordinates.
 *
 * @author patrickmcdan
 */
interface WalkingRouteRepository {
  /**
   * Retrieves a walking route.
   *
   * @param origin route starting point
   * @param destination route ending point
   * @return the walking route
   * @throws AppException.NotFound if no walking route exists between the points
   * @throws AppException.Network if the service cannot be reached or is unavailable
   * @throws AppException.InvalidData if the response is malformed or incomplete
   * @throws AppException.Unknown for any other failure, including a missing or rejected API key
   * @author patrickmcdan
   */
  suspend fun getWalkingRoute(
      origin: Coordinate,
      destination: Coordinate,
  ): WalkingRoute
}

/**
 * A walking route with its geometry and travel metrics.
 *
 * @property path route coordinates in travel order
 * @property duration estimated walking time
 * @property distanceMeters route distance rounded to the nearest meter
 * @author patrickmcdan
 */
data class WalkingRoute(
    val path: List<Coordinate>,
    val duration: Duration,
    val distanceMeters: Int,
)
