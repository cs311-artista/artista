// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Qwen2.5-coder
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: DeepSeek Flash
package com.artista.artista.model.artwork

import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artist.ArtistCandidate
import com.artista.artista.model.artist.ArtistId
import com.artista.artista.model.error.AppException
import com.artista.artista.model.map.Location
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Date
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * A utility class to parse JSON responses from the Wikidata SPARQL API.
 *
 * @author 5kyPhy
 */
open class JsonResponseParser {

  /**
   * Parses a SPARQL JSON response into a list of Artwork objects.
   *
   * Wikidata answers one row per combination of the optional values it is asked for, so an item
   * carrying two images - or two artists, two collections, two locations - comes back as several
   * rows holding the same item id. The result carries one artwork per id, in the order the ids are
   * first seen, each built from the richest of its rows.
   *
   * That collapse is lossy on purpose: an [Artwork] holds a single image, artist and location, so
   * the extra values of a repeated row have nowhere to go and are dropped. Showing every image of
   * an artwork needs the model to carry a list, which is a change of its own; until then this keeps
   * as much as the model can hold, and keeps which row wins deterministic.
   *
   * @param responseBody raw SPARQL JSON body returned by the endpoint
   * @return one artwork per distinct item id, each built from its richest row
   * @author 5kyPhy, timo-by
   */
  open fun parseArtworkResults(responseBody: String): List<Artwork> {
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

    // Each row is paired with how complete it is, so the rows of one item can be compared before
    // being collapsed.
    val rows = mutableListOf<Pair<Artwork, Int>>()

    for (i in 0 until bindings.length()) {
      val binding = bindings.getJSONObject(i)
      val item = binding.optJSONObject("item")
      val itemLabel = binding.optJSONObject("itemLabel")
      val artistLabel = binding.optJSONObject("artistLabel")
      val inception = binding.optJSONObject("inception")
      val collectionLabel = binding.optJSONObject("collectionLabel")
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

      val locationLabel = binding.optJSONObject("locationLabel")
      val coordinates = binding.optJSONObject("locationCoordinates")?.optString("value")
      val location = parseLocation(coordinates, locationLabel?.getString("value"))

      // Dimension handling: Ignore incomplete data, setting to null if either width or height was
      // not provided
      val width = binding.optJSONObject("width")
      val height = binding.optJSONObject("height")
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

      val artwork =
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
      rows.add(artwork to artwork.richness(hasCoordinates = coordinates != null))
    }

    // An artwork is identified by its uid, and the lists of Overview and Saved Artworks use it as
    // their Compose key, so a repeated uid makes Compose throw "Key ... was already used" as soon
    // as the list is measured. Collapsing here, where the model is built, fixes every consumer at
    // once instead of each screen guarding its own list. groupBy keeps the order the ids are first
    // seen in, so the artworks that appear only once stay where Wikidata put them rather than being
    // reordered by how complete they are.
    return rows
        .groupBy { (artwork, _) -> artwork.uid }
        .map { (_, rowsOfTheSameItem) ->
          // maxBy keeps the first of two equally rich rows, so the winning row only depends on the
          // response and never on anything else.
          rowsOfTheSameItem.maxBy { (_, richness) -> richness }.first
        }
  }

  /**
   * Parses Wikidata entity-search results into artist candidates.
   *
   * @param responseBody raw JSON body returned by the Wikidata entity-search API Invalid individual
   *   candidates are skipped; valid candidates retain Wikidata's order.
   * @return valid candidates in the order returned by Wikidata
   * @throws AppException.InvalidData if the response is malformed or missing its search array
   * @author IJJA3141
   */
  open fun parseArtistCandidates(responseBody: String): List<ArtistCandidate> =
      parseCandidates(responseBody, "artist") { id, label, description ->
        ArtistCandidate(id, label, description)
      }

  /**
   * Parses Wikidata entity-search results into artwork candidates.
   *
   * @param responseBody raw JSON body returned by the Wikidata entity-search API Invalid individual
   *   candidates are skipped; valid candidates retain Wikidata's order.
   * @return valid candidates in the order returned by Wikidata
   * @throws AppException.InvalidData if the response is malformed or missing its search array
   * @author IJJA3141
   */
  open fun parseArtworkCandidates(responseBody: String): List<ArtworkCandidate> =
      parseCandidates(responseBody, "artwork") { id, label, description ->
        ArtworkCandidate(id, label, description)
      }

  /**
   * Parses Wikidata SPARQL query results into filtered artist IDs.
   *
   * Extracts artist entity IDs from the `results.bindings` array, retaining only valid Wikidata
   * entity IDs. Invalid JSON responses result in an exception.
   *
   * @param responseBody raw JSON body returned by the Wikidata SPARQL API
   * @return valid artist IDs in the order returned by Wikidata
   * @throws AppException.InvalidData if the response is malformed or missing the expected results
   *   structure
   * @author IJJA3141
   */
  open fun parseFilteredArtists(responseBody: String): List<ArtistId> =
      parseFilteredCandidates(responseBody, "artist") { it }

