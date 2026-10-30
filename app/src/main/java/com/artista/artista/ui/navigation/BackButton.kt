// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.artista.artista.R

/**
 * Back arrow shared by every screen that lets the user return to the previous destination, so the
 * icon and its accessibility label stay consistent across the app.
 *
 * @param onClick Callback invoked when the user taps the back arrow.
 * @param modifier Modifier applied to the button, typically carrying the screen's test tag.
 * @author Kaio R. Freitas Pereira Nascimento (krfpn)
 */
@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
  IconButton(onClick = onClick, modifier = modifier) {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = stringResource(R.string.back),
    )
  }
}
