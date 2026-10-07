package com.artista.artista.model.map

/**
 * Represents a geographic location that can optionally include a label.
 *
 * @property longitude the longitude of the location in degrees.
 * @property latitude the latitude of the location in degrees.
 * @property name the user-facing name of the location, if available.
 * @author 5kyPhy
 */
data class Location(
    val longitude: Double,
    val latitude: Double,
    val name: String?,
)
