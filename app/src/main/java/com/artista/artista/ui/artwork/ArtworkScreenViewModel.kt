// Co-authored-by: Gemini <gemini@google.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.artwork

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.SavedArtworksRepository
import com.artista.artista.ui.error.launchCatching
import com.artista.artista.ui.error.toMessageRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Immutable UI state model for the artwork details screen[cite: 1].
 *
 * @property artwork the active artwork model currently viewed, or null if uninitialized[cite: 1]
 * @property isSaved true if this artwork is currently bookmarked in local storage[cite: 1]
 * @property isSaveInProgress true while a bookmark change is being persisted
 * @property errorMessageResId localized message for the most recent repository failure
 * @author hixeum
 */
data class ArtworkScreenUIState(
    val artwork: Artwork? = null,
    val isSaved: Boolean = false,
    val isSaveInProgress: Boolean = false,
    @param:StringRes val errorMessageResId: Int? = null,
)

/**
 * ViewModel managing UI state and persistence operations for [ArtworkScreen][cite: 1].
 *
 * Operates strictly with [SavedArtworksRepository] abstractions to preserve MVVM boundaries[cite:
 * 1].
 *
 * @param savedArtworkRepository repository responsible for checking, saving, and deleting
 *   artworks[cite: 1]
 * @author hixeum
 */
class ArtworkScreenViewModel(
    private val savedArtworkRepository: SavedArtworksRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(ArtworkScreenUIState())

  /** Exposes read-only state for UI observation to ensure unidirectional data flow[cite: 1]. */
  val uiState: StateFlow<ArtworkScreenUIState> = _uiState.asStateFlow()

  /**
   * Sets the target artwork and verifies its persistence status asynchronously against the
   * repository[cite: 1].
   *
   * @param artwork the artwork entity to display and monitor[cite: 1]
   */
  fun setArtwork(artwork: Artwork) {
    viewModelScope.launchCatching(
        tag = TAG,
        onError = { error ->
          _uiState.update {
            it.copy(
                artwork = artwork,
                isSaved = false,
                errorMessageResId = error.toMessageRes(),
            )
          }
        },
    ) {
      val saved = savedArtworkRepository.isArtworkSaved(artwork.uid)
      _uiState.update { it.copy(artwork = artwork, isSaved = saved, errorMessageResId = null) }
    }
  }

  /**
   * Toggles the bookmark status of the current artwork with an optimistic update[cite: 1].
   *
   * Flips the visual bookmark state immediately for responsive UI, rolling back only if the
   * repository persistence operation fails[cite: 1].
   */
  fun toggleSaveArtwork() {
    val currentState = _uiState.value
    val artwork = currentState.artwork ?: return
    if (currentState.isSaveInProgress) return
    val wasSaved = currentState.isSaved

    // 1. Optimistic UI update
    _uiState.update {
      it.copy(
          isSaved = !wasSaved,
          isSaveInProgress = true,
          errorMessageResId = null,
      )
    }

    // 2. Perform background write to repository
    viewModelScope.launchCatching(
        tag = TAG,
        onError = { error ->
          _uiState.update {
            it.copy(
                isSaved = wasSaved,
                isSaveInProgress = false,
                errorMessageResId = error.toMessageRes(),
            )
          }
        },
    ) {
      try {
        if (wasSaved) {
          savedArtworkRepository.removeArtworkFromSaved(artwork.uid)
        } else {
          savedArtworkRepository.saveArtwork(artwork)
        }
      } finally {
        _uiState.update { it.copy(isSaveInProgress = false) }
      }
    }
  }

  /**
   * Clears the repository error after the screen has displayed it.
   *
   * @author hixeum
   */
  fun clearErrorMessage() {
    _uiState.update { it.copy(errorMessageResId = null) }
  }

  companion object {
    private const val TAG = "ArtworkScreenViewModel"

    /**
     * Creates a [ViewModelProvider.Factory] instance supplying dependencies to
     * [ArtworkScreenViewModel][cite: 1].
     *
     * @param repository persistence repository used to construct the ViewModel instance[cite: 1]
     * @return factory capable of producing instances of [ArtworkScreenViewModel][cite: 1]
     */
    fun provideFactory(
        repository: SavedArtworksRepository,
    ): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ArtworkScreenViewModel::class.java)) {
              return ArtworkScreenViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
          }
        }
  }
}
