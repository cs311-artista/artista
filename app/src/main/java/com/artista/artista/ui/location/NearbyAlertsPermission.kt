// Co-authored-by: Claude Sonnet 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.artista.artista.R

private fun Context.granted(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/**
 * Reports whether the necessary runtime permission for accessing device location from background
 * are granted.
 *
 * @param context The context to check the permission against.
 * @return True if the necessary runtime permission for accessing device location from background
 *   are granted, false otherwise.
 * @author 5kyPhy
 */
internal fun hasBackgroundLocationPermission(context: Context): Boolean =
    backgroundLocationPermission()?.let { context.granted(it) } ?: true

/**
 * Reports whether the necessary runtime permission for notifications are granted.
 *
 * @param context The context to check the permission against.
 * @return True if the necessary runtime permission for notifications are granted, false otherwise.
 * @author 5kyPhy
 */
internal fun hasNotificationPermission(context: Context): Boolean =
    notificationPermission()?.let { context.granted(it) } ?: true

/**
 * Reports whether all necessary runtime permissions are granted in order to serve location based
 * notifications to the user.
 *
 * @param context The context to check the permissions against.
 * @return True if all necessary runtime permissions are granted, false otherwise.
 * @author 5kyPhy
 */
internal fun hasNearbyAlertPermissions(context: Context): Boolean =
    hasUserLocationPermissions(context) &&
        hasBackgroundLocationPermission(context) &&
        hasNotificationPermission(context)

/**
 * Returns the required runtime permission for background location access permissions. Null below
 * API 19, where foreground location already covers background access.
 *
 * @return The required runtime permission for background location access, or null if not needed.
 * @author 5kyPhy
 */
internal fun backgroundLocationPermission(): String? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      Manifest.permission.ACCESS_BACKGROUND_LOCATION
    } else null

/**
 * Returns the required runtime permission for notifications. Null below API 33, where notifications
 * need no runtime permission.
 *
 * @return The required runtime permission for notifications, or null if not needed.
 * @author 5kyPhy
 */
internal fun notificationPermission(): String? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      Manifest.permission.POST_NOTIFICATIONS
    } else null

private enum class Step {
  Foreground,
  Background,
  Notifications,
}

/**
 * Walks the user through the permissions needed for nearby-artwork alerts, one step at a time.
 *
 * Nothing is requested until [requested] is true (the user opted in). Each step is asked at most
 * once per opt-in, so a denial cannot cause a prompt loop. [onPermissionResult] is called with the
 * current overall state after every step and whenever the screen resumes (e.g. back from system
 * settings), like [UserLocationPermission].
 */
@Composable
fun NearbyAlertsPermission(requested: Boolean, onPermissionResult: (Boolean) -> Unit) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val currentResult by rememberUpdatedState(onPermissionResult)

  var attempted by remember { mutableStateOf(emptySet<Step>()) }
  var showBackgroundRationale by remember { mutableStateOf(false) }
  var recheck by remember { mutableIntStateOf(0) }

  val foregroundLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        recheck++
      }
  val backgroundLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { recheck++ }
  val notificationLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { recheck++ }

  LaunchedEffect(requested) { if (!requested) attempted = emptySet() }

  LaunchedEffect(requested, recheck) {
    currentResult(hasNearbyAlertPermissions(context))
    if (!requested) return@LaunchedEffect

    val next =
        when {
          !hasUserLocationPermissions(context) -> Step.Foreground
          !hasBackgroundLocationPermission(context) -> Step.Background
          !hasNotificationPermission(context) -> Step.Notifications
          else -> null
        }
    // Done, or the user already answered this step: do not ask again.
    if (next == null || next in attempted) return@LaunchedEffect

    attempted = attempted + next
    when (next) {
      Step.Foreground -> foregroundLauncher.launch(USER_LOCATION_PERMISSIONS.toTypedArray())
      Step.Background -> showBackgroundRationale = true
      Step.Notifications -> notificationPermission()?.let { notificationLauncher.launch(it) }
    }
  }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) recheck++
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  if (showBackgroundRationale) {
    AlertDialog(
        onDismissRequest = {
          showBackgroundRationale = false
          recheck++
        },
        title = { Text(stringResource(R.string.nearby_alerts_permissions_background_title)) },
        text = { Text(stringResource(R.string.nearby_alerts_permissions_background_message)) },
        confirmButton = {
          TextButton(
              onClick = {
                showBackgroundRationale = false
                backgroundLocationPermission()?.let { backgroundLauncher.launch(it) }
              }
          ) {
            Text(stringResource(R.string.nearby_alerts_permissions_continue))
          }
        },
    )
  }
}
