// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.artwork

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.artista.artista.R
import com.artista.artista.model.artwork.ArtworkFilter
import com.artista.artista.model.artwork.suggestions
import com.artista.artista.ui.theme.Dimensions

/**
 * Displays reusable artwork filter controls in a dialog.
 *
 * @param filter currently applied criteria
 * @param artistOptions artist options from the unfiltered artwork list
 * @param museumOptions museum options from the unfiltered artwork list
 * @param locationOptions location options from the unfiltered artwork list
 * @param onApply called with the draft criteria
 * @param onClear called when all criteria should be removed
 * @param onDismiss called when the dialog is closed without applying changes
 * @author patrickmcdan
 */
@Composable
fun ArtworkFilterDialog(
    filter: ArtworkFilter,
    artistOptions: List<String>,
    museumOptions: List<String>,
    locationOptions: List<String>,
    onApply: (ArtworkFilter) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
  var artists by remember(filter) { mutableStateOf(filter.artists) }
  var museums by remember(filter) { mutableStateOf(filter.museums) }
  var locations by remember(filter) { mutableStateOf(filter.locations) }
  var fromYear by remember(filter) { mutableStateOf(filter.fromYear?.toString().orEmpty()) }
  var toYear by remember(filter) { mutableStateOf(filter.toYear?.toString().orEmpty()) }
  val from = fromYear.toIntOrNull()
  val to = toYear.toIntOrNull()
  val invalidRange = from != null && to != null && from > to

  AlertDialog(
      onDismissRequest = onDismiss,
      modifier = Modifier.testTag(ArtworkFilterTestTags.DIALOG),
      title = { Text(text = stringResource(R.string.overview_filter_title)) },
      text = {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .imePadding()
                    .padding(vertical = Dimensions.artworkFilterContentVerticalPadding),
            verticalArrangement = Arrangement.spacedBy(Dimensions.overviewFilterFieldSpacing),
        ) {
          MultiSelectField(
              label = stringResource(R.string.overview_filter_artist),
              options = artistOptions,
              selected = artists,
              onSelectedChange = { artists = it },
              tagPrefix = ArtworkFilterTestTags.ARTIST,
          )
          MultiSelectField(
              label = stringResource(R.string.overview_filter_museum),
              options = museumOptions,
              selected = museums,
              onSelectedChange = { museums = it },
              tagPrefix = ArtworkFilterTestTags.MUSEUM,
          )
          MultiSelectField(
              label = stringResource(R.string.overview_filter_location),
              options = locationOptions,
              selected = locations,
              onSelectedChange = { locations = it },
              tagPrefix = ArtworkFilterTestTags.LOCATION,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(Dimensions.overviewFilterFieldSpacing)) {
            YearField(
                value = fromYear,
                onValueChange = { fromYear = it.filter(Char::isDigit) },
                label = stringResource(R.string.overview_filter_from_year),
                tag = ArtworkFilterTestTags.FROM_YEAR,
                modifier = Modifier.weight(1f),
            )
            YearField(
                value = toYear,
                onValueChange = { toYear = it.filter(Char::isDigit) },
                label = stringResource(R.string.overview_filter_to_year),
                tag = ArtworkFilterTestTags.TO_YEAR,
                modifier = Modifier.weight(1f),
            )
          }
          if (invalidRange) {
            Text(
                text = stringResource(R.string.overview_filter_invalid_year_range),
                modifier = Modifier.testTag(ArtworkFilterTestTags.YEAR_ERROR),
            )
          }
        }
      },
      confirmButton = {
        Button(
            onClick = {
              onApply(
                  ArtworkFilter(
                      artists = artists,
                      museums = museums,
                      locations = locations,
                      fromYear = from,
                      toYear = to,
                  )
              )
            },
            enabled = !invalidRange,
            modifier = Modifier.testTag(ArtworkFilterTestTags.APPLY_BUTTON),
        ) {
          Text(text = stringResource(R.string.overview_filter_apply))
        }
      },
      dismissButton = {
        TextButton(
            onClick = onClear,
            modifier = Modifier.testTag(ArtworkFilterTestTags.CLEAR_BUTTON),
        ) {
          Text(text = stringResource(R.string.overview_filter_clear))
        }
      },
  )
}

@Composable
private fun MultiSelectField(
    label: String,
    options: List<String>,
    selected: Set<String>,
    onSelectedChange: (Set<String>) -> Unit,
    tagPrefix: String,
) {
  var query by remember { mutableStateOf("") }
  // Suggestions only appear once the user has typed something, to keep the dialog compact.
  val matchingOptions = if (query.isBlank()) emptyList() else suggestions(query, options, selected)

  OutlinedTextField(
      value = query,
      onValueChange = { query = it },
      modifier = Modifier.fillMaxWidth().testTag("$tagPrefix-input"),
      label = { Text(label) },
      singleLine = true,
  )
  Row(horizontalArrangement = Arrangement.spacedBy(Dimensions.overviewFilterFieldSpacing)) {
    selected.sorted().forEach { value ->
      AssistChip(
          onClick = { onSelectedChange(selected - value) },
          label = { Text(value) },
          trailingIcon = {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.overview_filter_remove),
            )
          },
          modifier = Modifier.testTag("$tagPrefix-chip-$value"),
      )
    }
  }
  if (matchingOptions.isNotEmpty()) {
    LazyColumn(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(max = Dimensions.artworkFilterSuggestionsMaxHeight)
                .testTag("$tagPrefix-suggestions"),
    ) {
      items(matchingOptions) { option ->
        FilterChip(
            selected = false,
            onClick = {
              onSelectedChange(selected + option)
              query = ""
            },
            label = { Text(option) },
            modifier = Modifier.fillMaxWidth().testTag("$tagPrefix-option-$option"),
        )
      }
    }
  }
}

@Composable
private fun YearField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    tag: String,
    modifier: Modifier = Modifier,
) {
  OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      modifier = modifier.testTag(tag),
      label = { Text(label) },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      singleLine = true,
  )
}

/**
 * Provides stable tags for reusable artwork filter controls.
 *
 * @author patrickmcdan
 */
object ArtworkFilterTestTags {
  const val DIALOG = "artwork_filter_dialog"
  const val ARTIST = "artwork_filter_artist"
  const val MUSEUM = "artwork_filter_museum"
  const val LOCATION = "artwork_filter_location"
  const val FROM_YEAR = "artwork_filter_from_year"
  const val TO_YEAR = "artwork_filter_to_year"
  const val YEAR_ERROR = "artwork_filter_year_error"
  const val APPLY_BUTTON = "artwork_filter_apply_button"
  const val CLEAR_BUTTON = "artwork_filter_clear_button"
}
