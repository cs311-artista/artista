// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: DeepSeek Flash
package com.artista.artista

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navigation
import coil3.compose.setSingletonImageLoaderFactory
import com.artista.artista.model.artwork.ArtworkRepositoryProvider
import com.artista.artista.model.artwork.SavedArtworksRepository
import com.artista.artista.model.artwork.WikiDataRepository
import com.artista.artista.model.artwork.createArtworkImageLoader
import com.artista.artista.model.authentication.AuthRepositoryProvider
import com.artista.artista.model.user.UserRepositoryProvider
import com.artista.artista.model.user.UsernameCacheDataStore
import com.artista.artista.model.user.usernameDataStore
import com.artista.artista.model.walking.WalkingRouteRepository
import com.artista.artista.model.walking.WalkingRouteRepositoryRemote
import com.artista.artista.resources.C
import com.artista.artista.ui.artwork.ArtworkScreen
import com.artista.artista.ui.artwork.ArtworkScreenViewModel
import com.artista.artista.ui.artwork.SavedArtworksScreen
import com.artista.artista.ui.artwork.SavedArtworksViewModel
import com.artista.artista.ui.authentication.AuthScreen
import com.artista.artista.ui.authentication.AuthSession
import com.artista.artista.ui.authentication.AuthViewModel
import com.artista.artista.ui.authentication.GoogleCredentialProvider
import com.artista.artista.ui.authentication.rememberGoogleCredentialProvider
import com.artista.artista.ui.map.MapScreen
import com.artista.artista.ui.map.MapViewModel
import com.artista.artista.ui.navigation.NavigationActions
import com.artista.artista.ui.navigation.Screen
import com.artista.artista.ui.overview.OverviewScreen
import com.artista.artista.ui.overview.OverviewViewModel
import com.artista.artista.ui.preferences.ArtistPreferencesScreen
import com.artista.artista.ui.preferences.ArtistPreferencesViewModel
import com.artista.artista.ui.profile.UserProfileScreen
import com.artista.artista.ui.profile.UserProfileViewModel
import com.artista.artista.ui.search.SearchScreen
import com.artista.artista.ui.search.SearchScreenViewModel
import com.artista.artista.ui.theme.ArtistaTheme

/**
 * Hosts the Artista app's root composition, and through it the navigation graph.
 *
 * @author hixeum, patrickmcdan
 */
class MainActivity : ComponentActivity() {
  /**
   * Creates the activity and displays the root navigation host.
   *
   * @param savedInstanceState Previously saved activity state, or null on first creation.
   * @author hixeum
   */
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContent {
      // Makes our image loader the app-wide default, so every AsyncImage uses it without being
      // given it. Its Artista user agent is what lets Wikimedia accept the image requests.
      setSingletonImageLoaderFactory { context -> createArtworkImageLoader(context) }
      ArtistaTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
          ArtistaApp()
        }
      }
    }
  }
}

/**
 * Root of the application: gates the navigation graph on the authentication session.
 *
 * The session, not a destination of the graph, decides what is displayed, so the three situations
 * it can be in map to three compositions instead of three entries in a back stack:
 *
 * - [AuthSession.Restoring]: the repository has not reported the persisted session yet, so nothing
 *   is drawn. The [Surface] of [MainActivity] already paints the app background, so the handover
 *   from the system splash screen stays invisible, and a user who is signed in already is never
 *   offered a sign-in.
 * - [AuthSession.SignedOut]: the sign-in screen. It reports nothing when a sign-in succeeds; the
 *   session turning [AuthSession.SignedIn] replaces the composition.
 * - [AuthSession.SignedIn]: the navigation graph.
 *
 * Because the graph is only composed while a user is signed in, signing out - from the profile
 * screen, or because Firebase revoked the session - removes it along with its back stack. The next
 * sign-in therefore starts from Overview instead of restoring whichever screen the previous user
 * had left open.
 *
 * Removing the graph is not enough to remove what it holds: [NavHost] keeps the destinations'
 * ViewModels in a store owned by the nearest [LocalViewModelStoreOwner], which is the activity
 * here. Dropping the graph pops nothing, and a back stack entry's ViewModel is only cleared when it
 * is popped, so those ViewModels - and the collectors they run - would outlive the sign-out that
 * made them useless, and would keep reacting to the next user's session. The graph is therefore
 * given a store of its own, scoped to this branch, which is cleared with it.
 *
 * @param authViewModel the authentication ViewModel holding the session; injectable so a test can
 *   drive the gate with a fake repository
 * @param credentialProvider the seam used to obtain a Google ID token; injectable so a test never
 *   reaches the real account chooser
 * @param wikiDataRepository source of the artworks
 * @param savedArtworksRepository storage of the saved artworks
 * @param walkingRouteRepository source of the walking routes between artworks
 * @author hixeum
 */
