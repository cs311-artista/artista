// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.SavedArtworksRepository
import com.artista.artista.model.artwork.WikiDataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
  private val _savedArtworks = MutableStateFlow<List<Artwork>>(emptyList())
  var savedArtworks = _savedArtworks.asStateFlow()

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
    val isCurrentlySaved = artwork.uid in _savedArtworks.value.map { it.uid }

    viewModelScope.launch {
      try {
        if (isCurrentlySaved) {
          savedArtworksRepository.removeArtworkFromSaved(artwork.uid)
        } else {
          savedArtworksRepository.saveArtwork(artwork)
        }
      } catch (e: Exception) {
        // TODO: define error in interface and decide how to display it to the user
      }

      refreshSavedArtworks()
    }
  }

  private suspend fun refreshSavedArtworks() {
    _savedArtworks.value = savedArtworksRepository.getSavedArtworks()
  }

  private fun updateRecommendations(query: String) {
    _recommendations.value =
        if (query.isBlank()) {
          emptyList()
        } else {
          _savedArtworks.value
              .asSequence()
              .filter { artwork -> artwork.name.contains(query, ignoreCase = true) }
              .map(Artwork::name)
              .distinct()
              .toList()
        }
  }
}
