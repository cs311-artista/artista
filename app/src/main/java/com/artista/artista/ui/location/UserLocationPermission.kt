// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

internal val USER_LOCATION_PERMISSIONS =
    setOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

/**
 * Requests the permissions required to access the user's location.
 *
 * The permission state is checked again whenever the hosting screen resumes so that a permission
 * change made in system settings is reported without requiring the screen to be recreated.
 *
 * @param onPermissionResult called with the current permission state.
 * @author 5kyPhy
 */
@Composable
fun UserLocationPermission(
    onPermissionResult: (Boolean) -> Unit,
    hasPermissions: (Context) -> Boolean = ::hasUserLocationPermissions,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val currentPermissionResult by rememberUpdatedState(onPermissionResult)
  val hasAllPermissions = hasPermissions(context)
  val permissionLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        currentPermissionResult(hasPermissions(context))
      }

  LaunchedEffect(Unit) {
    if (hasAllPermissions) {
      currentPermissionResult(true)
    } else {
      permissionLauncher.launch(USER_LOCATION_PERMISSIONS.toTypedArray())
    }
  }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        currentPermissionResult(hasPermissions(context))
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }
}

internal fun hasUserLocationPermissions(context: Context): Boolean = USER_LOCATION_PERMISSIONS.all {
  ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}
