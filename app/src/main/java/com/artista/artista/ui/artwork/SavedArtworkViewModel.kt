// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.artwork

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Saved artworks UI state
// ---------------------------------------------------------------------------

/**
 * Contains the state required to display the saved-artworks screen.
 *
 * @property artworks artworks currently saved by the user
 * @property isLoading whether saved artworks are being loaded
 * @property loadErrorMessage error message produced while loading artworks
 * @property unsaveErrorMessage error message produced while unsaving an artwork
 * @author Kaio R. Freitas Pereira Nascimento (krfpn)
 */
data class SavedArtworkUIState(
    val artworks: List<Artwork> = emptyList(),
    val isLoading: Boolean = true,
    val loadErrorMessage: String? = null,
    val unsaveErrorMessage: String? = null,
)

// ---------------------------------------------------------------------------
// Saved artworks ViewModel
// ---------------------------------------------------------------------------

/**
 * Provides saved-artwork state and actions to [SavedArtworkScreen].
 *
 * @param artworkRepository repository used to load and remove saved artworks
 * @author Kaio R. Freitas Pereira Nascimento (krfpn)
 */
class SavedArtworkViewModel(
    private val artworkRepository: ArtworkRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(SavedArtworkUIState())

  /** Read-only saved-artwork state observed by the UI. */
  val uiState: StateFlow<SavedArtworkUIState> = _uiState.asStateFlow()

  init {
    refreshSavedArtworks()
  }

  /**
   * Loads the user's saved artworks from the repository.
   *
   * @author Kaio R. Freitas Pereira Nascimento (krfpn)
   */
  fun refreshSavedArtworks() {
    _uiState.update { it.copy(isLoading = true, loadErrorMessage = null) }
    viewModelScope.launch {
      try {
        val artworks = artworkRepository.getSavedArtworks()
        _uiState.update { it.copy(artworks = artworks, isLoading = false, loadErrorMessage = null) }
      } catch (exception: Exception) {
        Log.e(TAG, "Failed to load saved artworks.", exception)
        _uiState.update {
          it.copy(
              isLoading = false,
              loadErrorMessage = exception.message ?: "Unable to load saved artworks.",
          )
        }
      }
    }
  }

  /**
   * Removes an artwork from the saved list.
   *
   * The artwork disappears from the displayed state immediately and is restored at its previous
   * position only if the repository fails to remove it.
   *
   * @param artwork artwork to remove from the saved list
   * @author Kaio R. Freitas Pereira Nascimento (krfpn)
   */
  fun onArtworkUnsave(artwork: Artwork) {
    val previousIndex = _uiState.value.artworks.indexOfFirst { it.name == artwork.name }
    // Removing the card before the repository call prevents the user from tapping it twice.
    _uiState.update { state ->
      state.copy(artworks = state.artworks.filterNot { it.name == artwork.name })
    }
    viewModelScope.launch {
      try {
        artworkRepository.removeArtwork(artwork.name)
      } catch (exception: NoSuchElementException) {
        // The artwork is already absent from storage, which is the outcome the user asked for.
      } catch (exception: Exception) {
        Log.e(TAG, "Failed to unsave artwork: ${artwork.name}.", exception)
        _uiState.update { state ->
          state.copy(
              artworks = state.artworks.restore(artwork, previousIndex),
              unsaveErrorMessage = exception.message ?: "Unable to unsave artwork.",
          )
        }
      }
    }
  }

  /**
   * Puts back an artwork that was optimistically removed from the displayed list.
   *
   * @param artwork artwork to restore
   * @param index position the artwork occupied before its removal, or -1 if it was not displayed
   * @return the list containing the artwork at its previous position, clamped to the list size
   */
  private fun List<Artwork>.restore(artwork: Artwork, index: Int): List<Artwork> {
    // A concurrent refresh may already have brought the artwork back, so avoid duplicating it.
    if (index < 0 || any { it.name == artwork.name }) return this
    return toMutableList().apply { add(index.coerceAtMost(size), artwork) }
  }

  /**
   * Clears the loading error after the UI has displayed it.
   *
   * @author Kaio R. Freitas Pereira Nascimento (krfpn)
   */
  fun clearLoadError() {
    _uiState.update { it.copy(loadErrorMessage = null) }
  }

  /**
   * Clears the unsave error after the UI has displayed it.
   *
   * @author Kaio R. Freitas Pereira Nascimento (krfpn)
   */
  fun clearUnsaveError() {
    _uiState.update { it.copy(unsaveErrorMessage = null) }
  }

  /** Provides constants used by the ViewModel for structured diagnostic logging. */
  private companion object {
    /** Log tag identifying messages emitted by [SavedArtworkViewModel]. */
    private const val TAG = "SavedArtworkViewModel"
  }
}
