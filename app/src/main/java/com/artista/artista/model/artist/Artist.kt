// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.model.artist

import java.util.Date

/**
 * Represents an artist identified independently of their display name.
 *
 * @property uid stable identifier of the artist
 * @property name display name of the artist
 * @property description description of the artist
 * @property imageUrl URL of the artist's image
 * @property birthInstant instant of the artist's birth
 * @property birthPlace place of the artist's birth
 * @property deathInstant instant of the artist's death
 * @property deathPlace place of the artist's death
 * @property citizenships list of the artist's citizenships
 * @property movements list of the artist's movements
 * @author patrickmcdan, IJJA3141
 */
data class Artist(
    val uid: ArtistId,
    val name: String,
    val description: String? = null,
    val imageUrl: String? = null,
    val birthInstant: Date? = null,
    val birthPlace: String? = null,
    val deathInstant: Date? = null,
    val deathPlace: String? = null,
    val citizenships: List<String>? = null,
    val movements: List<String>? = null,
)

/** Stable identifier type for an artist. */
typealias ArtistId = String
