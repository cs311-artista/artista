package com.artista.artista.ui.navigation

import androidx.navigation.NavHostController
import org.junit.Assert.assertEquals
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

  /** Verifies that each bottom-navigation tab points to its matching screen. */
  @Test
  fun tabs_pointToExpectedScreens() {
    assertEquals(Screen.Overview, Tab.Overview.destination)
    assertEquals(Screen.Map, Tab.Map.destination)
    assertEquals(Screen.Profile, Tab.Profile.destination)
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
}
