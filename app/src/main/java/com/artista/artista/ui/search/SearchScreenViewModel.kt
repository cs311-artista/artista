// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.search

import androidx.annotation.StringRes
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.artista.artista.model.artist.Artist
import com.artista.artista.model.artist.ArtistCandidate
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkCandidate
import com.artista.artista.model.artwork.ArtworkId
import com.artista.artista.model.artwork.ArtworkType
import com.artista.artista.model.artwork.SavedArtworksRepository
import com.artista.artista.model.artwork.SearchArea
import com.artista.artista.model.artwork.WikiDataRepository
import com.artista.artista.model.error.AppException
import com.artista.artista.model.location.LocationProvider
import com.artista.artista.ui.error.launchCatching
import com.artista.artista.ui.error.toMessageRes
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Immutable UI state displayed by the Search screen.
 *
 * @property selectedTab tab (artworks or artists) currently selected
 * @property isSuggesting whether the suggestions pane is visible
 * @property query current text entered in the search field
 * @property selection current cursor or text selection range
 * @property typeValues artwork types included in submitted searches
 * @property savedArtworks artworks saved by the current user
 * @property nearArtworks artworks near the users location
 * @property savedRecommendations saved artworks matching the current query
 * @property nearRecommendations artworks near the users location matching the current query
 * @property wikiDataRecommendations artworks from Wikidata matching the current query
 * @property artistRecommendations artists from Wikidata matching the current query
 * @property resultArtworks artwork results returned for the last submitted artwork query
 * @property resultArtist artist results returned for the last submitted artist query
 * @property isLoading whether a search is running on the selected tab (derived from [loadingTabs])
 * @property hasInvalidSearchInput whether the last search on the selected tab failed because its
 *   input was invalid (derived from [invalidInputTabs])
 * @property searchedTabs tabs on which a search has completed, successfully or not
 * @property fetchErrorMessageResId string resource of the message shown when fetching an artwork
 *   fails
 * @property saveErrorMessageResId string resource of the message shown when saving or unsaving an
 *   artwork fails
 * @property loadingTabs tabs with a submitted search currently running
 * @property invalidInputTabs tabs whose last search failed because its input was invalid
 * @author IJJA3141
 */
data class SearchScreenUiState(
    val selectedTab: SearchTab = SearchTab.ARTWORKS,
    val isSuggesting: Boolean = false,
    val query: String = "",
    val selection: TextRange = TextRange.Zero,
    val typeValues: List<ArtworkType> = ArtworkType.entries,
    val savedArtworks: List<Artwork> = emptyList(),
    val nearArtworks: List<Artwork> = emptyList(),
    val savedRecommendations: List<ArtworkCandidate> = emptyList(),
    val nearRecommendations: List<ArtworkCandidate> = emptyList(),
    val wikiDataRecommendations: List<ArtworkCandidate> = emptyList(),
    val artistRecommendations: List<ArtistCandidate> = emptyList(),
    val resultArtworks: List<Artwork> = emptyList(),
    val resultArtist: List<Artist> = emptyList(),
    val isLoading: Boolean = false,
    val hasInvalidSearchInput: Boolean = false,
    val searchedTabs: Set<SearchTab> = emptySet(),
    @param:StringRes val fetchErrorMessageResId: Int? = null,
    @param:StringRes val saveErrorMessageResId: Int? = null,
    val loadingTabs: Set<SearchTab> = emptySet(),
    val invalidInputTabs: Set<SearchTab> = emptySet(),
) {
  /** Whether a search has completed on the selected tab, so an empty result means "no match". */
  val hasAlreadySearched: Boolean
    get() = selectedTab in searchedTabs
}

/**
 * Holds search input, recommendations, and results for the search screen.
 *
 * Each tab has its own search request, loading flag and invalid-input flag, so switching tabs never
 * cancels a search or leaks one tab's status onto the other. Suggestions are a single request that
 * always belongs to the selected tab.
 *
 * @param savedArtworksRepository provides the current user's saved artworks for suggestions
 * @param wikiDataRepository searches Wikidata for submitted queries
 * @param locationClient provides the user's location for nearby artwork searches
 * @author IJJA3141
 */
