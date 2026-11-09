// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.search

import androidx.annotation.StringRes
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkId
import com.artista.artista.model.artwork.ArtworkType
import com.artista.artista.model.artwork.SavedArtworksRepository
import com.artista.artista.model.artwork.WikiDataRepository
import com.artista.artista.model.error.AppException
import com.artista.artista.ui.error.launchCatching
import com.artista.artista.ui.error.toMessageRes
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Immutable UI state displayed by the Search screen.
 *
 * @property query current text entered in the search field
 * @property selection current cursor or text selection range
 * @property savedArtworks artworks saved by the current user
 * @property typeValues artwork types included in submitted searches
 * @property recommendations saved artworks matching the current query
 * @property artworks results returned for the submitted query
 * @property isLoading whether submitted search results are being fetched
 * @property hasInvalidSearchInput whether the last search failed because its input was invalid
 * @property hasAlreadySearched whether a search as already been submitted
 * @property searchErrorMessageResId string resource of the message shown when submitting a query
 *   fails
 * @property saveErrorMessageResId string resource of the message shown when saving or unsaving an
 *   artwork fails
 * @author IJJA3141
 */
data class SearchScreenUiState(
    val query: String = "",
    val selection: TextRange = TextRange.Zero,
    val savedArtworks: List<Artwork> = emptyList(),
    val typeValues: List<ArtworkType> = ArtworkType.entries,
    val recommendations: List<Artwork> = emptyList(),
    val artworks: List<Artwork> = emptyList(),
    val isLoading: Boolean = false,
    val hasInvalidSearchInput: Boolean = false,
    val hasAlreadySearched: Boolean = false,
    @param:StringRes val searchErrorMessageResId: Int? = null,
    @param:StringRes val saveErrorMessageResId: Int? = null,
)

/**
 * Holds search input, recommendations, and artwork results for the search screen.
 *
 * @param savedArtworksRepository provides the current user's saved artworks for suggestions
 * @param wikiDataRepository searches Wikidata for submitted queries
 * @author IJJA3141
 */
