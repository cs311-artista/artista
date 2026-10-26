// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.search

import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkRepositoryProvider
import com.artista.artista.model.artwork.SavedArtworksRepository
import com.artista.artista.model.artwork.WikiDataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Immutable UI state displayed by the Search screen.
 *
 * @property query current text entered in the search field
 * @property selection current cursor or text selection range
 * @property savedArtworks artworks saved by the current user
 * @property recommendations saved artworks matching the current query
 * @property artworks results returned for the submitted query
 * @property isLoading whether submitted search results are being fetched
 * @author IJJA3141
 */
data class SearchScreenUiState(
    val query: String = "",
    val selection: TextRange = TextRange.Zero,
    val savedArtworks: List<Artwork> = emptyList(),
    val recommendations: List<Artwork> = emptyList(),
    val artworks: List<Artwork> = emptyList(),
    val isLoading: Boolean = false,
)

/**
 * Holds search input, recommendations, and artwork results for the search screen.
 *
 * @param savedArtworksRepository provides the current user's saved artworks for suggestions
 * @param wikiDataRepository searches Wikidata for submitted queries
 * @author IJJA3141
 */
class SearchScreenViewModel(
    private val savedArtworksRepository: SavedArtworksRepository =
        ArtworkRepositoryProvider.repository,
    private val wikiDataRepository: WikiDataRepository = ArtworkRepositoryProvider.repository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(SearchScreenUiState())

  /** Current state displayed by the Search screen. */
  val uiState: StateFlow<SearchScreenUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      val savedArtworks = savedArtworksRepository.getSavedArtworks()
      _uiState.value =
          _uiState.value.copy(
              savedArtworks = savedArtworks,
              recommendations = recommendationsFor(_uiState.value.query, savedArtworks),
          )
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
    _uiState.value =
        currentState.copy(
            query = query,
            selection = selection,
            recommendations = recommendationsFor(query, currentState.savedArtworks),
            artworks = if (query.isBlank()) emptyList() else currentState.artworks,
        )
  }

  /**
   * Handles a query submitted from the search field.
   *
   * @param query the submitted search text
   * @author IJJA3141
   */
  fun onSearchSubmitted(query: String) {
    _uiState.value =
        _uiState.value.copy(
            query = query,
            recommendations = emptyList(),
            artworks = if (query.isBlank()) emptyList() else _uiState.value.artworks,
            isLoading = query.isNotBlank(),
        )
    if (query.isBlank()) return

    viewModelScope.launch {
      try {
        val results = wikiDataRepository.searchArtworks(query)
        _uiState.value = _uiState.value.copy(artworks = results)
      } catch (e: Exception) {
          // TODO: define error in interface and decide how to display it to the user

          // this line ensures that if an error occurs, the artworks list is cleared to avoid showing stale data
          _uiState.value = _uiState.value.copy(artworks = emptyList())
      }finally {
        _uiState.value = _uiState.value.copy(isLoading = false)
      }
    }
  }

  /**
   * Saves or removes an artwork from the current user's saved collection.
   *
   * @param artwork the artwork to toggle
   * @author IJJA3141
   */
  fun toggleSavedArtwork(artwork: Artwork) {
    val isCurrentlySaved = artwork.uid in _uiState.value.savedArtworks.map { it.uid }

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

      val savedArtworks = savedArtworksRepository.getSavedArtworks()
      _uiState.value =
          _uiState.value.copy(
              savedArtworks = savedArtworks,
              recommendations = recommendationsFor(_uiState.value.query, savedArtworks),
          )
    }
  }

  private fun recommendationsFor(query: String, savedArtworks: List<Artwork>): List<Artwork> =
      if (query.isBlank()) {
        emptyList()
      } else {
        savedArtworks
            .asSequence()
            .filter { artwork -> artwork.name.contains(query, ignoreCase = true) }
            .distinct()
            .toList()
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
