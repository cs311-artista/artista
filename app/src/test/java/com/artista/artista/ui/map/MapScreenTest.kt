// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.map

import android.Manifest
import androidx.compose.ui.graphics.toArgb
import com.artista.artista.model.map.UserLocation
import com.artista.artista.resources.C
import com.artista.artista.ui.theme.ArtistaColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests camera positioning behavior used by the map screen.
 *
 * @author Felix Burchardt
 */
class MapScreenTest {

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

    assertEquals(C.Constants.DEFAULT_MAP_LAT, camera.target.latitude, COORDINATE_TOLERANCE)
    assertEquals(C.Constants.DEFAULT_MAP_LONG, camera.target.longitude, COORDINATE_TOLERANCE)
    assertEquals(C.Constants.DEFAULT_MAP_ZOOM, camera.zoom)
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

  /** Verifies that saved artwork markers use the shared blue marker color. */
  @Test
  fun markerType_savedArtwork_usesSavedArtworkColor() {
    assertEquals(ArtistaColors.SavedArtworkMarker.toArgb(), MarkerType.SavedArtwork.color)
  }

  /** Verifies that remote artwork markers use the shared red marker color. */
  @Test
  fun markerType_remoteArtwork_usesRemoteArtworkColor() {
    assertEquals(ArtistaColors.RemoteArtworkMarker.toArgb(), MarkerType.RemoteArtwork.color)
  }

  /** Verifies that saved artwork markers expose the saved artwork test tag. */
  @Test
  fun markerTag_savedArtwork_usesSavedArtworkTag() {
    assertEquals(MapScreenTestTags.SAVED_ARTWORK_MARKER, markerTag(MarkerType.SavedArtwork))
  }

  /** Verifies that remote artwork markers expose the remote artwork test tag. */
  @Test
  fun markerTag_remoteArtwork_usesRemoteArtworkTag() {
    assertEquals(MapScreenTestTags.REMOTE_ARTWORK_MARKER, markerTag(MarkerType.RemoteArtwork))
  }
}
