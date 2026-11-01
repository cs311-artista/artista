package com.artista.artista.ui.preferences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artist.ArtistId
import com.artista.artista.model.artwork.WikiDataRepository
import com.artista.artista.ui.error.launchCatching
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
/**
 * UI state of the artist preferences screen.
 *
 * Kept separate from [com.artista.artista.model.user.UserPreference]: the suggested artists are not
 * user data, and the selection only becomes a preference once the user saves it.
 *
 * @property popularArtists the artists suggested to the user, in display order.
 * @property selectedArtists identifiers of the artists the user has selected as favorites.
 * @property searchQuery the text typed in the search field, exactly as the user entered it.
 * @author Timz3rr, patrickmcdan, Kaio R. Freitas Pereira Nascimento (krfpn)
 */
data class ArtistPreferencesUiState(
    val popularArtists: List<Artist> = emptyList(),
    val selectedArtists: Set<ArtistId> = emptySet(),
    val searchQuery: String = "",
) {
  /**
   * The popular artists whose name matches [searchQuery], in display order.
   *
   * Derived rather than stored, so it stays correct when the artists finish loading after the user
   * has started typing.
   */
  val displayedArtists: List<Artist>
    get() {
      // Trimmed so stray spaces do not hide every artist; an empty query matches all of them.
      val query = searchQuery.trim()
      return popularArtists.filter { it.name.contains(query, ignoreCase = true) }
    }
}

/**
 * Holds the popular artists and the selection made by the user on the artist preferences screen.
 *
 * @author Timz3rr, patrickmcdan
 */
class ArtistPreferencesViewModel(
    private val artworkRepository: WikiDataRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(ArtistPreferencesUiState())

  /**
   * The current state of the artist preferences screen.
   *
   * @author Timz3rr
   */
  val uiState: StateFlow<ArtistPreferencesUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launchCatching(
        tag = TAG,
        onError = {
          // Already logged by launchCatching. The grid stays empty until a retry exists.
        },
    ) {
      _uiState.update { it.copy(popularArtists = artworkRepository.getPopularArtists()) }
    }
  }

  /**
   * Selects the given artist, or deselects it if it is already selected.
   *
   * @param artistId the identifier of the artist to toggle.
   * @author Timz3rr, patrickmcdan
   */
  fun toggleArtist(artistId: ArtistId) {
    _uiState.update { state ->
      val selectedArtists =
          if (artistId in state.selectedArtists) state.selectedArtists - artistId
          else state.selectedArtists + artistId
      state.copy(selectedArtists = selectedArtists)
    }
  }

  /**
   * Updates the search query, which filters the artists displayed by the screen.
   *
   * @param query the text currently typed in the search field.
   * @author Kaio R. Freitas Pereira Nascimento (krfpn)
   */
  fun onSearchQueryChange(query: String) {
    _uiState.update { it.copy(searchQuery = query) }
  }

  companion object {
    /** Log tag identifying messages emitted by [ArtistPreferencesViewModel]. */
    private const val TAG = "ArtistPreferencesViewModel"

    /**
     * Creates a factory for an artist-preferences ViewModel.
     *
     * @param repository repository used to load popular artists
     * @return factory that creates [ArtistPreferencesViewModel] instances
     * @author patrickmcdan
     */
    fun provideFactory(repository: WikiDataRepository): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ArtistPreferencesViewModel::class.java)) {
              return ArtistPreferencesViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
          }
        }
  }
}
