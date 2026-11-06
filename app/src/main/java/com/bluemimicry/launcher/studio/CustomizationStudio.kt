package com.bluemimicry.launcher.studio

import android.app.WallpaperManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.bluemimicry.launcher.LauncherViewModel
import com.bluemimicry.launcher.data.*
import com.bluemimicry.launcher.data.DisplayMode
import com.bluemimicry.launcher.gestures.GestureAction
import com.bluemimicry.launcher.system.SystemActions
import com.bluemimicry.launcher.ui.theme.LocalPalette

private val TABS = listOf("Engine", "3D", "Themes", "Icons", "Wallpaper", "Fonts", "Motion", "Layout", "Gestures", "Presets", "System")

/**
 * Customization Studio. Shown as a bottom panel (about half the screen) over the LIVE home screen,
 * so every change is its own live preview.
 */
@Composable
fun CustomizationStudio(vm: LauncherViewModel, initialTab: Int = 0, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val pal = LocalPalette.current
    val s by vm.settings.collectAsState()
    var tab by remember { mutableIntStateOf(initialTab) }

    Column(modifier.fillMaxWidth().fillMaxHeight(0.5f).background(pal.bg, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Studio", color = pal.text, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            Text("Done", color = pal.accent, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onClose).padding(8.dp))
        }
        ScrollableTabRow(selectedTabIndex = tab, containerColor = Color.Transparent, edgePadding = 8.dp) {
            TABS.forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t, fontSize = 13.sp) }) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp)) {
            when (tab) {
                0 -> EngineTab(vm, s)
                1 -> ShapeTab(vm, s)
                2 -> ThemesTab(vm, s)
                3 -> IconsTab(vm, s)
                4 -> WallpaperTab(vm, s)
                5 -> FontsTab(vm, s)
                6 -> MotionTab(vm, s)
                7 -> LayoutTab(vm, s)
                8 -> GesturesTab(vm, s)
                9 -> PresetsTab(vm)
                else -> SystemTab(vm, s)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EngineTab(vm: LauncherViewModel, s: LauncherSettings) {
    ChipRow("Display engine", DisplayMode.values().toList(), s.mode, { it.label }) { m -> vm.update { it.copy(mode = m) } }
    ChipRow("Performance profile", PerfProfile.values().toList(), s.perf, { it.label }) { p -> vm.update { it.copy(perf = p) } }
    SwitchRow("Reduced motion", s.reducedMotion, "Stops continuous animation in every engine") { v -> vm.update { it.copy(reducedMotion = v) } }
    when (s.mode) {
        DisplayMode.FLOATING -> {
            Section("Floating")
            SliderRow("Speed", s.floatSpeed, 0.2f..3f, { v -> vm.update { it.copy(floatSpeed = v) } })
            SliderRow("Movement intensity", s.floatIntensity, 0.2f..2f, { v -> vm.update { it.copy(floatIntensity = v) } })
            SwitchRow("Paused", s.floatPaused, "Long-press an app → Pin to freeze it in place") { v -> vm.update { it.copy(floatPaused = v) } }
        }
        DisplayMode.SHUFFLE -> {
            Section("Auto shuffle")
            SliderRow("Interval (seconds)", s.shuffleSeconds.toFloat(), 1f..30f, { v -> vm.update { it.copy(shuffleSeconds = v.toInt()) } }) { "${it.toInt()}s" }
            SwitchRow("Paused", s.shufflePaused) { v -> vm.update { it.copy(shufflePaused = v) } }
            SwitchRow("Spring transition", s.spring, "Off = linear slide") { v -> vm.update { it.copy(spring = v) } }
            Text("Pinned apps never move. Taps are ignored while icons are moving.", fontSize = 11.sp, color = LocalPalette.current.subtext)
        }
        DisplayMode.ORBITAL -> {
            Section("Orbital")
            SliderRow("Speed", s.orbitSpeed, 0.1f..3f, { v -> vm.update { it.copy(orbitSpeed = v) } })
            SwitchRow("Reverse direction", s.orbitReverse) { v -> vm.update { it.copy(orbitReverse = v) } }
        }
        DisplayMode.GALAXY, DisplayMode.CAROUSEL, DisplayMode.DNA, DisplayMode.MATRIX ->
            SliderRow("Speed", s.rotationSpeed, 0.1f..3f, { v -> vm.update { it.copy(rotationSpeed = v) } })
        else -> Unit
    }
}

@Composable
private fun ShapeTab(vm: LauncherViewModel, s: LauncherSettings) {
    ChipRow("Structure (use the 3D engine)", Shape3D.values().toList(), s.shape3d, { it.label }) { v -> vm.update { it.copy(shape3d = v, mode = DisplayMode.SHAPES3D) } }
    SwitchRow("Auto-rotate", s.autoRotate) { v -> vm.update { it.copy(autoRotate = v) } }
    SliderRow("Rotation speed", s.rotationSpeed, 0f..4f, { v -> vm.update { it.copy(rotationSpeed = v) } })
    SliderRow("Camera field of view", s.fov, 25f..90f, { v -> vm.update { it.copy(fov = v) } }) { "${it.toInt()}°" }
    SliderRow("Light intensity", s.lightIntensity, 0.4f..1.5f, { v -> vm.update { it.copy(lightIntensity = v) } })
    SliderRow("Material: metallic", s.shapeMetallic, 0f..1f, { v -> vm.update { it.copy(shapeMetallic = v) } })
    SwitchRow("Glass body", s.shapeGlass) { v -> vm.update { it.copy(shapeGlass = v) } }
    ChipRow("Background", listOf(0, 1, 2), s.bg3d, { listOf("Light", "Dark", "Blue")[it] }) { v -> vm.update { it.copy(bg3d = v) } }
    ChipRow("Body colour", listOf(0xFFDADCE2L, 0xFF2B2D36L, 0xFF6FBDEDL, 0xFFFFB3CCL, 0xFFB9F6CAL), s.shapeColor, { listOf("Silver", "Graphite", "Sky", "Rose", "Mint")[listOf(0xFFDADCE2L, 0xFF2B2D36L, 0xFF6FBDEDL, 0xFFFFB3CCL, 0xFFB9F6CAL).indexOf(it)] }) { v -> vm.update { it.copy(shapeColor = v) } }
    Text("Drag to rotate • pinch to zoom • tap an icon to launch. Max 64 apps per shape (36 on Lightweight).", fontSize = 11.sp, color = LocalPalette.current.subtext)
}

@Composable
private fun ThemesTab(vm: LauncherViewModel, s: LauncherSettings) {
    ChipRow("Theme", ThemeId.values().toList(), s.theme, { it.label }) { v -> vm.update { it.copy(theme = v, statusBarDark = v != ThemeId.AMOLED && v != ThemeId.FUTURISTIC && v != ThemeId.GLASS) } }
    SliderRow("Container radius", s.containerRadius, 4f..48f, { v -> vm.update { it.copy(containerRadius = v) } }) { "${it.toInt()}dp" }
    SliderRow("Shadow intensity", s.shadowIntensity, 0f..2f, { v -> vm.update { it.copy(shadowIntensity = v) } })
    SliderRow("Container transparency", s.containerAlpha, 0.2f..1f, { v -> vm.update { it.copy(containerAlpha = v) } })
    Section("Custom theme colours (background gradient)")
    val presets = listOf("Grey" to (0xFFD2D2D2L to 0xFFC4C4C4L), "Mist" to (0xFFE6EBF2L to 0xFFD3DCE9L), "Sand" to (0xFFEDE3D3L to 0xFFDCCFB9L), "Slate" to (0xFF3A3F4BL to 0xFF22252DL), "Ocean" to (0xFFBFD9EAL to 0xFF86AECB))
    ChipRow("Gradient", presets, presets.firstOrNull { it.second.first == s.gradientTop } ?: presets[0], { it.first }) { p -> vm.update { it.copy(gradientTop = p.second.first, gradientBottom = p.second.second) } }
}

@Composable
private fun IconsTab(vm: LauncherViewModel, s: LauncherSettings) {
    val ctx = LocalContext.current
    ChipRow("Icon style", IconStyle.values().toList(), s.iconStyle, { it.label }) { v -> vm.update { it.copy(iconStyle = v) } }
    SliderRow("Icon size", s.iconSize, 36f..72f, { v -> vm.update { it.copy(iconSize = v) } }) { "${it.toInt()}dp" }
    val packs = remember { vm.repo.installedIconPacks() }
    Section("Icon packs")
    if (packs.isEmpty()) Text("No icon packs installed. Install any ADW/Nova-compatible pack and it appears here.", fontSize = 12.sp, color = LocalPalette.current.subtext)
    else ChipRow("Pack", listOf<Pair<String, String>?>(null) + packs, packs.firstOrNull { it.first == s.iconPack }, { it?.second ?: "System icons" }) { p -> vm.update { it.copy(iconPack = p?.first) } }
    Text("Replace one icon: long-press any app → Change icon…", fontSize = 12.sp, color = LocalPalette.current.subtext)
}

@Composable
private fun WallpaperTab(vm: LauncherViewModel, s: LauncherSettings) {
    val ctx = LocalContext.current
    val pickImg = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u -> if (u != null) vm.importWallpaper(u) }
    val pickVid = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u -> if (u != null) vm.importVideoWallpaper(u) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionButton("Image") { pickImg.launch("image/*") }
        ActionButton("Video") { pickVid.launch("video/*") }
        ActionButton("Clear") { vm.update { it.copy(wallpaperPath = null, videoWallpaperPath = null) } }
    }
    SwitchRow("Animated gradient background", s.animatedBackground) { v -> vm.update { it.copy(animatedBackground = v) } }
    SliderRow("Blur (Android 12+)", s.wallpaperBlur, 0f..30f, { v -> vm.update { it.copy(wallpaperBlur = v) } }) { "${it.toInt()}" }
    Section("System wallpaper (separate from the launcher background)")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val apply = { flag: Int, label: String ->
            val p = s.wallpaperPath
            if (p == null) Toast.makeText(ctx, "Pick an image first", Toast.LENGTH_SHORT).show()
            else runCatching {
                WallpaperManager.getInstance(ctx).setBitmap(BitmapFactory.decodeFile(p), null, true, flag)
                Toast.makeText(ctx, "Set as $label wallpaper", Toast.LENGTH_SHORT).show()
            }.onFailure { Toast.makeText(ctx, "This device refused the change", Toast.LENGTH_SHORT).show() }
        }
        ActionButton("Home") { apply(WallpaperManager.FLAG_SYSTEM, "home") }
        ActionButton("Lock") { apply(WallpaperManager.FLAG_LOCK, "lock-screen") }
    }
    ActionButton("Choose a live wallpaper (system picker)") { SystemActions.liveWallpaperPicker(ctx) }
}