@Composable
fun ArtistaApp(
    authViewModel: AuthViewModel =
        viewModel(factory = AuthViewModel.provideFactory(AuthRepositoryProvider.repository)),
    credentialProvider: GoogleCredentialProvider = rememberGoogleCredentialProvider(),
    wikiDataRepository: WikiDataRepository = ArtworkRepositoryProvider.wikiDataRepository,
    savedArtworksRepository: SavedArtworksRepository =
        ArtworkRepositoryProvider.savedArtworksRepository,
    walkingRouteRepository: WalkingRouteRepository = WalkingRouteRepositoryRemote(),
) {
  val state by authViewModel.uiState.collectAsStateWithLifecycle()

  when (state.session) {
    // Nothing yet: the background of the activity is all this state should show.
    AuthSession.Restoring -> Unit
    AuthSession.SignedOut ->
        AuthScreen(viewModel = authViewModel, credentialProvider = credentialProvider)
    is AuthSession.SignedIn -> {
      // Scoping the graph's ViewModelStore to this composition is what makes dropping the graph
      // clear it: it is cleared when the owner leaves, whereas the activity's store would keep the
      // destinations' ViewModels (and their running collectors) until the activity dies.
      val graphViewModelStoreOwner = rememberViewModelStoreOwner()
      CompositionLocalProvider(LocalViewModelStoreOwner provides graphViewModelStoreOwner) {
        ArtistaNavHost(
            onLogout = authViewModel::signOut,
            wikiDataRepository = wikiDataRepository,
            savedArtworksRepository = savedArtworksRepository,
            walkingRouteRepository = walkingRouteRepository,
        )
      }
    }
  }
}

/**
 * Displays the navigation graph of the signed-in application.
 *
 * Composed only by [ArtistaApp], once a user is signed in: it holds no authentication logic of its
 * own, and reports the logout intent through [onLogout] instead of navigating to a sign-in
 * destination, because there is no such destination to return to.
 *
 * The repositories are required rather than defaulted, unlike those of [ArtistaApp]: the graph is
 * then a function of its arguments only, so a test composes it without reading - or having to
 * initialise - the repositories of the application. The root keeps the defaults, because it is the
 * single place that chooses the application's implementations.
 *
 * Every destination takes its ViewModel from the owner of its own back stack entry, which is
 * cleared when it is popped, with one exception: the profile screen's ViewModel is taken from the
 * store [ArtistaApp] scopes to this composition. It is read once per session rather than on every
 * return to its tab, so it does not reload the profile nor blank the name it displays; and it must
 * not outlive the session either, so it cannot be held by the activity, which is what it was while
 * nothing could sign out.
 *
 * @param onLogout invoked when the user asks to sign out
 * @param wikiDataRepository source of the artworks
 * @param savedArtworksRepository storage of the saved artworks
 * @param walkingRouteRepository source of the walking routes between artworks
 * @param navController Controller used to manage destinations in the root graph.
 * @param startDestination Route displayed when the graph is first opened.
 * @author hixeum
 */
