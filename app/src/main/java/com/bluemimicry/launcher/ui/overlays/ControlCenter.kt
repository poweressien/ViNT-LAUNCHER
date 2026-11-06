package com.bluemimicry.launcher.ui.overlays

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemimicry.launcher.LauncherViewModel
import com.bluemimicry.launcher.system.SystemActions
import com.bluemimicry.launcher.ui.components.AppTile
import com.bluemimicry.launcher.ui.components.rememberBattery
import com.bluemimicry.launcher.ui.theme.LocalPalette

@Composable
private fun CcTile(label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val pal = LocalPalette.current
    Column(
        modifier.background(if (active) pal.accent else pal.surface, RoundedCornerShape(22.dp)).clickable(onClick = onClick).padding(vertical = 18.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { Text(label, color = if (active) Color.White else pal.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
}

/** iOS-style control centre built only from supported Android APIs and settings intents. */
@Composable
fun ControlCenter(vm: LauncherViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val pal = LocalPalette.current
    val battery by rememberBattery()
    val dock by vm.dock.collectAsState()
    val apps by vm.allApps.collectAsState()
    var torch by remember { mutableStateOf(false) }
    var dnd by remember { mutableStateOf(SystemActions.dndOn(ctx)) }
    var rot by remember { mutableStateOf<Boolean?>(null) }
    val (v0, vMax) = remember { SystemActions.volume(ctx) }
    var vol by remember { mutableFloatStateOf(v0.toFloat()) }
    val window = (ctx as? Activity)?.window
    var bright by remember { mutableFloatStateOf(window?.attributes?.screenBrightness?.takeIf { it >= 0 } ?: 0.6f) }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable(onClick = onClose)) {
        Column(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(16.dp).background(pal.bg, RoundedCornerShape(34.dp))
                .clickable(enabled = false) {}.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Control Center  •  battery ${battery.percent}%${if (battery.charging) " (charging)" else ""}", color = pal.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CcTile("Wi-Fi", false, Modifier.weight(1f)) { SystemActions.wifi(ctx) }
                CcTile("Bluetooth", false, Modifier.weight(1f)) { SystemActions.bluetooth(ctx) }
                CcTile("Airplane", false, Modifier.weight(1f)) { SystemActions.airplane(ctx) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CcTile("Torch", torch, Modifier.weight(1f)) { SystemActions.toggleTorch(ctx)?.let { torch = it } }
                CcTile("Do Not Disturb", dnd, Modifier.weight(1f)) { SystemActions.toggleDnd(ctx)?.let { dnd = it } }
                CcTile("Rotation", rot == true, Modifier.weight(1f)) { rot = SystemActions.toggleRotation(ctx) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CcTile("Screen record", false, Modifier.weight(1f)) { SystemActions.screenRecord(ctx) }
                CcTile("Quick settings", false, Modifier.weight(1f)) { onClose(); SystemActions.expandSystemShade(ctx, true) }
                CcTile("Lock", false, Modifier.weight(1f)) { onClose(); SystemActions.lockScreen(ctx) }
            }
            Text("Volume", color = pal.subtext, fontSize = 12.sp)
            Slider(vol, { vol = it; SystemActions.setVolume(ctx, it.toInt()) }, valueRange = 0f..vMax.toFloat().coerceAtLeast(1f))
            Text("Launcher brightness (this screen only; system brightness stays in Settings)", color = pal.subtext, fontSize = 12.sp)
            Slider(bright, { bright = it; window?.let { w -> w.attributes = w.attributes.also { a -> a.screenBrightness = it.coerceIn(0.02f, 1f) } } })
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 4.dp)) {
                val byKey = apps.associateBy { it.key }
                dock.mapNotNull { byKey[it.key] }.take(4).forEach { AppTile(it, vm, 48.dp, showLabel = false, onTap = { vm.launch(it); onClose() }) }
            }
        }
    }
}