class SearchScreenViewModel(
    private val savedArtworksRepository: SavedArtworksRepository,
    private val wikiDataRepository: WikiDataRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(SearchScreenUiState())
  private val _pendingSaveIds = mutableSetOf<ArtworkId>()
  private var searchJob: Job? = null
  private var searchRequestId = 0

  /** Current state displayed by the Search screen. */
  val uiState: StateFlow<SearchScreenUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launchCatching(
        tag = TAG,
        onError = { error ->
          if (error is AppException.Unauthorized) {
            // Signed-out users can still search; they just have no saved-artwork recommendations.
            _uiState.update { it.copy(savedArtworks = emptyList(), recommendations = emptyList()) }
          } else {
            _uiState.update { it.copy(searchErrorMessageResId = error.toMessageRes()) }
          }
        },
    ) {
      val savedArtworks = savedArtworksRepository.getSavedArtworks()
      _uiState.update {
        it.copy(
            savedArtworks = savedArtworks,
            recommendations = recommendationsFor(it.query, savedArtworks),
        )
      }
    }
  }

  /**
   * Updates the search text and its cursor or selection range.
   *
   * @param query text currently entered in the field
   * @param selection cursor or selected range in the field
   * @author IJJA3141
   */
  fun onSearchInputChanged(query: String, selection: TextRange) {
    val currentState = _uiState.value
    val queryChanged = query != currentState.query
    if (queryChanged && searchJob?.isActive == true) {
      searchRequestId++
      searchJob?.cancel()
    }
    _uiState.value =
        currentState.copy(
            query = query,
            selection = selection,
            recommendations = recommendationsFor(query, currentState.savedArtworks),
            artworks = currentState.artworks,
            isLoading = if (queryChanged) false else currentState.isLoading,
            searchErrorMessageResId = null,
            hasInvalidSearchInput = false,
        )
  }

  /**
   * Handles a query submitted from the search field.
   *
   * @param query the submitted search text
   * @author IJJA3141
   */
  fun onSearchSubmitted(query: String) {
    searchRequestId++
    val requestId = searchRequestId
    searchJob?.cancel()
    searchJob = null
    _uiState.value =
        _uiState.value.copy(
            query = query,
            recommendations = emptyList(),
            artworks = if (query.isBlank()) emptyList() else _uiState.value.artworks,
            isLoading = query.isNotBlank(),
            searchErrorMessageResId = null,
            hasInvalidSearchInput = false,
            hasAlreadySearched = query.isNotBlank(),
        )
    if (query.isBlank()) return
    val typeValues = _uiState.value.typeValues

    searchJob =
        viewModelScope.launchCatching(
            tag = TAG,
            onError = { error ->
              if (requestId == searchRequestId) {
                _uiState.update {
                  it.copy(
                      artworks = emptyList(),
                      isLoading = false,
                      searchErrorMessageResId = error.toMessageRes(),
                      hasInvalidSearchInput = error is AppException.InvalidInput,
                  )
                }
              }
            },
        ) {
          val results = wikiDataRepository.searchArtworks(query, typeValues)
          if (requestId == searchRequestId) {
            _uiState.update {
              it.copy(
                  artworks = results,
                  isLoading = false,
                  searchErrorMessageResId = null,
                  hasInvalidSearchInput = false,
              )
            }
          }
        }
  }

  /**
   * Toggles an artwork type included in submitted searches.
   *
   * The selection always retains at least one type because the repository requires a non-empty type
   * list.
   *
   * @param type artwork type to add or remove
   * @author IJJA3141
   */
  fun toggleArtworkType(type: ArtworkType) {
    _uiState.update { state ->
      val updatedTypes =
          if (type in state.typeValues) {
            if (state.typeValues.size == 1) return@update state
            state.typeValues - type
          } else {
            ArtworkType.entries.filter { it in state.typeValues || it == type }
          }
      state.copy(typeValues = updatedTypes)
    }
  }

  /**
   * Saves or removes an artwork from the current user's saved collection.
   *
   * @param artwork the artwork to toggle
   * @author IJJA3141
   */
  fun toggleSavedArtwork(artwork: Artwork) {
    // add() returns false if already present, so a second tap is dropped here, before launching.
    if (!_pendingSaveIds.add(artwork.uid)) return

    val isCurrentlySaved = artwork.uid in _uiState.value.savedArtworks.map { it.uid }

    viewModelScope
        .launchCatching(
            tag = TAG,
            onError = { error ->
              _uiState.update { it.copy(saveErrorMessageResId = error.toMessageRes()) }
            },
        ) {
          if (isCurrentlySaved) {
            savedArtworksRepository.removeArtworkFromSaved(artwork.uid)
          } else {
            savedArtworksRepository.saveArtwork(artwork)
          }

          val savedArtworks = savedArtworksRepository.getSavedArtworks()
          _uiState.update {
            it.copy(
                savedArtworks = savedArtworks,
                recommendations = recommendationsFor(it.query, savedArtworks),
                saveErrorMessageResId = null,
            )
          }
        }
        .invokeOnCompletion { _pendingSaveIds.remove(artwork.uid) }
  }

  /** Clears the search error after the UI has shown it. */
  fun clearSearchError() {
    _uiState.update { it.copy(searchErrorMessageResId = null) }
  }

  /** Clears the save-toggle error after the UI has shown it. */
  fun clearSaveError() {
    _uiState.update { it.copy(saveErrorMessageResId = null) }
  }

  private fun recommendationsFor(query: String, savedArtworks: List<Artwork>): List<Artwork> =
      if (query.isBlank()) {
        emptyList()
      } else {
        savedArtworks
            .asSequence()
            // Artworks without a name can never match a name search.
            .filter { artwork -> artwork.name?.contains(query, ignoreCase = true) == true }
            .distinct()
            .toList()
      }

  companion object {
    private const val TAG = "SearchScreenViewModel"

    /**
     * Creates a factory for a search ViewModel with its repository dependencies.
     *
     * @param savedArtworksRepository provides saved artworks for recommendations
     * @param wikiDataRepository provides artwork search results
     * @return factory that creates [SearchScreenViewModel] instances
     * @author hixeum
     */
    fun provideFactory(
        savedArtworksRepository: SavedArtworksRepository,
        wikiDataRepository: WikiDataRepository,
    ): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SearchScreenViewModel::class.java)) {
              return SearchScreenViewModel(savedArtworksRepository, wikiDataRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
          }
        }
  }
}
