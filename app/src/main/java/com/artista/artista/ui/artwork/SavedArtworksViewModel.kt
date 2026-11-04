// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.artwork

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.SavedArtworksRepository
import com.artista.artista.model.error.AppException
import com.artista.artista.ui.error.launchCatching
import com.artista.artista.ui.error.toMessageRes
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Contains the state required to display the saved artworks screen.
 *
 * @property artworks artworks currently saved by the user
 * @property isLoading whether saved artworks are being loaded
 * @property hasLoadFailed whether the last load failed. Unlike [loadErrorMessageResId], it stays
 *   set after the error has been shown, so the screen can keep offering a retry.
 * @property loadErrorMessageResId string resource of the message shown when loading artworks fails
 * @property unsaveErrorMessageResId string resource of the message shown when unsaving an artwork
 *   fails
 * @author krfpn
 */
data class SavedArtworksUIState(
    val artworks: List<Artwork> = emptyList(),
    val isLoading: Boolean = true,
    val hasLoadFailed: Boolean = false,
    @param:StringRes val loadErrorMessageResId: Int? = null,
    @param:StringRes val unsaveErrorMessageResId: Int? = null,
)

/**
 * Provides saved artworks state and actions to [SavedArtworksScreen].
 *
 * @param savedArtworksRepository repository used to load and remove saved artworks
 * @author krfpn
 */
class SavedArtworksViewModel(
    private val savedArtworksRepository: SavedArtworksRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(SavedArtworksUIState())

  val uiState: StateFlow<SavedArtworksUIState> = _uiState.asStateFlow()

  // Loads can overlap (e.g. quickly leaving and coming back to the screen) and a slow older load
  // could finish last and overwrite newer data, so only the most recent load is kept running.
  private var loadJob: Job? = null

  /**
   * Loads the user's saved artworks from the repository.
   *
   * The loading state is only shown when no artworks are displayed yet, so refreshing an existing
   * list keeps it visible until the new one arrives.
   *
   * @author krfpn
   */
  fun refreshSavedArtworks() {
    _uiState.update { it.copy(isLoading = it.artworks.isEmpty(), loadErrorMessageResId = null) }
    // A previous load may still be waiting for the repository. Its answer is older than the one
    // we are about to request, so stop it: otherwise, if it arrived last, it would replace the
    // newer list on screen (e.g. showing an artwork the user just unsaved). The stopped load ends
    // silently: launchCatching does not report a cancellation as an error.
    loadJob?.cancel()
    loadJob =
        viewModelScope.launchCatching(
            tag = TAG,
            onError = { error ->
              _uiState.update {
                it.copy(
                    isLoading = false,
                    hasLoadFailed = true,
                    loadErrorMessageResId = error.toMessageRes(),
                )
              }
            },
        ) {
          val artworks = savedArtworksRepository.getSavedArtworks()
          _uiState.update {
            it.copy(
                artworks = artworks,
                isLoading = false,
                hasLoadFailed = false,
                loadErrorMessageResId = null,
            )
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
   * @author krfpn
   */
  fun onArtworkUnsave(artwork: Artwork) {
    val previousIndex = _uiState.value.artworks.indexOfFirst { it.uid == artwork.uid }
    // Removing the card before the repository call prevents the user from tapping it twice.
    _uiState.update { state ->
      state.copy(artworks = state.artworks.filterNot { it.uid == artwork.uid })
    }
    viewModelScope.launchCatching(
        tag = TAG,
        onError = { error ->
          _uiState.update { state ->
            state.copy(
                artworks = state.artworks.restore(artwork, previousIndex),
                unsaveErrorMessageResId = error.toMessageRes(),
            )
          }
        },
    ) {
      try {
        savedArtworksRepository.removeArtworkFromSaved(artwork.uid)
      } catch (exception: AppException.NotFound) {
        // The artwork is already absent from storage, which is the outcome the user asked for.
        // Caught here because launchCatching would log it as a failure and call onError.
      }
    }
  }

  /**
   * Puts back an artwork that was optimistically removed from the displayed list.
   *
   * @param artwork artwork to restore
   * @param index position the artwork occupied before its removal
   * @return the list containing the artwork at its previous position, clamped to the list bounds
   */
  private fun List<Artwork>.restore(artwork: Artwork, index: Int): List<Artwork> {
    // A concurrent refresh may already have brought the artwork back, so avoid duplicating it.
    if (any { it.uid == artwork.uid }) return this
    return toMutableList().apply { add(index.coerceIn(0, size), artwork) }
  }

  /**
   * Clears the loading error after the UI has displayed it.
   *
   * @author krfpn
   */
  fun clearLoadError() {
    _uiState.update { it.copy(loadErrorMessageResId = null) }
  }

  /**
   * Clears the unsave error after the UI has displayed it.
   *
   * @author krfpn
   */
  fun clearUnsaveError() {
    _uiState.update { it.copy(unsaveErrorMessageResId = null) }
  }

  /** Holds the log tag and the factory that creates [SavedArtworksViewModel]. */
  companion object {
    /** Log tag identifying messages emitted by [SavedArtworksViewModel]. */
    private const val TAG = "SavedArtworksViewModel"

    /**
     * Creates a factory for a saved-artworks ViewModel with its repository dependency.
     *
     * @param repository repository used to load and update saved artworks
     * @return factory that creates [SavedArtworksViewModel] instances
     * @author patrickmcdan
     */
    fun provideFactory(repository: SavedArtworksRepository): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SavedArtworksViewModel::class.java)) {
              return SavedArtworksViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
          }
        }
  }
}
