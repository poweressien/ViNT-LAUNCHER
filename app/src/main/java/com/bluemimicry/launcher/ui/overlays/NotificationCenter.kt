package com.bluemimicry.launcher.ui.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemimicry.launcher.LauncherViewModel
import com.bluemimicry.launcher.notifications.BadgeStore
import com.bluemimicry.launcher.notifications.NotifItem
import com.bluemimicry.launcher.system.SystemActions
import com.bluemimicry.launcher.ui.theme.LocalPalette

/**
 * Launcher notification centre: grouped by app, expandable, dismissable, with real actions
 * (the notification's own PendingIntents) and in-memory history. Needs Notification Access.
 */
@Composable
fun NotificationCenter(vm: LauncherViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val pal = LocalPalette.current
    val settings by vm.settings.collectAsState()
    val connected by BadgeStore.connected.collectAsState()
    val active by BadgeStore.active.collectAsState()
    val history by BadgeStore.history.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    val list = if (tab == 0) active else history
    val grouped = remember(list) { list.groupBy { it.pkg } }
    var expandedPkg by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable(onClick = onClose)) {
        Column(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(12.dp).fillMaxHeight(0.82f)
                .background(pal.bg, RoundedCornerShape(30.dp)).clickable(enabled = false) {}.padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Notifications", color = pal.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(if (tab == 0) "History" else "Active", color = pal.accent, modifier = Modifier.clickable { tab = 1 - tab }.padding(8.dp))
                if (tab == 0) Text("Clear all", color = pal.badge, modifier = Modifier.clickable { BadgeStore.dismissAll() }.padding(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Text("Hide content (privacy)", color = pal.subtext, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(settings.hideNotifContent, { v -> vm.update { it.copy(hideNotifContent = v) } })
            }
            if (!connected) {
                Text("Notification access is off, so there is nothing to show yet.", color = pal.subtext, fontSize = 14.sp, modifier = Modifier.padding(vertical = 10.dp))
                Text("Grant notification access", color = pal.accent, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { SystemActions.notificationAccess(ctx) }.padding(vertical = 6.dp))
                Text("Open the system shade instead", color = pal.accent, modifier = Modifier.clickable { onClose(); SystemActions.expandSystemShade(ctx) }.padding(vertical = 6.dp))
            } else if (list.isEmpty()) {
                Text(if (tab == 0) "You're all caught up." else "No history yet this session.", color = pal.subtext, modifier = Modifier.padding(vertical = 12.dp))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                grouped.forEach { (pkg, items) ->
                    item(key = "g$pkg$tab") {
                        val label = vm.allApps.value.firstOrNull { it.pkg == pkg }?.label ?: pkg
                        Column(Modifier.fillMaxWidth().background(pal.surface, RoundedCornerShape(20.dp)).padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { expandedPkg = if (expandedPkg == pkg) null else pkg }) {
                                Text(label, color = pal.text, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                Text("${items.size}", color = pal.subtext, fontSize = 12.sp)
                            }
                            val shown = if (expandedPkg == pkg) items else items.take(1)
                            shown.forEach { n -> Card(n, settings.hideNotifContent, tab == 0, vm) { onClose() } }
                            if (expandedPkg != pkg && items.size > 1) Text("+${items.size - 1} more", color = pal.accent, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Card(n: NotifItem, hide: Boolean, live: Boolean, vm: LauncherViewModel, onOpened: () -> Unit) {
    val pal = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(if (hide) "Notification" else n.title, color = pal.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(if (hide) "Content hidden" else n.text, color = pal.subtext, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (live) Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 4.dp)) {
            Text("Open", color = pal.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable {
                runCatching { n.intent?.send() }.onFailure { vm.allApps.value.firstOrNull { it.pkg == n.pkg }?.let(vm::launch) }; onOpened()
            })
            n.actions.take(2).forEach { (title, pi) -> if (title.isNotBlank()) Text(title, color = pal.accent, fontSize = 13.sp, modifier = Modifier.clickable { runCatching { pi?.send() } }) }
            if (n.clearable) Text("Dismiss", color = pal.badge, fontSize = 13.sp, modifier = Modifier.clickable { BadgeStore.dismiss(n.key) })
        }
    }
}
