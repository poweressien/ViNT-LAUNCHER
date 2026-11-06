package com.bluemimicry.launcher.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bluemimicry.launcher.LauncherViewModel
import com.bluemimicry.launcher.data.AppInfo

/** Long-press menu shared by every display engine. Every item performs a real action. */
@Composable
fun AppMenu(app: AppInfo?, vm: LauncherViewModel, onDismiss: () -> Unit) {
    if (app == null) return
    val meta by vm.meta.collectAsState()
    val m = meta[app.key]
    var dialog by remember(app.key) { mutableStateOf<String?>(null) }
    val shortcuts = remember(app.key) { vm.repo.shortcuts(app) }
    val pickIcon = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri -> if (uri != null) vm.setCustomIcon(app, uri); onDismiss() }

    if (dialog == null) {
        DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
            DropdownMenuItem(text = { Text("Open") }, onClick = { vm.launch(app); onDismiss() })
            shortcuts.take(4).forEach { s ->
                DropdownMenuItem(
                    text = { Text((s.shortLabel ?: s.id).toString()) },
                    onClick = { vm.repo.startShortcut(s); onDismiss() },
                )
            }
            DropdownMenuItem(text = { Text("Rename shortcut") }, onClick = { dialog = "rename" })
            DropdownMenuItem(
                text = { Text(if (m?.pinned == true) "Unpin" else "Pin (keep in place)") },
                onClick = { vm.pin(app, m?.pinned != true); onDismiss() },
            )
            DropdownMenuItem(text = { Text("Add to dock") }, onClick = { vm.addToDock(app); onDismiss() })
            DropdownMenuItem(text = { Text("Move to group…") }, onClick = { dialog = "move" })
            DropdownMenuItem(text = { Text("Change icon…") }, onClick = { pickIcon.launch("image/*") })
            if (m?.customIconPath != null) DropdownMenuItem(text = { Text("Reset icon") }, onClick = { vm.setCustomIcon(app, null); onDismiss() })
            DropdownMenuItem(text = { Text("Icon bigger") }, onClick = { vm.scale(app, (m?.iconSizeScale ?: 1f) + 0.15f); onDismiss() })
            DropdownMenuItem(text = { Text("Icon smaller") }, onClick = { vm.scale(app, (m?.iconSizeScale ?: 1f) - 0.15f); onDismiss() })
            DropdownMenuItem(text = { Text("Hide from launcher") }, onClick = { vm.hide(app, true); onDismiss() })
            DropdownMenuItem(text = { Text("App info") }, onClick = { vm.repo.openAppInfo(app); onDismiss() })
            if (!app.isSystem) DropdownMenuItem(text = { Text("Uninstall") }, onClick = { vm.repo.uninstall(app); onDismiss() })
        }
    }

    when (dialog) {
        "rename" -> {
            var text by remember { mutableStateOf(vm.labelOf(app)) }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Rename shortcut") },
                text = { OutlinedTextField(text, { text = it }, singleLine = true, supportingText = { Text("Only the launcher label changes, not the app.") }) },
                confirmButton = { TextButton({ vm.rename(app, text.takeIf { it != app.label }); onDismiss() }) { Text("Save") } },
                dismissButton = { TextButton({ vm.rename(app, null); onDismiss() }) { Text("Reset") } },
            )
        }
        "move" -> {
            val folders by vm.folders.collectAsState()
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Move to group") },
                text = {
                    LazyColumn {
                        items(folders) { f ->
                            Text(f.name, Modifier.fillMaxWidth().clickable { vm.moveToFolder(app, f); onDismiss() }.padding(vertical = 12.dp))
                        }
                    }
                },
                confirmButton = { TextButton(onDismiss) { Text("Cancel") } },
            )
        }
    }
}
