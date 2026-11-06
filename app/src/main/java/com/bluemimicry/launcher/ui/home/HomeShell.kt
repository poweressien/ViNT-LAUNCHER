package com.bluemimicry.launcher.ui.home

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.bluemimicry.launcher.LauncherViewModel
import com.bluemimicry.launcher.data.*
import com.bluemimicry.launcher.engines.*
import com.bluemimicry.launcher.gestures.GestureAction
import com.bluemimicry.launcher.gestures.MotionGestures
import com.bluemimicry.launcher.gestures.launcherGestures
import com.bluemimicry.launcher.lockscreen.LockCompanionActivity
import com.bluemimicry.launcher.messaging.SmsActivity
import com.bluemimicry.launcher.notifications.BadgeStore
import com.bluemimicry.launcher.studio.CustomizationStudio
import com.bluemimicry.launcher.system.SystemActions
import com.bluemimicry.launcher.ui.components.*
import com.bluemimicry.launcher.ui.overlays.ControlCenter
import com.bluemimicry.launcher.ui.overlays.NotificationCenter
import com.bluemimicry.launcher.ui.theme.LocalPalette
import com.bluemimicry.launcher.widgets.LocalWidgetController
import com.bluemimicry.launcher.widgets.WidgetPicker
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private enum class Overlay { NONE, DRAWER, SEARCH, NOTIFS, CONTROL, STUDIO }

