package com.artista.artista.model.artwork

import com.artista.artista.model.map.Location
import java.util.Date

/**
 * Represents an artwork entry in the application.
 *
 * @property name The title or name of the artwork.
 * @property artistName The name of the artist who created the artwork. If *null*, the artistName
 *   was not provided by the API or is *unknown*.
 * @property description The optional information about the artwork such as how it has been created,
 *   in which life period of the artist, etc.
 * @property conceptionDate The optional date when the artwork was conceived or created.
 * @property museum The optional museum where the artwork is exhibited.
 * @property location The optional location associated with the artwork.
 * @property dimension The optional physical dimensions of the artwork.
 * @author 5kyPhy
 */
data class Artwork(
    val name: String,
    val artistName: String?,
    val description: String?,
    val conceptionDate: Date?,
    val museum: String?,
    val location: Location?,
    val dimension: Dimension?,
)
