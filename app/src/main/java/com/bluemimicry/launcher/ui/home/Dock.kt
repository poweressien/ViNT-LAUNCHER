package com.bluemimicry.launcher.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bluemimicry.launcher.LauncherViewModel
import com.bluemimicry.launcher.ui.components.AppTile
import com.bluemimicry.launcher.ui.components.SearchPill
import com.bluemimicry.launcher.ui.theme.LocalPalette

/** Favorite tray: large raised icons with real badges, a search pill above, and an optional drawer button. */
@Composable
fun Dock(vm: LauncherViewModel, onSearch: () -> Unit, onDrawer: () -> Unit, modifier: Modifier = Modifier) {
    val dock by vm.dock.collectAsState()
    val apps by vm.allApps.collectAsState()
    val settings by vm.settings.collectAsState()
    val pal = LocalPalette.current
    val byKey = apps.associateBy { it.key }

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        SearchPill(onSearch)
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp)
                .background(pal.surface.copy(alpha = settings.dockAlpha), RoundedCornerShape(34.dp)).padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
        ) {
            dock.mapNotNull { byKey[it.key] }.take(settings.dockSlots).forEach { a ->
                AppTile(a, vm, 62.dp, showLabel = false)
            }
            Icon(
                Icons.Filled.Apps, "All apps", tint = pal.text,
                modifier = Modifier.size(34.dp).clickable(onClick = onDrawer),
            )
        }
    }
}