@Composable
fun HomeShell(vm: LauncherViewModel) {
    val ctx = LocalContext.current
    val pal = LocalPalette.current
    val s by vm.settings.collectAsState()
    var overlay by remember { mutableStateOf(Overlay.NONE) }
    var studioTab by remember { mutableIntStateOf(0) }
    var openFolder by remember { mutableStateOf<FolderEntity?>(null) }
    var editing by remember { mutableStateOf(false) }
    var addWidget by remember { mutableStateOf(false) }
    var addFolder by remember { mutableStateOf(false) }
    var size by remember { mutableStateOf(IntSize(1, 1)) }
    val widgetController = LocalWidgetController.current
    val fonts = remember(s.clockFont, s.labelFont, s.customFontPath) {
        LauncherFonts(Assets.fontFamily(s.clockFont, s.customFontPath), Assets.fontFamily(s.labelFont, s.customFontPath))
    }

    // Home button / re-launch: close everything and return to a clean home.
    LaunchedEffect(Unit) { vm.homeEvents.collect { overlay = Overlay.NONE; openFolder = null; editing = false } }
    BackHandler(overlay != Overlay.NONE || openFolder != null || editing) {
        when { openFolder != null -> openFolder = null; overlay != Overlay.NONE -> overlay = Overlay.NONE; else -> editing = false }
    }

    fun perform(a: GestureAction) {
        when (a) {
            GestureAction.NONE -> Unit
            GestureAction.DRAWER -> overlay = Overlay.DRAWER
            GestureAction.SEARCH -> overlay = Overlay.SEARCH
            GestureAction.NOTIFICATIONS -> overlay = Overlay.NOTIFS
            GestureAction.SYSTEM_SHADE -> SystemActions.expandSystemShade(ctx)
            GestureAction.CONTROL_CENTER -> overlay = Overlay.CONTROL
            GestureAction.STUDIO -> { studioTab = 0; overlay = Overlay.STUDIO }
            GestureAction.NEXT_MODE -> vm.update { cur -> val all = DisplayMode.values(); cur.copy(mode = all[(cur.mode.ordinal + 1) % all.size]) }
            GestureAction.SHUFFLE -> vm.update { it.copy(mode = DisplayMode.SHUFFLE, shufflePaused = false) }
            GestureAction.ROTATE_3D -> vm.update { it.copy(mode = DisplayMode.SHAPES3D, autoRotate = true) }
            GestureAction.FAVORITES -> overlay = Overlay.DRAWER
            GestureAction.EDIT -> if (s.mode == DisplayMode.CLASSIC) editing = true else { studioTab = 0; overlay = Overlay.STUDIO }
            GestureAction.LOCK -> SystemActions.lockScreen(ctx)
            GestureAction.LOCK_COMPANION -> ctx.startActivity(Intent(ctx, LockCompanionActivity::class.java))
            GestureAction.MESSAGES -> ctx.startActivity(Intent(ctx, SmsActivity::class.java))
        }
    }
    MotionGestures(s) { perform(it) }

    Box(
        Modifier.fillMaxSize().onSizeChanged { size = it }
            .launcherGestures(s, size, pinchEnabled = s.mode != DisplayMode.SHAPES3D) { perform(it) },
    ) {
        Background(s)

        // ---- engine --------------------------------------------------------------------
        Box(Modifier.fillMaxSize()) {
            when (s.mode) {
                DisplayMode.CLASSIC -> ClassicHome(vm, editing, fonts, onOpenFolder = { openFolder = it }, onSearch = { overlay = Overlay.SEARCH })
                DisplayMode.FLOATING -> FloatingEngine(vm)
                DisplayMode.SHUFFLE -> ShuffleEngine(vm)
                DisplayMode.ORBITAL -> OrbitalEngine(vm)
                DisplayMode.SHAPES3D -> ShapesEngine(vm)
                DisplayMode.TIMELINE -> TimelineEngine(vm)
                DisplayMode.ADAPTIVE -> AdaptiveEngine(vm)
                else -> ProjectedEngine(s.mode, vm)
            }
        }

        // ---- top area -------------------------------------------------------------------
        Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            StatusStrip(vm, fonts)
            if (s.showSeconds && s.mode == DisplayMode.CLASSIC) SecondsChip(Modifier.padding(top = 6.dp), fonts.clock)
        }

        // ---- dock (always reachable, in every engine) --------------------------------------
        Dock(
            vm, onSearch = { overlay = Overlay.SEARCH }, onDrawer = { overlay = Overlay.DRAWER },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 62.dp),
        )
        EngineQuickBar(vm, s, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 4.dp))

        // ---- edit bar -------------------------------------------------------------------------
        if (editing) Row(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 56.dp).background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(24.dp)).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            EditBtn("+ Group") { addFolder = true }
            if (widgetController != null) EditBtn("+ Widget") { addWidget = true }
            EditBtn("Studio") { editing = false; studioTab = 7; overlay = Overlay.STUDIO }
            EditBtn("Done") { editing = false }
        }

        // ---- overlays -----------------------------------------------------------------------
        openFolder?.let { FolderOverlay(it, vm) { openFolder = null } }
        when (overlay) {
            Overlay.DRAWER -> AppDrawer(vm) { overlay = Overlay.NONE }
            Overlay.SEARCH -> UniversalSearch(vm, { overlay = Overlay.NONE }, { openFolder = it }) { act ->
                when (act) { "ENGINE" -> { studioTab = 0; overlay = Overlay.STUDIO }; "STUDIO" -> { studioTab = 2; overlay = Overlay.STUDIO }; "CONTROL" -> overlay = Overlay.CONTROL; "NOTIFS" -> overlay = Overlay.NOTIFS; else -> Unit }
            }
            Overlay.NOTIFS -> NotificationCenter(vm) { overlay = Overlay.NONE }
            Overlay.CONTROL -> ControlCenter(vm) { overlay = Overlay.NONE }
            Overlay.STUDIO -> Box(Modifier.fillMaxSize()) {
                CustomizationStudio(vm, studioTab, onClose = { overlay = Overlay.NONE }, modifier = Modifier.align(Alignment.BottomCenter))
            }
            Overlay.NONE -> Unit
        }
    }

    if (addWidget && widgetController != null) WidgetPicker(widgetController) { addWidget = false }
    if (addFolder) {
        var name by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { addFolder = false }, title = { Text("New group") },
            text = { androidx.compose.material3.OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }) },
            confirmButton = { androidx.compose.material3.TextButton({ if (name.isNotBlank()) vm.createFolder(name.trim()); addFolder = false }) { Text("Create") } },
        )
    }
}

@Composable private fun EditBtn(t: String, onClick: () -> Unit) = Text(t, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.clickable(onClick = onClick).padding(4.dp))

