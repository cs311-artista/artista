package com.artista.artista.ui.navigation

import androidx.navigation.NavHostController

// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
/**
 * Represents a screen route used by the application's navigation layer.
 *
 * @param route Stable route identifier used when connecting the screen to a navigation host.
 * @param name Human-readable screen name.
 * @param isTopLevelDestination Whether the screen is represented in bottom navigation.
 * @author patrickmcdan
 */
sealed class Screen(
    val route: String,
    val name: String,
    val isTopLevelDestination: Boolean = false,
) {

  /** The overview screen. */
  data object Overview : Screen("overview", "Overview", true)

  /** The map screen. */
  data object Map : Screen("map", "Map", true)

  /** The profile screen. */
  data object Profile : Screen("profile", "Profile", true)
}

/**
 * Provides navigation operations for the application's navigation host.
 *
 * @param navController Controller used to execute navigation operations.
 * @author patrickmcdan
 */
open class NavigationActions(private val navController: NavHostController) {

  /**
   * Navigates to the specified screen.
   *
   * @param screen Destination to navigate to.
   */
  open fun navigateTo(screen: Screen) {
    if (screen.isTopLevelDestination && currentRoute() == screen.route) {
      return
    }

    navController.navigate(screen.route) {
      if (screen.isTopLevelDestination) {
        launchSingleTop = true
        popUpTo(screen.route) { inclusive = true }
      }
      restoreState = true
    }
  }

  /** Navigates back to the previous destination. */
  open fun goBack() {
    navController.popBackStack()
  }

  /**
   * Returns the route currently displayed by the navigation host.
   *
   * @return Current route, or an empty string when no destination is available.
   */
  open fun currentRoute(): String {
    return navController.currentDestination?.route ?: ""
  }
}
