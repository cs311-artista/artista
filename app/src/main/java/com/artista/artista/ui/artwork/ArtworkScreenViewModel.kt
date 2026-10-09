// Co-authored-by: Gemini <gemini@google.com>
package com.artista.artista.ui.artwork

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Immutable UI state model for the artwork details screen[cite: 1].
 *
 * @author hixeum
 * @property artwork the active artwork model currently viewed, or null if uninitialized[cite: 1]
 * @property isSaved true if this artwork is currently bookmarked in local storage[cite: 1]
 */
data class ArtworkScreenUIState(
  val artwork: Artwork? = null,
  val isSaved: Boolean = false,
)

/**
 * ViewModel managing UI state and persistence operations for [ArtworkScreen][cite: 1].
 *
 * Operates strictly with [ArtworkRepository] abstractions to preserve MVVM boundaries[cite: 1].
 *
 * @author hixeum
 * @param savedArtworkRepository repository responsible for checking, saving, and deleting artworks[cite: 1]
 */
class ArtworkScreenViewModel(
  private val savedArtworkRepository: ArtworkRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(ArtworkScreenUIState())

  /** Exposes read-only state for UI observation to ensure unidirectional data flow[cite: 1]. */
  val uiState: StateFlow<ArtworkScreenUIState> = _uiState.asStateFlow()

  /**
   * Sets the target artwork and verifies its persistence status asynchronously against the repository[cite: 1].
   *
   * @param artwork the artwork entity to display and monitor[cite: 1]
   */
  fun setArtwork(artwork: Artwork) {
    viewModelScope.launch {
      val saved = savedArtworkRepository.isArtworkSaved(artwork.name)
      _uiState.update { it.copy(artwork = artwork, isSaved = saved) }
    }
  }

  /**
   * Toggles the bookmark status of the current artwork with an optimistic update[cite: 1].
   *
   * Flips the visual bookmark state immediately for responsive UI, rolling back only
   * if the repository persistence operation fails[cite: 1].
   */
  fun toggleSaveArtwork() {
    val artwork = _uiState.value.artwork ?: return
    val wasSaved = _uiState.value.isSaved

    // 1. Optimistic UI update
    _uiState.update { it.copy(isSaved = !wasSaved) }

    // 2. Perform background write to repository
    viewModelScope.launch {
      try {
        if (wasSaved) {
          savedArtworkRepository.removeArtwork(artwork.name)
        } else {
          savedArtworkRepository.saveArtwork(artwork)
        }
      } catch (e: Exception) {
        // Revert state if the operation failed
        _uiState.update { it.copy(isSaved = wasSaved) }
      }
    }
  }

  companion object {
    /**
     * Creates a [ViewModelProvider.Factory] instance supplying dependencies to [ArtworkScreenViewModel][cite: 1].
     *
     * @param repository persistence repository used to construct the ViewModel instance[cite: 1]
     * @return factory capable of producing instances of [ArtworkScreenViewModel][cite: 1]
     */
    fun provideFactory(
      repository: ArtworkRepository,
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