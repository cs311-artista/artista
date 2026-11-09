// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Gemini <gemini@google.com>
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.artwork

import android.util.Log
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.artista.artista.BuildConfig
import com.artista.artista.R
import com.artista.artista.model.artwork.ArtworkRepositoryProvider
import com.artista.artista.model.artwork.mockArtworks
import com.artista.artista.model.map.Location
import com.artista.artista.resources.C
import com.artista.artista.ui.navigation.BackButton
import com.artista.artista.ui.theme.ArtistaTheme
import com.artista.artista.ui.theme.Dimensions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import java.text.DateFormat

/**
 * Provides stable semantics test tags for ArtworkScreen UI tests.
 *
 * @author hixeum
 */
object ArtworkTestTags {
  const val ARTWORK_SCREEN = "artwork_screen"
  const val SAVE_BUTTON = "artwork_save_button"
  const val BACK_BUTTON = "artwork_back_button"
  const val ARTWORK_TITLE = "artwork_title"
  const val ARTWORK_MAP_TILE = "artwork_map_tile"
  const val ARTWORK_IMAGE = "artwork_image"
  const val ARTWORK_IMAGE_FALLBACK = "artwork_image_fallback"
  const val ARTWORK_LOADING_INDICATOR = "artwork_loading_indicator"
  const val ARTWORK_ERROR_MESSAGE = "artwork_error_message"
  const val ARTWORK_RETRY_BUTTON = "artwork_retry_button"
}

/**
 * Displays the detailed view for an artwork, allowing the user to view its image and metadata and
 * bookmark it.
 *
 * @param modifier optional layout modifier for the root container
 * @param artworkScreenViewModel ViewModel managing screen state and persistence actions
 * @param onBackClick callback triggered when navigating back to the previous screen
 * @author hixeum
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtworkScreen(
    modifier: Modifier = Modifier,
    artworkScreenViewModel: ArtworkScreenViewModel,
    onBackClick: () -> Unit,
) {
  val uiState by artworkScreenViewModel.uiState.collectAsState()
  val isSaved = uiState.isSaved
  val artwork = uiState.artwork
  val context = LocalContext.current
  val errorMessage = uiState.errorMessageResId?.let { stringResource(it) }
  val loadErrorMessage = uiState.loadErrorMessageResId?.let { stringResource(it) }

  LaunchedEffect(errorMessage) {
    errorMessage?.let { message ->
      Toast.makeText(context, message, Toast.LENGTH_LONG).show()
      artworkScreenViewModel.clearErrorMessage()
    }
  }

  val locale = LocalLocale.current.platformLocale
  // Cache the formatter to prevent re-instantiation across recompositions
  val dateFormatter = remember(locale) { DateFormat.getDateInstance(DateFormat.MEDIUM, locale) }

  // Set the camera position and marker position of the artwork, if the [artwork.location] is
  // specified. This only needs to be done once, as the artwork is not going to be moving around.
  val mapTileCameraPositionState = rememberCameraPositionState {
    position =
        artwork?.location?.let {
          CameraPosition.fromLatLngZoom(
              LatLng(it.latitude, it.longitude),
              C.Constants.DEFAULT_MAP_ZOOM,
          )
        } ?: C.Constants.DEFAULT_MAP_POSITION
  }
  val mapTileMarkerState = remember {
    MarkerState(
        position =
            artwork?.location?.let { LatLng(it.latitude, it.longitude) }
                ?: LatLng(C.Constants.DEFAULT_MAP_LAT, C.Constants.DEFAULT_MAP_LONG)
    )
  }

  Scaffold(
      modifier = modifier.testTag(ArtworkTestTags.ARTWORK_SCREEN),
      topBar = {
        CenterAlignedTopAppBar(
            title = {
              Text(
                  text = artwork?.name ?: stringResource(R.string.artwork_unknown_title),
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.testTag(ArtworkTestTags.ARTWORK_TITLE),
              )
            },
            navigationIcon = {
              BackButton(
                  onClick = onBackClick,
                  modifier = Modifier.testTag(ArtworkTestTags.BACK_BUTTON),
              )
            },
            actions = {
              IconButton(
                  onClick = { artworkScreenViewModel.toggleSaveArtwork() },
                  enabled = !uiState.isSaveInProgress,
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
                .padding(horizontal = Dimensions.artworkDetailsContentHorizontalPadding)
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dimensions.artworkDetailsSectionSpacing),
    ) {
      Spacer(modifier = Modifier.height(Dimensions.artworkDetailsImageVerticalSpacing))

      if (uiState.isLoading) {
        CircularProgressIndicator(
            modifier =
                Modifier.align(Alignment.CenterHorizontally)
                    .testTag(ArtworkTestTags.ARTWORK_LOADING_INDICATOR)
        )
        return@Column
      }

      if (artwork == null) {
        // A null artwork after loading means the repository request failed.
        if (BuildConfig.DEBUG && loadErrorMessage == null) {
          Log.w("ArtworkScreen", "Artwork is null but no error message is provided.")
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(
              text = loadErrorMessage ?: stringResource(R.string.error_unknown),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.testTag(ArtworkTestTags.ARTWORK_ERROR_MESSAGE),
          )
          Spacer(modifier = Modifier.height(Dimensions.artworkDetailsSectionSpacing))
          Button(
              onClick = artworkScreenViewModel::retryArtwork,
              modifier = Modifier.testTag(ArtworkTestTags.ARTWORK_RETRY_BUTTON),
          ) {
            Text(text = stringResource(R.string.artwork_try_again))
          }
        }
        return@Column
      }

      Card(
          modifier = Modifier.fillMaxWidth().height(Dimensions.artworkDetailsImageHeight),
          shape = RoundedCornerShape(Dimensions.cornerRadiusMedium),
          elevation =
              CardDefaults.cardElevation(defaultElevation = Dimensions.artworkCardElevation),
      ) {
        // Same background as the artwork cards, so the bars around a fitted image look intentional.
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
          // Fit keeps the whole painting visible: unlike the cropped list cards, this screen is
          // where the user looks at the artwork itself.
          ArtworkImage(
              imageUrl = artwork.imageUrl,
              contentDescription =
                  stringResource(
                      R.string.artwork_card_image_description,
                      artwork.name ?: stringResource(R.string.artwork_unknown_title),
                  ),
              contentScale = ContentScale.Fit,
              imageTestTag = ArtworkTestTags.ARTWORK_IMAGE,
              fallbackTestTag = ArtworkTestTags.ARTWORK_IMAGE_FALLBACK,
              modifier = Modifier.fillMaxSize(),
          )
        }
      }

      Spacer(modifier = Modifier.height(Dimensions.artworkDetailsImageVerticalSpacing))

      ArtworkFieldItem(
          label = stringResource(R.string.artwork_label_artist),
          value = artwork.artist?.name ?: stringResource(R.string.artwork_unknown_artist),
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

      // Only show the map-tile if the location of the artwork is actually specified
      if (artwork.location != null) {
        MapTile(
            modifier,
            mapTileCameraPositionState,
            mapTileMarkerState,
            artwork.location,
            artwork.name,
        )
      }
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
      shape = RoundedCornerShape(Dimensions.cornerRadiusSmall),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(Dimensions.artworkFieldContentPadding)) {
      Text(
          text = label,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.SemiBold,
      )
      Spacer(modifier = Modifier.height(Dimensions.artworkFieldLabelValueSpacing))
      Text(
          text = value,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

/**
 * Displays the Map Tile on the screen
 *
 * @param modifier optional modifier for the card container
 * @param cameraPositionState the state of the camera position on the map
 * @param markerState the state of the marker position on the map
 * @param location the location of the artwork which is displayed on the map
 * @param artworkName the name of the artwork which is displayed on the map
 * @author 5kyPhy
 */
