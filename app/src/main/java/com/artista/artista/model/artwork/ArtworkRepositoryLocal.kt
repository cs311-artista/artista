// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.model.artwork

import com.artista.artista.model.artist.Artist
import com.artista.artista.model.error.AppException
import com.artista.artista.ui.map.UserLocation

// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
/**
 * In-memory repository for artwork data used during local app development and testing.
 *
 * <p>The repository keeps two internal maps: one for all known artworks and one for the current
 * user's saved artworks. It supports lookups by identifier, search by name or artist, and spatial
 * filtering by a radius from a given location.</p>
 *
 * @author 5kyPhy, patrickmcdan
 */
class ArtworkRepositoryLocal : SavedArtworksRepository, WikiDataRepository {
  /** Stores all artworks available in the local repository. */
  private val artworks: HashMap<ArtworkId, Artwork> = linkedMapOf()

  /** Stores the artworks currently saved by the user. */
  private val artworksSaved: HashMap<ArtworkId, Artwork> = hashMapOf()

  /** Returns the artworks populated in the local repository. */
  override suspend fun getArtworksNearLocation(location: UserLocation?): List<Artwork> =
      artworks.values.toList()

  /** Returns the locally defined popular artists. */
  override suspend fun getPopularArtists(): List<Artist> = mockPopularArtists

  override suspend fun getArtwork(uid: ArtworkId): Artwork {
    if (uid.isBlank()) throw AppException.InvalidInput("uid is blank")
    return artworks[uid] ?: throw AppException.NotFound("No Artwork match uid: $uid was found")
  }

  override suspend fun searchArtworks(query: String): List<Artwork> {
    if (query.isBlank()) return emptyList()
    // Artworks without a name can never match a name search.
    return artworks.values.filter { it.name?.contains(query, ignoreCase = true) == true }
  }

  override suspend fun isArtworkSaved(uid: ArtworkId): Boolean {
    if (uid.isBlank()) throw AppException.InvalidInput("uid is empty")
    return artworksSaved.containsKey(uid)
  }

  override suspend fun saveArtwork(artwork: Artwork) {
    if (isArtworkSaved(artwork.uid)) return
    artworksSaved[artwork.uid] = artwork
  }

  override suspend fun removeArtworkFromSaved(uid: ArtworkId) {
    if (!isArtworkSaved(uid)) throw AppException.NotFound("No artwork matching uid $uid was found")
    artworksSaved.remove(uid)
  }

  override suspend fun getSavedArtworks(): List<Artwork> {
    return artworksSaved.values.toList()
  }

  /**
   * This function is a helper to instantiate a test/demo environment. It accesses parts of the
   * Repository which would not be accessible like it in production code. Its purpose is to set up
   * the repository in a way such that tests and demos using [ArtworkRepositoryLocal] can have data
   * to work with.
   *
   * It is not part of the repository interface, so a duplicate uid here is a programming mistake,
   * never an error shown to the user: it keeps [IllegalArgumentException] instead of an
   * [AppException].
   *
   * @param artwork the [Artwork] which should be added to the fictional backend
   * @throws IllegalArgumentException if trying to put a second artwork with the same [Artwork.uid]
   *   into the repo
   * @author 5kyPhy
   */
  fun putArtworkInRepositoryUnsavedSection(artwork: Artwork) {
    if (artworks.keys.contains(artwork.uid))
        throw IllegalArgumentException("uid $artwork.uid already in use!")
    artworks[artwork.uid] = artwork
  }
}
