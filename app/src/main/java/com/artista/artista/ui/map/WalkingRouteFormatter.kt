// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.map

import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Formats walking route duration and distance for display.
 *
 * @param duration estimated walking duration
 * @param distanceMeters route distance in meters
 * @param locale locale used for decimal separators
 * @return formatted route summary
 * @author patrickmcdan
 */
fun formatWalkingRouteSummary(
    duration: Duration,
    distanceMeters: Int,
    locale: Locale = Locale.getDefault(),
): String {
  val totalMinutes = duration.inWholeMinutes
  val durationText =
      when {
        duration < 60.seconds -> "<1 min"
        totalMinutes < 60 -> "$totalMinutes min"
        else -> {
          val hours = duration.inWholeHours
          val remainingMinutes = totalMinutes % 60
          if (remainingMinutes == 0L) "$hours h" else "$hours h $remainingMinutes min"
        }
      }
  val distanceText =
      if (distanceMeters < 1000) {
        "$distanceMeters m"
      } else {
        String.format(locale, "%.1f km", distanceMeters / 1000.0)
      }
  return "$durationText · $distanceText"
}
