// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.search

import androidx.lifecycle.ViewModel
import com.artista.artista.model.artwork.Artwork
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds search input, recommendations, and artwork results for the search screen.
 *
 * @author IJJA3141
 */
class SearchScreenViewModel : ViewModel() {
  private val _searchQuery = MutableStateFlow("")

  /** The text currently entered in the search field. */
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  private val _recommendations = MutableStateFlow<List<String>>(emptyList())

  /** Recommendation strings to display in the search field dropdown. */
  val recommendations: StateFlow<List<String>> = _recommendations.asStateFlow()

  private val _artworks = MutableStateFlow<List<Artwork>>(emptyList())

  /** Artwork results to display below the search field. */
  val artworks: StateFlow<List<Artwork>> = _artworks.asStateFlow()

  /**
   * Updates the search text.
   *
   * @param query the text entered by the user
   * @author IJJA3141
   */
  fun onSearchQueryChanged(query: String) {
    _searchQuery.value = query
  }

  /**
   * Handles a query submitted from the search field.
   *
   * @param query the submitted search text
   * @author IJJA3141
   */
  fun onSearchSubmitted(query: String) {
    _searchQuery.value = query
    // TODO: Search the artwork repository and publish the results.
  }

  /**
   * Handles a recommendation selected from the search dropdown.
   *
   * @param recommendation the selected recommendation
   * @author IJJA3141
   */
  @Suppress("UNUSED_PARAMETER")
  fun onRecommendationSelected(recommendation: String) {
    // TODO: Define and implement recommendation selection behavior.
  }
}
