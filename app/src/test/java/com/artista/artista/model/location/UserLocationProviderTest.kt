package com.artista.artista.model.location

import android.location.Location
import com.artista.artista.model.error.AppException
import com.artista.artista.model.map.UserLocation
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserLocationProviderTest {

  /** Builds a client whose getCurrentLocation(Int, CancellationToken) is handled by [handler]. */
  private fun fakeClient(
      handler: (priority: Int, token: CancellationToken) -> Task<Location>
  ): FusedLocationProviderClient =
      Proxy.newProxyInstance(
          javaClass.classLoader,
          arrayOf(FusedLocationProviderClient::class.java),
          InvocationHandler { _, method, args ->
            if (method.name == "getCurrentLocation" && args[0] is Int) {
              handler(args[0] as Int, args[1] as CancellationToken)
            } else {
              throw UnsupportedOperationException("Unexpected call: ${method.name}")
            }
          },
      ) as FusedLocationProviderClient

  private fun providerReturning(task: Task<Location>) =
      UserLocationProvider(fakeClient { _, _ -> task })

  @Test
  fun `returns mapped location on success`() = runTest {
    val location =
        Location("test").apply {
          latitude = 46.72
          longitude = 6.53
        }
    val provider = providerReturning(Tasks.forResult(location))

    assertEquals(UserLocation(46.72, 6.53), provider.getUserLocation())
  }

  @Test
  fun `requests high accuracy`() = runTest {
    var requestedPriority = -1
    val provider =
        UserLocationProvider(
            fakeClient { priority, _ ->
              requestedPriority = priority
              Tasks.forResult(Location("test"))
            }
        )

    provider.getUserLocation()

    assertEquals(Priority.PRIORITY_HIGH_ACCURACY, requestedPriority)
  }

  @Test
  fun `throws UserLocationError when location is null`() = runTest {
    val provider = providerReturning(Tasks.forResult(null))

    assertThrowsSuspend<AppException.UserLocationError> { provider.getUserLocation() }
  }

  @Test
  fun `wraps task failure in UserLocationError`() = runTest {
    val boom = RuntimeException("boom")
    val provider = providerReturning(Tasks.forException(boom))

    val e = assertThrowsSuspend<AppException.UserLocationError> { provider.getUserLocation() }
    assertSame(boom, e.cause)
  }

  @Test
  fun `maps SecurityException thrown synchronously to PermissionsError`() = runTest {
    val denied = SecurityException("no permission")
    val provider = UserLocationProvider(fakeClient { _, _ -> throw denied })

    val e = assertThrowsSuspend<AppException.PermissionsError> { provider.getUserLocation() }
    assertSame(denied, e.cause)
  }

  @Test
  fun `maps SecurityException from the task to PermissionsError`() = runTest {
    val provider = providerReturning(Tasks.forException(SecurityException("denied")))

    assertThrowsSuspend<AppException.PermissionsError> { provider.getUserLocation() }
  }

  @Test
  fun `cancels the token when the caller is cancelled`() = runTest {
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
