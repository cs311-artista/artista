// Co-authored-by: Gemini <gemini@google.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
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
import com.artista.artista.model.map.AddressRepository
import com.artista.artista.model.map.Coordinate
import com.artista.artista.model.map.Location
import com.artista.artista.ui.error.launchCatching
import com.artista.artista.ui.error.toMessageRes
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * State of the human-readable address shown in place of the artwork coordinates.
 *
 * Sealed rather than separate flags, so exactly one of these states applies at a time.
 *
 * @author Timz3rr
 */
sealed interface ArtworkAddressState {
  /** The artwork has no coordinates, so there is no address to look up. */
  data object NoLocation : ArtworkAddressState

  /** The address of the artwork coordinates is being looked up. */
  data object Loading : ArtworkAddressState

  /**
   * The address of the artwork coordinates was found.
   *
   * @property address the full address on one line
   */
  data class Found(val address: String) : ArtworkAddressState

  /** The artwork has coordinates, but no address could be found for them. */
  data object Unavailable : ArtworkAddressState
}

/**
 * Immutable UI state model for the artwork details screen[cite: 1].
 *
 * @property artwork the active artwork model currently viewed, or null if uninitialized[cite: 1]
 * @property isLoading true while the artwork is being fetched
 * @property isSaved true if this artwork is currently bookmarked in local storage[cite: 1]
 * @property isSaveInProgress true while a bookmark change is being persisted
 * @property hasLoadFailed true when the latest artwork fetch failed
 * @property loadErrorMessageResId localized message for the latest artwork fetch failure
 * @property errorMessageResId localized message for the most recent repository failure
 * @property addressState state of the address looked up from the artwork coordinates
 * @author hixeum, Timz3rr
 */
data class ArtworkScreenUIState(
    val artwork: Artwork? = null,
    val isLoading: Boolean = true,
    val isSaved: Boolean = false,
    val isSaveInProgress: Boolean = false,
    val hasLoadFailed: Boolean = false,
    @param:StringRes val loadErrorMessageResId: Int? = null,
    @param:StringRes val errorMessageResId: Int? = null,
    val addressState: ArtworkAddressState = ArtworkAddressState.NoLocation,
)

/**
 * ViewModel managing UI state and persistence operations for [ArtworkScreen][cite: 1].
 *
 * Operates strictly with [SavedArtworksRepository] abstractions to preserve MVVM boundaries[cite:
 * 1].
 *
 * @param artworkId the [ArtworkId] of the artwork whose information is to be displayed on screen
 * @param wikiDataRepository repository responsible for calls to the external [Artwork] source
 * @param savedArtworksRepository repository responsible for checking, saving, and deleting
 *   artworks[cite: 1]
 * @param addressRepository repository turning the artwork coordinates into a readable address
 * @author hixeum, Timz3rr
 */
class ArtworkScreenViewModel(
    private val artworkId: ArtworkId,
    private val wikiDataRepository: WikiDataRepository,
    private val savedArtworksRepository: SavedArtworksRepository,
    private val addressRepository: AddressRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(ArtworkScreenUIState())

  /** Exposes read-only state for UI observation to ensure unidirectional data flow[cite: 1]. */
  val uiState: StateFlow<ArtworkScreenUIState> = _uiState.asStateFlow()

  // Declared before init: init starts the first load, which can assign this field right away on
  // the main dispatcher, and a later initializer would then reset it to null.
  private var addressJob: Job? = null

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
    _uiState.update {
      it.copy(
          isLoading = true,
          hasLoadFailed = false,
          loadErrorMessageResId = null,
          errorMessageResId = null,
      )
    }

    viewModelScope.launchCatching(
        tag = TAG,
        onError = { error ->
          _uiState.update {
            it.copy(
                artwork = null,
                isLoading = false,
                isSaved = false,
                hasLoadFailed = true,
                loadErrorMessageResId = error.toMessageRes(),
                errorMessageResId = error.toMessageRes(),
            )
          }
        },
    ) {
      // isArtworkSaved may throw an exception if the user is not authenticated, so we catch it and
      // treat it as "not saved". Displaying an artwork should not depend on whether the current
      // user is logged in.
      val saved =
          try {
            savedArtworksRepository.isArtworkSaved(artworkId)
          } catch (appException: AppException.Unauthorized) {
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
            isLoading = false,
            isSaved = saved,
            hasLoadFailed = false,
            loadErrorMessageResId = null,
            errorMessageResId = null,
        )
      }
      loadAddress(artwork.location)
    }
  }

  /**
   * Looks up the address of the artwork coordinates, replacing any lookup still in flight.
   *
   * It runs apart from the artwork load, so a failed lookup only marks the address unavailable
   * instead of failing the whole screen.
   *
   * @param location the coordinates of the displayed artwork, or null if it has none
   */
  private fun loadAddress(location: Location?) {
    // A lookup started for a previous load would otherwise answer late and overwrite this one.
    addressJob?.cancel()
    if (location == null) {
      _uiState.update { it.copy(addressState = ArtworkAddressState.NoLocation) }
      return
    }
    _uiState.update { it.copy(addressState = ArtworkAddressState.Loading) }
    addressJob =
        viewModelScope.launchCatching(
            tag = TAG,
            onError = {
              _uiState.update { it.copy(addressState = ArtworkAddressState.Unavailable) }
            },
        ) {
          val address =
              addressRepository.getAddress(
                  Coordinate(latitude = location.latitude, longitude = location.longitude)
              )
          _uiState.update { it.copy(addressState = ArtworkAddressState.Found(address)) }
        }
  }

  /** Retries loading the artwork currently associated with this screen. */
  fun retryArtwork() {
    setArtwork(artworkId)
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
          savedArtworksRepository.removeArtworkFromSaved(artwork.uid)
        } else {
          savedArtworksRepository.saveArtwork(artwork)
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
     * @param artworkId the ID of the artwork to display
     * @param wikiDataRepository the repository for fetching wiki data
     * @param savedArtworksRepository the repository for managing saved artworks
     * @param addressRepository the repository turning coordinates into addresses
     * @return factory capable of producing instances of [ArtworkScreenViewModel][cite: 1]
     */
    fun provideFactory(
        artworkId: ArtworkId,
        wikiDataRepository: WikiDataRepository,
        savedArtworksRepository: SavedArtworksRepository,
        addressRepository: AddressRepository,
    ): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ArtworkScreenViewModel::class.java)) {
              return ArtworkScreenViewModel(
                  artworkId,
                  wikiDataRepository,
                  savedArtworksRepository,
                  addressRepository,
              )
                  as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
          }
        }
  }
}
