// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.map

import android.location.Location
import com.artista.artista.R
import com.artista.artista.model.artwork.ArtworkRepositoryProvider
import com.artista.artista.model.map.UserLocation
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.tasks.Tasks
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests state updates exposed by [MapViewModel].
 *
 * @author Felix Burchardt
 */
class MapViewModelTest {

  /**
   * Verifies that a location update exposes the device coordinates.
   *
   * @author Felix Burchardt
   */
  @Test
  fun updateUserLocation_storesLocationAndClearsError() {
    val viewModel = createViewModel()
    viewModel.setLocationError(R.string.map_location_acquisition_error)

    val location = UserLocation(latitude = 46.5201, longitude = 6.6332)
    viewModel.updateUserLocation(location)

    assertEquals(location, viewModel.uiState.userLocation)
    assertNull(viewModel.uiState.errorMsg)
  }

  /**
   * Verifies that location errors are exposed without discarding a known location.
   *
   * @author Felix Burchardt
   */
  @Test
  fun setLocationError_storesMessageAndPreservesLocation() {
    val viewModel = createViewModel()
    val location = UserLocation(latitude = 46.5201, longitude = 6.6332)
    viewModel.updateUserLocation(location)

    viewModel.setLocationError(R.string.map_location_unavailable_error)

    assertEquals(location, viewModel.uiState.userLocation)
    assertEquals(R.string.map_location_unavailable_error, viewModel.uiState.errorMsg)
  }

  /** Verifies that a successful location request stores the acquired coordinates. */
  @Test
  fun acquireLocation_withPermission_storesAcquiredLocation() {
    val viewModel = createViewModel()
    val locationClient = fakeLocationClient(LOCATION)

    viewModel.acquireLocation(locationClient)

    assertEquals(
        UserLocation(LOCATION.latitude, LOCATION.longitude),
        viewModel.uiState.userLocation,
    )
    assertNull(viewModel.uiState.errorMsg)
  }

  /** Verifies that a null location exposes the unavailable-location error. */
  @Test
  fun acquireLocation_withNoLocation_exposesUnavailableError() {
    val viewModel = createViewModel()
    val locationClient = fakeLocationClient(null)

    viewModel.acquireLocation(locationClient)

    assertEquals(R.string.map_location_unavailable_error, viewModel.uiState.errorMsg)
  }

  /** Verifies that a failed location request exposes the acquisition error. */
  @Test
  fun acquireLocation_whenRequestFails_exposesAcquisitionError() {
    val viewModel = createViewModel()
    val locationClient = fakeLocationClientFailure()

    viewModel.acquireLocation(locationClient)

    assertEquals(R.string.map_location_acquisition_error, viewModel.uiState.errorMsg)
  }

  private fun createViewModel(): MapViewModel =
      MapViewModel(
          ArtworkRepositoryProvider.mockRepository,
          ArtworkRepositoryProvider.mockRepository,
      )

  private fun fakeLocationClient(location: Location?): FusedLocationProviderClient =
      Proxy.newProxyInstance(
          FusedLocationProviderClient::class.java.classLoader,
          arrayOf(FusedLocationProviderClient::class.java),
      ) { _, method, _ ->
        if (method.name == "getCurrentLocation") Tasks.forResult(location)
        else throw UnsupportedOperationException(method.name)
      } as FusedLocationProviderClient

  private fun fakeLocationClientFailure(): FusedLocationProviderClient =
      Proxy.newProxyInstance(
          FusedLocationProviderClient::class.java.classLoader,
          arrayOf(FusedLocationProviderClient::class.java),
      ) { _, method, _ ->
        if (method.name == "getCurrentLocation") {
          Tasks.forException<Location>(LOCATION_EXCEPTION)
        } else {
          throw UnsupportedOperationException(method.name)
        }
      } as FusedLocationProviderClient

  private companion object {
    val LOCATION =
        Location("test").apply {
          latitude = 46.5201
          longitude = 6.6332
        }
    val LOCATION_EXCEPTION = IllegalStateException("location failure")
  }
}
