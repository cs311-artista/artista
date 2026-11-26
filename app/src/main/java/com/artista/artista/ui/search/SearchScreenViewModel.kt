// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.search

import android.util.Log
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
 * @property selectedTab currently selected tab
 * @property isSuggesting whether the suggestions pane is visible
 * @property query current text entered in the search field
 * @property selection current cursor or text selection range
 * @property typeValues artwork types included in submitted searches
 * @property savedArtworks artworks saved by the current user
 * @property nearArtworks artworks near the users location
 * @property savedRecommendations saved artworks matching the current query
 * @property nearRecommendations artworks near the users location matching the current query
 * @property wikiDataRecommendations artworks from Wikidata matching the current query
 * @property resultArtworks results returned for the submitted query
 * @property isLoading whether submitted search results are being fetched
 * @property hasInvalidSearchInput whether the last search failed because its input was invalid
 * @property hasAlreadySearched whether a search as already been submitted
 * @property fetchErrorMessageResId string resource of the message shown when fetching an artwork
 *   fails
 * @property saveErrorMessageResId string resource of the message shown when saving or unsaving an
 *   artwork fails
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
    val resultArtworks: List<Artwork> = emptyList(),
    val artistRecommendations: List<ArtistCandidate> = emptyList(),
    val resultArtist: List<Artist> = emptyList(),
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
    private var searchJob: Job? = null
    private var suggestionJob: Job? = null
    private var searchRequestId = 0

    /** Current state displayed by the Search screen. */
    val uiState: StateFlow<SearchScreenUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launchCatching(
            tag = TAG,
            onError = { error ->
                if (error is AppException.Unauthorized) {
                    // Signed-out users can still search; they just have no saved-artwork recommendations.
                    _uiState.update {
                        it.copy(savedArtworks = emptyList(), savedRecommendations = emptyList())
                    }
                } else {
                    _uiState.update { it.copy(fetchErrorMessageResId = error.toMessageRes()) }
                }
            },
        ) {
            refreshSavedArtwork()
        }

        viewModelScope.launchCatching(
            tag = TAG,
            onError = { error ->
                if (
                    error is AppException.InvalidData ||
                    error is AppException.NotFound ||
                    error is AppException.Network
                ) {
                    // Unlocated users can still search; they just have no near-artwork recommendations.
                    _uiState.update {
                        it.copy(nearArtworks = emptyList(), nearRecommendations = emptyList())
                    }
                } else {
                    _uiState.update { it.copy(fetchErrorMessageResId = error.toMessageRes()) }
                }
            },
        ) {
            val userLocation = locationClient.getUserLocation()
            val searchArea = SearchArea(userLocation.latitude, userLocation.longitude, 50.0)

            // _uiState.value.typeValues is voluntarily not given to fetch all artwork and then filter
            // with `getRecommendation`
            val nearArtworks = wikiDataRepository.getArtworksNearLocation(searchArea)

            _uiState.update {
                it.copy(
                    nearArtworks = nearArtworks,
                    nearRecommendations = recommendationsFor(it.query, nearArtworks),
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
        val queryChanged = query != _uiState.value.query
        val tooShort = query.trim().length < MIN_SUGGESTION_LENGTH

        if (!queryChanged) {
            _uiState.update {
                it.copy(
                    query = query,
                    selection = selection,
                    fetchErrorMessageResId = null,
                    hasInvalidSearchInput = false,
                    isLoading = it.isLoading,
                )
            }

            return // cursor moves must not restart anything
        }

        if(_uiState.value.selectedTab == SearchTab.ARTWORKS) {
            _uiState.update {
                it.copy(
                    query = query,
                    selection = selection,
                    savedRecommendations = recommendationsFor( query, it.savedArtworks ),
                    nearRecommendations = recommendationsFor( query, it.nearArtworks ),
                    wikiDataRecommendations = if(tooShort) emptyList() else it.wikiDataRecommendations,
                    artistRecommendations =if(tooShort) emptyList() else it.artistRecommendations,
                    fetchErrorMessageResId = null,
                    hasInvalidSearchInput = false,
                    isLoading = false,
                )
            }
        }

        searchRequestId++ // invalidates a running submit and any pending suggestion
        searchJob?.cancel()
        scheduleSuggestions(query.trim())
    }

    /**
     * Handles a query submitted from the search field.
     *
     * @param query the submitted search text
     * @author IJJA3141
     */
    fun onSearchSubmitted(query: String) {
        if (query.isBlank()) return
        searchRequestId++
        val requestId = searchRequestId
        searchJob?.cancel()
        searchJob = null
        _uiState.value =
            _uiState.value.copy(
                query = query,
                savedRecommendations = emptyList(),
                resultArtworks = if (query.isBlank()) emptyList() else _uiState.value.resultArtworks,
                resultArtist = if (query.isBlank()) emptyList() else _uiState.value.resultArtist,
                isLoading = query.isNotBlank(),
                fetchErrorMessageResId = null,
                hasInvalidSearchInput = false,
                hasAlreadySearched = true,
            )
        val typeValues = _uiState.value.typeValues

        searchJob =
            viewModelScope.launchCatching(
                tag = TAG,
                onError = { error ->
                    if (requestId == searchRequestId) {
                        _uiState.update {
                            it.copy(
                                resultArtworks = emptyList(),
                                resultArtist = emptyList(),
                                isLoading = false,
                                fetchErrorMessageResId = error.toMessageRes(),
                                hasInvalidSearchInput = error is AppException.InvalidInput,
                            )
                        }
                    }
                },
            ) {
                if(_uiState.value.selectedTab == SearchTab.ARTWORKS) {
                    val results = wikiDataRepository.getArtworksFromQuery(query, typeValues)

                    if (requestId == searchRequestId) {
                        _uiState.update {
                            it.copy(
                                resultArtworks = results,
                                isLoading = false,
                                fetchErrorMessageResId = null,
                                hasInvalidSearchInput = false,
                            )
                        }
                    }
                } else {
                    val results = wikiDataRepository.getArtistsFromQuery(query)

                    if (requestId == searchRequestId) {
                        _uiState.update {
                            it.copy(
                                resultArtist = results,
                                isLoading = false,
                                fetchErrorMessageResId = null,
                                hasInvalidSearchInput = false,
                            )
                        }
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
            state.copy(
                typeValues = updatedTypes,
                savedRecommendations = recommendationsFor(state.query, state.savedArtworks),
                nearRecommendations = recommendationsFor(state.query, state.nearArtworks),
            )
        }

        scheduleSuggestions(_uiState.value.query.trim())
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
                if (isCurrentlySaved) savedArtworksRepository.removeArtworkFromSaved(artwork.uid)
                else savedArtworksRepository.saveArtwork(artwork)

                refreshSavedArtwork()
            }
            .invokeOnCompletion { _pendingSaveIds.remove(artwork.uid) }
    }

    private suspend fun refreshSavedArtwork() {
        val savedArtworks = savedArtworksRepository.getSavedArtworks()
        _uiState.update {
            it.copy(
                savedArtworks = savedArtworks,
                savedRecommendations = recommendationsFor(it.query, savedArtworks),
                saveErrorMessageResId = null,
            )
        }
    }

    private fun scheduleSuggestions(query: String) {
        suggestionJob?.cancel()
        if (query.length < MIN_SUGGESTION_LENGTH) return
        val requestId = searchRequestId

        suggestionJob =
            viewModelScope.launchCatching(
                tag = TAG,
                onError = { /*TODO(best effort: keep the previous suggestions)*/ },
            ) {
                delay(SUGGESTION_DEBOUNCE_MS.milliseconds)
                if(_uiState.value.selectedTab == SearchTab.ARTWORKS) {
                    val candidates =
                        wikiDataRepository.getArtworkCandidates(query, _uiState.value.typeValues)

                    if (requestId == searchRequestId) {
                        _uiState.update { it.copy(wikiDataRecommendations = candidates) }
                    }
                }
                else {
                 val candidates =    wikiDataRepository.getArtistCandidates(query)

                    if (requestId == searchRequestId) {
                        _uiState.update { it.copy(artistRecommendations = candidates) }
                    }
                }
            }
    }

    fun onSearchTabSelected(tab: SearchTab)
    {
        if(_uiState.value.selectedTab == tab) return

        _uiState.update { it.copy(selectedTab = tab ) }
        searchRequestId++ // invalidates a running submit and any pending suggestion
        searchJob?.cancel()
        scheduleSuggestions(_uiState.value.query.trim())
    }

    fun onSuggestingChange(isSuggesting: Boolean)
    {
        _uiState.update { it.copy(isSuggesting=isSuggesting) }
    }

    /** Clears the search error after the UI has shown it. */
    fun clearSearchError() {
        _uiState.update { it.copy(fetchErrorMessageResId = null) }
    }

    /** Clears the save-toggle error after the UI has shown it. */
    fun clearSaveError() {
        _uiState.update { it.copy(saveErrorMessageResId = null) }
    }

    private fun recommendationsFor(
        query: String,
        savedArtworks: List<Artwork>,
    ): List<ArtworkCandidate> =
        if (query.isBlank()) {
            emptyList()
        } else {
            savedArtworks
                .asSequence()
                // Artworks without a name can never match a name search.
                .filter { artwork -> artwork.name?.contains(query, ignoreCase = true) == true }
                .distinctBy { it.uid }
                .toList()
                .map { ArtworkCandidate(it.uid, it.name ?: "", it.artist?.name) }
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
