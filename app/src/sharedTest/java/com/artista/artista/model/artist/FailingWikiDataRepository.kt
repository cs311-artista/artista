// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.model.artist

import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkRepositoryLocal
import com.artista.artista.model.artwork.ArtworkType
import com.artista.artista.model.artwork.SearchArea
import com.artista.artista.model.artwork.WikiDataRepository

/**
 * Creates a repository whose nearby-artworks load always fails, to test load-error handling.
 *
 * @param failure exception thrown by the load; the default is an unexpected failure
 * @return a [WikiDataRepository] that throws [failure] on
 *   [WikiDataRepository.getArtworksNearLocation]
 * @author patrickmcdan
 * @author Timz3rr
 */
fun failingWikiDataRepository(
    failure: Exception = IllegalStateException("Artwork provider failure")
): WikiDataRepository =
    object : WikiDataRepository by ArtworkRepositoryLocal() {
      override suspend fun getArtworksNearLocation(
          searchArea: SearchArea,
          typeValues: List<ArtworkType>,
          limit: Int,
      ): List<Artwork> = throw failure
    }
