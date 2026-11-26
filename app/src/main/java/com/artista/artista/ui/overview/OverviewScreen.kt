// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.artista.artista.ui.overview

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.artista.artista.R
import com.artista.artista.model.artwork.Artwork
import com.artista.artista.model.artwork.ArtworkFilter
import com.artista.artista.model.artwork.ArtworkRepositoryLocal
import com.artista.artista.model.artwork.mockArtworks
import com.artista.artista.model.error.AppException
import com.artista.artista.model.location.UserLocationProvider
import com.artista.artista.ui.artwork.ArtworkFilterDialog
import com.artista.artista.ui.artwork.ArtworkListCard
import com.artista.artista.ui.artwork.ArtworkTile
import com.artista.artista.ui.location.UserLocationPermission
import com.artista.artista.ui.location.hasUserLocationPermissions
import com.artista.artista.ui.navigation.BottomNavigationMenu
import com.artista.artista.ui.navigation.Tab
import com.artista.artista.ui.theme.ArtistaTheme
import com.artista.artista.ui.theme.Dimensions
import com.artista.artista.ui.theme.OverviewPalette
import com.artista.artista.ui.theme.SearchBarPalette
import com.google.android.gms.location.LocationServices

/**
 * Provides stable semantics tags for Overview screen UI tests.
 *
 * @author patrickmcdan
 */
object OverviewTestTags {
  const val SCREEN = "overview_screen"
  const val ARTWORK_LIST = "overview_artwork_list"
  const val ARTWORK_ITEM = "overview_artwork_item"
  const val ERROR_MESSAGE = "overview_error_message"
  const val SAVED_ARTWORKS_BUTTON = "overview_saved_artworks_button"
  const val SEARCH_BAR = "overview_search_bar"
  const val FILTER_BUTTON = "overview_filter_button"
  const val FILTER_ACTIVE = "overview_filter_active"
  const val EMPTY_FILTER_RESULTS = "overview_empty_filter_results"
  const val CLEAR_FILTERS_BUTTON = "overview_clear_filters_button"
  const val NEARBY_ARTWORKS_TITLE = "overview_nearby_artworks_title"
  const val DISPLAY_MODE_BUTTON = "overview_display_mode_button"
}

/**
 * Displays the artwork overview using the state supplied by its ViewModel.
 *
 * @param onTabSelected callback invoked when a bottom-navigation destination is selected
 * @param onArtworkClick callback invoked when an artwork is selected to open its details
 * @param onSavedArtworksClick callback invoked when the user chooses saved artworks after an
 *   overview loading error
 * @param onMapClick callback invoked when the user selects the map action for an artwork
 * @param viewModel ViewModel supplying the artwork entries
 * @param onSearchClick callback invoked when the search control is selected
 * @param hasLocationPermissions checks whether the location permissions are granted
 * @author patrickmcdan, hixeum
 */