  /**
   * Parses Wikidata SPARQL query results into filtered artwork IDs.
   *
   * Extracts artwork entity IDs from the `results.bindings` array, retaining only valid Wikidata
   * entity IDs. Invalid JSON responses result in an exception.
   *
   * @param responseBody raw JSON body returned by the Wikidata SPARQL API
   * @return valid artwork IDs in the order returned by Wikidata
   * @throws AppException.InvalidData if the response is malformed or missing the expected results
   *   structure
   * @author IJJA3141
   */
  open fun parseFilteredArtworks(responseBody: String): List<ArtworkId> =
      parseFilteredCandidates(responseBody, "artwork") { it }

  /**
   * Parses every artist in a SPARQL response into an [Artist], in response order.
   *
   * Rows without an English artist label are skipped. Rows that repeat an artist are dropped after
   * the first one, so the list never contains the same uid twice.
   *
   * @param responseBody raw SPARQL JSON body returned by the endpoint
   * @return the parsed artists, empty when the response has no bindings
   * @throws AppException.InvalidData if the response is missing its results object or bindings
   *   array, or a row has an invalid artist URI
   * @author IJJA3141
   */
  open fun parseArtistsResults(responseBody: String): List<Artist> =
      try {
        val bindings = artistBindings(responseBody)
        (0 until bindings.length())
            .mapNotNull { parseArtistBinding(bindings.getJSONObject(it)) }
            .distinctBy { it.uid }
      } catch (exception: JSONException) {
        throw AppException.InvalidData("Invalid artists response", exception)
      }

  /**
   * Parses the first artist in a SPARQL response into an [Artist].
   *
   * @param responseBody raw SPARQL JSON body returned by the endpoint
   * @return the first artist, or null when the response has no bindings or its artist label is
   *   missing
   * @throws AppException.InvalidData if the response is missing its results object or bindings
   *   array
   * @throws JSONException if the response body or a required binding has an invalid JSON structure
   * @author IJJA3141
   */
  open fun parseArtistResults(responseBody: String): Artist? =
      try {
        val bindings = artistBindings(responseBody)
        if (bindings.length() == 0) null else parseArtistBinding(bindings.getJSONObject(0))
      } catch (exception: JSONException) {
        throw AppException.InvalidData("Invalid curated artist response", exception)
      }

  private fun artistBindings(responseBody: String): JSONArray {
    val results = JSONObject(responseBody).getJSONObject("results")
    if (!results.has("bindings")) {
      throw AppException.InvalidData("No artist found in the response")
    }
    return results.getJSONArray("bindings")
  }

  /** Returns the artist of one SPARQL row, or null when the row has no usable label. */
  private fun parseArtistBinding(binding: JSONObject): Artist? {
    val uid = binding.getJSONObject("artist").getString("value").substringAfterLast('/')
    if (!uid.matches(WikiDataRepositoryRemote.ITEM_ID_REGEX)) {
      throw AppException.InvalidData("Artist response contains an invalid artist URI")
    }
    val name = binding.optBindingValue("artistLabel")?.takeIf { it.isNotBlank() } ?: return null

    return Artist(
        uid = uid,
        name = name,
        description = binding.optBindingValue("description")?.takeIf { it.isNotBlank() },
        imageUrl = binding.optBindingValue("image")?.let(::parseImageUrl),
        birthInstant = binding.optBindingValue("birthDate")?.let(::parseDate),
        birthPlace = binding.optBindingValue("birthPlaceLabel"),
        deathInstant = binding.optBindingValue("deathDate")?.let(::parseDate),
        deathPlace = binding.optBindingValue("deathPlaceLabel"),
        citizenships = binding.optBindingValue("citizenships")?.splitPipeList(),
        movements = binding.optBindingValue("movements")?.splitPipeList(),
    )
  }

  /**
   * Parses the selected Wikidata artist QID from a curated-artist SPARQL response.
   *
   * @param responseBody raw SPARQL JSON body returned by the endpoint
   * @return the selected artist's QID, or null when the response has no bindings
   * @throws AppException.InvalidData if the response is malformed or contains an invalid artist URI
   * @author IJJA3141
   */
  open fun parseCuratedArtistQid(responseBody: String): ArtistId? =
      try {
        val bindings = JSONObject(responseBody).getJSONObject("results").getJSONArray("bindings")
        if (bindings.length() == 0) return null

        val artistUri = bindings.getJSONObject(0).getJSONObject("artist").getString("value")
        val qid = artistUri.substringAfterLast('/')
        if (!qid.matches(WikiDataRepositoryRemote.ITEM_ID_REGEX)) {
          throw AppException.InvalidData("Curated artist response contains an invalid artist URI")
        }
        return qid
      } catch (exception: JSONException) {
        throw AppException.InvalidData("Invalid curated artist response", exception)
      }

