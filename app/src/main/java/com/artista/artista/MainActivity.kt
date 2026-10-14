package com.artista.artista

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import com.artista.artista.resources.C
import com.artista.artista.ui.artwork.SavedArtworksScreen
import com.artista.artista.ui.authentication.AuthScreenContent
import com.artista.artista.ui.authentication.AuthUiState
import com.artista.artista.ui.map.MapScreen
import com.artista.artista.ui.navigation.NavigationActions
import com.artista.artista.ui.navigation.Screen
import com.artista.artista.ui.overview.OverviewScreen
import com.artista.artista.ui.profile.UserProfileScreen
import com.artista.artista.ui.theme.ArtistaTheme

// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
/**
 * Hosts the Artista app's root navigation graph.
 *
 * @author hixeum
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
      ArtistaTheme {
        Surface(
            modifier = Modifier.fillMaxSize().semantics { testTag = C.Tag.main_screen_container },
            color = MaterialTheme.colorScheme.background,
        ) {
          ArtistaApp()
        }
      }
    }
  }
}

/**
 * Displays the root navigation graph, starting with authentication.
 *
 * @param navController Controller used to manage destinations in the root graph.
 * @param startDestination Route displayed when the graph is first opened.
 * @author hixeum
 */
@Composable
fun ArtistaApp(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Auth.route,
) {
  val navigationActions = remember { NavigationActions(navController) }

  NavHost(navController = navController, startDestination = startDestination) {

    // 1. Auth Flow
    composable(Screen.Auth.route) {
      AuthScreenContent(
          state = AuthUiState(),
          snackbarHostState = remember { SnackbarHostState() },
          onSignInClick = {
            navController.navigate(Screen.Overview.route) {
              popUpTo(Screen.Auth.route) { inclusive = true }
            }
          },
          onSignedIn = {},
      )
    }

    // 2. Overview Tab Flow
    navigation(
        startDestination = Screen.Overview.route,
        route = "overview_graph",
    ) {
      composable(Screen.Overview.route) {
        OverviewScreen(
            onTabSelected = { tab -> navigationActions.navigateTo(tab.destination) },
            onSavedArtworksClick = { navController.navigate(Screen.SavedArtworks.route) },
        )
      }
    }

    // 3. Map Tab Flow
    navigation(
        startDestination = Screen.Map.route,
        route = "map_graph",
    ) {
      composable(Screen.Map.route) {
        MapScreen(
            onNavigationBarTabSelected = { tab -> navigationActions.navigateTo(tab.destination) }
        )
      }
    }

    // 4. Profile Tab Flow
    navigation(
        startDestination = Screen.Profile.route,
        route = "profile_graph",
    ) {
      composable(Screen.Profile.route) {
        UserProfileScreen(
            onLogoutClick = {
              navController.navigate(Screen.Auth.route) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
              }
            },
            onSavedArtworkClick = { navController.navigate(Screen.SavedArtworks.route) },
            onTabSelected = { tab -> navigationActions.navigateTo(tab.destination) },
        )
      }
    }

    // Shared Screens
    composable(Screen.SavedArtworks.route) {
      SavedArtworksScreen(
          onBackClick = {},
          onArtworkDetailsClick = {},
          onTabSelected = { tab -> navigationActions.navigateTo(tab.destination) },
      )
    }
  }
}
