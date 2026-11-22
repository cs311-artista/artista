// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.search

import androidx.annotation.StringRes
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkFilter
import com.artista.artista.model.artwork.ArtworkId
import com.artista.artista.model.artwork.ArtworkType
import com.artista.artista.model.artwork.SavedArtworksRepository
import com.artista.artista.model.artwork.WikiDataRepository
import com.artista.artista.model.artwork.applyFilter
import com.artista.artista.model.artwork.artistOptions
import com.artista.artista.model.artwork.locationOptions
import com.artista.artista.model.artwork.museumOptions
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
 * @property activeFilter criteria applied to the search: its types restrict the query sent to
 *   Wikidata, and its other criteria filter the returned results
 * @property artistOptions distinct artists from the unfiltered results
 * @property museumOptions distinct museums from the unfiltered results
 * @property locationOptions distinct location names from the unfiltered results
 * @property recommendations saved artworks matching the current query
 * @property artworks results returned for the submitted query, filtered by [activeFilter]
 * @property isLoading whether submitted search results are being fetched
 * @property hasInvalidSearchInput whether the last search failed because its input was invalid
 * @property hasAlreadySearched whether a search as already been submitted
 * @property fetchErrorMessageResId string resource of the message shown when fetching an artwork
 *   fails
 * @property saveErrorMessageResId string resource of the message shown when saving or unsaving an
 *   artwork fails
 * @author IJJA3141, hixeum
 */
data class SearchScreenUiState(
    val query: String = "",
    val selection: TextRange = TextRange.Zero,
    val savedArtworks: List<Artwork> = emptyList(),
    val activeFilter: ArtworkFilter = ArtworkFilter(),
    val artistOptions: List<String> = emptyList(),
    val museumOptions: List<String> = emptyList(),
    val locationOptions: List<String> = emptyList(),
    val recommendations: List<Artwork> = emptyList(),
    val artworks: List<Artwork> = emptyList(),
    val isLoading: Boolean = false,
    val hasInvalidSearchInput: Boolean = false,
    val hasAlreadySearched: Boolean = false,
    @param:StringRes val fetchErrorMessageResId: Int? = null,
    @param:StringRes val saveErrorMessageResId: Int? = null,
)

/**
 * Holds search input, recommendations, and artwork results for the search screen.
 *
 * @param savedArtworksRepository provides the current user's saved artworks for suggestions
 * @param wikiDataRepository searches Wikidata for submitted queries
 * @author IJJA3141, hixeum
 */
class SearchScreenViewModel(
    private val savedArtworksRepository: SavedArtworksRepository,
    private val wikiDataRepository: WikiDataRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(SearchScreenUiState())
  private val _pendingSaveIds = mutableSetOf<ArtworkId>()
  private var searchJob: Job? = null
  private var searchRequestId = 0

  // Results of the last search before the client-side criteria, so that changing those criteria
  // does not need a new request.
  private var allResults: List<Artwork> = emptyList()

  // The query of the last search, kept apart from the field's text, which may have been edited
  // since. A changed type selection re-runs this query, not the unsubmitted text.
  private var lastSubmittedQuery: String? = null

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
            _uiState.update { it.copy(fetchErrorMessageResId = error.toMessageRes()) }
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
            fetchErrorMessageResId = null,
            hasInvalidSearchInput = false,
        )
  }

  /**
   * Handles a query submitted from the search field.
   *
   * @param query the submitted search text
   * @author IJJA3141, hixeum
   */
  fun onSearchSubmitted(query: String) {
    if (query.isBlank()) return
    _uiState.update { it.copy(query = query, recommendations = emptyList()) }
    search(query)
  }

  /**
   * Applies filter criteria to the search.
   *
   * The types restrict what Wikidata returns, so a changed type selection re-runs the last
   * submitted query. The other criteria only filter the results already fetched, so they apply at
   * once without a new request.
   *
   * @param filter criteria to apply
   * @author hixeum
   */
  fun applyFilter(filter: ArtworkFilter) {
    val typesChanged = filter.types != _uiState.value.activeFilter.types
    _uiState.update {
      it.copy(activeFilter = filter, artworks = allResults.applyClientSideCriteria(filter))
    }
    val query = lastSubmittedQuery
    if (typesChanged && query != null) search(query)
  }

  /**
   * Fetches the artworks matching [query] and the active types, replacing the current results.
   *
   * Only the latest request may update the state: an earlier one still running is cancelled, and a
   * result arriving after a newer request started is ignored.
   */
  private fun search(query: String) {
    lastSubmittedQuery = query
    searchRequestId++
    val requestId = searchRequestId
    searchJob?.cancel()
    searchJob = null
    _uiState.update {
      it.copy(
          isLoading = true,
          fetchErrorMessageResId = null,
          hasInvalidSearchInput = false,
          hasAlreadySearched = true,
      )
    }
    val types = _uiState.value.activeFilter.types
    // Keeps the enum order so the same selection always builds the same, cacheable, query. No
    // selected type means no type criterion, but the repository needs at least one type.
    val typeValues = ArtworkType.entries.filter { it in types }.ifEmpty { ArtworkType.entries }

    searchJob =
        viewModelScope.launchCatching(
            tag = TAG,
            onError = { error ->
              if (requestId == searchRequestId) {
                showResults(emptyList())
                _uiState.update {
                  it.copy(
                      isLoading = false,
                      fetchErrorMessageResId = error.toMessageRes(),
                      hasInvalidSearchInput = error is AppException.InvalidInput,
                  )
                }
              }
            },
        ) {
          val results = wikiDataRepository.searchArtworks(query, typeValues)
          if (requestId == searchRequestId) {
            showResults(results)
            _uiState.update {
              it.copy(
                  isLoading = false,
                  fetchErrorMessageResId = null,
                  hasInvalidSearchInput = false,
              )
            }
          }
        }
  }

  /** Replaces the results, filtering them by the active criteria and rebuilding the options. */
  private fun showResults(results: List<Artwork>) {
    allResults = results
    _uiState.update {
      it.copy(
          artworks = results.applyClientSideCriteria(it.activeFilter),
          artistOptions = artistOptions(results),
          museumOptions = museumOptions(results),
          locationOptions = locationOptions(results),
      )
    }
  }

  /**
   * Applies every criterion of [filter] except the types.
   *
   * Wikidata already restricted the results to the selected types, so filtering them again here
   * would only drop results whose type the app failed to parse.
   */
  private fun List<Artwork>.applyClientSideCriteria(filter: ArtworkFilter): List<Artwork> =
      applyFilter(filter.copy(types = emptySet()))

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
    _uiState.update { it.copy(fetchErrorMessageResId = null) }
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
