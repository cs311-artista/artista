// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.search

import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkId
import com.artista.artista.model.artwork.SavedArtworksRepository
import com.artista.artista.model.artwork.WikiDataRepository
import com.artista.artista.ui.map.UserLocation

/** Shared artwork samples and repository fakes for Search tests. */
internal object SearchTestData {
  val starryNight =
      Artwork(
          uid = "starryId",
          name = "The Starry Night",
          artist = Artist("test-van-gogh-1", "Vincent van Gogh"),
          location = null,
          conceptionDate = null,
          dimension = null,
          description = null,
          museum = null,
      )

  val girlWithPearlEarring =
      Artwork(
          uid = "girlId",
          name = "Girl with a Pearl Earring",
          artist = Artist("test-vermeer", "Johannes Vermeer"),
          location = null,
          conceptionDate = null,
          dimension = null,
          description = null,
          museum = null,
      )

  val sunflowers =
      Artwork(
          uid = "sunflowersId",
          name = "Sunflowers",
          artist = Artist("test-van-gogh-1", "Vincent van Gogh"),
          location = null,
          conceptionDate = null,
          dimension = null,
          description = null,
          museum = null,
      )

  val untitledStudy =
      Artwork(
          uid = "untitledId",
          name = "Untitled study",
          artist = null,
          location = null,
          conceptionDate = null,
          dimension = null,
          description = null,
          museum = null,
      )

  val artworks = listOf(starryNight, girlWithPearlEarring, sunflowers, untitledStudy)

  val savedArtworks = artworks

  /** Creates a saved-artwork fake initialized with the supplied artworks. */
  fun savedArtworksRepository(
      initialSavedArtworks: List<Artwork> = savedArtworks,
  ): FakeSavedArtworksRepository = FakeSavedArtworksRepository(initialSavedArtworks)

  /** Creates a Wikidata fake that searches the shared artwork samples. */
  fun wikiDataRepository(): FakeWikiDataRepository = FakeWikiDataRepository(artworks)
}

/** In-memory saved-artwork repository used by Search screen tests. */
internal class FakeSavedArtworksRepository(
    initialSavedArtworks: List<Artwork>,
) : SavedArtworksRepository {
  val savedArtworks = initialSavedArtworks.toMutableList()
  var shouldThrowError = false

  override suspend fun isArtworkSaved(uid: ArtworkId): Boolean {
    if (shouldThrowError) throw RuntimeException("Simulated error")
    return savedArtworks.any { it.uid == uid }
  }

  override suspend fun saveArtwork(artwork: Artwork) {
    if (shouldThrowError) throw RuntimeException("Simulated database failure")
    savedArtworks.removeAll { it.uid == artwork.uid }
    savedArtworks.add(artwork)
  }

  override suspend fun removeArtworkFromSaved(uid: ArtworkId) {
    if (shouldThrowError) throw RuntimeException("Simulated database failure")
    savedArtworks.removeAll { it.uid == uid }
  }

  override suspend fun getSavedArtworks(): List<Artwork> = savedArtworks.toList()
}

/** In-memory Wikidata repository that searches the shared artwork samples. */
internal class FakeWikiDataRepository(
    private val artworks: List<Artwork>,
) : WikiDataRepository {
  val searchedQueries = mutableListOf<String>()

  override suspend fun getArtworksNearLocation(location: UserLocation?): List<Artwork> {
    throw NotImplementedError("Not yet implemented")
  }

  override suspend fun getPopularArtists(): List<Artist> {
    throw NotImplementedError("Not yet implemented")
  }

  override suspend fun getArtwork(uid: ArtworkId): Artwork =
      artworks.firstOrNull { it.uid == uid }
          ?: throw NoSuchElementException("No artwork matching uid $uid was found")

  override suspend fun searchArtworks(query: String): List<Artwork> {
    searchedQueries.add(query)
    return artworks.filter {
      it.name?.contains(query, ignoreCase = true) == true ||
          it.artist?.name?.contains(query, ignoreCase = true) == true
    }
  }
}
