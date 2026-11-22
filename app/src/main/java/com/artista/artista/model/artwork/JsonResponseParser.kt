// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Qwen2.5-coder
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: DeepSeek Flash
package com.artista.artista.model.artwork

import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artist.ArtistId
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
   * [Artwork.types] is the exception: the model holds a set, so the types of every row of an item
   * are merged rather than taken from the richest row alone. Classes outside [ArtworkType] are
   * ignored.
   *
   * @param responseBody raw SPARQL JSON body returned by the endpoint
   * @return one artwork per distinct item id, each built from its richest row and carrying the
   *   types of all its rows
   * @author 5kyPhy, timo-by, hixeum
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
      val type =
          binding
              .optJSONObject("instanceOf")
              ?.optString("value")
              ?.substringAfterLast('/')
              ?.let(ArtworkType::fromQid)

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
              types = setOfNotNull(type),
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
          val richest = rowsOfTheSameItem.maxBy { (_, richness) -> richness }.first
          // Each row carries at most one type, so an item that is both a painting and an oil
          // painting only gets both by merging its rows.
          richest.copy(
              types = rowsOfTheSameItem.flatMapTo(mutableSetOf()) { (row, _) -> row.types }
          )
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
      try {
        val json = JSONObject(responseBody)
        val results =
            json.optJSONArray("search")
                ?: throw AppException.InvalidData(
                    "Wikidata artist search response must contain a 'search' array " +
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
                    ArtistCandidate(id, label, description)
                  }
                } catch (_: JSONException) {
                  null
                }
            candidate?.let(::add)
          }
        }
      } catch (exception: JSONException) {
        throw AppException.InvalidData("Invalid Wikidata artist search response", exception)
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
        val json = JSONObject(responseBody)
        val results = json.getJSONObject("results")
        if (!results.has("bindings")) {
          throw AppException.InvalidData("No artist found in the response")
        }
        val bindings = results.getJSONArray("bindings")
        if (bindings.length() == 0) {
          return null
        }
        val binding = bindings.getJSONObject(0)
        val artistLabel = binding.optJSONObject("artistLabel")
        val description = binding.optJSONObject("description")
        val image = binding.optJSONObject("image")
        val uid = binding.getJSONObject("artist").getString("value").substringAfterLast('/')

        val birthDate = binding.optJSONObject("birthDate")?.getString("value")
        val birthInstant = birthDate?.let(::parseDate)

        val deathDate = binding.optJSONObject("deathDate")?.getString("value")
        val deathInstant = deathDate?.let(::parseDate)

        val birthPlace = binding.optJSONObject("birthPlaceLabel")?.getString("value")
        val deathPlace = binding.optJSONObject("deathPlaceLabel")?.getString("value")

        val citizenships = binding.optJSONObject("citizenships")?.getString("value")?.split(" | ")
        val movements = binding.optJSONObject("movements")?.getString("value")?.split(" | ")

        if (!uid.matches(WikiDataRepositoryRemote.ITEM_ID_REGEX))
            throw AppException.InvalidData("Artist response contains an invalid artist URI")
        val name = artistLabel?.getString("value") ?: return null

        val descriptionText =
            if (description != null) {

              description.getString("value").takeIf { it.isNotBlank() }
            } else {
              null
            }
        val imageUrl = image?.optString("value")?.let(::parseImageUrl)
        return Artist(
            uid = uid,
            name = name,
            description = descriptionText,
            imageUrl = imageUrl,
            birthInstant = birthInstant,
            birthPlace = birthPlace,
            deathInstant = deathInstant,
            deathPlace = deathPlace,
            citizenships = citizenships,
            movements = movements,
        )
      } catch (exception: JSONException) {
        throw AppException.InvalidData("Invalid curated artist response", exception)
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
