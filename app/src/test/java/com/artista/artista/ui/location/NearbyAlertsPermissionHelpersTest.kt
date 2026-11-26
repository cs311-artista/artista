// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.location

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class NearbyAlertsPermissionHelpersTest {
  private val app: Application = ApplicationProvider.getApplicationContext()

  private fun deny(vararg permissions: String) = shadowOf(app).denyPermissions(*permissions)

  @Test
  @Config(sdk = [28])
  fun `API 28 needs neither extra permission`() {
    deny(Manifest.permission.ACCESS_BACKGROUND_LOCATION, Manifest.permission.POST_NOTIFICATIONS)

    assertNull(backgroundLocationPermission())
    assertNull(notificationPermission())
    assertTrue(hasBackgroundLocationPermission(app))
    assertTrue(hasNotificationPermission(app))
  }

  @Test
  @Config(sdk = [30])
  fun `API 30 needs background location but not notifications`() {
    assertEquals(Manifest.permission.ACCESS_BACKGROUND_LOCATION, backgroundLocationPermission())
    assertNull(notificationPermission())
    assertTrue(hasNotificationPermission(app))
  }

  @Test
  @Config(sdk = [34])
  fun `API 34 needs all of them`() {
    assertEquals(Manifest.permission.POST_NOTIFICATIONS, notificationPermission())

    val all =
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
        )
    deny(*all)
    assertFalse(hasNearbyAlertPermissions(app))

    // each missing permission alone is enough to make the overall check fail
    all.forEach { missing ->
      shadowOf(app).grantPermissions(*all.filter { it != missing }.toTypedArray())
      deny(missing)
      assertFalse("$missing missing", hasNearbyAlertPermissions(app))
    }

    shadowOf(app).grantPermissions(*all)
    assertTrue(hasNearbyAlertPermissions(app))
  }
}
