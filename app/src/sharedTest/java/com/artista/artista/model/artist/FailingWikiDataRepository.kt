package com.artista.artista.model.artist

import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkRepositoryLocal
import com.artista.artista.model.artwork.ArtworkType
import com.artista.artista.model.artwork.SearchArea
import com.artista.artista.model.artwork.WikiDataRepository

/**
 * Creates a repository whose nearby-artworks load always fails, to test load-error handling.
 *
 * @return a [WikiDataRepository] that throws on [WikiDataRepository.getArtworksNearLocation]
 * @author your-github-id
 */
fun failingWikiDataRepository(): WikiDataRepository =
    object : WikiDataRepository by ArtworkRepositoryLocal() {
      override suspend fun getArtworksNearLocation(
          searchArea: SearchArea,
          typeValues: List<ArtworkType>,
          limit: Int,
      ): List<Artwork> = error("Artwork provider failure")
    }
