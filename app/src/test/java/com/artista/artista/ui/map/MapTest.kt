// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.map

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests camera positioning behavior used by the map screen.
 *
 * @author Felix Burchardt
 */
class MapTest {

  private companion object {
    const val COORDINATE_TOLERANCE = 0.0001
  }

  /**
   * Verifies that the map starts centered on the configured default location.
   *
   * @author Felix Burchardt
   */
  @Test
  fun cameraPositionFor_withoutUserLocation_usesDefaultCenter() {
    val camera = cameraPositionFor(null)

    assertEquals(DEFAULT_MAP_LATITUDE, camera.target.latitude, COORDINATE_TOLERANCE)
    assertEquals(DEFAULT_MAP_LONGITUDE, camera.target.longitude, COORDINATE_TOLERANCE)
    assertEquals(DEFAULT_MAP_ZOOM, camera.zoom)
  }

  /**
   * Verifies that an available user location becomes the camera center.
   *
   * @author Felix Burchardt
   */
  @Test
  fun cameraPositionFor_withUserLocation_centersOnUser() {
    val userLocation = UserLocation(latitude = 46.5201, longitude = 6.6332)
    val camera = cameraPositionFor(userLocation)

    assertEquals(userLocation.latitude, camera.target.latitude, COORDINATE_TOLERANCE)
    assertEquals(userLocation.longitude, camera.target.longitude, COORDINATE_TOLERANCE)
    assertEquals(USER_LOCATION_ZOOM, camera.zoom)
  }

  /** Verifies that permission requests include fine and coarse location access. */
  @Test
  fun requiredLocationPermissions_includeFineAndCoarseAccess() {
    assertEquals(2, REQUIRED_LOCATION_PERMISSIONS.size)
    assertTrue(REQUIRED_LOCATION_PERMISSIONS.contains(Manifest.permission.ACCESS_FINE_LOCATION))
    assertTrue(REQUIRED_LOCATION_PERMISSIONS.contains(Manifest.permission.ACCESS_COARSE_LOCATION))
  }
}