@Composable
private fun FontsTab(vm: LauncherViewModel, s: LauncherSettings) {
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u -> if (u != null) vm.importFont(u) }
    val names = Assets.builtInFonts + if (s.customFontPath != null) listOf("Custom") else emptyList()
    ChipRow("Clock font", names, s.clockFont, { it }) { v -> vm.update { it.copy(clockFont = v) } }
    ChipRow("Label & widget font", names, s.labelFont, { it }) { v -> vm.update { it.copy(labelFont = v) } }
    SliderRow("Typography scale", s.fontScale, 0.7f..1.4f, { v -> vm.update { it.copy(fontScale = v) } })
    ActionButton("Import a .ttf / .otf font") { pick.launch("*/*") }
    Text("Applies launcher-wide. Android does not let a launcher replace the system font.", fontSize = 11.sp, color = LocalPalette.current.subtext)
}

@Composable
private fun MotionTab(vm: LauncherViewModel, s: LauncherSettings) {
    SliderRow("Animation intensity", s.animIntensity, 0f..2f, { v -> vm.update { it.copy(animIntensity = v) } })
    SliderRow("Duration", s.animDurationMs.toFloat(), 100f..900f, { v -> vm.update { it.copy(animDurationMs = v.toInt()) } }) { "${it.toInt()}ms" }
    SwitchRow("Spring & bounce", s.spring) { v -> vm.update { it.copy(spring = v) } }
    SwitchRow("Reduced motion", s.reducedMotion) { v -> vm.update { it.copy(reducedMotion = v) } }
}

