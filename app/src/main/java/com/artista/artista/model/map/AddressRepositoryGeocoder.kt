// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.model.map

import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import com.artista.artista.model.error.AppException
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Implementation of [AddressRepository] backed by the Android [Geocoder].
 *
 * The Geocoder needs no API key and answers in the device language. It relies on a geocoding
 * service provided by the system (Google Play Services on most phones), so its absence is reported
 * as [AppException.Unknown].
 *
 * @param geocoder the Android geocoder to query (the Android object that translates coordinates
 *   into addresses)
 * @param ioDispatcher dispatcher for the blocking lookup used before Android 13, used with
 *   withContext(ioDispatcher). That lookup can be long, so it must not run on the main thread,
 *   where it would freeze the app. It is a parameter rather than hard-coded, like in the other
 *   remote repositories, so the caller can choose the thread.
 * @param sdkInt Android version of the device, which decides which lookup is used. It is a
 *   parameter rather than read from Build.VERSION.SDK_INT, so tests running on one Android version
 *   can also exercise the lookup of the other versions.
 * @author Timz3rr
 */
class AddressRepositoryGeocoder(
    private val geocoder: Geocoder,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) : AddressRepository {
  override suspend fun getAddress(coordinate: Coordinate): String {
    if (!Geocoder.isPresent()) {
      throw AppException.Unknown("No geocoding service on this device")
    }
    val addresses =
        try {
          if (hasListenerLookup()) {
            lookUpWithListener(coordinate)
          } else {
            lookUpBlocking(coordinate)
          }
        } catch (exception: CancellationException) {
          throw exception
        } catch (exception: AppException) {
          throw exception
        } catch (exception: IOException) {
          throw AppException.Network("Geocoding service unreachable", exception)
        } catch (exception: Exception) {
          throw AppException.Unknown("Geocoding failed unexpectedly", exception)
        }
    return firstAddressLine(addresses)
  }

  // Lint only recognises a direct check of Build.VERSION.SDK_INT, so this annotation tells it that
  // the injected sdkInt check also guards the calls that need Android 13.
  @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
  private fun hasListenerLookup(): Boolean = sdkInt >= Build.VERSION_CODES.TIRAMISU

  // Android 13 and later answer through a listener, so the coroutine is suspended until it calls
  // back. The Geocoder cannot cancel a lookup: if the coroutine is cancelled first, the late answer
  // is simply ignored.
  @RequiresApi(Build.VERSION_CODES.TIRAMISU)
  private suspend fun lookUpWithListener(coordinate: Coordinate): List<Address> =
      suspendCancellableCoroutine { continuation ->
        geocoder.getFromLocation(
            coordinate.latitude,
            coordinate.longitude,
            MAX_RESULTS,
            object : Geocoder.GeocodeListener {
              override fun onGeocode(addresses: MutableList<Address>) {
                continuation.resume(addresses)
              }

              override fun onError(errorMessage: String?) {
                continuation.resumeWithException(
                    AppException.Network("Geocoding failed: $errorMessage")
                )
              }
            },
        )
      }

  // Deprecated since Android 13 because it blocks, but it is the only lookup before that version,
  // so it runs on ioDispatcher to keep the main thread free.
  @Suppress("DEPRECATION")
  private suspend fun lookUpBlocking(coordinate: Coordinate): List<Address> =
      withContext(ioDispatcher) {
        geocoder.getFromLocation(coordinate.latitude, coordinate.longitude, MAX_RESULTS).orEmpty()
      }

  // Both lookups end here, so an empty result and an address without text are handled once.
  private fun firstAddressLine(addresses: List<Address>): String =
      addresses.firstOrNull()?.getAddressLine(0)
          ?: throw AppException.NotFound("No address known for this coordinate")

  private companion object {
    // Only the closest address is displayed.
    const val MAX_RESULTS = 1
  }
}
