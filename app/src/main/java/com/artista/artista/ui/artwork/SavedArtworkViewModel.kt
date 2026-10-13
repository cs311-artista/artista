// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.artwork

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.artista.artista.R
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkRepositoryProvider
import com.artista.artista.model.artwork.SavedArtworksRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Contains the state required to display the saved-artworks screen.
 *
 * @property artworks artworks currently saved by the user
 * @property isLoading whether saved artworks are being loaded
 * @property loadErrorMessageResId string resource of the message shown when loading artworks fails
 * @property unsaveErrorMessageResId string resource of the message shown when unsaving an artwork
 *   fails
 * @author Kaio R. Freitas Pereira Nascimento (krfpn)
 */
data class SavedArtworkUIState(
    val artworks: List<Artwork> = emptyList(),
    val isLoading: Boolean = true,
    @param:StringRes val loadErrorMessageResId: Int? = null,
    @param:StringRes val unsaveErrorMessageResId: Int? = null,
)

/**
 * Provides saved-artwork state and actions to [SavedArtworkScreen].
 *
 * @param savedArtworksRepository repository used to load and remove saved artworks. Defaults to the
 *   shared [ArtworkRepositoryProvider.repository]; tests pass a fake instead.
 * @author Kaio R. Freitas Pereira Nascimento (krfpn)
 */
class SavedArtworkViewModel(
    private val savedArtworksRepository: SavedArtworksRepository =
        ArtworkRepositoryProvider.repository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(SavedArtworkUIState())

  /** Read-only saved-artwork state observed by the UI. */
  val uiState: StateFlow<SavedArtworkUIState> = _uiState.asStateFlow()

  // Loads can overlap (e.g. quickly leaving and coming back to the screen) and a slow older load
  // could finish last and overwrite newer data, so only the most recent load is kept running.
  private var loadJob: Job? = null

  /**
   * Loads the user's saved artworks from the repository.
   *
   * The loading state is only shown when no artworks are displayed yet, so refreshing an existing
   * list keeps it visible until the new one arrives.
   *
   * @author Kaio R. Freitas Pereira Nascimento (krfpn)
   */
  fun refreshSavedArtworks() {
    _uiState.update { it.copy(isLoading = it.artworks.isEmpty(), loadErrorMessageResId = null) }
    // A previous load may still be waiting for the repository. Its answer is older than the one
    // we are about to request, so stop it: otherwise, if it arrived last, it would replace the
    // newer list on screen (e.g. showing an artwork the user just unsaved). The stopped load ends
    // silently; the CancellationException catch below makes sure it is not shown as an error.
    loadJob?.cancel()
    loadJob = viewModelScope.launch {
      try {
        val artworks = savedArtworksRepository.getSavedArtworks()
        _uiState.update {
          it.copy(artworks = artworks, isLoading = false, loadErrorMessageResId = null)
        }
      } catch (exception: CancellationException) {
        // Leaving the screen cancels viewModelScope, and CancellationException extends Exception.
        // Rethrowing it lets the coroutine stop instead of reporting the cancellation as a failed
        // load, so it must stay before the generic catch below.
        throw exception
      } catch (exception: Exception) {
        Log.e(TAG, "Failed to load saved artworks.", exception)
        _uiState.update {
          it.copy(
              isLoading = false,
              loadErrorMessageResId = R.string.saved_artworks_load_error,
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
    val previousIndex = _uiState.value.artworks.indexOfFirst { it.uid == artwork.uid }
    // Removing the card before the repository call prevents the user from tapping it twice.
    _uiState.update { state ->
      state.copy(artworks = state.artworks.filterNot { it.uid == artwork.uid })
    }
    viewModelScope.launch {
      try {
        savedArtworksRepository.removeArtworkFromSaved(artwork.uid)
      } catch (exception: NoSuchElementException) {
        // The artwork is already absent from storage, which is the outcome the user asked for.
      } catch (exception: CancellationException) {
        // Leaving the screen cancels viewModelScope, and CancellationException extends Exception.
        // Rethrowing it lets the coroutine stop instead of logging a failed unsave and restoring
        // the artwork, so it must stay before the generic catch below.
        throw exception
      } catch (exception: Exception) {
        Log.e(TAG, "Failed to unsave artwork ${artwork.uid} (${artwork.name}).", exception)
        _uiState.update { state ->
          state.copy(
              artworks = state.artworks.restore(artwork, previousIndex),
              unsaveErrorMessageResId = R.string.saved_artworks_unsave_error,
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
    if (index < 0 || any { it.uid == artwork.uid }) return this
    return toMutableList().apply { add(index.coerceAtMost(size), artwork) }
  }

  /**
   * Clears the loading error after the UI has displayed it.
   *
   * @author Kaio R. Freitas Pereira Nascimento (krfpn)
   */
  fun clearLoadError() {
    _uiState.update { it.copy(loadErrorMessageResId = null) }
  }

  /**
   * Clears the unsave error after the UI has displayed it.
   *
   * @author Kaio R. Freitas Pereira Nascimento (krfpn)
   */
  fun clearUnsaveError() {
    _uiState.update { it.copy(unsaveErrorMessageResId = null) }
  }

  /** Provides constants used by the ViewModel for structured diagnostic logging. */
  private companion object {
    /** Log tag identifying messages emitted by [SavedArtworkViewModel]. */
    private const val TAG = "SavedArtworkViewModel"
  }
}
