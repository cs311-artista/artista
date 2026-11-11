// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.location

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test

/**
 * Verifies that [UserLocationPermission] requests all required location permissions and reports the
 * result to its callback.
 *
 * @author 5kyPhy
 */
class UserLocationPermissionTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val context: Context = ApplicationProvider.getApplicationContext()

  /** Fake registry that records launch inputs and immediately dispatches a fixed result. */
  private class FakeRegistryOwner(private val grantedInResult: Boolean) :
      ActivityResultRegistryOwner {
    val launchedPermissions = mutableListOf<List<String>>()

    override val activityResultRegistry =
        object : ActivityResultRegistry() {
          override fun <I : Any?, O : Any?> onLaunch(
              requestCode: Int,
              contract: ActivityResultContract<I, O>,
              input: I,
              options: ActivityOptionsCompat?,
          ) {
            @Suppress("UNCHECKED_CAST")
            launchedPermissions += (input as Array<String>).toList()
            @Suppress("UNCHECKED_CAST")
            dispatchResult(
                requestCode,
                USER_LOCATION_PERMISSIONS.associateWith { grantedInResult } as O,
            )
          }
        }
  }

  /**
   * Checks whether the test device has already granted every location permission required by the
   * component.
   *
   * @return `true` when all required location permissions are granted
   */
  private fun permissionsAlreadyGranted(): Boolean = USER_LOCATION_PERMISSIONS.all {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
  }

  /**
   * Verifies that missing permissions are requested and that a denied result is reported through
   * the callback.
   */
  @Test
  fun launchesRequestAndReportsDenial_whenPermissionsMissing() {
    // The callback reads the real permission state, so this needs a device without the grant.
    assumeFalse(permissionsAlreadyGranted())
    val owner = FakeRegistryOwner(grantedInResult = false)
    val results = mutableListOf<Boolean>()

    composeTestRule.setContent {
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
        UserLocationPermission { results += it }
      }
    }
    composeTestRule.waitForIdle()

    assertEquals(1, owner.launchedPermissions.size)
    assertEquals(USER_LOCATION_PERMISSIONS, owner.launchedPermissions.single().toSet())
    assertTrue(results.isNotEmpty())
    assertTrue(results.all { !it })
  }
}