@Composable
private fun LayoutTab(vm: LauncherViewModel, s: LauncherSettings) {
    SliderRow("Icon size", s.iconSize, 36f..72f, { v -> vm.update { it.copy(iconSize = v) } }) { "${it.toInt()}dp" }
    SliderRow("Dock slots", s.dockSlots.toFloat(), 3f..6f, { v -> vm.update { it.copy(dockSlots = v.toInt()) } }) { "${it.toInt()}" }
    SliderRow("Dock transparency", s.dockAlpha, 0f..1f, { v -> vm.update { it.copy(dockAlpha = v) } })
    SwitchRow("App labels", s.showLabels) { v -> vm.update { it.copy(showLabels = v) } }
    Section("Widgets on page 1")
    SwitchRow("Clock + wave", s.showClockWidget) { v -> vm.update { it.copy(showClockWidget = v) } }
    SwitchRow("Seconds chip", s.showSeconds) { v -> vm.update { it.copy(showSeconds = v) } }
    SwitchRow("Search bar", s.showSearchBar) { v -> vm.update { it.copy(showSearchBar = v) } }
    SwitchRow("Screen time", s.showScreenTime) { v -> vm.update { it.copy(showScreenTime = v) } }
    SwitchRow("Dynamic Island pill", s.showIsland) { v -> vm.update { it.copy(showIsland = v) } }
    Text("Move/resize groups: long-press empty home → Edit, then drag a group or use − / +.", fontSize = 11.sp, color = LocalPalette.current.subtext)
}

