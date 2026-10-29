// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Qwen2.5-coder
package com.artista.artista.model.artwork

import com.artista.artista.model.error.AppException
import com.artista.artista.model.map.Location
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Date
import org.json.JSONException
import org.json.JSONObject

/**
 * A utility class to parse JSON responses from the Wikidata SPARQL API.
 *
 * @author 5kyPhy
 */
class JsonResponseParser {

  /**
   * Parses a SPARQL JSON response into a list of Artwork objects.
   *
   * @param responseBody raw SPARQL JSON body returned by the endpoint
   * @return the artworks encoded in the response
   * @author 5kyPhy
   */
  fun parseArtworkResults(responseBody: String): List<Artwork> {
    val json = JSONObject(responseBody)
    val results =
        try {
          json.getJSONObject("results")
        } catch (e: JSONException) {
          throw AppException.InvalidData("No result provided in json $json", e)
        }
    if (!results.has("bindings")) {
      throw AppException.NotFound("No artwork found in the response")
    }
    val bindings = results.getJSONArray("bindings")

    val artworks = mutableListOf<Artwork>()

    for (i in 0 until bindings.length()) {
      val binding = bindings.getJSONObject(i)
      val item = binding.optJSONObject("item")
      val itemLabel = binding.optJSONObject("itemLabel")
      val artistLabel = binding.optJSONObject("artistLabel")
      val inception = binding.optJSONObject("inception")
      val collectionLabel = binding.optJSONObject("collectionLabel")
      val locationLabel = binding.optJSONObject("locationLabel")
      val width = binding.optJSONObject("width")
      val height = binding.optJSONObject("height")
      val description = binding.optJSONObject("description")

      val uid = item?.getString("value")?.substringAfterLast('/') ?: continue
      val name = itemLabel?.getString("value") ?: continue
      val artistName = artistLabel?.getString("value")
      val descriptionText = description?.getString("value")
      val conceptionDate = inception?.getString("value")?.let(::parseDate)
      val museum = collectionLabel?.getString("value")
      val coordinates = binding.optJSONObject("locationCoordinates")?.optString("value")
      val location = parseLocation(coordinates, locationLabel?.getString("value"))
      val dimension: Dimension? = if (width != null && height != null ) {
        Dimension(
          width.optString("value").toDouble(),
          height.optString("value").toDouble()
        )
      } else {
        null
      }

      artworks.add(
          Artwork(
              uid = uid,
              name = name,
              artistName = artistName,
              description = descriptionText,
              conceptionDate = conceptionDate,
              museum = museum,
              location = location,
              dimension = dimension,
          )
      )
    }

    return artworks
  }

  private fun parseDate(value: String): Date? {
    return runCatching { Date.from(Instant.parse(value)) }
        .recoverCatching {
          Date.from(LocalDate.parse(value.take(10)).atStartOfDay(ZoneOffset.UTC).toInstant())
        }
        .getOrNull()
  }

  private fun parseLocation(coordinates: String?, label: String?): Location? {
    val match = coordinates?.let { POINT_PATTERN.find(it) }
    return when {
      match != null ->
          Location(
              longitude = match.groupValues[1].toDouble(),
              latitude = match.groupValues[2].toDouble(),
              name = label,
          )

      label != null -> Location(0.0, 0.0, label)
      else -> null
    }
  }

  private companion object {
    val POINT_PATTERN = Regex("""Point\(\s*(-?\d+(?:\.\d+)?)\s+(-?\d+(?:\.\d+)?)\s*\)""")
  }
}
