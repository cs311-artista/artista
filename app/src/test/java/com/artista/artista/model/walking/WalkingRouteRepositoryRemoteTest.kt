// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.artista.artista.model.walking

import com.artista.artista.model.error.AppException
import com.artista.artista.model.map.Coordinate
import java.io.IOException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for [WalkingRouteRepositoryRemote].
 *
 * @author patrickmcdan
 */
@RunWith(RobolectricTestRunner::class)
class WalkingRouteRepositoryRemoteTest {
  @Test
  fun getWalkingRoute_successParsesRouteAndRequest() = runTest {
    val client = RecordingHttpClient(responseBody = readResponse(SUCCESS_RESPONSE))
    val repository = repository(client)

    val route = repository.getWalkingRoute(ORIGIN, DESTINATION)
    assertEquals(946.9.seconds, route.duration)
    assertEquals(1315, route.distanceMeters)
    assertEquals(71, route.path.size)
    assertEquals(Coordinate(46.516961, 6.628988), route.path.first())
    assertEquals(Coordinate(46.506984, 6.627013), route.path.last())
    val request = requireNotNull(client.lastRequest)
    assertEquals("POST", request.method)
    assertEquals(
        "https://api.openrouteservice.org/v2/directions/foot-walking/geojson",
        request.url.toString(),
    )
    assertEquals("test-key", request.header("Authorization"))
    assertEquals(
        """{"coordinates":[[6.629,46.517],[6.627,46.507]]}""",
        request.body?.let { body -> Buffer().also { buffer -> body.writeTo(buffer) }.readUtf8() },
    )
  }

  @Test
  fun getWalkingRoute_distanceIsRoundedToNearestMeter() = runTest {
    val repository = repository(RecordingHttpClient(responseBody = routeJson(distance = 1315.6)))

    val route = repository.getWalkingRoute(ORIGIN, DESTINATION)

    assertEquals(1316, route.distanceMeters)
  }

  @Test
  fun getWalkingRoute_noRouteCodeThrowsNotFound() = runTest {
    for (code in listOf(2009, 2010, 2004)) {
      val repository =
          repository(RecordingHttpClient(statusCode = 400, responseBody = errorJson(code)))

      assertThrowsSuspend<AppException.NotFound> { repository.getWalkingRoute(ORIGIN, DESTINATION) }
    }
  }

  @Test
  fun getWalkingRoute_realNoRouteResponseThrowsNotFound() = runTest {
    // Real ORS answer (code 2010) captured with curl, so the actual error shape is covered.
    val body = readResponse(FAILURE_RESPONSE)
    val repository = repository(RecordingHttpClient(statusCode = 404, responseBody = body))

    assertThrowsSuspend<AppException.NotFound> { repository.getWalkingRoute(ORIGIN, DESTINATION) }
  }

  @Test
  fun getWalkingRoute_unknownOrsErrorCodeThrowsUnknown() = runTest {
    val repository =
        repository(RecordingHttpClient(statusCode = 400, responseBody = errorJson(9999)))

    assertThrowsSuspend<AppException.Unknown> { repository.getWalkingRoute(ORIGIN, DESTINATION) }
  }

  @Test
  fun getWalkingRoute_nonJsonErrorBodyThrowsUnknown() = runTest {
    val repository = repository(RecordingHttpClient(statusCode = 400, responseBody = "not-json"))

    assertThrowsSuspend<AppException.Unknown> { repository.getWalkingRoute(ORIGIN, DESTINATION) }
  }

  @Test
  fun getWalkingRoute_rejectedApiKeyThrowsUnknown() = runTest {
    for (status in listOf(401, 403)) {
      val repository = repository(RecordingHttpClient(statusCode = status))

      assertThrowsSuspend<AppException.Unknown> { repository.getWalkingRoute(ORIGIN, DESTINATION) }
    }
  }

  @Test
  fun getWalkingRoute_blankApiKeyThrowsUnknownWithoutRequest() = runTest {
    val client = RecordingHttpClient()
    val repository = repository(client, apiKey = "")

    assertThrowsSuspend<AppException.Unknown> { repository.getWalkingRoute(ORIGIN, DESTINATION) }
    assertNull(client.lastRequest)
  }

  @Test
  fun getWalkingRoute_rateLimitThrowsUnknown() = runTest {
    val repository = repository(RecordingHttpClient(statusCode = 429))

    assertThrowsSuspend<AppException.Unknown> { repository.getWalkingRoute(ORIGIN, DESTINATION) }
  }

  @Test
  fun getWalkingRoute_serviceUnavailableThrowsNetwork() = runTest {
    val repository = repository(RecordingHttpClient(statusCode = 503))

    assertThrowsSuspend<AppException.Network> { repository.getWalkingRoute(ORIGIN, DESTINATION) }
  }