  /**
   * Scores how much of an item one row carries, so the rows of the same item can be compared.
   *
   * Coordinates weigh the most because the map button, the map marker and the location filter all
   * depend on them.
   *
   * @param hasCoordinates whether the row bound coordinates, for the artwork or for its location
   * @return a bit per piece of data the row carries
   */
  private fun Artwork.richness(hasCoordinates: Boolean): Int =
      (if (hasCoordinates) 1 shl 6 else 0) or
          (if (imageUrl != null) 1 shl 5 else 0) or
          (if (artist != null) 1 shl 4 else 0) or
          (if (museum != null) 1 shl 3 else 0) or
          (if (description != null) 1 shl 2 else 0) or
          (if (conceptionDate != null) 1 shl 1 else 0) or
          (if (dimension != null) 1 shl 0 else 0)

  private fun parseDate(value: String): Date? {
    return runCatching { Date.from(Instant.parse(value)) }
        .recoverCatching {
          Date.from(LocalDate.parse(value.take(10)).atStartOfDay(ZoneOffset.UTC).toInstant())
        }
        .getOrNull()
  }

  /**
   * Parses the location of an artwork from its coordinates and label. Locations without coordinates
   * are useless for us, therefore we set location to null if only a label is provided by the JSON
   * object.
   *
   * @param coordinates the coordinates of the location, in the format "Point(longitude latitude)"
   * @param label the label of the location
   * @return the parsed location, or null if the coordinates are invalid or malformed.
   * @author 5kyPhy
   */
  private fun parseLocation(coordinates: String?, label: String?): Location? {
    val match = coordinates?.let { POINT_PATTERN.find(it) }
    return when {
      match != null ->
          Location(
              longitude = match.groupValues[1].toDouble(),
              latitude = match.groupValues[2].toDouble(),
              name = label,
          )
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

  /** Value of a SPARQL binding, or null when the variable is unbound or empty. */
  private fun JSONObject.optBindingValue(key: String): String? =
      optJSONObject(key)?.optString("value")?.takeIf { it.isNotEmpty() }

  /** Splits a `GROUP_CONCAT` value (separator " | "), or null when nothing is left. */
  private fun String.splitPipeList(): List<String>? =
      split(" | ").map { it.trim() }.filter { it.isNotEmpty() }.takeIf { it.isNotEmpty() }

  private fun <T> parseFilteredCandidates(
      responseBody: String,
      bindingKey: String,
      createId: (String) -> T,
  ): List<T> =
      try {
        val json = JSONObject(responseBody)
        val results = json.getJSONObject("results")
        val bindings = results.getJSONArray("bindings")

        buildList {
          for (index in 0 until bindings.length()) {
            val obj = bindings.getJSONObject(index)
            val value = obj.getJSONObject(bindingKey).getString("value")
            val qid = value.substringAfterLast('/')

            if (qid.matches(WikiDataRepositoryRemote.ITEM_ID_REGEX)) {
              add(createId(qid))
            }
          }
        }
      } catch (exception: JSONException) {
        throw AppException.InvalidData(
            "Invalid Wikidata $bindingKey search response",
            exception,
        )
      }

  private fun <T> parseCandidates(
      responseBody: String,
      entityType: String,
      createCandidate: (String, String, String?) -> T,
  ): List<T> =
      try {
        val json = JSONObject(responseBody)
        val results =
            json.optJSONArray("search")
                ?: throw AppException.InvalidData(
                    "Wikidata $entityType search response must contain a 'search' array " +
                        "(response length=${responseBody.length})"
                )

        buildList {
          for (index in 0 until results.length()) {
            val candidate =
                try {
                  val result = results.getJSONObject(index)
                  val id = result.getString("id")
                  val label = result.getString("label").takeIf { it.isNotBlank() }

                  if (!id.matches(WikiDataRepositoryRemote.ITEM_ID_REGEX) || label == null) {
                    null
                  } else {
                    val description =
                        if (result.has("description") && !result.isNull("description")) {
                          result.getString("description").takeIf { it.isNotBlank() }
                        } else {
                          null
                        }

                    createCandidate(id, label, description)
                  }
                } catch (_: JSONException) {
                  null
                }

            candidate?.let(::add)
          }
        }
      } catch (exception: JSONException) {
        throw AppException.InvalidData(
            "Invalid Wikidata $entityType search response",
            exception,
        )
      }

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