@Composable
private fun GesturesTab(vm: LauncherViewModel, s: LauncherSettings) {
    val opts = GestureAction.values().map { it.name to it.label }
    DropdownRow("Double tap", s.gestureDoubleTap, opts) { v -> vm.update { it.copy(gestureDoubleTap = v) } }
    DropdownRow("Long press", s.gestureLongPress, opts) { v -> vm.update { it.copy(gestureLongPress = v) } }
    DropdownRow("Swipe up (bottom edge)", s.gestureSwipeUp, opts) { v -> vm.update { it.copy(gestureSwipeUp = v) } }
    DropdownRow("Swipe down (top edge)", s.gestureSwipeDown, opts) { v -> vm.update { it.copy(gestureSwipeDown = v) } }
    DropdownRow("Swipe left (bottom edge)", s.gestureSwipeLeft, opts) { v -> vm.update { it.copy(gestureSwipeLeft = v) } }
    DropdownRow("Swipe right (bottom edge)", s.gestureSwipeRight, opts) { v -> vm.update { it.copy(gestureSwipeRight = v) } }
    DropdownRow("Two-finger swipe", s.gestureTwoFinger, opts) { v -> vm.update { it.copy(gestureTwoFinger = v) } }
    DropdownRow("Pinch (not in 3D)", s.gesturePinch, opts) { v -> vm.update { it.copy(gesturePinch = v) } }
    DropdownRow("Shake", s.gestureShake, opts) { v -> vm.update { it.copy(gestureShake = v) } }
    DropdownRow("Tilt", s.gestureTilt, opts) { v -> vm.update { it.copy(gestureTilt = v) } }
    Text("Edge swipes start in a screen edge so they never fight with scrolling or 3D rotation.", fontSize = 11.sp, color = LocalPalette.current.subtext)
}

@Composable
private fun PresetsTab(vm: LauncherViewModel) {
    val presets by vm.presets.collectAsState()
    var name by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Save current environment as…") }, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        ActionButton("Save") { if (name.isNotBlank()) { vm.savePreset(name.trim()); name = "" } }
    }
    presets.forEach { p ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(p.name, color = LocalPalette.current.text, modifier = Modifier.weight(1f))
            ActionButton("Apply") { vm.applyPreset(p) }
            ActionButton("Delete") { vm.deletePreset(p) }
        }
    }
    if (presets.isEmpty()) Text("A preset stores the display engine, 3D structure, theme, icon style and engine speeds.", fontSize = 12.sp, color = LocalPalette.current.subtext)
}

@Composable
private fun SystemTab(vm: LauncherViewModel, s: LauncherSettings) {
    val ctx = LocalContext.current
    SwitchRow("Smart suggestions (on-device only)", s.smartSuggestions, "Uses the launcher's own launch counts. Nothing is sent anywhere.") { v -> vm.update { it.copy(smartSuggestions = v) } }
    SwitchRow("Dark status-bar icons", s.statusBarDark, "Light = white icons for dark themes") { v -> vm.update { it.copy(statusBarDark = v) } }
    Section("Permissions & setup")
    ActionButton("Notification access (badges, island, notification centre)") { SystemActions.notificationAccess(ctx) }
    ActionButton("Usage access (screen-time widget)") { SystemActions.usageAccess(ctx) }
    ActionButton("Lock-screen service (Lock gesture)") { SystemActions.accessibility(ctx) }
    ActionButton("Battery optimisation (keep badges alive)") { SystemActions.batteryOptimisation(ctx) }
    ActionButton("Choose default home app") { SystemActions.defaultHomeSettings(ctx) }
    ActionButton("This app's system settings") { SystemActions.appDetails(ctx) }
    ActionButton("Re-scan installed apps") { vm.refresh() }
}
