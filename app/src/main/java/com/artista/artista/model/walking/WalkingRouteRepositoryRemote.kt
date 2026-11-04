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
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Remote implementation of [WalkingRouteRepository] backed by the OpenRouteService directions API.
 *
 * Failures are classified as follows:
 * - an OpenRouteService "no route" error code is [AppException.NotFound];
 * - a transport failure or an HTTP 5xx is [AppException.Network];
 * - a successful response that cannot be read is [AppException.InvalidData];
 * - anything else (blank or rejected API key, quota exceeded, rejected request) is
 *   [AppException.Unknown].
 *
 * @param client HTTP client used for requests; inject a test client to intercept requests
 * @param apiKey OpenRouteService API key
 * @param ioDispatcher dispatcher used for blocking HTTP I/O
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
            throw AppException.Unknown(
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

  private fun execute(request: Request): String =
      client.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw failureFor(response.code, body)
        body
      }

  /**
   * Classifies a non-successful response. The OpenRouteService error code is checked first because
   * "no route" is reported through it, not through a dedicated HTTP status.
   */
  private fun failureFor(status: Int, body: String): AppException {
    val errorCode = extractErrorCode(body)
    return when {
      errorCode in NO_ROUTE_CODES ->
          AppException.NotFound("OpenRouteService found no walking route (error code $errorCode)")
      status >= 500 -> AppException.Network("OpenRouteService unavailable: HTTP $status")
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
  private fun coordinatesBody(origin: Coordinate, destination: Coordinate): String =
      """{"coordinates":[[${origin.longitude},${origin.latitude}],[${destination.longitude},${destination.latitude}]]}"""

  private companion object {
    val ENDPOINT = "https://api.openrouteservice.org/v2/directions/foot-walking/geojson".toHttpUrl()
    val JSON_MEDIA_TYPE = "application/json".toMediaType()

    // Error codes that mean "nothing to walk" rather than a failure: 2009 no route between the two
    // points, 2010 a point is not near any walkable road, 2004 a request limit was exceeded (with
    // a fixed two-point request, this can only be the maximum routing distance).
    // See https://github.com/GIScience/openrouteservice/blob/main/docs/api-reference/error-codes.md
    val NO_ROUTE_CODES = setOf(2009, 2010, 2004)
  }
}
