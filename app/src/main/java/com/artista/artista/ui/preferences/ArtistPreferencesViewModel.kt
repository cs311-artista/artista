package com.artista.artista.ui.preferences

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
/**
 * UI state of the artist preferences screen.
 *
 * @property popularArtists the artists suggested to the user, in display order.
 * @property selectedArtists the artists the user has selected as favorites.
 * @author Timz3rr
 */
data class ArtistPreferencesUiState(
    val popularArtists: List<String> = emptyList(),
    val selectedArtists: Set<String> = emptySet(),
)

// Placeholder data taken from the mockup, until popular artists are provided by a repository.
private val PLACEHOLDER_POPULAR_ARTISTS =
    listOf(
        "Pablo Picasso",
        "Vincent van Gogh",
        "Pierre-Auguste Renoir",
        "Claude Monet",
        "Gustav Klimt",
        "Henri Matisse",
    )

/**
 * Holds the popular artists and the selection made by the user on the artist preferences screen.
 *
 * @author Timz3rr
 */
class ArtistPreferencesViewModel : ViewModel() {

  private val _uiState =
      MutableStateFlow(ArtistPreferencesUiState(popularArtists = PLACEHOLDER_POPULAR_ARTISTS))

  /**
   * The current state of the artist preferences screen.
   *
   * @author Timz3rr
   */
  val uiState: StateFlow<ArtistPreferencesUiState> = _uiState.asStateFlow()

  /**
   * Selects the given artist, or deselects it if it is already selected.
   *
   * @param artist the name of the artist to toggle.
   * @author Timz3rr
   */
  fun toggleArtist(artist: String) {
    _uiState.update { state ->
      val selectedArtists =
          if (artist in state.selectedArtists) state.selectedArtists - artist
          else state.selectedArtists + artist
      state.copy(selectedArtists = selectedArtists)
    }
  }
}
