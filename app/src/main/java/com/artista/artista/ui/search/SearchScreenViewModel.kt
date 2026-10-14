// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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
  private var savedArtworks: List<Artwork> = emptyList()

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
      savedArtworks = savedArtworksRepository.getSavedArtworks()
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

  companion object {
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
