package com.artista.artista.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
/**
 * Verifies the user-visible behavior of the bottom navigation menu.
 *
 * @author patrickmcdan
 */
@RunWith(AndroidJUnit4::class)
class BottomNavigationMenuTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  /**
   * Verifies that the menu exposes the bottom navigation container and all top-level destinations.
   */
  @Test
  fun bottomNavigationMenu_displaysOverviewMapAndProfileTabs() {
    setNavigationContent()

    composeTestRule.onNodeWithTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU).assertIsDisplayed()
    composeTestRule
        .onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Overview))
        .assertIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Map)).assertIsDisplayed()
    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Profile)).assertIsDisplayed()
  }

  /** Verifies that the overview destination is selected when the menu is first displayed. */
  @Test
  fun bottomNavigationMenu_selectsOverviewInitially() {
    setNavigationContent()

    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Overview)).assertIsSelected()
    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Map)).assertIsNotSelected()
    composeTestRule
        .onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Profile))
        .assertIsNotSelected()
  }

  /** Verifies that selecting the map tab reports the map destination. */
  @Test
  fun bottomNavigationMenu_navigatesToMapWhenMapTabIsClicked() {
    var selectedTab: Tab? = null
    setNavigationContent { selectedTab = it }

    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Map)).performClick()

    assertEquals(Tab.Map, selectedTab)
  }

  /** Verifies that selecting the overview tab reports the overview destination. */
  @Test
  fun bottomNavigationMenu_navigatesToOverviewWhenOverviewTabIsClicked() {
    var selectedTab: Tab? = null
    setNavigationContent(selectedTab = { Tab.Map }) { selectedTab = it }

    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Overview)).performClick()

    assertEquals(Tab.Overview, selectedTab)
  }

  /** Verifies that selecting the profile tab reports the profile destination. */
  @Test
  fun bottomNavigationMenu_navigatesToProfileWhenProfileTabIsClicked() {
    var selectedTab: Tab? = null
    setNavigationContent { selectedTab = it }

    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Profile)).performClick()

    assertEquals(Tab.Profile, selectedTab)
  }

  /** Verifies that the selected state follows each destination chosen by the user. */
  @Test
  fun bottomNavigationMenu_updatesSelectedTabAfterNavigation() {
    var selectedTab by mutableStateOf<Tab>(Tab.Overview)
    setNavigationContent(selectedTab = { selectedTab }) { selectedTab = it }

    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Map)).performClick()
    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Map)).assertIsSelected()
    composeTestRule
        .onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Overview))
        .assertIsNotSelected()

    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Profile)).performClick()
    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Profile)).assertIsSelected()
    composeTestRule.onNodeWithTag(NavigationTestTags.getTabTestTag(Tab.Map)).assertIsNotSelected()
  }

  private fun setNavigationContent(
      selectedTab: () -> Tab = { Tab.Overview },
      onTabSelected: (Tab) -> Unit = {},
  ) {
    composeTestRule.setContent {
      MaterialTheme {
        BottomNavigationMenu(
            selectedTab = selectedTab(),
            onTabSelected = onTabSelected,
        )
      }
    }
  }
}
