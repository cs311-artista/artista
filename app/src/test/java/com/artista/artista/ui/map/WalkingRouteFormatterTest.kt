// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.map

import java.util.Locale
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests formatting of walking route metrics for display.
 *
 * @author patrickmcdan
 */
class WalkingRouteFormatterTest {

  @Test
  fun durationUnderOneMinute_usesLessThanOneMinuteLabel() {
    assertEquals(
        "<1 min · 850 m",
        formatWalkingRouteSummary(59.seconds, 850, Locale.US),
    )
  }

  @Test
  fun exactlyOneThousandMeters_usesKilometers() {
    assertEquals(
        "12 min · 1.0 km",
        formatWalkingRouteSummary(12.minutes, 1000, Locale.US),
    )
  }

  @Test
  fun kilometerDistance_roundsToOneDecimalUsingLocale() {
    assertEquals(
        "12 min · 1,3 km",
        formatWalkingRouteSummary(12.minutes, 1250, Locale.GERMANY),
    )
  }

  @Test
  fun exactlyOneHour_usesHoursLabel() {
    assertEquals(
        "1 h · 2.0 km",
        formatWalkingRouteSummary(1.hours, 2000, Locale.US),
    )
  }

  @Test
  fun hoursWithRemainingMinutes_includesBothUnits() {
    assertEquals(
        "2 h 15 min · 850 m",
        formatWalkingRouteSummary(2.hours + 15.minutes, 850, Locale.US),
    )
  }
}
