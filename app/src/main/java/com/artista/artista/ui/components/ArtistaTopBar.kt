// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.artista.artista.ui.navigation.BackButton
import com.artista.artista.ui.theme.ArtistaTheme
import com.artista.artista.ui.theme.Dimensions

/**
 * Provides stable semantics tags for [ArtistaTopBar] UI tests.
 *
 * @author hixeum
 */
object ArtistaTopBarTestTags {
  const val BAR = "artista_top_bar"
  const val TITLE = "artista_top_bar_title"
  const val LEFT_SLOT = "artista_top_bar_left_slot"
  const val RIGHT_SLOT = "artista_top_bar_right_slot"
}

private const val LEFT_SLOT_ID = "left"
private const val TITLE_ID = "title"
private const val RIGHT_SLOT_ID = "right"

/**
 * Top app bar shared by the app's screens, with an optional button on each side and an optional
 * text in the middle.
 *
 * The text stays centered on the screen whatever buttons are present: the space it may use is
 * reduced by the widest side slot on both sides, so a long text is cut with an ellipsis instead of
 * overlapping a button. Without a text, the buttons still sit at the left and right edges. The bar
 * draws behind the status bar and pads its content below it, so screens don't add
 * `statusBarsPadding()` themselves.
 *
 * @param modifier modifier applied to the bar's container
 * @param title text shown in the middle of the bar, or null to show only the buttons
 * @param titleTestTag test tag applied to the text, so each screen keeps its own title tag
 * @param leftButton button shown at the left edge, such as a back or search button, or null
 * @param rightButton button shown at the right edge, such as a filter, save or logout button, or
 *   null
 * @author hixeum
 */
@Composable
fun ArtistaTopBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    titleTestTag: String = ArtistaTopBarTestTags.TITLE,
    leftButton: (@Composable () -> Unit)? = null,
    rightButton: (@Composable () -> Unit)? = null,
) {
  Surface(
      modifier = modifier.fillMaxWidth().testTag(ArtistaTopBarTestTags.BAR),
      color = MaterialTheme.colorScheme.surface,
      contentColor = MaterialTheme.colorScheme.onSurface,
  ) {
    Layout(
        modifier =
            Modifier.windowInsetsPadding(WindowInsets.statusBars)
                .height(Dimensions.topBarHeight)
                .padding(horizontal = Dimensions.topBarHorizontalPadding),
        content = {
          leftButton?.let {
            TopBarSlot(
                modifier = Modifier.layoutId(LEFT_SLOT_ID).testTag(ArtistaTopBarTestTags.LEFT_SLOT),
                content = it,
            )
          }
          title?.let {
            Text(
                text = it,
                modifier = Modifier.layoutId(TITLE_ID).testTag(titleTestTag),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
          }
          rightButton?.let {
            TopBarSlot(
                modifier =
                    Modifier.layoutId(RIGHT_SLOT_ID).testTag(ArtistaTopBarTestTags.RIGHT_SLOT),
                content = it,
            )
          }
        },
    ) { measurables, constraints ->
      val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
      val left = measurables.firstOrNull { it.layoutId == LEFT_SLOT_ID }?.measure(looseConstraints)
      val right =
          measurables.firstOrNull { it.layoutId == RIGHT_SLOT_ID }?.measure(looseConstraints)
      // Both sides give up the width of the widest slot, so the text stays centered on the screen
      // even when only one button is present, and never runs under either button.
      val sideWidth = maxOf(left?.width ?: 0, right?.width ?: 0)
      val titleMaxWidth = (constraints.maxWidth - 2 * sideWidth).coerceAtLeast(0)
      val titlePlaceable =
          measurables
              .firstOrNull { it.layoutId == TITLE_ID }
              ?.measure(looseConstraints.copy(maxWidth = titleMaxWidth))

      val width = constraints.maxWidth
      val height = constraints.maxHeight
      layout(width, height) {
        left?.placeRelative(0, (height - left.height) / 2)
        titlePlaceable?.placeRelative(
            (width - titlePlaceable.width) / 2,
            (height - titlePlaceable.height) / 2,
        )
        right?.placeRelative(width - right.width, (height - right.height) / 2)
      }
    }
  }
}

/**
 * Holds a side button of [ArtistaTopBar], giving it at least the size of a touch target so the bar
 * keeps the same layout whatever button a screen passes.
 *
 * @param modifier modifier applied to the slot, carrying its layout id and test tag
 * @param content button drawn in the slot
 * @author hixeum
 */
@Composable
private fun TopBarSlot(modifier: Modifier, content: @Composable () -> Unit) {
  Box(
      modifier =
          modifier.sizeIn(
              minWidth = Dimensions.topBarButtonSize,
              minHeight = Dimensions.topBarButtonSize,
          ),
      contentAlignment = Alignment.Center,
  ) {
    content()
  }
}

/** Previews the top bar with a back button, a title and a filter button. */
@Preview(showBackground = true)
@Composable
internal fun ArtistaTopBarPreview() {
  ArtistaTheme {
    ArtistaTopBar(
        title = "Saved Artworks",
        leftButton = { BackButton(onClick = {}) },
        rightButton = {
          IconButton(onClick = {}) {
            Icon(imageVector = Icons.Outlined.FilterList, contentDescription = null)
          }
        },
    )
  }
}