@Composable
fun ArtistaNavHost(
    onLogout: () -> Unit,
    wikiDataRepository: WikiDataRepository,
    savedArtworksRepository: SavedArtworksRepository,
    walkingRouteRepository: WalkingRouteRepository,
    navController: NavHostController = rememberNavController(),
    // The start destination has to be a direct child of the root graph, and Overview lives in the
    // nested graph of its tab, so the graph route is used rather than Screen.Overview.route.
    startDestination: String = C.Constants.OVERVIEW_GRAPH_ROUTE,
) {
  val navigationActions = remember { NavigationActions(navController) }
  // Read here, outside the NavHost, where the owner is the store [ArtistaApp] scopes to the graph:
  // inside a destination it is the navigation entry, which the bottom bar discards on every tab
  // change.
  val graphViewModelStoreOwner =
      checkNotNull(LocalViewModelStoreOwner.current) {
        "ArtistaNavHost needs a ViewModelStoreOwner"
      }
  val mapViewModel =
      viewModel<MapViewModel>(
          viewModelStoreOwner = graphViewModelStoreOwner,
          factory =
              MapViewModel.provideFactory(
                  wikiDataRepository,
                  savedArtworksRepository,
                  walkingRouteRepository,
              ),
      )

  NavHost(navController = navController, startDestination = startDestination) {

    // 1. Overview Tab Flow
    navigation(
        startDestination = Screen.Overview.route,
        route = C.Constants.OVERVIEW_GRAPH_ROUTE,
    ) {
      composable(Screen.Overview.route) {
        OverviewScreen(
            viewModel =
                viewModel(
                    factory =
                        OverviewViewModel.provideFactory(
                            wikiDataRepository,
                            savedArtworksRepository,
                        )
                ),
            onTabSelected = { tab -> navigationActions.navigateTo(tab.destination) },
            onArtworkClick = { artwork ->
              navController.navigate(Screen.ArtworkDetails.createRoute(artwork.uid))
            },
            onSavedArtworksClick = { navController.navigate(Screen.SavedArtworks.route) },
            onSearchClick = { navController.navigate(Screen.Search.route) },
            onMapClick = mapViewModel::loadRouteTo,
        )
      }
      composable(Screen.Search.route) {
        SearchScreen(
            viewModel =
                viewModel(
                    factory =
                        SearchScreenViewModel.provideFactory(
                            savedArtworksRepository,
                            wikiDataRepository,
                        )
                ),
            onTabSelected = { tab -> navigationActions.navigateTo(tab.destination) },
            onArtworkClick = { artwork ->
              navController.navigate(Screen.ArtworkDetails.createRoute(artwork.uid))
            },
            onMapClick = mapViewModel::loadRouteTo,
            onRecommendationSelected = { recommendation ->
              navController.navigate(Screen.ArtworkDetails.createRoute(recommendation.uid))
            },
        )
      }
    }

    // 3. Map Tab Flow
    navigation(
        startDestination = Screen.Map.route,
        route = C.Constants.MAP_GRAPH_ROUTE,
    ) {
      composable(Screen.Map.route) {
        MapScreen(
            mapViewModel = mapViewModel,
            onArtworkMarkerClick = mapViewModel::loadRouteTo,
            onNavigationBarTabSelected = { tab -> navigationActions.navigateTo(tab.destination) },
        )
      }
    }

    // 4. Profile Tab Flow
    navigation(
        startDestination = Screen.Profile.route,
        route = C.Constants.PROFILE_GRAPH_ROUTE,
    ) {
      composable(Screen.Profile.route) {
        UserProfileScreen(
            userProfileViewModel =
                viewModel(
                    // Read once per session, so returning to the tab does not read the profile
                    // again nor blank the name being displayed. Scoped to the graph rather than to
                    // the activity: signing out has to take this ViewModel with it.
                    viewModelStoreOwner = graphViewModelStoreOwner,
                    factory =
                        UserProfileViewModel.provideFactory(
                            authRepository = AuthRepositoryProvider.repository,
                            userRepository = UserRepositoryProvider.repository,
                            usernameCache =
                                UsernameCacheDataStore(
                                    LocalContext.current.applicationContext.usernameDataStore
                                ),
                        ),
                ),
            onLogoutClick = onLogout,
            onPreferencesClick = { navController.navigate(Screen.ArtistPreferences.route) },
            onSavedArtworkClick = { navController.navigate(Screen.SavedArtworks.route) },
            onTabSelected = { tab -> navigationActions.navigateTo(tab.destination) },
        )
      }
      composable(Screen.ArtistPreferences.route) {
        ArtistPreferencesScreen(
            artistPreferencesViewModel =
                viewModel(
                    factory =
                        ArtistPreferencesViewModel.provideFactory(
                            wikiDataRepository = wikiDataRepository,
                            authRepository = AuthRepositoryProvider.repository,
                            userRepository = UserRepositoryProvider.repository,
                        )
                ),
            onBackClick = { navigationActions.goBack() },
            onTabSelected = { tab -> navigationActions.navigateTo(tab.destination) },
        )
      }
    }

    // Shared Screens
    composable(Screen.SavedArtworks.route) {
      SavedArtworksScreen(
          viewModel =
              viewModel(factory = SavedArtworksViewModel.provideFactory(savedArtworksRepository)),
          onBackClick = { navigationActions.goBack() },
          onArtworkDetailsClick = { artwork ->
            navController.navigate(Screen.ArtworkDetails.createRoute(artwork.uid))
          },
          onTabSelected = { tab -> navigationActions.navigateTo(tab.destination) },
          onMapClick = mapViewModel::loadRouteTo,
      )
    }

    composable(
        route = Screen.ArtworkDetails.route,
        arguments = listOf(navArgument("artworkId") { type = NavType.StringType }),
    ) { backStackEntry ->
      val artworkId = backStackEntry.arguments?.getString("artworkId")

      if (artworkId != null) {
        ArtworkScreen(
            artworkScreenViewModel =
                viewModel(
                    factory =
                        ArtworkScreenViewModel.provideFactory(
                            artworkId,
                            wikiDataRepository,
                            savedArtworksRepository,
                        )
                ),
            onBackClick = { navController.popBackStack() },
        )
      }
    }
  }
}
