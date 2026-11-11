// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.map

import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artista.artista.R
import com.artista.artista.model.artwork.ArtworkRepositoryProvider
import com.artista.artista.model.walking.WalkingRouteRepositoryRemote
import com.artista.artista.ui.location.USER_LOCATION_PERMISSIONS
import com.artista.artista.ui.theme.ArtistaTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the map screen's connected user interface.
 *
 * @author Felix Burchardt
 */
@RunWith(AndroidJUnit4::class)
class MapScreenTestRevokedPermissions {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val instrumentation = InstrumentationRegistry.getInstrumentation()

  /** Revokes location permissions before the screen starts its location request. */
  @Before
  fun revokeLocationPermissions() {
    instrumentation.uiAutomation.adoptShellPermissionIdentity()
    USER_LOCATION_PERMISSIONS.forEach { permission ->
      if (
          ContextCompat.checkSelfPermission(
              instrumentation.targetContext,
              permission,
          ) == PackageManager.PERMISSION_GRANTED
      ) {
        instrumentation.uiAutomation.revokeRuntimePermission(
            instrumentation.targetContext.packageName,
            permission,
        )
      }
    }
  }

  /** Releases shell permissions after each test. */
  @After
  fun releaseShellPermissions() {
    instrumentation.uiAutomation.dropShellPermissionIdentity()
  }

  /** Verifies that denying location permission exposes the location error message. */
  @Test
  fun mapScreen_setsLocationPermissionError_whenPermissionIsDenied() {
    USER_LOCATION_PERMISSIONS.forEach { permission ->
      instrumentation.uiAutomation.revokeRuntimePermission(
          instrumentation.targetContext.packageName,
          permission,
      )
    }
    val mapViewModel =
        MapViewModel(
            ArtworkRepositoryProvider.mockRepository,
            ArtworkRepositoryProvider.mockRepository,
            WalkingRouteRepositoryRemote(),
            locationProvider = LocationProvider { callback -> callback(mockDeviceLocation()) },
        )

    composeTestRule.setContent {
      CompositionLocalProvider(
          LocalActivityResultRegistryOwner provides
              FakeActivityResultRegistryOwner(
                  mapOf(
                      Pair("android.permission.ACCESS_FINE_LOCATION", false),
                      Pair("android.permission.ACCESS_COARSE_LOCATION", false),
                  )
              )
      ) {
        ArtistaTheme { MapScreen(mapViewModel = mapViewModel) }
      }
    }

    composeTestRule.waitForIdle()

    assertEquals(R.string.error_user_location, mapViewModel.uiState.errorMsg)
  }

  private companion object {
    const val MOCK_LATITUDE = 46.5201
    const val MOCK_LONGITUDE = 6.6332
  }

  private fun mockDeviceLocation(): Location =
      Location("mock").apply {
        latitude = MOCK_LATITUDE
        longitude = MOCK_LONGITUDE
      }

  private class FakeActivityResultRegistryOwner(
      private val permissionResult: Map<String, Boolean>
  ) : ActivityResultRegistryOwner {
    override val activityResultRegistry: ActivityResultRegistry =
        object : ActivityResultRegistry() {
          @Suppress("UNCHECKED_CAST")
          override fun <I, O> onLaunch(
              requestCode: Int,
              contract: ActivityResultContract<I, O>,
              input: I,
              options: ActivityOptionsCompat?,
          ) {
            dispatchResult(requestCode, permissionResult as O)
          }
        }
  }
}
