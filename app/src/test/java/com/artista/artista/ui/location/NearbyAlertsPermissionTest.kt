// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.location

import android.Manifest
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artista.artista.R
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class NearbyAlertsPermissionTest {
  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

  private val app: Application = ApplicationProvider.getApplicationContext()
  private val registry = RecordingRegistry()
  private val results = mutableListOf<Boolean>()
  private var requested by mutableStateOf(false)

  private class RecordingRegistry : ActivityResultRegistry() {
    val launches = mutableListOf<Pair<Int, Any?>>()

    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?,
    ) {
      launches += requestCode to input
    }
  }

  private fun setGranted(vararg granted: String) {
    shadowOf(app)
        .denyPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
        )
    shadowOf(app).grantPermissions(*granted)
  }

  private fun show() {
    val owner =
        object : ActivityResultRegistryOwner {
          override val activityResultRegistry: ActivityResultRegistry = registry
        }
    composeRule.setContent {
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
        NearbyAlertsPermission(requested = requested, onPermissionResult = { results += it })
      }
    }
    composeRule.waitForIdle()
  }

  /** The user closes the last system prompt; whatever [setGranted] said is what they chose. */
  private fun completeLastLaunch() {
    val (code, input) = registry.launches.last()
    val result: Any = if (input is Array<*>) emptyMap<String, Boolean>() else false
    composeRule.runOnUiThread { registry.dispatchResult(code, result) }
    composeRule.waitForIdle()
  }

  private fun text(id: Int) = app.getString(id)

  private val foreground =
      arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

  @Test
  fun `asks for nothing until the user opts in`() {
    setGranted()
    show()

    assertTrue(registry.launches.isEmpty())
    assertEquals(false, results.last())
  }

  @Test
  fun `first asks for foreground location`() {
    setGranted()
    requested = true
    show()

    val input = registry.launches.single().second as Array<*>
    assertEquals(USER_LOCATION_PERMISSIONS, input.toSet())
  }

  @Test
  fun `explains background access before requesting it`() {
    setGranted(*foreground)
    requested = true
    show()

    composeRule
        .onNodeWithText(text(R.string.nearby_alerts_permissions_background_title))
        .assertIsDisplayed()
    assertTrue(registry.launches.isEmpty())

    composeRule.onNodeWithText(text(R.string.nearby_alerts_permissions_continue)).performClick()
    composeRule.waitForIdle()

    assertEquals(Manifest.permission.ACCESS_BACKGROUND_LOCATION, registry.launches.single().second)
  }

  @Test
  fun `asks for notifications last`() {
    setGranted(*foreground, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    requested = true
    show()

    assertEquals(Manifest.permission.POST_NOTIFICATIONS, registry.launches.single().second)
  }

  @Test
  fun `reports true and asks nothing when everything is granted`() {
    setGranted(
        *foreground,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        Manifest.permission.POST_NOTIFICATIONS,
    )
    requested = true
    show()

    assertTrue(registry.launches.isEmpty())
    assertEquals(true, results.last())
  }

  @Test
  fun `a denied step is not asked again`() {
    setGranted()
    requested = true
    show()
    assertEquals(1, registry.launches.size)

    completeLastLaunch() // the user said no

    assertEquals(1, registry.launches.size)
    assertEquals(false, results.last())
  }

  @Test
  fun `opting out and in again asks again`() {
    setGranted()
    requested = true
    show()
    completeLastLaunch()

    composeRule.runOnUiThread { requested = false }
    composeRule.waitForIdle()
    composeRule.runOnUiThread { requested = true }
    composeRule.waitForIdle()

    assertEquals(2, registry.launches.size)
  }

  @Test
  fun `moves on to the next step once one is granted`() {
    setGranted()
    requested = true
    show()

    setGranted(*foreground)
    completeLastLaunch()

    composeRule
        .onNodeWithText(text(R.string.nearby_alerts_permissions_background_title))
        .assertIsDisplayed()
  }

  @Test
  fun `picks up a permission granted in system settings when the screen resumes`() {
    setGranted(*foreground)
    requested = true
    show()
    composeRule.onNodeWithText(text(R.string.nearby_alerts_permissions_continue)).performClick()
    composeRule.waitForIdle()

    setGranted(*foreground, Manifest.permission.ACCESS_BACKGROUND_LOCATION) // changed in settings
    composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
    composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
    composeRule.waitForIdle()

    assertEquals(Manifest.permission.POST_NOTIFICATIONS, registry.launches.last().second)
  }

  @Test
  @Config(sdk = [28])
  fun `on API 28 there is no background or notification step`() {
    setGranted(*foreground)
    requested = true
    show()

    composeRule
        .onNodeWithText(text(R.string.nearby_alerts_permissions_background_title))
        .assertDoesNotExist()
    assertTrue(registry.launches.isEmpty())
    assertEquals(true, results.last())
  }
}
