package com.artista.artista.model.artwork

import com.artista.artista.model.map.Location
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * In-memory repository for artwork data used during local app development and testing.
 *
 * <p>The repository keeps two internal maps: one for all known artworks and one for the current
 * user's saved artworks. It supports lookups by identifier, search by name or artist, and spatial
 * filtering by a radius from a given location.</p>
 *
 * @author 5kyPhy
 */
class ArtworkRepositoryLocal : ArtworkRepository {
  /** Stores all artworks available in the local repository. */
  private val artworks: HashMap<Artwork_ID, Artwork> = hashMapOf()

  /** Stores the artworks currently saved by the user. */
  private val artworksSaved: HashMap<Artwork_ID, Artwork> = hashMapOf()

  override suspend fun getArtwork(uid: Artwork_ID): Artwork {
    if (uid.isBlank()) throw IllegalArgumentException("uid is blank")
    return artworks[uid] ?: throw NoSuchElementException("No Artwork match uid: $uid was found")
  }

  override suspend fun searchArtworkByArtworkName(name: String): List<Artwork> {
    return artworks.values.filter { artwork -> artwork.name == name }
  }

  override suspend fun searchArtworkByArtistName(artistName: String): List<Artwork> {
    return artworks.values.filter { artwork -> artwork.artistName == artistName }
  }

  override suspend fun searchArtworkByLocation(location: Location, radius: Double): List<Artwork> {
    return artworks.values.filter { artwork -> distanceMeters(artwork.location, location) < radius }
  }

  override suspend fun isArtworkSaved(uid: Artwork_ID): Boolean {
    if (uid.isBlank()) throw IllegalArgumentException("uid is empty")
    return artworksSaved.containsKey(uid)
  }

  override suspend fun saveArtwork(artwork: Artwork) {
    if (isArtworkSaved(artwork.uid)) return
    artworksSaved[artwork.uid] = artwork
  }

  override suspend fun removeArtwork(uid: Artwork_ID) {}

  override suspend fun getSavedArtworks(): List<Artwork> {
    return artworksSaved.values.toList()
  }

  /**
   * Computes the distance in meters between two geographic locations.
   *
   * @param a the first location, or null when no location data is available
   * @param b the second location
   * @return the distance between the two points in meters; returns a very large value when `a` is
   *   null
   * @author 5kyPhy
   */
  private fun distanceMeters(a: Location?, b: Location): Double {
    if (a == null) return Double.MAX_VALUE
    val earthRadius = 6_371_000.0

    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val deltaLat = Math.toRadians(b.latitude - a.latitude)
    val deltaLon = Math.toRadians(b.longitude - a.longitude)

    val h = sin(deltaLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(deltaLon / 2).pow(2)

    val clampedH = h.coerceIn(0.0, 1.0)

    return 2 * earthRadius * asin(sqrt(clampedH))
  }
}
