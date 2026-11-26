// Co-authored-by: DeepSeek Flash <noreply@deepseek.com>

package com.artista.artista.ui.permission

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.artista.artista.R
import com.artista.artista.ui.navigation.BackButton
import com.artista.artista.ui.theme.ArtistaTheme
import com.artista.artista.ui.theme.Dimensions

/**
 * Stateless explanation of the location permission request.
 *
 * Carries no platform interaction so it is directly previewable and testable. [permissionDenied]
 * selects the state shown after the user refuses: the same explanation plus the consequence, a back
 * action that returns to the initial explanation, and a single continue action instead of asking
 * again.
 *
 * @param permissionDenied whether the system dialog has already been refused
 * @param onAllowClick invoked when the user asks for location access
 * @param onContinueWithoutLocation invoked when the user continues without location
 * @param onBackClick invoked when the user returns to the initial explanation to grant access
 * @param modifier modifier applied to the root container
 * @author timo-by
 */
@Composable
fun LocationPermissionScreen(
    permissionDenied: Boolean,
    onAllowClick: () -> Unit,
    onContinueWithoutLocation: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Box(
      modifier =
          modifier
              .fillMaxSize()
              .background(MaterialTheme.colorScheme.background)
              .statusBarsPadding()
              .navigationBarsPadding()
              .testTag(LocationPermissionTestTags.SCREEN),
  ) {
    Column(
        modifier =
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = Dimensions.locationPermissionHorizontalPadding,
                    vertical = Dimensions.locationPermissionVerticalPadding,
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
      Box(
          modifier =
              Modifier.size(Dimensions.locationPermissionIconContainerSize)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.primaryContainer),
          contentAlignment = Alignment.Center,
      ) {
        Icon(
            imageVector = Icons.Outlined.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(Dimensions.locationPermissionIconSize),
        )
      }

      Spacer(Modifier.height(Dimensions.locationPermissionIconTitleSpacing))

      Text(
          text = stringResource(R.string.location_permission_title),
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.onBackground,
          textAlign = TextAlign.Center,
          modifier = Modifier.testTag(LocationPermissionTestTags.TITLE).semantics { heading() },
      )

      Spacer(Modifier.height(Dimensions.locationPermissionTitleBodySpacing))

      Text(
          text = stringResource(R.string.location_permission_rationale),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.testTag(LocationPermissionTestTags.RATIONALE),
      )

      if (permissionDenied) {
        Spacer(Modifier.height(Dimensions.locationPermissionDeniedMessageSpacing))

        Text(
            text = stringResource(R.string.location_permission_denied),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(LocationPermissionTestTags.DENIED_MESSAGE),
        )
      }

      Spacer(Modifier.height(Dimensions.locationPermissionActionTopSpacing))

      if (permissionDenied) {
        Button(
            onClick = onContinueWithoutLocation,
            modifier =
                Modifier.fillMaxWidth()
                    .height(Dimensions.standardTouchTargetSize)
                    .testTag(LocationPermissionTestTags.CONTINUE_BUTTON),
        ) {
          Text(text = stringResource(R.string.location_permission_continue))
        }
      } else {
        Button(
            onClick = onAllowClick,
            modifier =
                Modifier.fillMaxWidth()
                    .height(Dimensions.standardTouchTargetSize)
                    .testTag(LocationPermissionTestTags.ALLOW_BUTTON),
        ) {
          Text(text = stringResource(R.string.location_permission_allow))
        }

        Spacer(Modifier.height(Dimensions.locationPermissionActionSpacing))

        TextButton(
            onClick = onContinueWithoutLocation,
            modifier = Modifier.testTag(LocationPermissionTestTags.NOT_NOW_BUTTON),
        ) {
          Text(text = stringResource(R.string.location_permission_not_now))
        }
      }
    }

    if (permissionDenied) {
      BackButton(
          onClick = onBackClick,
          modifier =
              Modifier.align(Alignment.TopStart)
                  .padding(
                      start = Dimensions.locationPermissionHorizontalPadding,
                      top = Dimensions.locationPermissionBackButtonTopPadding,
                  )
                  .testTag(LocationPermissionTestTags.BACK_BUTTON),
      )
    }
  }
}

/** Previews the explanation shown before the location permission request, in both states. */
@Preview(showBackground = true)
@Composable
internal fun LocationPermissionScreenPreview(
    @PreviewParameter(PermissionDeniedPreviewParameter::class) permissionDenied: Boolean
) {
  ArtistaTheme {
    LocationPermissionScreen(
        permissionDenied = permissionDenied,
        onAllowClick = {},
        onContinueWithoutLocation = {},
        onBackClick = {},
    )
  }
}

/** Supplies the two states of the explanation to the preview. */
private class PermissionDeniedPreviewParameter : PreviewParameterProvider<Boolean> {
  override val values = sequenceOf(false, true)
}
