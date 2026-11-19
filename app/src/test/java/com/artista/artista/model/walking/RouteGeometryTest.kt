// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.artista.artista.model.walking

import com.artista.artista.model.map.Coordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the route projection and trimming functions. */
class RouteGeometryTest {
  // About 111 m per 0.001 degree of latitude; the path runs due north.
  private val path =
      listOf(
          Coordinate(46.000, 6.0),
          Coordinate(46.001, 6.0),
          Coordinate(46.002, 6.0),
          Coordinate(46.003, 6.0),
      )

  @Test
  fun projectOntoSegment_snapsToPerpendicularFoot() {
    val result = projectOntoSegment(Coordinate(46.0005, 6.0001), path[0], path[1])
    assertEquals(0.5, result.fraction, 1e-3)
    assertEquals(46.0005, result.coordinate.latitude, 1e-6)
    assertEquals(6.0, result.coordinate.longitude, 1e-9)
  }

  @Test
  fun projectOntoSegment_clampsToSegmentEnds() {
    assertEquals(0.0, projectOntoSegment(Coordinate(45.999, 6.0), path[0], path[1]).fraction, 0.0)
    assertEquals(1.0, projectOntoSegment(Coordinate(46.002, 6.0), path[0], path[1]).fraction, 0.0)
  }

  @Test
  fun projectOntoSegment_respectsMinFraction() {
    val result = projectOntoSegment(Coordinate(46.0002, 6.0), path[0], path[1], minFraction = 0.6)
    assertEquals(0.6, result.fraction, 1e-9)
  }

  @Test
  fun projectOntoSegment_handlesZeroLengthSegment() {
    val result = projectOntoSegment(Coordinate(46.1, 6.1), path[0], path[0])
    assertEquals(path[0], result.coordinate)
  }

  @Test
  fun advanceProgress_returnsNullForShortPath() {
    assertNull(advanceProgress(listOf(path[0]), path[0]))
  }

  @Test
  fun advanceProgress_findsNearestSegment() {
    val progress = advanceProgress(path, Coordinate(46.0015, 6.0))!!
    assertEquals(1, progress.segmentIndex)
    assertEquals(0.5, progress.fraction, 1e-3)
  }

  @Test
  fun advanceProgress_neverMovesBackwards() {
    val ahead = advanceProgress(path, Coordinate(46.0025, 6.0))!!
    val behind = advanceProgress(path, Coordinate(46.0005, 6.0), previous = ahead)!!
    assertEquals(ahead.segmentIndex, behind.segmentIndex)
    assertTrue(behind.fraction >= ahead.fraction)
  }

  @Test
  fun advanceProgress_loopingPathDoesNotResetProgress() {
    // The route returns to its starting point, so the start is also the nearest vertex.
    val loop =
        listOf(
            Coordinate(46.000, 6.000),
            Coordinate(46.001, 6.000),
            Coordinate(46.001, 6.001),
            Coordinate(46.000, 6.001),
            Coordinate(46.000, 6.000),
        )
    val midway = advanceProgress(loop, Coordinate(46.001, 6.0005))!!
    val atStartAgain = advanceProgress(loop, Coordinate(46.0, 6.0), previous = midway)!!
    assertTrue(atStartAgain.segmentIndex >= midway.segmentIndex)
  }

  @Test
  fun advanceProgress_ignoresMatchesBeyondLookahead() {
    val far = advanceProgress(path, Coordinate(46.003, 6.0), lookaheadMeters = 50.0)!!
    assertEquals(0, far.segmentIndex)
  }

  @Test
  fun trimPath_startsAtProjectedPosition() {
    val progress = advanceProgress(path, Coordinate(46.0015, 6.0))!!
    val trimmed = trimPath(path, progress)
    assertEquals(progress.position, trimmed.first())
    assertEquals(path.subList(2, 4), trimmed.drop(1))
  }

  @Test
  fun trimPath_doesNotDuplicateVertexWhenOnIt() {
    val progress = advanceProgress(path, path[2])!!
    val trimmed = trimPath(path, progress)
    assertEquals(path.subList(2, 4), trimmed)
  }

  @Test
  fun trimPath_atDestinationKeepsTwoPoints() {
    val progress = advanceProgress(path, path.last(), previous = advanceProgress(path, path[2]))!!
    assertEquals(2, trimPath(path, progress).size)
  }

  @Test
  fun trimmedLength_shrinksAsUserWalks() {
    val full = pathLengthMeters(path)
    val progress = advanceProgress(path, Coordinate(46.0015, 6.0))!!
    val remaining = pathLengthMeters(trimPath(path, progress))
    // 1.5 of the 3 equal segments are left.
    assertEquals(full / 2, remaining, 1.0)
  }

  @Test
  fun advanceProgress_movesOntoTheNextSegment_whenTheCurrentOneIsLongerThanTheLookahead() {
    // The first segment is about 556 m, far longer than the 100 m lookahead.
    val path = listOf(Coordinate(46.000, 6.0), Coordinate(46.005, 6.0), Coordinate(46.005, 6.01))
    val nearTheCorner = advanceProgress(path, Coordinate(46.0049, 6.0), lookaheadMeters = 100.0)!!
    assertEquals(0, nearTheCorner.segmentIndex)

    val pastTheCorner =
        advanceProgress(
            path,
            Coordinate(46.005, 6.0005),
            previous = nearTheCorner,
            lookaheadMeters = 100.0,
        )!!

    assertEquals(1, pastTheCorner.segmentIndex)
  }
}
