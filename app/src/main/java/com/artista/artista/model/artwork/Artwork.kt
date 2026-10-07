package com.artista.artista.model.artwork

import com.artista.artista.model.map.Location
import java.util.Date

/**
 * Represents an artwork entry in the application.
 *
 * @property name The title or name of the artwork.
 * @property artistName The name of the artist who created the artwork. If *null*, the artistName
 *   was not provided by the API or is *unknown*.
 * @property location The optional location associated with the artwork.
 * @property conceptionDate The optional date when the artwork was conceived or created.
 * @property dimension The optional physical dimensions of the artwork.
 * @author 5kyPhy
 */
data class Artwork(
    val name: String,
    val artistName: String?,
    val location: Location?,
    val conceptionDate: Date?,
    val dimension: Dimension?,
)