class SearchScreenViewModel(
    private val savedArtworksRepository: SavedArtworksRepository,
    private val wikiDataRepository: WikiDataRepository,
    private val locationClient: LocationProvider,
) : ViewModel() {
  private val _uiState = MutableStateFlow(SearchScreenUiState())
  private val _pendingSaveIds = mutableSetOf<ArtworkId>()

  /** One request slot per tab for submitted searches. */
  private val searchSlots = SearchTab.entries.associateWith { RequestSlot() }

  /** Request slot for suggestions of the selected tab. */
  private val suggestionSlot = RequestSlot()

  /** Current state displayed by the Search screen. */
  val uiState: StateFlow<SearchScreenUiState> = _uiState.asStateFlow()

  init {
    loadSavedArtworks()
    loadNearbyArtworks()
  }

  /**
   * Updates the search text and its cursor or selection range.
   *
   * Only a change of the text restarts suggestions and cancels pending searches; moving the cursor
   * does not.
   *
   * @param query text currently entered in the field
   * @param selection cursor or selected range in the field
   * @author IJJA3141
   */
  fun onSearchInputChanged(query: String, selection: TextRange) {
    if (query == _uiState.value.query) {
      updateState { it.copy(selection = selection) }
      return
    }

    // Running searches were for the old text.
    searchSlots.values.forEach { it.cancel() }
    updateState { state ->
      state
          .copy(
              query = query,
              selection = selection,
              loadingTabs = emptySet(),
              invalidInputTabs = emptySet(),
              fetchErrorMessageResId = null,
          )
          // Suggestions of the other tab are for the old text and would flash when switching.
          .withoutRemoteSuggestions(SearchTab.entries - state.selectedTab)
          .withLocalRecommendations()
    }
    refreshSuggestions(debounce = true)
  }

  /**
   * Handles a query submitted from the search field, on the selected tab.
   *
   * @param query the submitted search text
   * @author IJJA3141
   */
  fun onSearchSubmitted(query: String) {
    if (query.isBlank()) return

    // Captured now: the user may switch tab while the request is running.
    val state = _uiState.value
    val tab = state.selectedTab
    val typeValues = state.typeValues

    suggestionSlot.cancel()
    val slot = searchSlots.getValue(tab)
    val requestId = slot.cancel()

    updateState {
      it.copy(
              query = query,
              isSuggesting = false,
              loadingTabs = it.loadingTabs + tab,
              invalidInputTabs = it.invalidInputTabs - tab,
              fetchErrorMessageResId = null,
          )
          .withLocalRecommendations()
    }

    slot.track(
        viewModelScope.launchCatching(
            tag = TAG,
            onError = { error ->
              finishSearch(slot, requestId, tab) {
                it.withoutResults(tab)
                    .copy(
                        fetchErrorMessageResId = error.toMessageRes(),
                        invalidInputTabs =
                            if (error is AppException.InvalidInput) it.invalidInputTabs + tab
                            else it.invalidInputTabs,
                    )
              }
            },
        ) {
          finishSearch(slot, requestId, tab, fetchResults(tab, query, typeValues))
        }
    )
  }

  /**
   * Switches between searching artworks and artists.
   *
   * Searches keep running in the background; each tab shows its own loading state and results.
   *
   * @param tab tab to select
   */
  fun onSearchTabSelected(tab: SearchTab) {
    if (_uiState.value.selectedTab == tab) return

    suggestionSlot.cancel()
    updateState { it.copy(selectedTab = tab) }
    if (_uiState.value.isSuggesting) refreshSuggestions(debounce = false)
  }

  /**
   * Opens or closes the suggestions pane.
   *
   * Opening it fetches suggestions for the current query right away (the repository caches them),
   * and closing it drops any pending suggestion request.
   *
   * @param isSuggesting whether the pane should be visible
   */
  fun onSuggestingChange(isSuggesting: Boolean) {
    if (_uiState.value.isSuggesting == isSuggesting) return

    updateState { it.copy(isSuggesting = isSuggesting) }
    if (isSuggesting) refreshSuggestions(debounce = false) else suggestionSlot.cancel()
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
    val current = _uiState.value.typeValues
    val updated =
        if (type in current) {
          if (current.size == 1) return
          current - type
        } else {
          ArtworkType.entries.filter { it in current || it == type }
        }

    updateState { it.copy(typeValues = updated) }

    // Artwork types only affect artwork suggestions; never keep ones fetched with other types.
    if (_uiState.value.selectedTab == SearchTab.ARTWORKS) {
      refreshSuggestions(debounce = false)
    } else {
      updateState { it.withoutRemoteSuggestions(listOf(SearchTab.ARTWORKS)) }
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

    val isCurrentlySaved = _uiState.value.savedArtworks.any { it.uid == artwork.uid }

    viewModelScope
        .launchCatching(
            tag = TAG,
            onError = { error ->
              updateState { it.copy(saveErrorMessageResId = error.toMessageRes()) }
            },
        ) {
          if (isCurrentlySaved) savedArtworksRepository.removeArtworkFromSaved(artwork.uid)
          else savedArtworksRepository.saveArtwork(artwork)

          refreshSavedArtwork()
        }
        .invokeOnCompletion { _pendingSaveIds.remove(artwork.uid) }
  }

  /** Clears the search error after the UI has shown it. */
  fun clearSearchError() {
    updateState { it.copy(fetchErrorMessageResId = null) }
  }

  /** Clears the save-toggle error after the UI has shown it. */
  fun clearSaveError() {
    updateState { it.copy(saveErrorMessageResId = null) }
  }

  // region Loading of local recommendation sources

  private fun loadSavedArtworks() {
    viewModelScope.launchCatching(
        tag = TAG,
        onError = { error ->
          if (error is AppException.Unauthorized) {
            // Signed-out users can still search; they just have no saved-artwork recommendations.
            updateState { it.copy(savedArtworks = emptyList()).withLocalRecommendations() }
          } else {
            updateState { it.copy(fetchErrorMessageResId = error.toMessageRes()) }
          }
        },
    ) {
      refreshSavedArtwork()
    }
  }

  private fun loadNearbyArtworks() {
    viewModelScope.launchCatching(
        tag = TAG,
        onError = { error ->
          if (
              error is AppException.InvalidData ||
                  error is AppException.NotFound ||
                  error is AppException.Network
          ) {
            // Unlocated users can still search; they just have no near-artwork recommendations.
            updateState { it.copy(nearArtworks = emptyList()).withLocalRecommendations() }
          } else {
            updateState { it.copy(fetchErrorMessageResId = error.toMessageRes()) }
          }
        },
    ) {
      val userLocation = locationClient.getUserLocation()
      val searchArea = SearchArea(userLocation.latitude, userLocation.longitude, 50.0)

      // The selected artwork types are deliberately not passed: all nearby artworks are fetched and
      // then filtered locally by the query.
      val nearArtworks = wikiDataRepository.getArtworksNearLocation(searchArea)

      updateState { it.copy(nearArtworks = nearArtworks).withLocalRecommendations() }
    }
  }

  private suspend fun refreshSavedArtwork() {
    val savedArtworks = savedArtworksRepository.getSavedArtworks()
    updateState {
      it.copy(savedArtworks = savedArtworks, saveErrorMessageResId = null)
          .withLocalRecommendations()
    }
  }

  // endregion

  // region Suggestions

  /**
   * Fetches suggestions for the selected tab and the current query.
   *
   * A query that is too short only clears the remote suggestions. Failures are ignored on purpose:
   * suggestions are best effort, so the previous ones stay visible.
   *
   * @param debounce whether to wait for a pause in typing first; false for actions that are not
   *   typing, such as switching tab or opening the pane
   */
  private fun refreshSuggestions(debounce: Boolean) {
    val requestId = suggestionSlot.cancel()

    val state = _uiState.value
    val query = state.query.trim()
    if (query.length < MIN_SUGGESTION_LENGTH) {
      updateState { it.withoutRemoteSuggestions() }
      return
    }

    val tab = state.selectedTab
    val typeValues = state.typeValues

    suggestionSlot.track(
        viewModelScope.launchCatching(
            tag = TAG,
            onError = { /* best effort: keep the previous suggestions */ },
        ) {
          if (debounce) delay(SUGGESTION_DEBOUNCE_MS.milliseconds)

          val applySuggestions = fetchSuggestions(tab, query, typeValues)
          if (suggestionSlot.isCurrent(requestId)) updateState(applySuggestions)
        }
    )
  }

  private suspend fun fetchSuggestions(
      tab: SearchTab,
      query: String,
      typeValues: List<ArtworkType>,
  ): (SearchScreenUiState) -> SearchScreenUiState =
      when (tab) {
        SearchTab.ARTWORKS ->
            wikiDataRepository.getArtworkCandidates(query, typeValues).let { candidates ->
              { state: SearchScreenUiState -> state.copy(wikiDataRecommendations = candidates) }
            }
        SearchTab.ARTISTS ->
            wikiDataRepository.getArtistCandidates(query).let { candidates ->
              { state: SearchScreenUiState -> state.copy(artistRecommendations = candidates) }
            }
      }

  // endregion

  // region Submitted searches

  private suspend fun fetchResults(
      tab: SearchTab,
      query: String,
      typeValues: List<ArtworkType>,
  ): (SearchScreenUiState) -> SearchScreenUiState =
      when (tab) {
        SearchTab.ARTWORKS ->
            wikiDataRepository.getArtworksFromQuery(query, typeValues).let { results ->
              { state: SearchScreenUiState -> state.copy(resultArtworks = results) }
            }
        SearchTab.ARTISTS ->
            wikiDataRepository.getArtistsFromQuery(query).let { results ->
              { state: SearchScreenUiState -> state.copy(resultArtist = results) }
            }
      }

  /**
   * Applies a finished search of [tab] and marks the tab as searched and no longer loading.
   *
   * Ignored when a newer request has started on the same slot since [requestId].
   */
  private fun finishSearch(
      slot: RequestSlot,
      requestId: Int,
      tab: SearchTab,
      transform: (SearchScreenUiState) -> SearchScreenUiState,
  ) {
    if (!slot.isCurrent(requestId)) return

    updateState { state ->
      val next = transform(state)
      next.copy(loadingTabs = next.loadingTabs - tab, searchedTabs = next.searchedTabs + tab)
    }
  }

  // endregion

  // region State helpers

  /**
   * Applies [transform] and re-derives the selected tab's [SearchScreenUiState.isLoading] and
   * [SearchScreenUiState.hasInvalidSearchInput] from the per-tab sets, so they cannot go stale.
   */
  private fun updateState(transform: (SearchScreenUiState) -> SearchScreenUiState) {
    _uiState.update { state ->
      val next = transform(state)
      next.copy(
          isLoading = next.selectedTab in next.loadingTabs,
          hasInvalidSearchInput = next.selectedTab in next.invalidInputTabs,
      )
    }
  }

  private fun SearchScreenUiState.withoutResults(tab: SearchTab): SearchScreenUiState =
      when (tab) {
        SearchTab.ARTWORKS -> copy(resultArtworks = emptyList())
        SearchTab.ARTISTS -> copy(resultArtist = emptyList())
      }

  private fun SearchScreenUiState.withoutRemoteSuggestions(
      tabs: Collection<SearchTab> = SearchTab.entries
  ): SearchScreenUiState =
      copy(
          wikiDataRecommendations =
              if (SearchTab.ARTWORKS in tabs) emptyList() else wikiDataRecommendations,
          artistRecommendations =
              if (SearchTab.ARTISTS in tabs) emptyList() else artistRecommendations,
      )

  /** Recomputes the local (saved and nearby) recommendations from the current query. */
  private fun SearchScreenUiState.withLocalRecommendations(): SearchScreenUiState =
      copy(
          savedRecommendations = savedArtworks.matching(query),
          nearRecommendations = nearArtworks.matching(query),
      )

  private fun List<Artwork>.matching(query: String): List<ArtworkCandidate> =
      if (query.isBlank()) {
        emptyList()
      } else {
        distinctBy { it.uid }
            .mapNotNull { artwork ->
              // Artworks without a name can never match a name search.
              artwork.name
                  ?.takeIf { it.contains(query, ignoreCase = true) }
                  ?.let { name -> ArtworkCandidate(artwork.uid, name, artwork.artist?.name) }
            }
      }

  // endregion

  /**
   * Tracks the latest request of one kind.
   *
   * [cancel] cancels the running job and starts a new generation, and a finished request is applied
   * only while [isCurrent] still holds for the generation it started with. The check also covers
   * the window where a result arrives just as the job is cancelled.
   */
  private class RequestSlot {
    private var generation = 0
    private var job: Job? = null

    /** Cancels the running request and returns the id of the next one. */
    fun cancel(): Int {
      job?.cancel()
      job = null
      return ++generation
    }

    fun track(job: Job) {
      this.job = job
    }

    fun isCurrent(id: Int): Boolean = id == generation
  }

  companion object {
    private const val TAG = "SearchScreenViewModel"
    private const val SUGGESTION_DEBOUNCE_MS = 350L
    private const val MIN_SUGGESTION_LENGTH = 2

    /**
     * Creates a factory for a search ViewModel with its repository dependencies.
     *
     * @param savedArtworksRepository provides saved artworks for recommendations
     * @param wikiDataRepository provides artwork search results
     * @param locationClient provides the user's location for nearby artworks
     * @return factory that creates [SearchScreenViewModel] instances
     * @author hixeum
     */
    fun provideFactory(
        savedArtworksRepository: SavedArtworksRepository,
        wikiDataRepository: WikiDataRepository,
        locationClient: LocationProvider,
    ): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SearchScreenViewModel::class.java)) {
              return SearchScreenViewModel(
                  savedArtworksRepository,
                  wikiDataRepository,
                  locationClient,
              )
                  as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
          }
        }
  }
}
