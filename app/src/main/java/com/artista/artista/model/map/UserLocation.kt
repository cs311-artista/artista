// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.model.map

/**
 * Geographic coordinates of the user.
 *
 * @property latitude Latitude in degrees.
 * @property longitude Longitude in degrees.
 * @author Felix Burchardt
 */
data class UserLocation(
    val latitude: Double,
    val longitude: Double,
)
