package com.artista.artista.ui.navigation

import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.artista.artista.R

// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
/**
 * Represents a destination that can be selected from the bottom navigation menu.
 *
 * @param nameResId String resource for the tab icon's accessibility description.
 * @param icon Icon displayed for the destination.
 * @param destination The route associated with the destination.
 * @author patrickmcdan
 */
sealed class Tab(
    @StringRes val nameResId: Int,
    val icon: ImageVector,
    val destination: Screen,
) {

  /** The overview destination. */
  data object Overview : Tab(R.string.navigation_overview, Icons.Outlined.Home, Screen.Overview)

  /** The map destination. */
  data object Map : Tab(R.string.navigation_map, Icons.Outlined.LocationOn, Screen.Map)

  /** The profile destination. */
  data object Profile : Tab(R.string.navigation_profile, Icons.Outlined.Person, Screen.Profile)
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
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
      tabs.forEach { tab ->
        val name = stringResource(tab.nameResId)
        NavigationBarItem(
            selected = tab == selectedTab,
            onClick = { onTabSelected(tab) },
            icon = { Icon(tab.icon, contentDescription = name) },
            label = null,
            modifier = Modifier.testTag(NavigationTestTags.getTabTestTag(tab)),
        )
      }
    }
  }
}