@Composable
fun OverviewScreen(
    viewModel: OverviewViewModel,
    onTabSelected: (Tab) -> Unit = {},
    onArtworkClick: (Artwork) -> Unit = {},
    onSavedArtworksClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onMapClick: (Artwork) -> Unit = {},
    hasLocationPermissions: (Context) -> Boolean = ::hasUserLocationPermissions,
) {
  val uiState by viewModel.uiState.collectAsState()
  val lifecycleOwner = LocalLifecycleOwner.current
  var filterDialogVisible by remember { mutableStateOf(false) }
  val context = LocalContext.current
  val errorMessage = uiState.errorMessageRes?.let { stringResource(it) }
  val saveErrorMessage = uiState.saveErrorMessageRes?.let { stringResource(it) }
  var locationAcquisitionRequested by remember { mutableStateOf(false) }

  // Overview may stay in memory while the artwork screen changes saved-artwork storage. Reload
  // saved IDs whenever Overview resumes so its bookmark icons reflect those external changes.
  DisposableEffect(lifecycleOwner, viewModel) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        viewModel.refreshSavedArtworkIds()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  UserLocationPermission(
      onPermissionResult = { granted ->
        viewModel.handleLocationPermissionResult(granted)
        if (granted && (!locationAcquisitionRequested || uiState.userLocation == null)) {
          locationAcquisitionRequested = true
          viewModel.refreshUiState()
        } else if (!granted) {
          viewModel.setErrorMessage(AppException.PermissionsError())
        }
      },
      hasPermissions = hasLocationPermissions,
  )

  LaunchedEffect(errorMessage) {
    errorMessage?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
  }

  LaunchedEffect(saveErrorMessage) {
    saveErrorMessage?.let {
      Toast.makeText(context, it, Toast.LENGTH_LONG).show()
      viewModel.clearSaveErrorMessage()
    }
  }

  Scaffold(
      modifier = Modifier.fillMaxSize().testTag(OverviewTestTags.SCREEN),
      topBar = {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .statusBarsPadding()
                    .padding(
                        horizontal = Dimensions.overviewSearchBarHorizontalPadding,
                        vertical = Dimensions.overviewSearchBarVerticalPadding,
                    ),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.overviewSearchBarFilterSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
          OverviewSearchBar(
              onClick = onSearchClick,
              modifier = Modifier.weight(1f),
          )
          // Same container as the artwork cards, so the controls read as distinct shapes on the
          // screen background instead of blending into it.
          FilledIconButton(
              onClick = { filterDialogVisible = true },
              modifier =
                  Modifier.size(Dimensions.overviewFilterButtonSize)
                      .testTag(OverviewTestTags.FILTER_BUTTON),
              shape = CircleShape,
              colors =
                  IconButtonDefaults.filledIconButtonColors(
                      containerColor = CardDefaults.cardColors().containerColor,
                      contentColor = MaterialTheme.colorScheme.onSurface,
                  ),
          ) {
            BadgedBox(
                badge = {
                  if (uiState.activeFilter.isActive()) {
                    Badge(modifier = Modifier.testTag(OverviewTestTags.FILTER_ACTIVE))
                  }
                }
            ) {
              Icon(
                  imageVector = Icons.Outlined.FilterList,
                  contentDescription = stringResource(R.string.artwork_filter_open),
                  tint = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }
      },
      bottomBar = {
        BottomNavigationMenu(selectedTab = Tab.Overview, onTabSelected = onTabSelected)
      },
  ) { innerPadding ->
    if (filterDialogVisible) {
      ArtworkFilterDialog(
          filter = uiState.activeFilter,
          onApply = {
            viewModel.applyFilter(it)
            filterDialogVisible = false
          },
          onClear = {
            viewModel.applyFilter(ArtworkFilter())
            filterDialogVisible = false
          },
          onDismiss = { filterDialogVisible = false },
          artistOptions = uiState.artistOptions,
          museumOptions = uiState.museumOptions,
          locationOptions = uiState.locationOptions,
      )
    }
    val contentModifier = Modifier.fillMaxSize().padding(innerPadding)
    if (errorMessage != null) {
      OverviewMessageWithAction(
          message = stringResource(R.string.overview_load_error),
          actionLabel = stringResource(R.string.overview_saved_artworks_action),
          onActionClick = onSavedArtworksClick,
          actionTestTag = OverviewTestTags.SAVED_ARTWORKS_BUTTON,
          modifier = contentModifier.testTag(OverviewTestTags.ERROR_MESSAGE),
      )
    } else if (uiState.artworks.isEmpty() && uiState.activeFilter.isActive()) {
      OverviewMessageWithAction(
          message = stringResource(R.string.overview_empty_filter_results),
          actionLabel = stringResource(R.string.overview_clear_filters),
          onActionClick = viewModel::clearFilter,
          actionTestTag = OverviewTestTags.CLEAR_FILTERS_BUTTON,
          modifier = contentModifier.testTag(OverviewTestTags.EMPTY_FILTER_RESULTS),
      )
    } else {
      OverviewArtworks(
          uiState = uiState,
          onArtworkClick = onArtworkClick,
          onSaveClick = viewModel::toggleSavedArtwork,
          onMapClick = onMapClick,
          onDisplayModeClick = viewModel::toggleDisplayMode,
          modifier = contentModifier,
      )
    }
  }
}

/**
 * Displays the scrolling content of the overview: the section title with the layout button,
 * followed by the nearby artworks as a grid or as a list.
 *
 * The title lives in the lazy grid as a full-width item so that it scrolls with the artworks.
 *
 * @param uiState state providing the artworks, their saved status and the display mode
 * @param onArtworkClick called when an artwork is selected to open its details
 * @param onSaveClick called when an artwork's bookmark is selected
 * @param onMapClick called when an artwork's map action is selected
 * @param onDisplayModeClick called when the user switches between the grid and the list
 * @param modifier modifier applied to the lazy grid
 * @author hixeum
 */
@Composable
private fun OverviewArtworks(
    uiState: OverviewUiState,
    onArtworkClick: (Artwork) -> Unit,
    onSaveClick: (Artwork) -> Unit,
    onMapClick: (Artwork) -> Unit,
    onDisplayModeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val isGrid = uiState.displayMode == OverviewDisplayMode.GRID
  LazyVerticalGrid(
      columns = GridCells.Fixed(if (isGrid) GRID_COLUMNS else LIST_COLUMNS),
      modifier = modifier.testTag(OverviewTestTags.ARTWORK_LIST),
      contentPadding =
          PaddingValues(
              horizontal = Dimensions.overviewListHorizontalPadding,
              vertical = Dimensions.overviewListVerticalPadding,
          ),
      horizontalArrangement = Arrangement.spacedBy(Dimensions.overviewGridItemSpacing),
      verticalArrangement = Arrangement.spacedBy(Dimensions.overviewGridItemSpacing),
  ) {
    item(key = NEARBY_TITLE_KEY, span = { GridItemSpan(maxLineSpan) }) {
      NearbyArtworksHeader(
          displayMode = uiState.displayMode,
          onDisplayModeClick = onDisplayModeClick,
      )
    }
    items(uiState.artworks, key = { it.uid }) { artwork ->
      val isSaved = artwork.uid in uiState.savedArtworkIds
      val itemModifier = Modifier.testTag(OverviewTestTags.ARTWORK_ITEM)
      if (isGrid) {
        ArtworkTile(
            artwork = artwork,
            isSaved = isSaved,
            onClick = onArtworkClick,
            onSaveClick = onSaveClick,
            onMapClick = onMapClick,
            modifier = itemModifier,
        )
      } else {
        ArtworkListCard(
            artwork = artwork,
            isSaved = isSaved,
            onClick = onArtworkClick,
            onSaveClick = onSaveClick,
            onMapClick = onMapClick,
            modifier = itemModifier,
        )
      }
    }
  }
}

/**
 * Displays the title of the nearby artworks section and the button switching its layout.
 *
 * The button shows the icon of the current layout, as in the V2 mockup, and its description names
 * the layout it switches to.
 *
 * @param displayMode layout currently used by the nearby artworks
 * @param onDisplayModeClick called when the user taps the layout button
 * @param modifier modifier applied to the header row
 * @author hixeum
 */
@Composable
private fun NearbyArtworksHeader(
    displayMode: OverviewDisplayMode,
    onDisplayModeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    OverviewSectionTitle(
        text = stringResource(R.string.overview_nearby_artworks_title),
        modifier = Modifier.weight(1f).testTag(OverviewTestTags.NEARBY_ARTWORKS_TITLE),
    )
    IconButton(
        onClick = onDisplayModeClick,
        modifier = Modifier.testTag(OverviewTestTags.DISPLAY_MODE_BUTTON),
    ) {
      when (displayMode) {
        OverviewDisplayMode.GRID ->
            Icon(
                imageVector = Icons.Outlined.GridView,
                contentDescription = stringResource(R.string.overview_show_as_list),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        OverviewDisplayMode.LIST ->
            Icon(
                imageVector = Icons.Outlined.ViewAgenda,
                contentDescription = stringResource(R.string.overview_show_as_grid),
                tint = MaterialTheme.colorScheme.onSurface,
            )
      }
    }
  }
}

/**
 * Displays the title of an overview section.
 *
 * @param text title to display
 * @param modifier modifier applied to the text
 * @author hixeum
 */
@Composable
private fun OverviewSectionTitle(text: String, modifier: Modifier = Modifier) {
  Text(
      text = text,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onBackground,
      modifier = modifier.padding(top = Dimensions.overviewSectionTitleTopPadding),
  )
}

/**
 * Displays a non-editable search control that delegates search navigation to its caller.
 *
 * @param onClick callback invoked when the search control is selected
 * @param modifier modifier applied to the search control
 * @author patrickmcdan
 */
@Composable
private fun OverviewSearchBar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Surface(
      modifier =
          modifier
              .fillMaxWidth()
              .height(Dimensions.overviewSearchBarHeight)
              .clickable(role = Role.Button, onClick = onClick)
              .semantics(mergeDescendants = true) {}
              .testTag(OverviewTestTags.SEARCH_BAR),
      // Same fill as the artwork cards, but fully rounded to pair with the round filter button.
      // No tonal elevation: it tinted the white towards the purple surface tint, which made the
      // bar blend into the background.
      shape = MaterialTheme.shapes.extraLarge,
      color = CardDefaults.cardColors().containerColor,
  ) {
    Row(
        modifier =
            Modifier.fillMaxSize()
                .padding(horizontal = Dimensions.overviewSearchBarContentHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
          imageVector = Icons.Outlined.Search,
          contentDescription = null,
          tint = SearchBarPalette.content,
      )
      Text(
          text = stringResource(R.string.overview_search_placeholder),
          modifier = Modifier.padding(start = Dimensions.overviewSearchBarIconTextSpacing),
          color = SearchBarPalette.content,
      )
    }
  }
}

/**
 * Displays a centered message with a single grey action button below it, used by the overview
 * states that replace the artworks: the loading error and the empty filter result.
 *
 * @param message text explaining the state
 * @param actionLabel label of the action button
 * @param onActionClick called when the action button is selected
 * @param actionTestTag test tag applied to the action button
 * @param modifier modifier applied to the container
 * @author patrickmcdan, hixeum
 */
@Composable
private fun OverviewMessageWithAction(
    message: String,
    actionLabel: String,
    onActionClick: () -> Unit,
    actionTestTag: String,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier = modifier.padding(horizontal = Dimensions.overviewEmptyStateHorizontalPadding),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement =
          Arrangement.spacedBy(Dimensions.overviewErrorActionSpacing, Alignment.CenterVertically),
  ) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
    )
    Button(
        onClick = onActionClick,
        modifier = Modifier.testTag(actionTestTag),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = OverviewPalette.container,
                contentColor = OverviewPalette.content,
            ),
    ) {
      Text(text = actionLabel)
    }
  }
}

private const val GRID_COLUMNS = 2
private const val LIST_COLUMNS = 1
private const val NEARBY_TITLE_KEY = "overview_nearby_title"

/** Previews the overview screen with a sample set of artworks in Android Studio. */
@Preview(showBackground = true)
@Composable
internal fun OverviewScreenPreview() {
  val locationClient = LocationServices.getFusedLocationProviderClient(LocalContext.current)
  val viewModel = remember {
    val repository = ArtworkRepositoryLocal()
    mockArtworks.forEach { repository.putArtworkInRepositoryUnsavedSection(it) }
    OverviewViewModel(
        repository,
        repository,
        UserLocationProvider(locationClient),
    )
  }
  ArtistaTheme { OverviewScreen(viewModel = viewModel) }
}
