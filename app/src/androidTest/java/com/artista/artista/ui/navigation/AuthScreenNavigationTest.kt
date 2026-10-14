package com.artista.artista.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.MainActivity
import com.artista.artista.resources.C
import com.artista.artista.ui.overview.OverviewTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
/**
 * Verifies the root navigation flow exposed by the application.
 *
 * @author hixeum
 */
@RunWith(AndroidJUnit4::class)
class AuthScreenNavigationTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

  /**
   * Verifies that authentication is the initial destination and sign-in opens the overview.
   *
   * @author hixeum
   */
  @Test
  fun authIsInitialDestination_andSignInOpensOverview() {
    composeTestRule.onNodeWithTag(C.Tag.auth_screen_container).assertIsDisplayed()

    composeTestRule.onNodeWithTag(C.Tag.auth_google_sign_in_button).performClick()

    composeTestRule.onNodeWithTag(OverviewTestTags.SCREEN).assertIsDisplayed()
  }
}
