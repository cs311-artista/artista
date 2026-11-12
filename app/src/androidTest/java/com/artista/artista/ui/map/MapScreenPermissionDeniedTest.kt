// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.map

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import com.artista.artista.model.artwork.ArtworkRepositoryProvider
import com.artista.artista.model.error.AppException
import com.artista.artista.model.walking.WalkingRouteRepositoryRemote
import com.artista.artista.ui.error.toMessageRes
import com.artista.artista.ui.location.USER_LOCATION_PERMISSIONS
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

/**
 * Verifies that [MapScreen] exposes the appropriate location error when the user denies every
 * requested location permission.
 *
 * @author 5kyPhy
 */
class MapScreenPermissionDeniedTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  /** Activity-result registry that answers every location permission request with a denial. */
  private val denyingOwner =
      object : ActivityResultRegistryOwner {
        override val activityResultRegistry =
            object : ActivityResultRegistry() {
              override fun <I : Any?, O : Any?> onLaunch(
                  requestCode: Int,
                  contract: ActivityResultContract<I, O>,
                  input: I,
                  options: ActivityOptionsCompat?,
              ) {
                @Suppress("UNCHECKED_CAST")
                dispatchResult(
                    requestCode,
                    USER_LOCATION_PERMISSIONS.associateWith { false } as O,
                )
              }
            }
      }

  /** Verifies that denying location access updates the map screen state with the expected error. */
  @Test
  fun deniedPermission_setsLocationPermissionError() {
    val viewModel =
        MapViewModel(
            wikiDataRepository = ArtworkRepositoryProvider.mockRepository,
            savedArtworksRepository = ArtworkRepositoryProvider.mockRepository,
            walkingRouteRepository = WalkingRouteRepositoryRemote(),
        )

    composeTestRule.setContent {
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides denyingOwner) {
        MapScreen(mapViewModel = viewModel, hasLocationPermissions = { false })
      }
    }
    composeTestRule.waitForIdle()

    Assert.assertFalse(viewModel.uiState.hasLocationPermission)
    Assert.assertEquals(AppException.UserLocationError().toMessageRes(), viewModel.uiState.errorMsg)
  }
}
