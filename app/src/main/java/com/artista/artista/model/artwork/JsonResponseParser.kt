// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Qwen2.5-coder
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.model.artwork

import com.artista.artista.model.artist.Artist
import com.artista.artista.model.error.AppException
import com.artista.artista.model.map.Location
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Date
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
          throw AppException.NotFound("No result provided in json $json", e)
        }
    if (results == null || !results.has("bindings")) {
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
      // Many artworks have no image on Wikidata, and the binding is then missing, null or an
      // empty string: optJSONObject returns null in all three cases instead of throwing.
      val image = binding.optJSONObject("image")

      val uid = item?.getString("value")?.substringAfterLast('/') ?: continue
      val name = itemLabel?.getString("value") ?: continue
      val artistName = artistLabel?.optString("value")?.takeIf { it.isNotBlank() }
      val artistModel =
          if (artistName != null) {
            // TODO: Replace the name-based UID with the Wikidata artist entity ID.
            Artist(uid = "uid-$artistName", name = artistName)
          } else {
            null
          }
      val descriptionText = description?.getString("value")
      val conceptionDate = inception?.getString("value")?.let(::parseDate)
      val museum = collectionLabel?.getString("value")
      val coordinates = binding.optJSONObject("locationCoordinates")?.optString("value")
      val location = parseLocation(coordinates, locationLabel?.getString("value"))
      val dimension: Dimension? =
          if (width != null && height != null) {
            Dimension(
                width.optString("value").toDouble(),
                height.optString("value").toDouble(),
            )
          } else {
            null
          }
      // The raw Commons link cannot be loaded as is (http, full resolution), so it goes through
      // parseImageUrl. A missing image stays null and the card shows its fallback icon.
      val imageUrl = image?.optString("value")?.let(::parseImageUrl)

      artworks.add(
          Artwork(
              uid = uid,
              name = name,
              artist = artistModel,
              description = descriptionText,
              conceptionDate = conceptionDate,
              museum = museum,
              location = location,
              dimension = dimension,
              imageUrl = imageUrl,
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

  /**
   * Turns the Wikimedia Commons link of an artwork image into a link the app can load.
   *
   * Wikidata returns http links, which Android blocks by default, so the scheme is upgraded to
   * https. Commons serves the full-resolution file unless asked otherwise, and some paintings weigh
   * tens of megabytes, so a thumbnail width is requested instead.
   *
   * A well-formed binding carries the image link in its `value` field:
   * ```
   * "image": { "type": "uri", "value": "http://commons.wikimedia.org/.../Mona%20Lisa.jpg" }
   * ```
   *
   * A malformed binding has an empty `value`, none at all, or one that is not an http(s) link:
   * ```
   * "image": { "type": "uri", "value": "" }
   * "image": { "type": "uri" }
   * "image": { "type": "uri", "value": "not a url" }
   * ```
   *
   * An artwork without an image normally has no `image` binding at all, so this function is not
   * called for it.
   *
   * @param value raw image link from the Wikidata `image` binding
   * @return the https thumbnail link, or null when [value] is not a valid http(s) link, which only
   *   happens with a malformed binding
   * @author Timz3rr
   */
  private fun parseImageUrl(value: String): String? =
      // HttpUrl parses the link instead of editing it as text: an invalid link gives null, and the
      // width is merged with any query the link already has. setQueryParameter, unlike
      // addQueryParameter, replaces a width already present instead of sending it twice.
      value
          .trim()
          .toHttpUrlOrNull()
          ?.newBuilder()
          ?.scheme("https")
          ?.setQueryParameter("width", THUMBNAIL_WIDTH_PX.toString())
          ?.build()
          ?.toString()

  private companion object {
    /**
     * Width of the requested thumbnails, in pixels. It covers a full-width card on common phone
     * screens without downloading the original file.
     *
     * It stays here rather than in `ui/theme/Dimensions.kt`: it is a pixel count sent to Commons in
     * the image link, not a layout size in dp, and the model must not depend on the UI layer.
     */
    const val THUMBNAIL_WIDTH_PX = 800

    val POINT_PATTERN = Regex("""Point\(\s*(-?\d+(?:\.\d+)?)\s+(-?\d+(?:\.\d+)?)\s*\)""")
  }
}