  @Test
  fun getWalkingRoute_ioFailureThrowsNetworkWithCause() = runTest {
    val client = OkHttpClient.Builder().addInterceptor { throw IOException("offline") }.build()
    val repository = repository(client)

    val exception =
        assertThrowsSuspend<AppException.Network> {
          repository.getWalkingRoute(ORIGIN, DESTINATION)
        }
    assertNotNull(exception.cause)
  }

  @Test
  fun getWalkingRoute_malformedJsonThrowsInvalidData() = runTest {
    val repository = repository(RecordingHttpClient(responseBody = "{not-json"))

    assertThrowsSuspend<AppException.InvalidData> {
      repository.getWalkingRoute(ORIGIN, DESTINATION)
    }
  }

  @Test
  fun getWalkingRoute_missingFeaturesThrowsInvalidData() = runTest {
    val repository = repository(RecordingHttpClient(responseBody = "{}"))

    assertThrowsSuspend<AppException.InvalidData> {
      repository.getWalkingRoute(ORIGIN, DESTINATION)
    }
  }

  @Test
  fun getWalkingRoute_emptyFeaturesThrowsInvalidData() = runTest {
    val repository = repository(RecordingHttpClient(responseBody = """{"features":[]}"""))

    assertThrowsSuspend<AppException.InvalidData> {
      repository.getWalkingRoute(ORIGIN, DESTINATION)
    }
  }

  @Test
  fun getWalkingRoute_featureWithoutRouteDataThrowsInvalidData() = runTest {
    val repository = repository(RecordingHttpClient(responseBody = """{"features":[{}]}"""))

    assertThrowsSuspend<AppException.InvalidData> {
      repository.getWalkingRoute(ORIGIN, DESTINATION)
    }
  }

  @Test
  fun getWalkingRoute_emptyCoordinatesThrowsInvalidData() = runTest {
    val repository = repository(RecordingHttpClient(responseBody = routeJson(coordinates = "[]")))

    assertThrowsSuspend<AppException.InvalidData> {
      repository.getWalkingRoute(ORIGIN, DESTINATION)
    }
  }

  @Test
  fun getWalkingRoute_cancellationPropagates() = runTest {
    val cancellation = CancellationException("cancelled")
    val client = OkHttpClient.Builder().addInterceptor { throw cancellation }.build()
    val repository = repository(client)

    try {
      repository.getWalkingRoute(ORIGIN, DESTINATION)
      fail("Expected CancellationException")
    } catch (exception: CancellationException) {
      assertEquals(cancellation.message, exception.message)
    }
  }

  private fun repository(
      client: OkHttpClient,
      apiKey: String = "test-key",
  ): WalkingRouteRepositoryRemote =
      WalkingRouteRepositoryRemote(
          client = client,
          apiKey = apiKey,
          ioDispatcher = Dispatchers.IO,
      )

  private fun readResponse(fileName: String): String =
      this::class.java.getResourceAsStream("/ors_responses/$fileName")!!.bufferedReader().use {
        it.readText()
      }

  /** Smallest valid ORS success body, so each test can vary just the field it cares about. */
  private fun routeJson(coordinates: String = "[[6.6,46.5]]", distance: Double = 10.0): String =
      """{"features":[{"geometry":{"coordinates":$coordinates},"properties":{"summary":{"duration":10.0,"distance":$distance}}}]}"""

  private fun errorJson(code: Int): String = """{"error":{"code":$code}}"""

  /** Replaces the network: answers every call with a canned response and records the request. */
  private class RecordingHttpClient(
      private val statusCode: Int = 200,
      private val responseBody: String = "",
  ) : OkHttpClient() {
    var lastRequest: Request? = null
      private set

    private val delegate =
        Builder()
            .addInterceptor { chain ->
              lastRequest = chain.request()
              Response.Builder()
                  .request(chain.request())
                  .protocol(Protocol.HTTP_1_1)
                  .code(statusCode)
                  .message("stub") // required by OkHttp, never read by the code under test
                  .body(responseBody.toResponseBody())
                  .build()
            }
            .build()

    override fun newCall(request: Request) = delegate.newCall(request)
  }

  /**
   * Like JUnit's assertThrows, but for suspend code (assertThrows only takes a plain lambda). Runs
   * [block] and returns the exception if it is a [T] so the test can inspect it; fails if a
   * different exception, or none, is thrown.
   */
  private suspend inline fun <reified T : Throwable> assertThrowsSuspend(
      crossinline block: suspend () -> Unit
  ): T {
    try {
      block()
    } catch (exception: Throwable) {
      if (exception is T) return exception
      throw AssertionError(
          "Expected ${T::class.simpleName}, got ${exception::class.simpleName}",
          exception,
      )
    }
    throw AssertionError("Expected ${T::class.simpleName} but nothing was thrown")
  }

  private companion object {
    const val SUCCESS_RESPONSE = "ors_response_walking_success.json"
    const val FAILURE_RESPONSE = "ors_response_walking_failure.json"
    val ORIGIN = Coordinate(latitude = 46.517, longitude = 6.629)
    val DESTINATION = Coordinate(latitude = 46.507, longitude = 6.627)
  }
}
