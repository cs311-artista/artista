package com.artista.artista.ui.map

import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artist.Artist
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests marker-source precedence used by the map. */
class MapMarkerTest {

  @Test
  fun remoteArtworksNotSaved_removesSavedDuplicates() {
    val saved = artwork("saved")
    val remoteOnly = artwork("remote")

    assertEquals(
        listOf(remoteOnly),
        remoteArtworksNotSaved(listOf(saved), listOf(saved, remoteOnly)),
    )
  }

  @Test
  fun remoteArtworksNotSaved_keepsAllWhenNothingIsSaved() {
    val remote = listOf(artwork("one"), artwork("two"))

    assertEquals(remote, remoteArtworksNotSaved(emptyList(), remote))
  }

  private fun artwork(uid: String): Artwork =
      Artwork(uid, uid, Artist("artist", "Artist"), null, null, null, null, null)
}
