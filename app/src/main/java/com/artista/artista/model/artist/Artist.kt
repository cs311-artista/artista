// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.model.artist

/**
 * Represents an artist identified independently of their display name.
 *
 * @property uid stable identifier of the artist
 * @property name display name of the artist
 * @property imageUrl link to a picture of the artist, or null when no picture is known
 * @author patrickmcdan, Timz3rr
 */
data class Artist(
    val uid: ArtistId,
    val name: String,
    val imageUrl: String? = null,
)

/** Stable identifier type for an artist. */
typealias ArtistId = String
