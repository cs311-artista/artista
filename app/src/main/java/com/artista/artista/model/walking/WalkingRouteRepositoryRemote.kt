// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.artista.artista.model.walking

import com.artista.artista.BuildConfig
import com.artista.artista.model.error.AppException
import com.artista.artista.model.map.Coordinate
import java.io.IOException
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

/**
 * Remote implementation of [WalkingRouteRepository] backed by the OpenRouteService directions API.
 *
 * Failures are classified as follows:
 * - an OpenRouteService "no route" error code is [AppException.NotFound];
 * - HTTP 429 (per-minute limit) or a 403 naming the quota (daily limit) is
 *   [AppException.RateLimited], transient only for the per-minute limit;
 * - a blank API key, HTTP 401 or any other 403 is [AppException.Unauthorized];
 * - a transport failure or an HTTP 503 is [AppException.Network];
 * - a successful response that cannot be read is [AppException.InvalidData];
 * - anything else (e.g. an internal ORS error, HTTP 500) is [AppException.Unknown].
 *
 * Cancelling the calling coroutine cancels the HTTP call; see [execute].
 *
 * @param client HTTP client used for requests; inject a test client to intercept requests
 * @param apiKey OpenRouteService API key
 * @param ioDispatcher dispatcher the request is launched and its response parsed on
 * @author patrickmcdan
 */
class WalkingRouteRepositoryRemote(
    private val client: OkHttpClient = OkHttpClient(),
    private val apiKey: String = BuildConfig.ORS_API_KEY,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : WalkingRouteRepository {
  override suspend fun getWalkingRoute(
      origin: Coordinate,
      destination: Coordinate,
  ): WalkingRoute =
      withContext(ioDispatcher) {
        try {
          if (apiKey.isBlank()) {
            throw AppException.Unauthorized(
                "OpenRouteService API key is blank: set ORS_API_KEY in local.properties"
            )
          }
          parseRoute(execute(buildRequest(origin, destination)))
        } catch (exception: CancellationException) {
          throw exception
        } catch (exception: AppException) {
          throw exception
        } catch (exception: IOException) {
          throw AppException.Network("OpenRouteService request failed", exception)
        } catch (exception: Exception) {
          throw AppException.Unknown("OpenRouteService request failed unexpectedly", exception)
        }
      }

  private fun buildRequest(origin: Coordinate, destination: Coordinate): Request =
      Request.Builder()
          .url(ENDPOINT)
          .header("Authorization", apiKey)
          .post(coordinatesBody(origin, destination).toRequestBody(JSON_MEDIA_TYPE))
          .build()

  /**
   * Sends [request] and returns the response body, or throws the matching [AppException].
   *
   * Uses OkHttp's asynchronous API so the HTTP call is tied to the calling coroutine: when that
   * coroutine is canceled (e.g. the user leaves the screen and the ViewModel is cleared), the call
   * is canceled too, the connection is dropped and the response is never read. A request that ORS
   * has already received still counts against the quota, so cancelling does not refund it; avoiding
   * wasted quota is up to the callers.
   */
  private suspend fun execute(request: Request): String =
      suspendCancellableCoroutine { continuation ->
        val httpCall = client.newCall(request)
        continuation.invokeOnCancellation { httpCall.cancel() }
        httpCall.enqueue(
            object : Callback {
              override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWith(Result.failure(e))
              }

              override fun onResponse(call: Call, response: Response) {
                // Read the body here, on OkHttp's thread, so the caller never blocks on it.
                continuation.resumeWith(
                    runCatching {
                      response.use {
                        val body = it.body?.string().orEmpty()
                        if (!it.isSuccessful) throw failureFor(it.code, body)
                        body
                      }
                    }
                )
              }
            }
        )
      }

  /**
   * Classifies a non-successful response. The ORS error code is checked first because "no route" is
   * only identifiable through it. Then come the gateway statuses: ORS documents 403 for the daily
   * limit and 429 for the per-minute limit. A 403 is also returned for an unauthorized key, and
   * only its body tells them apart: the gateway answers a daily limit with "Quota exceeded". Of the
   * server errors, 503 is the only one ORS documents as "unavailable"; 500 is an internal error and
   * 501 an unsupported feature, so they stay [AppException.Unknown].
   */
  private fun failureFor(status: Int, body: String): AppException {
    val errorCode = extractErrorCode(body)
    return when {
      errorCode in NO_ROUTE_CODES ->
          AppException.NotFound("OpenRouteService found no walking route (error code $errorCode)")
      status == 429 ->
          AppException.RateLimited(
              isTransient = true,
              message = "OpenRouteService per-minute limit reached",
          )
      status == 403 && body.contains("quota", ignoreCase = true) ->
          AppException.RateLimited(
              isTransient = false,
              message = "OpenRouteService daily quota exhausted",
          )
      status == 401 || status == 403 ->
          AppException.Unauthorized("OpenRouteService rejected the API key: HTTP $status")
      status == 503 -> AppException.Network("OpenRouteService unavailable: HTTP $status")
      else ->
          AppException.Unknown(
              "OpenRouteService request failed: HTTP $status, error code $errorCode"
          )
    }
  }

  private fun extractErrorCode(body: String): Int? = runCatching {
    JSONObject(body).getJSONObject("error").getInt("code")
  }
      .getOrNull()

  private fun parseRoute(body: String): WalkingRoute {
    try {
      val features = JSONObject(body).getJSONArray("features")
      if (features.length() == 0) {
        throw AppException.InvalidData("OpenRouteService response contains no features")
      }
      val feature = features.getJSONObject(0)
      val coordinates = feature.getJSONObject("geometry").getJSONArray("coordinates")
      val summary = feature.getJSONObject("properties").getJSONObject("summary")
      return WalkingRoute(
          path = parsePath(coordinates),
          duration = summary.getDouble("duration").seconds,
          distanceMeters = summary.getDouble("distance").roundToInt(),
      )
    } catch (exception: AppException) {
      throw exception
    } catch (exception: Exception) {
      throw AppException.InvalidData(
          "OpenRouteService response is incomplete or malformed",
          exception,
      )
    }
  }

  private fun parsePath(coordinates: JSONArray): List<Coordinate> {
    if (coordinates.length() == 0) {
      throw AppException.InvalidData("OpenRouteService response contains no coordinates")
    }
    return buildList {
      for (index in 0 until coordinates.length()) {
        val coordinate = coordinates.getJSONArray(index)
        add(Coordinate(latitude = coordinate.getDouble(1), longitude = coordinate.getDouble(0)))
      }
    }
  }

  // OpenRouteService expects longitude first, the reverse of Coordinate's order.
  // The instructions flag is set to false because turn-by-turn directions are not needed, just the
  // path.
  private fun coordinatesBody(origin: Coordinate, destination: Coordinate): String =
      """{"coordinates":[[${origin.longitude},${origin.latitude}],[${destination.longitude},${destination.latitude}]], "instructions":false}"""

  private companion object {
    val ENDPOINT =
        "https://api.heigit.org/openrouteservice/v2/directions/foot-walking/geojson".toHttpUrl()
    val JSON_MEDIA_TYPE = "application/json".toMediaType()

    // Error codes that mean "nothing to walk" rather than a failure: 2009 no route between the two
    // points, 2010 a point is not near any walkable road, 2004 a request limit was exceeded (with
    // a fixed two-point request, this can only be the maximum routing distance).
    // See https://github.com/GIScience/openrouteservice/blob/main/docs/api-reference/error-codes.md
    val NO_ROUTE_CODES = setOf(2009, 2010, 2004)
  }
}
