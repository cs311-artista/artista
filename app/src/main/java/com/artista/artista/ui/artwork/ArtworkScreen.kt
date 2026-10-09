// Co-authored-by: Gemini <gemini@google.com>
package com.artista.artista.ui.artwork

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.artista.artista.R
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkRepository
import com.artista.artista.ui.theme.ArtistaTheme
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/**
 * Displays the detailed view for an artwork, allowing the user to view its metadata and bookmark
 * it[cite: 1].
 *
 * Synchronizes the passed [artwork] instance with the [artworkScreenViewModel] to display and
 * toggle its persistent bookmark status in Firestore/local storage.
 *
 * @param artwork the artwork entity to display
 * @param modifier optional layout modifier for the root container
 * @param artworkScreenViewModel ViewModel managing screen state and persistence actions
 * @param onBackClick callback triggered when navigating back to the previous screen
 * @author hixeum
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtworkScreen(
    artwork: Artwork,
    modifier: Modifier = Modifier,
    artworkScreenViewModel: ArtworkScreenViewModel = viewModel(),
    onBackClick: () -> Unit = {},
) {
  // Sync artwork with ViewModel once when entered or when artwork changes
  LaunchedEffect(artwork.name) { artworkScreenViewModel.setArtwork(artwork) }

  val uiState by artworkScreenViewModel.uiState.collectAsState()
  val isSaved = uiState.isSaved

  val locale = LocalLocale.current.platformLocale
  // Cache the formatter to prevent re-instantiation across recompositions
  val dateFormatter = remember(locale) { DateFormat.getDateInstance(DateFormat.MEDIUM, locale) }

  Scaffold(
      modifier = modifier.testTag(ArtworkTestTags.ARTWORK_SCREEN),
      topBar = {
        CenterAlignedTopAppBar(
            title = {
              Text(
                  text = artwork.name,
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.testTag(ArtworkTestTags.ARTWORK_TITLE),
              )
            },
            navigationIcon = {
              IconButton(
                  onClick = onBackClick,
                  modifier = Modifier.testTag(ArtworkTestTags.BACK_BUTTON),
              ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                )
              }
            },
            actions = {
              IconButton(
                  onClick = { artworkScreenViewModel.toggleSaveArtwork() },
                  modifier = Modifier.testTag(ArtworkTestTags.SAVE_BUTTON),
              ) {
                Icon(
                    imageVector =
                        if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = null,
                    tint =
                        if (isSaved) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                )
              }
            },
        )
      },
  ) { innerPadding ->
    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Spacer(modifier = Modifier.height(4.dp))

      /**
       * TODO: This component serves as a placeholder for the image. Remove it, and implement
       *   component for the image.
       */
      Card(
          modifier = Modifier.fillMaxWidth().height(220.dp),
          shape = RoundedCornerShape(20.dp),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
      ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
          Column(
              modifier = Modifier.fillMaxSize(),
              verticalArrangement = Arrangement.Center,
              horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(
                text = "Picture/thumbnail",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(4.dp))

      ArtworkFieldItem(
          label = stringResource(R.string.artwork_label_artist),
          value = artwork.artistName ?: stringResource(R.string.artwork_unknown_artist),
      )

      ArtworkFieldItem(
          label = stringResource(R.string.artwork_label_conception_date),
          value =
              artwork.conceptionDate?.let { date -> dateFormatter.format(date) }
                  ?: stringResource(R.string.artwork_unknown_date),
      )

      ArtworkFieldItem(
          label = stringResource(R.string.artwork_label_dimensions),
          value = artwork.dimension?.toString() ?: stringResource(R.string.artwork_no_dimensions),
      )

      ArtworkFieldItem(
          label = stringResource(R.string.artwork_label_museum),
          value = artwork.museum ?: stringResource(R.string.artwork_no_museum),
      )

      ArtworkFieldItem(
          label = stringResource(R.string.artwork_label_description),
          value = artwork.description ?: stringResource(R.string.artwork_no_description),
      )

      ArtworkFieldItem(
          label = stringResource(R.string.artwork_label_location),
          value =
              artwork.location?.let {
                stringResource(R.string.artwork_location_format, it.latitude, it.longitude)
              } ?: stringResource(R.string.artwork_no_location),
      )
    }
  }
}

/**
 * Displays a single property of an artwork wrapped in an outlined card for readability[cite: 1].
 *
 * @param label header label identifying the artwork property
 * @param value value text to display, or fallback placeholder if the field is missing
 * @param modifier optional layout modifier for the card container
 * @author hixeum
 */
@Composable
fun ArtworkFieldItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
  OutlinedCard(
      modifier = modifier.fillMaxWidth(),
      shape = RoundedCornerShape(12.dp),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
      Text(
          text = label,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.SemiBold,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
          text = value,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

///**
// * Compose preview displaying a sample Leonardo da Vinci artwork entry inside [ArtworkScreen][cite:
// * 1].
// *
// * @author hixeum
// */
//@SuppressLint("ViewModelConstructorInComposable")
//@Preview
//@Composable
//fun ArtworkPreview() {
//  val monaLisaDate: Date =
//      Calendar.getInstance()
//          .apply {
//            clear()
//            set(Calendar.YEAR, 1503)
//          }
//          .time
//
//  val sampleArtwork =
//      Artwork(
//          name = "Mona Lisa",
//          artistName = "Leonardo da Vinci",
//          description = "A portrait of Lisa Gherardini, wife of Francesco del Giocondo.",
//          conceptionDate = monaLisaDate,
//          museum = "Musée du Louvre",
//          location = null,
//          dimension = null,
//      )
//
//  // Stubbed implementation to satisfy ViewModel construction in Compose Preview
//  val stubRepo =
//      object : ArtworkRepository {
//        override suspend fun isArtworkSaved(artworkName: String) = true
//
//        override suspend fun saveArtwork(artwork: Artwork) {}
//
//        override suspend fun removeArtwork(artworkName: String) {}
//      }
//
//  ArtistaTheme {
//    ArtworkScreen(
//        artwork = sampleArtwork,
//        artworkScreenViewModel = ArtworkScreenViewModel(stubRepo),
//    )
//  }
//}
