package com.artista.artista.ui.navigation

// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
/**
 * Provides stable semantics tags for navigation UI tests.
 *
 * @author patrickmcdan
 */
object NavigationTestTags {

  /** Tag identifying the bottom navigation container. */
  const val BOTTOM_NAVIGATION_MENU = "bottom_navigation_menu"

  /** Returns the tag identifying a specific navigation tab. */
  fun getTabTestTag(tab: Tab): String = "bottom_navigation_tab_${tab.name.lowercase()}"
}
