package com.bluemimicry.launcher.onboarding

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemimicry.launcher.LauncherViewModel
import com.bluemimicry.launcher.data.DisplayMode
import com.bluemimicry.launcher.data.ThemeId
import com.bluemimicry.launcher.studio.ActionButton
import com.bluemimicry.launcher.studio.ChipRow
import com.bluemimicry.launcher.system.SystemActions
import com.bluemimicry.launcher.ui.components.ClockWidget
import com.bluemimicry.launcher.ui.theme.LocalPalette

private fun isDefaultHome(ctx: Context): Boolean {
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    return ctx.packageManager.resolveActivity(i, 0)?.activityInfo?.packageName == ctx.packageName
}

/** Asks for the default-home role through Android's official mechanism (RoleManager on 10+, Settings otherwise). */
@Composable
fun rememberHomeRoleRequest(): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    return {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = ctx.getSystemService(RoleManager::class.java)
            if (rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) launcher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
        } else SystemActions.defaultHomeSettings(ctx)
    }
}

@Composable
fun Onboarding(vm: LauncherViewModel, onFinish: () -> Unit) {
    val pal = LocalPalette.current
    val ctx = LocalContext.current
    val settings by vm.settings.collectAsState()
    var page by remember { mutableIntStateOf(0) }
    val askHome = rememberHomeRoleRequest()
    val last = 5

    Column(Modifier.fillMaxSize().background(pal.bg).statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (page) {
                0 -> {
                    Spacer(Modifier.height(30.dp))
                    Text("Blue Mimicry", color = pal.text, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    Text("A launcher that lets your apps live in different worlds.", color = pal.subtext, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
                    ClockWidget(Modifier.fillMaxWidth().height(150.dp))
                }
                1 -> {
                    Title("How it looks")
                    Body("Soft grey surfaces, raised neumorphic icons, floating groups with titles, real red badges and a live clock. Everything on screen is one of your real installed apps — nothing is a placeholder.")
                    Body("A Dynamic-Island-style pill sits inside the launcher. It can't replace Android's real status bar, and it doesn't try to.")
                }
                2 -> {
                    Title("Permissions, only when you want them")
                    Body("Nothing is requested now. Each feature explains itself and asks when you switch it on. The launcher stays fully usable if you decline everything.")
                    Perm("Notification access", "Real badges, the island and the notification centre. Content stays in memory and is never stored or sent.") { SystemActions.notificationAccess(ctx) }
                    Perm("Usage access", "Real screen-time numbers and usage-based suggestions. Analysis stays on this device.") { SystemActions.usageAccess(ctx) }
                    Body("Contacts (search) and SMS (messages module) are asked inside those features.")
                }
                3 -> {
                    Title("Make it your home screen")
                    Body("Android only lets you choose a launcher yourself — we can't switch it silently. Tap below and confirm in the system dialog.")
                    ActionButton(if (isDefaultHome(ctx)) "Already your default launcher ✓" else "Set as default launcher") { askHome() }
                }
                4 -> {
                    Title("Pick a starting theme")
                    ChipRow("Theme", ThemeId.values().toList(), settings.theme, { it.label }) { v -> vm.update { it.copy(theme = v, statusBarDark = v != ThemeId.AMOLED && v != ThemeId.FUTURISTIC && v != ThemeId.GLASS) } }
                    Body("Your apps were sorted into groups automatically. Long-press empty space to edit, move or resize them.")
                }
                else -> {
                    Title("Experimental worlds")
                    Body("Open the Studio → Engine to switch how your apps are shown: floating, auto-shuffle every 4 seconds, orbital, real 3D shapes, holographic, galaxy, matrix and more. All of them use the same app list, and the Classic screen is one tap away.")
                    ChipRow("Try one now", listOf(DisplayMode.CLASSIC, DisplayMode.FLOATING, DisplayMode.ORBITAL, DisplayMode.SHAPES3D), settings.mode, { it.label }) { v -> vm.update { it.copy(mode = v) } }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (page > 0) ActionButton("Back") { page-- }
            Spacer(Modifier.weight(1f))
            ActionButton(if (page == last) "Start" else "Next") { if (page == last) onFinish() else page++ }
        }
    }
}

@Composable private fun Title(t: String) = Text(t, color = LocalPalette.current.text, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp, bottom = 10.dp))
@Composable private fun Body(t: String) = Text(t, color = LocalPalette.current.subtext, fontSize = 15.sp, modifier = Modifier.padding(bottom = 12.dp))
@Composable private fun Perm(title: String, why: String, onClick: () -> Unit) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(title, color = LocalPalette.current.text, fontWeight = FontWeight.SemiBold)
        Text(why, color = LocalPalette.current.subtext, fontSize = 13.sp)
        ActionButton("Open settings", onClick = onClick)
    }
}
