package com.artista.artista.ui.preferences

import com.artista.artista.model.artist.ArtistId

// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
/**
 * Provides stable semantics tags for the artist preferences screen UI tests.
 *
 * @author Timz3rr
 */
object ArtistPreferencesTestTags {

  /** Tag identifying the root container of the artist preferences screen. */
  const val SCREEN = "artist_preferences_screen"

  /** Tag identifying the button leaving the artist preferences screen. */
  const val BACK_BUTTON = "artist_preferences_back_button"

  /** Tag identifying the title of the screen. */
  const val TITLE = "artist_preferences_title"

  /** Tag identifying the text explaining what the preferences are used for. */
  const val DESCRIPTION = "artist_preferences_description"

  /** Tag identifying the field used to search for artists. */
  const val SEARCH_BAR = "artist_preferences_search_bar"

  /** Tag identifying the title of the popular artists section. */
  const val POPULAR_ARTISTS_TITLE = "artist_preferences_popular_artists_title"

  /** Tag identifying the grid displaying the popular artists. */
  const val ARTISTS_GRID = "artist_preferences_artists_grid"

  /** Tag identifying the button saving the selected artists. */
  const val SAVE_BUTTON = "artist_preferences_save_button"

  /**
   * Returns the tag identifying the card of a specific artist.
   *
   * @param artistId the identifier of the artist displayed by the card.
   * @return the tag of the card, unique for each artist.
   * @author Timz3rr, patrickmcdan
   */
  fun getArtistCardTestTag(artistId: ArtistId): String = "artist_preferences_card_$artistId"
}
