// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.model.artist

/**
 * Represents an artist identified independently of their display name.
 *
 * @property uid stable identifier of the artist
 * @property name display name of the artist
 * @author patrickmcdan
 */
data class Artist(
    val uid: ArtistId,
    val name: String,
)

/** Stable identifier type for an artist. */
typealias ArtistId = String
