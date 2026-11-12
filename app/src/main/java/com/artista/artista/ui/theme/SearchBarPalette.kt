// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Theme colors shared by the search bars on Overview and Search screens. */
internal object SearchBarPalette {
  val container: Color
    @Composable get() = MaterialTheme.colorScheme.surface

  val content: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

  val invalidContainer: Color
    @Composable get() = MaterialTheme.colorScheme.errorContainer

  val error: Color
    @Composable get() = MaterialTheme.colorScheme.error

  val cursor: Color
    @Composable get() = MaterialTheme.colorScheme.primary
}
