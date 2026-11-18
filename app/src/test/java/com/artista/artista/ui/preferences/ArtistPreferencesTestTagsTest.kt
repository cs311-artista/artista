// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Unit tests for the per-artist tags of [ArtistPreferencesTestTags].
 *
 * @author Timz3rr
 */
class ArtistPreferencesTestTagsTest {

  /** Checks that the card, picture and fallback tags of one artist are all different. */
  @Test
  fun artistTags_forSameArtist_areDistinct() {
    val tags =
        listOf(
            ArtistPreferencesTestTags.getArtistCardTestTag(ARTIST_ID),
            ArtistPreferencesTestTags.getArtistImageTestTag(ARTIST_ID),
            ArtistPreferencesTestTags.getArtistImageFallbackTestTag(ARTIST_ID),
        )

    assertEquals(tags.size, tags.toSet().size)
  }

  /** Checks that the picture and fallback tags differ from one artist to another. */
  @Test
  fun artistImageTags_forDifferentArtists_differ() {
    assertNotEquals(
        ArtistPreferencesTestTags.getArtistImageTestTag(ARTIST_ID),
        ArtistPreferencesTestTags.getArtistImageTestTag(OTHER_ARTIST_ID),
    )
    assertNotEquals(
        ArtistPreferencesTestTags.getArtistImageFallbackTestTag(ARTIST_ID),
        ArtistPreferencesTestTags.getArtistImageFallbackTestTag(OTHER_ARTIST_ID),
    )
  }

  private companion object {
    const val ARTIST_ID = "test-picasso"
    const val OTHER_ARTIST_ID = "test-monet"
  }
}
