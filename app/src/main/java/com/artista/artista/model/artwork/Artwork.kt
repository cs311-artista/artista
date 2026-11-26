// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.model.artwork

import com.artista.artista.model.artist.Artist
import com.artista.artista.model.map.Location
import java.util.Date

/**
 * Represents an artwork entry in the application.
 *
 * @property name The title or name of the artwork. If *null*, the name was not provided by the API
 *     * or is *unknown*.
 *
 * @property artist The artist who created the artwork. If *null*, the artist was not provided by
 *   the API or is *unknown*.
 * @property description The optional information about the artwork such as how it has been created,
 *   in which life period of the artist, etc.
 * @property conceptionDate The optional date when the artwork was conceived or created.
 * @property museum The optional museum where the artwork is exhibited.
 * @property location The optional location associated with the artwork.
 * @property dimension The optional physical dimensions of the artwork.
 * @property imageUrl The optional https link to a thumbnail of the artwork. If *null*, the API
 *   provided no image and the UI shows a fallback instead.
 * @author 5kyPhy, patrickmcdan, krfpn, Timz3rr
 */
data class Artwork(
    val uid: ArtworkId,
    val name: String?,
    val artist: Artist?,
    val description: String?,
    val conceptionDate: Date?,
    val museum: String?,
    val location: Location?,
    val dimension: Dimension?,
    // Defaults to null so that existing artworks and mocks without an image keep compiling.
    val imageUrl: String? = null,
)

typealias ArtworkId = String

/**
 * Wikidata artwork classes used when filtering search results.
 *
 * Each entry carries the Wikidata item identifier used in SPARQL queries.
 *
 * @property qid unique Wikidata QID for the artwork class
 * @author IJJA3141
 */
enum class ArtworkType(val qid: String) {
  PAINTING("Q3305213"),
  DRAWING("Q93184"),
  SCULPTURE("Q860861"),
  PRINT("Q11060274"),
  PHOTOGRAPH("Q125191"),
  MURAL("Q219423"),
  OIL_PAINTING("Q174705"),
  WATERCOLOR_PAINTING("Q18761202"),
  MOSAIC("Q133067");

  override fun toString(): String = qid
}

fun List<ArtworkType>.toSparqlValueList(): String = joinToString(separator = " ") { "wd:${it.qid}" }
