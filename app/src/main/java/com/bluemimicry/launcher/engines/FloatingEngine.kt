package com.bluemimicry.launcher.engines

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bluemimicry.launcher.LauncherViewModel
import com.bluemimicry.launcher.data.AppInfo
import com.bluemimicry.launcher.ui.components.AppTile
import kotlinx.coroutines.android.awaitFrame
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/** Mutable physics body. Kept outside Compose state so the 60fps loop allocates nothing per frame. */
class Body(val key: String, var x: Float, var y: Float, var vx: Float, var vy: Float, val r: Float) {
    var pinned = false
    var dragging = false
}

/**
 * MODE B — independent floating apps with wall bounce and soft elastic collisions
 * (positions are separated every frame, so icons never rest overlapped).
 * Dragging, pinning, pause/resume, speed and intensity are all live.
 */
@Composable
fun FloatingEngine(vm: LauncherViewModel, modifier: Modifier = Modifier) {
    val settings by vm.settings.collectAsState()
    val apps by vm.visibleApps.collectAsState()
    val meta by vm.meta.collectAsState()
    val density = LocalDensity.current
    val list = remember(apps) { apps.take(settings.perf.fpsCap.coerceAtMost(60)) } // cap bodies on weak profiles

    BoxWithConstraints(modifier.fillMaxSize()) {
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val base = with(density) { 54.dp.toPx() }

        val bodies = remember(list, wPx, hPx) {
            list.map { a ->
                val s = base * (meta[a.key]?.iconSizeScale ?: 1f)
                val r = s / 2f + 6f
                val ang = Random.nextFloat() * 6.2831f
                val sp = 40f + Random.nextFloat() * 90f
                Body(a.key, r + Random.nextFloat() * (wPx - 2 * r), r + Random.nextFloat() * (hPx * 0.8f - 2 * r), kotlin.math.cos(ang) * sp, kotlin.math.sin(ang) * sp, r)
                    .also { it.pinned = meta[a.key]?.pinned == true }
            }
        }
        var frame by remember { mutableIntStateOf(0) }

        LaunchedEffect(bodies, settings.floatPaused, settings.reducedMotion, settings.perf) {
            if (settings.floatPaused || settings.reducedMotion) return@LaunchedEffect
            var last = awaitFrame()
            val minDt = 1_000_000_000L / settings.perf.fpsCap
            while (true) {
                val t = awaitFrame()
                if (t - last < minDt) continue
                val dt = ((t - last) / 1e9f).coerceAtMost(0.05f)
                last = t
                step(bodies, dt, wPx, hPx * 0.86f, settings.floatSpeed * settings.floatIntensity)
                frame++
            }
        }

        val byKey = remember(list) { list.associateBy { it.key } }
        bodies.forEach { b ->
            val app = byKey[b.key] ?: return@forEach
            key(b.key) {
                AppTile(
                    app, vm, 54.dp, showLabel = false,
                    modifier = Modifier
                        // `frame` is read in the layout phase only: moves icons without recomposition.
                        .offset { frame; IntOffset((b.x - b.r + 6f).roundToInt(), (b.y - b.r + 6f).roundToInt()) }
                        .pointerInput(b) {
                            detectDragGestures(
                                onDragStart = { b.dragging = true },
                                onDragEnd = { b.dragging = false }, onDragCancel = { b.dragging = false },
                                onDrag = { c, d -> c.consume(); b.x += d.x; b.y += d.y; b.vx = 0f; b.vy = 0f; frame++ },
                            )
                        },
                )
            }
        }
    }
}

private fun step(bodies: List<Body>, dt: Float, w: Float, h: Float, speed: Float) {
    for (b in bodies) {
        if (b.pinned || b.dragging) continue
        b.x += b.vx * dt * speed; b.y += b.vy * dt * speed
        if (b.x < b.r) { b.x = b.r; b.vx = kotlin.math.abs(b.vx) }
        if (b.x > w - b.r) { b.x = w - b.r; b.vx = -kotlin.math.abs(b.vx) }
        if (b.y < b.r) { b.y = b.r; b.vy = kotlin.math.abs(b.vy) }
        if (b.y > h - b.r) { b.y = h - b.r; b.vy = -kotlin.math.abs(b.vy) }
        // keep gentle perpetual motion
        val sp = hypot(b.vx, b.vy)
        if (sp < 25f) { val a = Random.nextFloat() * 6.2831f; b.vx = kotlin.math.cos(a) * 40f; b.vy = kotlin.math.sin(a) * 40f }
    }
    // pairwise resolution
    for (i in bodies.indices) for (j in i + 1 until bodies.size) {
        val a = bodies[i]; val c = bodies[j]
        val dx = c.x - a.x; val dy = c.y - a.y
        val min = a.r + c.r
        val d2 = dx * dx + dy * dy
        if (d2 >= min * min || d2 == 0f) continue
        val d = kotlin.math.sqrt(d2)
        val nx = dx / d; val ny = dy / d
        val push = (min - d)
        val aFixed = a.pinned || a.dragging; val cFixed = c.pinned || c.dragging
        if (!aFixed) { a.x -= nx * push * (if (cFixed) 1f else 0.5f); a.y -= ny * push * (if (cFixed) 1f else 0.5f) }
        if (!cFixed) { c.x += nx * push * (if (aFixed) 1f else 0.5f); c.y += ny * push * (if (aFixed) 1f else 0.5f) }
        // elastic exchange along the normal
        val rv = (c.vx - a.vx) * nx + (c.vy - a.vy) * ny
        if (rv < 0) {
            val j2 = -rv
            if (!aFixed) { a.vx -= j2 * nx; a.vy -= j2 * ny }
            if (!cFixed) { c.vx += j2 * nx; c.vy += j2 * ny }
        }
    }
}
