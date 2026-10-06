package com.artista.artista.ui.navigation

import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
/**
 * Tests the navigation model and controller-independent navigation behavior.
 *
 * @author patrickmcdan
 */
@RunWith(RobolectricTestRunner::class)
class NavigationActionsTest {

  /** Verifies the route and display metadata of every top-level screen. */
  @Test
  fun topLevelScreens_exposeExpectedMetadata() {
    assertEquals("overview", Screen.Overview.route)
    assertEquals("Overview", Screen.Overview.name)
    assertTrue(Screen.Overview.isTopLevelDestination)

    assertEquals("map", Screen.Map.route)
    assertEquals("Map", Screen.Map.name)
    assertTrue(Screen.Map.isTopLevelDestination)

    assertEquals("profile", Screen.Profile.route)
    assertEquals("Profile", Screen.Profile.name)
    assertTrue(Screen.Profile.isTopLevelDestination)
  }

  /** Verifies that each bottom-navigation tab points to its matching screen. */
  @Test
  fun tabs_pointToExpectedScreens() {
    assertEquals(Screen.Overview, Tab.Overview.destination)
    assertEquals(Screen.Map, Tab.Map.destination)
    assertEquals(Screen.Profile, Tab.Profile.destination)
  }

  /** Verifies that navigation changes the controller to the requested route. */
  @Test
  fun navigateTo_changesCurrentRoute() {
    val controller = newControllerWithGraph()
    val actions = NavigationActions(controller)

    actions.navigateTo(Screen.Map)

    assertEquals(Screen.Map.route, actions.currentRoute())
  }

  /** Verifies that navigating back returns to the previous top-level destination. */
  @Test
  fun goBack_returnsToPreviousRoute() {
    val controller = newControllerWithGraph()
    val actions = NavigationActions(controller)

    actions.navigateTo(Screen.Map)
    actions.goBack()

    assertEquals(Screen.Overview.route, actions.currentRoute())
  }

  /** Verifies that a controller without a graph reports no current route. */
  @Test
  fun currentRoute_withoutDestination_returnsEmptyString() {
    val actions = NavigationActions(newController())

    assertEquals("", actions.currentRoute())
  }

  /** Verifies that going back is safe when the controller has no back stack. */
  @Test
  fun goBack_withoutBackStack_doesNotThrow() {
    NavigationActions(newController()).goBack()
  }

  private fun newController(): NavHostController {
    return NavHostController(RuntimeEnvironment.getApplication())
  }

  private fun newControllerWithGraph(): NavHostController {
    val controller = newController()

    controller.navigatorProvider.addNavigator(ComposeNavigator())
    controller.graph =
        controller.createGraph(startDestination = Screen.Overview.route) {
          composable(Screen.Overview.route) {}
          composable(Screen.Map.route) {}
          composable(Screen.Profile.route) {}
        }

    return controller
  }
}
