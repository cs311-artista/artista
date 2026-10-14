// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkId
import com.artista.artista.model.artwork.SavedArtworksRepository
import com.artista.artista.model.artwork.WikiDataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
  private var savedArtworks: List<Artwork> = emptyList()

  private val _savedArtworkIds = MutableStateFlow<Set<ArtworkId>>(emptySet())

  /** Identifiers of artworks currently saved by the user. */
  val savedArtworkIds: StateFlow<Set<ArtworkId>> = _savedArtworkIds.asStateFlow()

  private val _searchQuery = MutableStateFlow("")

  /** The text currently entered in the search field. */
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  private val _recommendations = MutableStateFlow<List<String>>(emptyList())

  /** Recommendation strings to display in the search field dropdown. */
  val recommendations: StateFlow<List<String>> = _recommendations.asStateFlow()

  private val _artworks = MutableStateFlow<List<Artwork>>(emptyList())

  /** Artwork results to display below the search field. */
  val artworks: StateFlow<List<Artwork>> = _artworks.asStateFlow()

  init {
    viewModelScope.launch {
      refreshSavedArtworks()
      updateRecommendations(_searchQuery.value)
    }
  }

  /**
   * Updates the search text.
   *
   * @param query the text entered by the user
   * @author IJJA3141
   */
  fun onSearchQueryChanged(query: String) {
    _searchQuery.value = query
    updateRecommendations(query)
    if (query.isBlank()) {
      _artworks.value = emptyList()
    }
  }

  /**
   * Handles a query submitted from the search field.
   *
   * @param query the submitted search text
   * @author IJJA3141
   */
  fun onSearchSubmitted(query: String) {
    _searchQuery.value = query
    _recommendations.value = emptyList()
    if (query.isBlank()) {
      _artworks.value = emptyList()
      return
    }

    viewModelScope.launch { _artworks.value = wikiDataRepository.searchArtworks(query) }
  }

  /**
   * Handles a recommendation selected from the search dropdown.
   *
   * @param recommendation the selected recommendation
   * @author IJJA3141
   */
  @Suppress("UNUSED_PARAMETER")
  fun onRecommendationSelected(recommendation: String) {
    // TODO: should navigate to artwork screen
  }

  /**
   * Saves or removes an artwork from the current user's saved collection.
   *
   * @param artwork the artwork to toggle
   * @author IJJA3141
   */
  fun toggleSavedArtwork(artwork: Artwork) {
    val isCurrentlySaved = artwork.uid in _savedArtworkIds.value
    _savedArtworkIds.update { if (isCurrentlySaved) it - artwork.uid else it + artwork.uid }

    viewModelScope.launch {
      try {
        if (isCurrentlySaved) {
          savedArtworksRepository.removeArtworkFromSaved(artwork.uid)
        } else {
          savedArtworksRepository.saveArtwork(artwork)
        }
      } catch (e: Exception) {
        // Revert state if the operation failed
        _savedArtworkIds.update { if (isCurrentlySaved) it + artwork.uid else it - artwork.uid }
      }
    }
  }

  private suspend fun refreshSavedArtworks() {
    savedArtworks = savedArtworksRepository.getSavedArtworks()
    _savedArtworkIds.value = savedArtworks.map(Artwork::uid).toSet()
  }

  private fun updateRecommendations(query: String) {
    _recommendations.value =
        if (query.isBlank()) {
          emptyList()
        } else {
          savedArtworks
              .asSequence()
              .filter { artwork -> artwork.name.contains(query, ignoreCase = true) }
              .map(Artwork::name)
              .distinct()
              .toList()
        }
  }
}
