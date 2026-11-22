// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.model.map

import android.location.Address
import android.location.Geocoder
import com.artista.artista.model.error.AppException
import java.util.Locale
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowGeocoder

/**
 * Unit tests for [AddressRepositoryGeocoder].
 *
 * They run on Android 14, so they go through the listener lookup of Android 13 and later. The
 * blocking lookup used below Android 13 would need a second Robolectric image and stays untested.
 *
 * @author Timz3rr
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AddressRepositoryGeocoderTest {

  private val geocoder = Geocoder(RuntimeEnvironment.getApplication())
  private val repository = AddressRepositoryGeocoder(geocoder)

  /** Checks that the full address of the closest match is returned. */
  @Test
  fun getAddress_returnsFirstAddressLine() = runTest {
    shadowOf(geocoder).setFromLocation(listOf(address(MUSEE_RODIN_ADDRESS), address("Elsewhere")))

    assertEquals(MUSEE_RODIN_ADDRESS, repository.getAddress(MUSEE_RODIN))
  }

  /**
   * Checks that the latitude and longitude reach the geocoder in the right order: the longitude
   * used here is not a valid latitude, so the Robolectric geocoder rejects a swapped call.
   */
  @Test
  fun getAddress_passesLatitudeAndLongitudeInOrder() = runTest {
    shadowOf(geocoder).setFromLocation(listOf(address(BEIJING_ADDRESS)))

    assertEquals(BEIJING_ADDRESS, repository.getAddress(BEIJING))
  }

  /** Checks that a point without any known address throws [AppException.NotFound]. */
  @Test
  fun getAddress_withoutResult_throwsNotFound() = runTest {
    shadowOf(geocoder).setFromLocation(emptyList())

    assertGetAddressThrows<AppException.NotFound>()
  }

  /** Checks that an address without any readable line throws [AppException.NotFound]. */
  @Test
  fun getAddress_withoutAddressLine_throwsNotFound() = runTest {
    shadowOf(geocoder).setFromLocation(listOf(Address(Locale.US)))

    assertGetAddressThrows<AppException.NotFound>()
  }

  /** Checks that a failure reported by the geocoding service throws [AppException.Network]. */
  @Test
  fun getAddress_whenGeocoderFails_throwsNetwork() = runTest {
    shadowOf(geocoder).setErrorMessage("Service not available")

    assertGetAddressThrows<AppException.Network>()
  }

  /** Checks that a device without any geocoding service throws [AppException.Unknown]. */
  @Test
  fun getAddress_withoutGeocodingService_throwsUnknown() = runTest {
    ShadowGeocoder.setIsPresent(false)

    assertGetAddressThrows<AppException.Unknown>()
  }

  private fun address(line: String): Address = Address(Locale.US).apply { setAddressLine(0, line) }

  private suspend inline fun <reified T : AppException> assertGetAddressThrows() {
    val error = runCatching { repository.getAddress(MUSEE_RODIN) }.exceptionOrNull()
    assertTrue("Expected ${T::class.simpleName} but got $error", error is T)
  }

  private companion object {
    val MUSEE_RODIN = Coordinate(latitude = 48.8553, longitude = 2.3158)
    const val MUSEE_RODIN_ADDRESS = "77 Rue de Varenne, 75007 Paris, France"
    val BEIJING = Coordinate(latitude = 39.9, longitude = 116.4)
    const val BEIJING_ADDRESS = "Beijing, China"
  }
}
