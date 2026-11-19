// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.artista.artista.model.location

import android.location.Location
import android.os.Looper
import com.artista.artista.model.error.AppException
import com.artista.artista.model.map.UserLocation
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserLocationProviderTest {

  /**
   * Builds a client whose getCurrentLocation(CurrentLocationRequest, CancellationToken) is handled
   * by [handler].
   */
  private fun fakeClient(
      handler: (request: CurrentLocationRequest, token: CancellationToken) -> Task<Location>
  ): FusedLocationProviderClient =
      Proxy.newProxyInstance(
          javaClass.classLoader,
          arrayOf(FusedLocationProviderClient::class.java),
          InvocationHandler { _, method, args ->
            if (method.name == "getCurrentLocation" && args[0] is CurrentLocationRequest) {
              handler(args[0] as CurrentLocationRequest, args[1] as CancellationToken)
            } else {
              throw UnsupportedOperationException("Unexpected call: ${method.name}")
            }
          },
      ) as FusedLocationProviderClient

  private fun providerReturning(task: Task<Location>) =
      UserLocationProvider(fakeClient { _, _ -> task })

  @Test
  fun getUserLocation_returnsMappedLocation_onSuccess() = runTest {
    val location =
        Location("test").apply {
          latitude = 46.72
          longitude = 6.53
        }
    val provider = providerReturning(Tasks.forResult(location))

    assertEquals(UserLocation(46.72, 6.53), provider.getUserLocation())
  }

  @Test
  fun getUserLocation_requestsHighAccuracy() = runTest {
    var requestedPriority = -1
    val provider =
        UserLocationProvider(
            fakeClient { request, _ ->
              requestedPriority = request.priority
              Tasks.forResult(Location("test"))
            }
        )

    provider.getUserLocation()

    assertEquals(Priority.PRIORITY_HIGH_ACCURACY, requestedPriority)
  }

  @Test
  fun getUserLocation_whenLocationIsNull_throwsUserLocationError() = runTest {
    val provider = providerReturning(Tasks.forResult(null))

    assertThrowsSuspend<AppException.UserLocationError> { provider.getUserLocation() }
  }

  @Test
  fun getUserLocation_wrapsTaskFailureInUserLocationError() = runTest {
    val boom = RuntimeException("boom")
    val provider = providerReturning(Tasks.forException(boom))

    val e = assertThrowsSuspend<AppException.UserLocationError> { provider.getUserLocation() }
    assertSame(boom, e.cause)
  }

  @Test
  fun getUserLocation_whenSecurityExceptionIsHit_throwsSynchronouslyPermissionsError() = runTest {
    val denied = SecurityException("no permission")
    val provider = UserLocationProvider(fakeClient { _, _ -> throw denied })

    val e = assertThrowsSuspend<AppException.PermissionsError> { provider.getUserLocation() }
    assertSame(denied, e.cause)
  }

  @Test
  fun getUserLocation_mapsSecurityException_toPermissionsError() = runTest {
    val provider = providerReturning(Tasks.forException(SecurityException("denied")))

    assertThrowsSuspend<AppException.PermissionsError> { provider.getUserLocation() }
  }

  @Test
  fun getUserLocation_whenTokenIsCancelled_cancelsTokenCorrectly() = runTest {
    var token: CancellationToken? = null
    val tcs = TaskCompletionSource<Location>() // never completed
    val provider =
        UserLocationProvider(
            fakeClient { _, t ->
              token = t
              tcs.task
            }
        )

    val job = launch(UnconfinedTestDispatcher(testScheduler)) { provider.getUserLocation() }
    job.cancelAndJoin()

    assertTrue(token!!.isCancellationRequested)
  }

  @Test
  fun locationUpdates_emitsEachReportedLocation() = runTest {
    var callback: LocationCallback? = null
    val provider =
        UserLocationProvider(
            updatesClient(
                onRequest = { _, c ->
                  callback = c
                  Tasks.forResult<Any?>(null)
                }
            )
        )
    val received = mutableListOf<UserLocation>()
    val job =
        launch(UnconfinedTestDispatcher(testScheduler)) {
          provider.locationUpdates().toList(received)
        }

    callback!!.onLocationResult(LocationResult.create(listOf(locationAt(46.72, 6.53))))
    callback!!.onLocationResult(LocationResult.create(listOf(locationAt(46.73, 6.54))))
    runCurrent()

    assertEquals(listOf(UserLocation(46.72, 6.53), UserLocation(46.73, 6.54)), received)
    job.cancelAndJoin()
  }

  @Test
  fun locationUpdates_requestsHighAccuracyEveryThreeSecondsAfterFiveMeters() = runTest {
    var requested: LocationRequest? = null
    val provider =
        UserLocationProvider(
            updatesClient(
                onRequest = { request, _ ->
                  requested = request
                  Tasks.forResult<Any?>(null)
                }
            )
        )

    val job =
        launch(UnconfinedTestDispatcher(testScheduler)) { provider.locationUpdates().collect {} }

    assertEquals(Priority.PRIORITY_HIGH_ACCURACY, requested!!.priority)
    assertEquals(3_000L, requested!!.intervalMillis)
    assertEquals(5f, requested!!.minUpdateDistanceMeters, 0f)
    job.cancelAndJoin()
  }

  @Test
  fun locationUpdates_whenCollectionIsCancelled_removesTheCallback() = runTest {
    var registered: LocationCallback? = null
    var removed: LocationCallback? = null
    val provider =
        UserLocationProvider(
            updatesClient(
                onRequest = { _, c ->
                  registered = c
                  Tasks.forResult<Any?>(null)
                },
                onRemove = { removed = it },
            )
        )
    val job =
        launch(UnconfinedTestDispatcher(testScheduler)) { provider.locationUpdates().collect {} }
    assertNull(removed)

    job.cancelAndJoin()

    assertSame(registered, removed)
  }

  @Test
  fun locationUpdates_whenTheRequestFails_throwsUserLocationError() = runTest {
    val boom = RuntimeException("boom")
    val provider =
        UserLocationProvider(updatesClient(onRequest = { _, _ -> Tasks.forException<Any?>(boom) }))

    val outcome =
        async(UnconfinedTestDispatcher(testScheduler)) {
          runCatching { provider.locationUpdates().first() }
        }
    // The failure listener is posted to the main looper, which Robolectric keeps paused.
    shadowOf(Looper.getMainLooper()).idle()

    val error = outcome.await().exceptionOrNull()
    assertTrue(error is AppException.UserLocationError)
    assertTrue(error!!.hasInCauseChain(boom))
  }

  @Test
  fun locationUpdates_whenPermissionIsMissing_throwsPermissionsError() = runTest {
    val denied = SecurityException("no permission")
    val provider = UserLocationProvider(updatesClient(onRequest = { _, _ -> throw denied }))

    val e =
        assertThrowsSuspend<AppException.PermissionsError> { provider.locationUpdates().first() }
    assertTrue(e.hasInCauseChain(denied))
  }

  @Test
  fun locationUpdates_whenPermissionFailsAsynchronously_throwsPermissionsError() = runTest {
    val denied = SecurityException("no permission, asynchronously")
    val provider =
        UserLocationProvider(
            updatesClient(onRequest = { _, _ -> Tasks.forException<Void>(denied) })
        )

    val result = async { runCatching { provider.locationUpdates().first() }.exceptionOrNull() }
    advanceUntilIdle()
    shadowOf(Looper.getMainLooper()).idle()

    val e = result.await()
    assertTrue(e is AppException.PermissionsError)
    assertTrue(e!!.hasInCauseChain(denied))
  }

  /**
   * Builds a client whose requestLocationUpdates is handled by [onRequest]. removeLocationUpdates
   * reports the removed callback to [onRemove].
   */
  private fun updatesClient(
      onRequest: (LocationRequest, LocationCallback) -> Task<*>,
      onRemove: (LocationCallback) -> Unit = {},
  ): FusedLocationProviderClient =
      Proxy.newProxyInstance(
          javaClass.classLoader,
          arrayOf(FusedLocationProviderClient::class.java),
      ) { _, method, args ->
        when (method.name) {
          "requestLocationUpdates" if args[0] is LocationRequest && args[1] is LocationCallback ->
              onRequest(args[0] as LocationRequest, args[1] as LocationCallback)

          "removeLocationUpdates" if args[0] is LocationCallback -> {
            onRemove(args[0] as LocationCallback)
            Tasks.forResult<Any?>(null)
          }

          else -> throw UnsupportedOperationException("Unexpected call: ${method.name}")
        }
      } as FusedLocationProviderClient

  private fun locationAt(latitude: Double, longitude: Double) =
      Location("test").apply {
        this.latitude = latitude
        this.longitude = longitude
      }

  /** Exceptions can be copied when they cross a coroutine boundary, so look through the chain. */
  private fun Throwable.hasInCauseChain(target: Throwable) =
      generateSequence(this) { it.cause }.any { it === target }

  /** Runs [block] and returns the thrown exception if it is a [T]; fails otherwise. */
  private suspend inline fun <reified T : Throwable> assertThrowsSuspend(
      block: suspend () -> Unit
  ): T {
    try {
      block()
    } catch (e: Throwable) {
      if (e is T) return e
      throw e // a different exception: let it fail the test with its original stack trace
    }
    throw AssertionError("Expected ${T::class.java.simpleName} but nothing was thrown")
  }
}
