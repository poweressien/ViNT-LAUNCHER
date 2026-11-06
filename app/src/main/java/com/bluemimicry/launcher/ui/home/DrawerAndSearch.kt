package com.bluemimicry.launcher.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.bluemimicry.launcher.LauncherViewModel
import com.bluemimicry.launcher.data.AppInfo
import com.bluemimicry.launcher.data.FolderEntity
import com.bluemimicry.launcher.ui.components.AppTile
import com.bluemimicry.launcher.ui.theme.LocalPalette

enum class SortMode(val label: String) { NAME("Name"), INSTALLED("Installed"), USAGE("Usage"), RECENT("Recent") }

private fun sorted(apps: List<AppInfo>, mode: SortMode, vm: LauncherViewModel): List<AppInfo> {
    val m = vm.meta.value
    return when (mode) {
        SortMode.NAME -> apps.sortedBy { vm.labelOf(it).lowercase() }
        SortMode.INSTALLED -> apps.sortedByDescending { it.installTime }
        SortMode.USAGE -> apps.sortedByDescending { m[it.key]?.launchCount ?: 0 }
        SortMode.RECENT -> apps.sortedByDescending { m[it.key]?.lastLaunch ?: 0L }
    }
}

/** Full app drawer: every launchable app, searchable and sortable, plus a hidden-apps restore section. */
@Composable
fun AppDrawer(vm: LauncherViewModel, onClose: () -> Unit) {
    val pal = LocalPalette.current
    val visible by vm.visibleApps.collectAsState()
    val all by vm.allApps.collectAsState()
    val meta by vm.meta.collectAsState()
    var sort by remember { mutableStateOf(SortMode.NAME) }
    var showHidden by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val source = if (showHidden) all.filter { meta[it.key]?.hidden == true } else visible
    val list = remember(source, sort, query, meta) {
        sorted(source, sort, vm).filter { query.isBlank() || vm.labelOf(it).contains(query, ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize().background(pal.bg.copy(alpha = 0.97f)).statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
            OutlinedTextField(
                query, { query = it }, singleLine = true, placeholder = { Text("Search apps") },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onClose) { Text("Close") }
        }
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SortMode.values().forEach { s ->
                FilterChip(selected = sort == s, onClick = { sort = s }, label = { Text(s.label) })
            }
            FilterChip(selected = showHidden, onClick = { showHidden = !showHidden }, label = { Text("Hidden") })
        }
        LazyVerticalGrid(GridCells.Adaptive(82.dp), Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(list, key = { it.key }) { a ->
                Box(contentAlignment = Alignment.Center) {
                    if (showHidden) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { vm.hide(a, false) }) {
                            AppTile(a, vm, 56.dp, enabled = false)
                            Text("Restore", color = pal.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else AppTile(a, vm, 56.dp, onTap = { vm.launch(a); onClose() })
                }
            }
        }
    }
}

private sealed interface Hit {
    data class App(val app: AppInfo) : Hit
    data class Folder(val f: FolderEntity) : Hit
    data class Setting(val title: String, val action: String) : Hit
    data class Contact(val name: String, val id: Long) : Hit
}

private val settingEntries = listOf(
    "Display engine" to "ENGINE", "Themes" to "STUDIO", "Icon style" to "STUDIO", "Wallpaper" to "STUDIO",
    "Fonts" to "STUDIO", "Animations" to "STUDIO", "Layout editor" to "STUDIO", "Gestures" to "STUDIO",
    "Control Center" to "CONTROL", "Notifications" to "NOTIFS",
)

/** Universal search: apps, groups, launcher settings and (with permission) contacts. IME "Search" launches the top hit. */
@Composable
fun UniversalSearch(vm: LauncherViewModel, onClose: () -> Unit, onOpenFolder: (FolderEntity) -> Unit, onAction: (String) -> Unit) {
    val pal = LocalPalette.current
    val ctx = LocalContext.current
    val apps by vm.visibleApps.collectAsState()
    val folders by vm.folders.collectAsState()
    var q by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    var contactsOk by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) }
    val askContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { contactsOk = it }

    LaunchedEffect(Unit) { focus.requestFocus() }

    val hits: List<Hit> = remember(q, apps, folders, contactsOk) {
        if (q.isBlank()) vm.suggestions(5).map { Hit.App(it) } else buildList {
            apps.filter { vm.labelOf(it).contains(q, true) }.sortedBy { !vm.labelOf(it).startsWith(q, true) }.take(10).forEach { add(Hit.App(it)) }
            folders.filter { it.name.contains(q, true) }.forEach { add(Hit.Folder(it)) }
            settingEntries.filter { it.first.contains(q, true) }.forEach { add(Hit.Setting(it.first, it.second)) }
            if (contactsOk && q.length >= 2) runCatching {
                ctx.contentResolver.query(
                    ContactsContract.Contacts.CONTENT_URI, arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
                    "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?", arrayOf("%$q%"), null,
                )?.use { c -> var n = 0; while (c.moveToNext() && n++ < 5) add(Hit.Contact(c.getString(1) ?: "", c.getLong(0))) }
            }
        }
    }

    fun activate(h: Hit) {
        when (h) {
            is Hit.App -> vm.launch(h.app)
            is Hit.Folder -> onOpenFolder(h.f)
            is Hit.Setting -> onAction(h.action)
            is Hit.Contact -> runCatching {
                ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, h.id.toString())).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        onClose()
    }

    Column(Modifier.fillMaxSize().background(pal.bg.copy(alpha = 0.98f)).statusBarsPadding().imePadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                q, { q = it }, singleLine = true, placeholder = { Text("Search apps, groups, settings, contacts") },
                modifier = Modifier.weight(1f).focusRequester(focus),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { hits.firstOrNull()?.let(::activate) }),
            )
            TextButton(onClose) { Text("Cancel") }
        }
        if (!contactsOk) TextButton({ askContacts.launch(Manifest.permission.READ_CONTACTS) }) { Text("Include contacts in search") }
        if (q.isBlank() && hits.isNotEmpty()) Text("Suggested", color = pal.subtext, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(hits) { h ->
                val (title, sub) = when (h) {
                    is Hit.App -> vm.labelOf(h.app) to "App"
                    is Hit.Folder -> h.f.name to "Group"
                    is Hit.Setting -> h.title to "Launcher setting"
                    is Hit.Contact -> h.name to "Contact"
                }
                Row(Modifier.fillMaxWidth().clickable { activate(h) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (h is Hit.App) {
                        com.bluemimicry.launcher.ui.components.AppIcon(h.app, vm.repo, vm.settings.value.iconStyle, 44.dp)
                        Spacer(Modifier.width(14.dp))
                    }
                    Column {
                        Text(title, color = pal.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(sub, color = pal.subtext, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/** Opens a group so every member (not just the ones visible on the card) is reachable. */
@Composable
fun FolderOverlay(folder: FolderEntity, vm: LauncherViewModel, onClose: () -> Unit) {
    val pal = LocalPalette.current
    val links by vm.folderApps.collectAsState()
    val apps by vm.visibleApps.collectAsState()
    val byKey = remember(apps) { apps.associateBy { it.key } }
    val members = links.filter { it.folderId == folder.id }.mapNotNull { byKey[it.key] }

    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(24.dp).background(pal.surface, RoundedCornerShape(32.dp)).clickable(enabled = false) {}.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(folder.name, color = pal.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(GridCells.Fixed(4), Modifier.heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(members, key = { it.key }) { a -> AppTile(a, vm, 54.dp, onTap = { vm.launch(a); onClose() }) }
            }
        }
    }
}
