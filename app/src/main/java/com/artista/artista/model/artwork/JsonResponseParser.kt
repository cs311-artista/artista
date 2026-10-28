package com.artista.artista.model.artwork

import com.artista.artista.model.map.Location
import java.util.Date
import org.json.JSONObject

/**
 * A utility class to parse JSON responses from the Wikidata SPARQL API.
 *
 * @author AI Assistant
 */
class JsonResponseParser {

  /**
   * Parses a SPARQL JSON response into a list of Artwork objects.
   *
   * @param responseBody raw SPARQL JSON body returned by the endpoint
   * @return the artworks encoded in the response
   * @author AI Assistant
   */
  fun parseArtworkResults(responseBody: String): List<Artwork> {
    val json = JSONObject(responseBody)
    val results = json.getJSONObject("results")
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

      val uid = item?.getString("value") ?: continue
      val name = itemLabel?.getString("value") ?: continue
      val artistName = artistLabel?.getString("value")
      val descriptionText = description?.getString("value")
      val conceptionDate = inception?.getString("value")?.let { Date(it.toLong() * 1000) }
      val museum = collectionLabel?.getString("value")
      val location =
          locationLabel?.getString("value")?.let {
            Location(0.0, 0.0, it)
          }
      val dimension =
          Dimension(
              width?.optInt("value")?.toDouble() ?: 0.0,
              height?.optInt("value")?.toDouble() ?: 0.0,
          )

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

  // Add similar methods for parsing artist results if needed
}
