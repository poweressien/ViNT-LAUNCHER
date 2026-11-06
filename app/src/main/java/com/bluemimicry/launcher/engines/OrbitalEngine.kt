package com.bluemimicry.launcher.engines

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.bluemimicry.launcher.LauncherViewModel
import com.bluemimicry.launcher.ui.components.AppTile
import com.bluemimicry.launcher.ui.components.rememberNow
import com.bluemimicry.launcher.ui.theme.LocalPalette
import kotlinx.coroutines.android.awaitFrame
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

/**
 * MODE E — apps orbit a central clock on several rings. Each ring has its own speed and
 * alternating direction; the global direction flips with [orbitReverse]. Tap an app to launch,
 * tap the centre to toggle selection highlighting off.
 */
@Composable
fun OrbitalEngine(vm: LauncherViewModel, modifier: Modifier = Modifier) {
    val settings by vm.settings.collectAsState()
    val apps by vm.visibleApps.collectAsState()
    val pal = LocalPalette.current
    val density = LocalDensity.current
    var t by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(settings.reducedMotion, settings.perf) {
        if (settings.reducedMotion) return@LaunchedEffect
        var last = awaitFrame()
        val minDt = 1_000_000_000L / settings.perf.fpsCap
        while (true) {
            val n = awaitFrame()
            if (n - last < minDt) continue
            t += (n - last) / 1e9f; last = n
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val cx = with(density) { maxWidth.toPx() } / 2f
        val cy = with(density) { maxHeight.toPx() } * 0.46f
        val maxR = minOf(cx, cy) - with(density) { 36.dp.toPx() }
        val ringCount = 4
        val perRing = remember(apps) {
            // inner rings hold fewer apps
            val caps = listOf(5, 8, 11, 14)
            val out = ArrayList<List<com.bluemimicry.launcher.data.AppInfo>>()
            var i = 0
            for (r in 0 until ringCount) { out += apps.drop(i).take(caps[r]); i += caps[r] }
            out
        }
        val dir = if (settings.orbitReverse) -1f else 1f
        val icon = 46.dp
        val iconPx = with(density) { icon.toPx() }

        // rings
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            for (r in 0 until ringCount) drawCircle(pal.text.copy(alpha = 0.07f), maxR * (r + 1.4f) / (ringCount + 0.4f), androidx.compose.ui.geometry.Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
        }

        perRing.forEachIndexed { ri, ring ->
            val radius = maxR * (ri + 1.4f) / (ringCount + 0.4f)
            val speed = (0.35f - ri * 0.06f) * settings.orbitSpeed * dir * (if (ri % 2 == 0) 1f else -1f)
            ring.forEachIndexed { i, a ->
                key(a.key) {
                    val phase0 = i * (6.2831f / ring.size)
                    AppTile(
                        a, vm, icon, showLabel = false,
                        // `t` is read in the layout phase only, so frames never recompose the tiles.
                        modifier = Modifier.offset {
                            val ang = t * speed + phase0
                            IntOffset((cx + cos(ang) * radius - iconPx / 2).roundToInt(), (cy + sin(ang) * radius - iconPx / 2).roundToInt())
                        },
                    )
                }
            }
        }

        // centre object: live clock
        val now by rememberNow(false)
        val time = remember(now) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)) }
        Box(
            Modifier.offset { IntOffset((cx - with(density) { 44.dp.toPx() }).roundToInt(), (cy - with(density) { 44.dp.toPx() }).roundToInt()) }
                .size(88.dp).background(pal.accent.copy(alpha = 0.9f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text(time, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
    }
}
