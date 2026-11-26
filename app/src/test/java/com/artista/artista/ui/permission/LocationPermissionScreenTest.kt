// Co-authored-by: DeepSeek Flash <noreply@deepseek.com>

package com.artista.artista.ui.permission

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Local (Robolectric) Compose UI tests for [LocationPermissionScreen].
 *
 * Drives the stateless screen in both permission states, so no permission dialog, lifecycle or
 * navigation is involved. Elements are located exclusively by test tag.
 */
@RunWith(AndroidJUnit4::class)
class LocationPermissionScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  /** Verifies that the preview renders the primary action that asks for access. */
  @Test
  fun preview_whenComposed_showsAllowAction() {
    composeTestRule.setContent { LocationPermissionScreenPreview(permissionDenied = false) }

    composeTestRule.onNodeWithTag(LocationPermissionTestTags.ALLOW_BUTTON).assertIsDisplayed()
  }

  /** Verifies that the preview's refused state renders the continue action. */
  @Test
  fun preview_whenDenied_showsContinueAction() {
    composeTestRule.setContent { LocationPermissionScreenPreview(permissionDenied = true) }

    composeTestRule.onNodeWithTag(LocationPermissionTestTags.CONTINUE_BUTTON).assertIsDisplayed()
  }

  /** Verifies that the explanation offers access and a way to continue without it. */
  @Test
  fun content_whenInitial_showsRationaleAndActions() {
    setContent(permissionDenied = false)

    composeTestRule.onNodeWithTag(LocationPermissionTestTags.SCREEN).assertIsDisplayed()
    assertSharedExplanationCopy()
    composeTestRule
        .onNodeWithTag(LocationPermissionTestTags.ALLOW_BUTTON)
        .assertIsDisplayed()
        .assertTextEquals(allowLabel)
    composeTestRule
        .onNodeWithTag(LocationPermissionTestTags.NOT_NOW_BUTTON)
        .assertIsDisplayed()
        .assertTextEquals(notNowLabel)
    composeTestRule.onNodeWithTag(LocationPermissionTestTags.DENIED_MESSAGE).assertDoesNotExist()
    composeTestRule.onNodeWithTag(LocationPermissionTestTags.CONTINUE_BUTTON).assertDoesNotExist()
  }

  /** Verifies that a refusal explains the consequence and offers the continue action. */
  @Test
  fun content_whenDenied_keepsSharedCopyAndShowsContinue() {
    setContent(permissionDenied = true)

    assertSharedExplanationCopy()
    composeTestRule
        .onNodeWithTag(LocationPermissionTestTags.DENIED_MESSAGE)
        .assertIsDisplayed()
        .assertTextEquals(deniedMessage)
    composeTestRule
        .onNodeWithTag(LocationPermissionTestTags.CONTINUE_BUTTON)
        .assertIsDisplayed()
        .assertTextEquals(continueLabel)
    composeTestRule.onNodeWithTag(LocationPermissionTestTags.ALLOW_BUTTON).assertDoesNotExist()
  }

  /** Verifies that the allow action reports the user's intent. */
  @Test
  fun content_whenAllowClicked_invokesAllowCallback() {
    var allowClicks = 0
    setContent(permissionDenied = false, onAllowClick = { allowClicks++ })

    composeTestRule.onNodeWithTag(LocationPermissionTestTags.ALLOW_BUTTON).performClick()
    composeTestRule.waitForIdle()

    assertEquals(1, allowClicks)
  }

  /** Verifies that Not now reports the same continue intent as the refused state. */
  @Test
  fun content_whenNotNowClicked_invokesContinueCallback() {
    var continueClicks = 0
    setContent(permissionDenied = false, onContinueWithoutLocation = { continueClicks++ })

    composeTestRule.onNodeWithTag(LocationPermissionTestTags.NOT_NOW_BUTTON).performClick()
    composeTestRule.waitForIdle()

    assertEquals(1, continueClicks)
  }

  /** Verifies that Continue without location reports the user's intent. */
  @Test
  fun content_whenContinueClicked_invokesContinueCallback() {
    var continueClicks = 0
    setContent(permissionDenied = true, onContinueWithoutLocation = { continueClicks++ })

    composeTestRule.onNodeWithTag(LocationPermissionTestTags.CONTINUE_BUTTON).performClick()
    composeTestRule.waitForIdle()

    assertEquals(1, continueClicks)
  }

  private fun setContent(
      permissionDenied: Boolean,
      onAllowClick: () -> Unit = {},
      onContinueWithoutLocation: () -> Unit = {},
  ) {
    composeTestRule.setContent {
      LocationPermissionScreen(
          permissionDenied = permissionDenied,
          onAllowClick = onAllowClick,
          onContinueWithoutLocation = onContinueWithoutLocation,
      )
    }
  }

  /** Asserts the copy both states share, so neither test can silently lose it. */
  private fun assertSharedExplanationCopy() {
    composeTestRule
        .onNodeWithTag(LocationPermissionTestTags.TITLE)
        .assertIsDisplayed()
        .assertTextEquals(titleLabel)
    composeTestRule
        .onNodeWithTag(LocationPermissionTestTags.RATIONALE)
        .assertIsDisplayed()
        .assertTextEquals(rationaleLabel)
  }

  private val allowLabel: String
    get() = context.getString(R.string.location_permission_allow)

  private val titleLabel: String
    get() = context.getString(R.string.location_permission_title)

  private val rationaleLabel: String
    get() = context.getString(R.string.location_permission_rationale)

  private val notNowLabel: String
    get() = context.getString(R.string.location_permission_not_now)

  private val continueLabel: String
    get() = context.getString(R.string.location_permission_continue)

  private val deniedMessage: String
    get() = context.getString(R.string.location_permission_denied)

  private val context: Context
    get() = ApplicationProvider.getApplicationContext()
}
