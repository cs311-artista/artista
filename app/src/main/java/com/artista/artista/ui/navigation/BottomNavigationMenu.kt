package com.artista.artista.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
/**
 * Represents a destination that can be selected from the bottom navigation menu.
 *
 * @param name The label displayed for the destination.
 * @param icon Icon displayed for the destination.
 * @param destination The route associated with the destination.
 * @author patrickmcdan
 */
sealed class Tab(
    val name: String,
    val icon: ImageVector,
    val destination: Screen,
) {

  /** The overview destination. */
  data object Overview : Tab("Overview", Icons.Outlined.Home, Screen.Overview)

  /** The map destination. */
  data object Map : Tab("Map", Icons.Outlined.LocationOn, Screen.Map)

  /** The profile destination. */
  data object Profile : Tab("Profile", Icons.Outlined.Person, Screen.Profile)
}

private val tabs = listOf(Tab.Overview, Tab.Map, Tab.Profile)

/**
 * Displays the application's top-level destinations in a bottom navigation bar.
 *
 * @param selectedTab The destination currently selected by the user, or null when the current
 *   screen is not a bottom-navigation destination.
 * @param onTabSelected Callback invoked with the destination selected by the user.
 * @param modifier Modifier applied to the navigation bar.
 * @author patrickmcdan
 */
@Composable
fun BottomNavigationMenu(
    selectedTab: Tab?,
    onTabSelected: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
  Box(modifier = modifier) {
    NavigationBar(
        modifier =
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .clip(RoundedCornerShape(32.dp))
                .testTag(NavigationTestTags.BOTTOM_NAVIGATION_MENU),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
      tabs.forEach { tab ->
        NavigationBarItem(
            selected = tab == selectedTab,
            onClick = { onTabSelected(tab) },
            icon = { Icon(tab.icon, contentDescription = tab.name) },
            label = null,
            modifier = Modifier.testTag(NavigationTestTags.getTabTestTag(tab)),
        )
      }
    }
  }
}