/** Wallpaper / video / animated gradient / plain themed gradient. */
@Composable
private fun Background(s: LauncherSettings) {
    val pal = LocalPalette.current
    val ctx = LocalContext.current
    val blurMod = if (s.wallpaperBlur > 0f && Build.VERSION.SDK_INT >= 31) Modifier.blur(s.wallpaperBlur.dp) else Modifier
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(pal.bg, pal.bgBottom)))) {
        when {
            s.videoWallpaperPath != null && File(s.videoWallpaperPath).exists() -> AndroidView(
                factory = { c -> android.widget.VideoView(c).apply {
                    setVideoPath(s.videoWallpaperPath)
                    setOnPreparedListener { it.isLooping = true; it.setVolume(0f, 0f); start() }
                } },
                update = { v -> if (!v.isPlaying) v.start() },
                onRelease = { it.stopPlayback() },
                modifier = Modifier.fillMaxSize().then(blurMod),
            )
            s.wallpaperPath != null -> {
                val bmp = remember(s.wallpaperPath) { runCatching { android.graphics.BitmapFactory.decodeFile(s.wallpaperPath, android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 })?.asImageBitmap() }.getOrNull() }
                if (bmp != null) Image(bmp, null, Modifier.fillMaxSize().then(blurMod), contentScale = ContentScale.Crop)
            }
            s.animatedBackground && !s.reducedMotion -> {
                val t by rememberInfiniteTransition(label = "bg").animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(20000, easing = LinearEasing)), label = "t")
                Canvas(Modifier.fillMaxSize()) {
                    for (i in 0 until 4) drawCircle(pal.accent.copy(alpha = 0.14f), size.minDimension * (0.35f + 0.05f * i),
                        Offset(size.width * (0.5f + 0.4f * cos(t + i * 1.7f)), size.height * (0.5f + 0.35f * sin(t * 0.8f + i))))
                }
            }
        }
    }
}

/** Left: time + notification dots. Right: network + battery. Centre: the island. Light/dark icon colour follows the theme. */
@Composable
private fun StatusStrip(vm: LauncherViewModel, fonts: LauncherFonts) {
    val ctx = LocalContext.current
    val s by vm.settings.collectAsState()
    val pal = LocalPalette.current
    val now by rememberNow(false)
    val battery by rememberBattery()
    val counts by vm.badges.collectAsState()
    val time = remember(now) { SimpleDateFormat("H:mm", Locale.getDefault()).format(Date(now)) }
    val net = remember(now) { networkLabel(ctx) }
    val total = counts.values.sum()

    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(time, color = pal.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = fonts.clock)
            if (total > 0) {
                Spacer(Modifier.width(8.dp))
                androidx.compose.foundation.layout.Box(Modifier.size(8.dp).background(pal.badge, androidx.compose.foundation.shape.CircleShape))
                Text(" $total", color = pal.text, fontSize = 12.sp)
            }
        }
        if (s.showIsland) DynamicIsland(vm.repo, onOpenApp = { pkg -> vm.allApps.value.firstOrNull { it.pkg == pkg }?.let(vm::launch) })
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text(net, color = pal.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text("${battery.percent}${if (battery.charging) "⚡" else "%"}", color = pal.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun networkLabel(ctx: Context): String = runCatching {
    val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = cm.getNetworkCapabilities(cm.activeNetwork)
    when {
        caps == null -> "Offline"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        else -> "Online"
    }
}.getOrDefault("")

/** Small always-available strip: switch display engine without opening the Studio. */
@Composable
private fun EngineQuickBar(vm: LauncherViewModel, s: LauncherSettings, modifier: Modifier) {
    val pal = LocalPalette.current
    val ctx = LocalContext.current
    Row(modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("‹ ${s.mode.label} ›", color = pal.subtext, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable { vm.update { c -> val all = DisplayMode.values(); c.copy(mode = all[(c.mode.ordinal + 1) % all.size]) } }.padding(6.dp))
        if (s.mode == DisplayMode.FLOATING) Text(if (s.floatPaused) "▶" else "⏸", color = pal.text, modifier = Modifier.clickable { vm.update { it.copy(floatPaused = !it.floatPaused) } }.padding(6.dp))
        if (s.mode == DisplayMode.SHUFFLE) Text(if (s.shufflePaused) "▶" else "⏸", color = pal.text, modifier = Modifier.clickable { vm.update { it.copy(shufflePaused = !it.shufflePaused) } }.padding(6.dp))
        if (s.mode == DisplayMode.SHAPES3D) Text("◀ ${s.shape3d.label} ▶", color = pal.text, fontSize = 12.sp,
            modifier = Modifier.clickable { vm.update { c -> val a = Shape3D.values(); c.copy(shape3d = a[(c.shape3d.ordinal + 1) % a.size]) } }.padding(6.dp))
    }
}