@Composable
fun MapTile(
    modifier: Modifier = Modifier,
    cameraPositionState: CameraPositionState,
    markerState: MarkerState,
    location: Location,
    artworkName: String?,
) {
  OutlinedCard(
      modifier = modifier.fillMaxWidth(),
      shape = RoundedCornerShape(Dimensions.cornerRadiusSmall),
  ) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .padding(Dimensions.artworkMapTileContentPadding)
                .testTag(ArtworkTestTags.ARTWORK_MAP_TILE)
    ) {
      Text(
          text = location.name?.let { location.name } ?: "",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.SemiBold,
      )
      Spacer(modifier = Modifier.height(Dimensions.artworkMapTileTitleSpacing))
      GoogleMap(
          modifier = Modifier.fillMaxWidth().height(Dimensions.artworkMapTileHeight),
          cameraPositionState = cameraPositionState,
      ) {
        Marker(
            state = markerState,
            title = artworkName,
        )
      }
    }
  }
}

/** Previews [ArtworkScreen] with a representative artwork from [mockArtworks]. */
@Preview
@Composable
internal fun ArtworkPreview() {
  val sampleArtwork = mockArtworks.first()
  try {
    ArtworkRepositoryProvider.populateAPI()
  } catch (e: Exception) {
    // Ignore any exceptions during preview population
  }

  ArtistaTheme {
    ArtworkScreen(
        artworkScreenViewModel =
            remember {
              ArtworkScreenViewModel(
                  sampleArtwork.uid,
                  ArtworkRepositoryProvider.mockRepository,
                  ArtworkRepositoryProvider.mockRepository,
              )
            },
        modifier = Modifier,
        onBackClick = {},
    )
  }
}
