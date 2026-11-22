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
 * @property types The artwork classes among [ArtworkType] that the artwork is an instance of. A
 *   Wikidata item can belong to several classes (e.g. both a painting and an oil painting), so this
 *   is a set. Empty when the type is unknown, for instance for artworks saved before types existed.
 * @author 5kyPhy, patrickmcdan, krfpn, Timz3rr, hixeum
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
    // Defaults to empty for the same reason, and because an empty set already means "unknown type".
    val types: Set<ArtworkType> = emptySet(),
)

typealias ArtworkId = String
