// Co-authored-by: Gemini <gemini@google.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.artwork

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkId
import com.artista.artista.model.artwork.SavedArtworksRepository
import com.artista.artista.model.artwork.WikiDataRepository
import com.artista.artista.model.error.AppException
import com.artista.artista.ui.error.launchCatching
import com.artista.artista.ui.error.toMessageRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Immutable UI state model for the artwork details screen[cite: 1].
 *
 * @property artwork the active artwork model currently viewed, or null if uninitialized[cite: 1]
 * @property isSaved true if this artwork is currently bookmarked in local storage[cite: 1]
 * @author hixeum
 */
data class ArtworkScreenUIState(
    val artwork: Artwork? = null,
    val isSaved: Boolean = false,
    @param:StringRes val errorMsg: Int? = null,
)

/**
 * ViewModel managing UI state and persistence operations for [ArtworkScreen][cite: 1].
 *
 * Operates strictly with [SavedArtworksRepository] abstractions to preserve MVVM boundaries[cite:
 * 1].
 *
 * @param savedArtworksRepository repository responsible for checking, saving, and deleting
 *   artworks[cite: 1]
 * @author hixeum
 */
class ArtworkScreenViewModel(
    artworkId: ArtworkId,
    private val wikiDataRepository: WikiDataRepository,
    private val savedArtworksRepository: SavedArtworksRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(ArtworkScreenUIState())

  /** Exposes read-only state for UI observation to ensure unidirectional data flow[cite: 1]. */
  val uiState: StateFlow<ArtworkScreenUIState> = _uiState.asStateFlow()

  init {
    setArtwork(artworkId)
  }

  /**
   * Sets the target artwork and verifies its persistence status asynchronously against the
   * repository[cite: 1].
   *
   * @param artworkId the artworkId of the entity to display and monitor[cite: 1]
   */
  fun setArtwork(artworkId: ArtworkId) {
    viewModelScope.launchCatching(
        tag = ArtworkScreenViewModel::class.java.simpleName,
        onError = ::setLoadingArtworkError,
    ) {
      // isArtworkSaved may throw an exception if the user is not authenticated, so we catch it and
      // treat it as "not saved". Displaying an artwork should not depend on whether the current
      // user is logged in.
      val saved =
          try {
            savedArtworksRepository.isArtworkSaved(artworkId)
          } catch (exception: Exception) {
            false
          }
      val artwork =
          if (saved) {
            savedArtworksRepository.getSavedArtworks().filter { it.uid == artworkId }[0]
          } else {
            wikiDataRepository.getArtwork(artworkId)
          }
      _uiState.update {
        it.copy(
            artwork = artwork,
            isSaved = saved,
        )
      }
    }
  }

  /**
   * Flips the visual bookmark state immediately for responsive UI, rolling back only if the
   * repository persistence operation fails[cite: 1].
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
          savedArtworksRepository.removeArtworkFromSaved(artwork.uid)
        } else {
          savedArtworksRepository.saveArtwork(artwork)
        }
      } catch (e: Exception) {
        // Revert state if the operation failed
        _uiState.update { it.copy(isSaved = wasSaved) }
        if (e is AppException) {
          setSavingArtworkError(e)
        }
      }
    }
  }

  private fun setLoadingArtworkError(error: AppException) {
    _uiState.update { it.copy(artwork = null, errorMsg = error.toMessageRes()) }
  }

  private fun setSavingArtworkError(error: AppException) {
    _uiState.update { it.copy(errorMsg = error.toMessageRes()) }
  }

  companion object {
    /**
     * Creates a [ViewModelProvider.Factory] instance supplying dependencies to
     * [ArtworkScreenViewModel][cite: 1].
     *
     * @param artworkId the ID of the artwork to display
     * @param wikiDataRepository the repository for fetching wiki data
     * @param savedArtworksRepository the repository for managing saved artworks
     * @return factory capable of producing instances of [ArtworkScreenViewModel][cite: 1]
     */
    fun provideFactory(
        artworkId: ArtworkId,
        wikiDataRepository: WikiDataRepository,
        savedArtworksRepository: SavedArtworksRepository,
    ): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ArtworkScreenViewModel::class.java)) {
              return ArtworkScreenViewModel(artworkId, wikiDataRepository, savedArtworksRepository)
                  as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
          }
        }
  }
}
