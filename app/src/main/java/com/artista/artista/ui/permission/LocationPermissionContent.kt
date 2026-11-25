// Co-authored-by: DeepSeek Flash <noreply@deepseek.com>

package com.artista.artista.ui.permission

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.artista.artista.ui.theme.ArtistaTheme

/**
 * Stateless content of the location permission explanation.
 *
 * Carries no platform interaction so it is directly previewable and testable. [permissionDenied]
 * selects the state shown after the user refuses.
 *
 * @param permissionDenied whether the system dialog has already been refused
 * @param onAllowClick invoked when the user asks for location access
 * @param onContinueWithoutLocation invoked when the user continues without location
 * @param modifier modifier applied to the root container
 * @author timo-by
 */
@Composable
fun LocationPermissionContent(
    permissionDenied: Boolean,
    onAllowClick: () -> Unit,
    onContinueWithoutLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
  // Skeleton for the RED commit; the explanation is implemented in GREEN.
  Box(modifier = modifier.fillMaxSize().testTag(LocationPermissionTestTags.SCREEN))
}

/** Previews the explanation shown before the location permission request, in both states. */
@Preview(showBackground = true)
@Composable
internal fun LocationPermissionContentPreview(
    @PreviewParameter(PermissionDeniedPreviewParameter::class) permissionDenied: Boolean
) {
  ArtistaTheme {
    LocationPermissionContent(
        permissionDenied = permissionDenied,
        onAllowClick = {},
        onContinueWithoutLocation = {},
    )
  }
}

/** Supplies the two states of the explanation to the preview. */
private class PermissionDeniedPreviewParameter : PreviewParameterProvider<Boolean> {
  override val values = sequenceOf(false, true)
}
