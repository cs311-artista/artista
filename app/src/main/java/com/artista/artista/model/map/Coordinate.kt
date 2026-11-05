// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.model.map

/**
 * A geographic coordinate independent of any map or UI library.
 *
 * @property latitude latitude in degrees
 * @property longitude longitude in degrees
 * @author patrickmcdan
 */
data class Coordinate(
    val latitude: Double,
    val longitude: Double,
)
