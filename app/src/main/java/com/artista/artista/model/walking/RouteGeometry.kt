// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.model.walking

import com.artista.artista.model.map.Coordinate
import kotlin.compareTo
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_METERS = 6_371_000.0

/**
 * How far ahead of the current progress, measured along the route, a new location may be matched.
 * Bounding the search keeps a path that loops back near itself from snapping the user far ahead.
 */
const val ROUTE_LOOKAHEAD_METERS = 300.0

/**
 * The result of projecting a point onto one route segment.
 *
 * @property coordinate closest point of the segment to the projected point
 * @property fraction position of [coordinate] along the segment, from 0.0 (start) to 1.0 (end)
 * @property distanceMeters distance between the projected point and [coordinate]
 * @author patrickmcdan
 */
data class SegmentProjection(
    val coordinate: Coordinate,
    val fraction: Double,
    val distanceMeters: Double,
)

/**
 * The user's position along a route.
 *
 * @property segmentIndex index of the segment (`path[i]` to `path[i + 1]`) the user is on
 * @property fraction position along that segment, from 0.0 to 1.0
 * @property position the user's location projected onto the route
 * @property offRouteMeters distance between the raw location and [position]
 * @author patrickmcdan
 */
data class RouteProgress(
    val segmentIndex: Int,
    val fraction: Double,
    val position: Coordinate,
    val offRouteMeters: Double,
)

/**
 * Computes the great-circle distance between two coordinates.
 *
 * @param a first coordinate
 * @param b second coordinate
 * @return the distance in meters
 * @author patrickmcdan
 */
fun distanceMeters(a: Coordinate, b: Coordinate): Double {
  val lat1 = Math.toRadians(a.latitude)
  val lat2 = Math.toRadians(b.latitude)
  val dLat = lat2 - lat1
  val dLon = Math.toRadians(b.longitude - a.longitude)
  val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
  return 2 * EARTH_RADIUS_METERS * asin(sqrt(h))
}

/**
 * Computes the length of a polyline.
 *
 * @param path ordered polyline vertices
 * @return the total length in meters, or 0 when the path has fewer than two points
 * @author patrickmcdan
 */
fun pathLengthMeters(path: List<Coordinate>): Double =
    path.zipWithNext().sumOf { (a, b) -> distanceMeters(a, b) }

/**
 * Projects a point onto a segment.
 *
 * The projection uses a local flat approximation (equirectangular around the point), which is
 * accurate for the short segments of a walking route.
 *
 * @param point the point to project
 * @param start segment start
 * @param end segment end
 * @param minFraction lower bound for the result's fraction, used to forbid moving backwards
 * @return the closest allowed point on the segment
 * @author patrickmcdan
 */
fun projectOntoSegment(
    point: Coordinate,
    start: Coordinate,
    end: Coordinate,
    minFraction: Double = 0.0,
): SegmentProjection {
  val metersPerDegreeLat = EARTH_RADIUS_METERS * PI / 180.0
  val metersPerDegreeLon = metersPerDegreeLat * cos(Math.toRadians(point.latitude))
  val bx = (end.longitude - start.longitude) * metersPerDegreeLon
  val by = (end.latitude - start.latitude) * metersPerDegreeLat
  val px = (point.longitude - start.longitude) * metersPerDegreeLon
  val py = (point.latitude - start.latitude) * metersPerDegreeLat
  val lengthSquared = bx * bx + by * by
  val raw = if (lengthSquared == 0.0) 0.0 else (px * bx + py * by) / lengthSquared
  val fraction = raw.coerceIn(minFraction.coerceIn(0.0, 1.0), 1.0)
  // Return the exact vertex at the ends so trimmed paths never contain near-duplicate points.
  val coordinate =
      when (fraction) {
        0.0 -> start
        1.0 -> end
        else ->
            Coordinate(
                start.latitude + fraction * (end.latitude - start.latitude),
                start.longitude + fraction * (end.longitude - start.longitude),
            )
      }
  return SegmentProjection(coordinate, fraction, distanceMeters(point, coordinate))
}

/**
 * Finds the user's progress along a route, never moving backwards from [previous].
 *
 * Only segments from the previous progress onwards, within [lookaheadMeters] along the route, are
 * considered. This keeps progress monotonic even when the route intersects or loops.
 *
 * @param path ordered route vertices
 * @param location the user's current location
 * @param previous the last known progress, or null at the start of a route
 * @param lookaheadMeters how far along the route, from the previous position, a match is allowed
 * @return the new progress, or null when the path has fewer than two points
 * @author patrickmcdan
 */
fun advanceProgress(
    path: List<Coordinate>,
    location: Coordinate,
    previous: RouteProgress? = null,
    lookaheadMeters: Double = ROUTE_LOOKAHEAD_METERS,
): RouteProgress? {
  if (path.size < 2) return null
  val firstSegment = min(previous?.segmentIndex ?: 0, path.size - 2)
  var best: RouteProgress? = null
  var scanned = 0.0
  for (i in firstSegment until path.size - 1) {
    val minFraction = if (i == firstSegment) previous?.fraction ?: 0.0 else 0.0
    val projection = projectOntoSegment(location, path[i], path[i + 1], minFraction)
    // Strict comparison: on a tie the earlier segment wins, which favors not skipping ahead.
    if (best == null || projection.distanceMeters < best.offRouteMeters) {
      best = RouteProgress(i, projection.fraction, projection.coordinate, projection.distanceMeters)
    }
    // Measure the window from the previous position: on the first segment only the part ahead of
    // it counts, so a segment longer than the lookahead cannot stop progress at its end.
    scanned += distanceMeters(path[i], path[i + 1]) * (1.0 - minFraction)
    if (scanned > lookaheadMeters) break
  }
  return best
}

/**
 * Removes the walked part of a path.
 *
 * @param path ordered route vertices
 * @param progress the user's progress along [path]
 * @return the remaining path, starting at the projected user position
 * @author patrickmcdan
 */
fun trimPath(path: List<Coordinate>, progress: RouteProgress): List<Coordinate> {
  val rest = path.drop(progress.segmentIndex + 1)
  val trimmed =
      if (rest.firstOrNull() == progress.position) rest else listOf(progress.position) + rest
  return if (trimmed.size > 2) trimmed else listOf(progress.position, path.last())
}
