package com.bluemimicry.launcher.engines

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemimicry.launcher.LauncherViewModel
import com.bluemimicry.launcher.data.AppInfo
import com.bluemimicry.launcher.data.DisplayMode
import com.bluemimicry.launcher.ui.components.AppTile
import com.bluemimicry.launcher.ui.theme.LocalPalette
import kotlinx.coroutines.android.awaitFrame
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

/** Screen-space placement computed per frame by a mode. z: larger = nearer. */
private class Place(var x: Float = 0f, var y: Float = 0f, var s: Float = 1f, var a: Float = 1f, var z: Float = 0f, var rot: Float = 0f)

/**
 * Galaxy, Matrix, Carousel, Magnetic, DNA, Liquid and Holographic. These are Compose-rendered
 * pseudo-3D (perspective-projected) layouts over the shared app registry — they are NOT OpenGL.
 * Positions are computed in the layout/draw phase from one clock, so frames never recompose.
 * Timeline and Adaptive are list/grid based and live in [TimelineEngine] / [AdaptiveEngine].
 */
@Composable
fun ProjectedEngine(mode: DisplayMode, vm: LauncherViewModel, modifier: Modifier = Modifier) {
    val settings by vm.settings.collectAsState()
    val allApps by vm.visibleApps.collectAsState()
    val pal = LocalPalette.current
    val density = LocalDensity.current
    val apps = remember(allApps, settings.perf) { allApps.take(if (settings.perf.fpsCap <= 30) 30 else 56) }
    var t by remember { mutableFloatStateOf(0f) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var touch by remember { mutableStateOf<Offset?>(null) }
    val places = remember(apps.size) { Array(apps.size) { Place() } }

    LaunchedEffect(settings.reducedMotion, settings.perf, mode) {
        if (settings.reducedMotion) return@LaunchedEffect
        var last = awaitFrame(); val minDt = 1_000_000_000L / settings.perf.fpsCap
        while (true) { val n = awaitFrame(); if (n - last < minDt) continue; t += (n - last) / 1e9f; last = n }
    }

    BoxWithConstraints(
        modifier.fillMaxSize()
            .pointerInput(mode) { detectDragGestures(onDragEnd = { touch = null }, onDragCancel = { touch = null }) { c, d -> c.consume(); dragX += d.x; dragY += d.y; touch = c.position } }
            .pointerInput(mode) { detectTapGestures(onPress = { touch = it; tryAwaitRelease(); touch = null }) },
    ) {
        val w = with(density) { maxWidth.toPx() }; val h = with(density) { maxHeight.toPx() }
        val sp = settings.rotationSpeed
        val base = 50.dp
        val basePx = with(density) { base.toPx() }

        if (mode == DisplayMode.LIQUID) Canvas(Modifier.fillMaxSize()) {
            // gooey backdrop blobs that follow the same clock
            for (i in 0 until 5) {
                val a = t * (0.3f + i * 0.11f) + i
                drawCircle(pal.accent.copy(alpha = 0.16f), size.minDimension * (0.18f + 0.04f * sin(a * 1.3f)), Offset(w * (0.5f + 0.32f * cos(a)), h * (0.45f + 0.28f * sin(a * 0.8f))))
            }
        }

        fun compute(i: Int, n: Int, p: Place) {
            val f = i / max(n - 1, 1).toFloat()
            when (mode) {
                DisplayMode.GALAXY -> {
                    val arm = i % 3; val r = 0.08f + 0.85f * (i / n.toFloat())
                    val ang = arm * 2.094f + r * 5f + t * 0.25f * sp + dragX * 0.004f
                    val x = cos(ang) * r; val z = sin(ang) * r
                    val tilt = 0.9f + dragY * 0.002f
                    val yy = z * sin(tilt); val depth = z * cos(tilt)
                    val persp = 1f / (1f + depth * 0.5f)
                    p.x = w / 2 + x * w * 0.46f * persp; p.y = h * 0.44f + yy * h * 0.30f * persp
                    p.s = 0.55f + 0.7f * persp; p.a = (0.45f + 0.55f * persp).coerceIn(0f, 1f); p.z = -depth
                }
                DisplayMode.MATRIX -> {
                    val cols = 6; val col = i % cols; val lane = i / cols
                    val speed = 40f + (col * 37 % 90) * sp
                    val span = h + 200f
                    val y = ((lane * (span / ceil(n / cols.toFloat())) + t * speed) % span) - 100f
                    p.x = w * (col + 0.5f) / cols; p.y = y; p.s = 0.9f
                    p.a = (1f - abs(y / h - 0.5f) * 0.8f).coerceIn(0.35f, 1f); p.z = 0f
                }
                DisplayMode.CAROUSEL -> {
                    val ang = (i / n.toFloat()) * 6.2831f + dragX * 0.006f + t * 0.2f * sp
                    val z = cos(ang); val x = sin(ang)
                    val persp = 1f / (1.6f - z * 0.6f)
                    p.x = w / 2 + x * w * 0.38f; p.y = h * 0.44f - z * h * 0.04f + (persp - 0.6f) * h * 0.05f
                    p.s = 0.7f + 0.6f * persp; p.a = (0.35f + 0.65f * (z * 0.5f + 0.5f)); p.z = z
                }
                DisplayMode.MAGNETIC -> {
                    val cols = 4; val col = i % cols; val row = i / cols
                    val hx = w * (col + 0.5f) / cols; val hy = h * 0.12f + row * (h * 0.74f / ceil(n / cols.toFloat()).coerceAtLeast(1f))
                    var dx = 0f; var dy = 0f
                    touch?.let { tp ->
                        val vx = tp.x - hx; val vy = tp.y - hy; val d = hypot(vx, vy).coerceAtLeast(1f)
                        val pull = (1f - (d / (w * 0.55f)).coerceIn(0f, 1f)); dx = vx / d * pull * 90f; dy = vy / d * pull * 90f
                    }
                    p.x = hx + dx + sin(t * 0.8f + i) * 3f; p.y = hy + dy + cos(t * 0.7f + i) * 3f
                    p.s = 1f + hypot(dx, dy) / 160f; p.z = hypot(dx, dy)
                }
                DisplayMode.DNA -> {
                    val strand = i % 2; val idx = i / 2
                    val y = h * 0.08f + (idx / ceil(n / 2f).coerceAtLeast(1f)) * h * 0.76f
                    val ang = idx * 0.55f + t * 0.9f * sp + dragX * 0.01f + strand * PI.toFloat()
                    p.x = w / 2 + cos(ang) * w * 0.30f; p.y = y
                    val z = sin(ang); p.s = 0.75f + 0.3f * z; p.a = 0.55f + 0.45f * (z * 0.5f + 0.5f); p.z = z
                }
                DisplayMode.LIQUID -> {
                    val cols = 4; val col = i % cols; val row = i / cols
                    val hx = w * (col + 0.5f) / cols; val hy = h * 0.13f + row * (h * 0.72f / ceil(n / cols.toFloat()).coerceAtLeast(1f))
                    p.x = hx + sin(t * 0.9f + row * 0.8f + col) * 14f * settings.animIntensity
                    p.y = hy + cos(t * 0.7f + col * 1.1f + row) * 12f * settings.animIntensity
                    p.s = 1f + 0.06f * sin(t * 1.4f + i); p.z = 0f
                }
                else -> { // HOLOGRAPHIC — layered translucent cards with parallax tilt
                    val cols = 4; val col = i % cols; val row = i / cols; val layer = i % 3
                    val par = (layer - 1) * 0.5f
                    p.x = w * (col + 0.5f) / cols + (dragX * 0.05f).coerceIn(-40f, 40f) * par + sin(t * 0.6f + i) * 4f
                    p.y = h * 0.13f + row * (h * 0.72f / ceil(n / cols.toFloat()).coerceAtLeast(1f)) + (dragY * 0.05f).coerceIn(-40f, 40f) * par
                    p.s = 0.9f + 0.1f * layer; p.a = 0.6f + 0.13f * layer; p.z = layer.toFloat(); p.rot = (dragX * 0.02f).coerceIn(-12f, 12f)
                }
            }
        }

        apps.forEachIndexed { i, a ->
            key(a.key) {
                val p = places[i]
                AppTile(
                    a, vm, base, showLabel = mode == DisplayMode.HOLOGRAPHIC || mode == DisplayMode.MAGNETIC || mode == DisplayMode.LIQUID,
                    modifier = Modifier
                        .offset { compute(i, apps.size, p); IntOffset((p.x - basePx / 2).roundToInt(), (p.y - basePx / 2).roundToInt()) }
                        .graphicsLayer {
                            scaleX = p.s; scaleY = p.s; alpha = p.a; rotationZ = p.rot
                            if (mode == DisplayMode.HOLOGRAPHIC) { rotationX = (dragY * 0.03f).coerceIn(-18f, 18f); rotationY = (-dragX * 0.03f).coerceIn(-18f, 18f); cameraDistance = 14f * density.density }
                        },
                )
            }
        }
    }
}

/** Timeline: apps grouped by install date, newest first. */
@Composable
fun TimelineEngine(vm: LauncherViewModel, modifier: Modifier = Modifier) {
    val pal = LocalPalette.current
    val apps by vm.visibleApps.collectAsState()
    val fmt = remember { SimpleDateFormat("MMM yyyy", Locale.getDefault()) }
    val groups = remember(apps) { apps.sortedByDescending { it.installTime }.groupBy { fmt.format(Date(it.installTime)) }.toList() }
    LazyColumn(modifier.fillMaxSize().padding(top = 100.dp, bottom = 190.dp, start = 16.dp, end = 16.dp)) {
        groups.forEach { (month, list) ->
            item(key = month) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)) {
                    Canvas(Modifier.size(10.dp)) { drawCircle(pal.accent) }
                    Spacer(Modifier.width(10.dp))
                    Text(month, color = pal.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
            items(list.chunked(5), key = { month + it.first().key }) { row ->
                Row(Modifier.padding(start = 20.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { AppTile(it, vm, 50.dp) }
                }
            }
        }
    }
}

/** Adaptive: context-aware — layout and content change by time of day and on-device usage. */
@Composable
fun AdaptiveEngine(vm: LauncherViewModel, modifier: Modifier = Modifier) {
    val pal = LocalPalette.current
    val apps by vm.visibleApps.collectAsState()
    val meta by vm.meta.collectAsState()
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val title = when (hour) { in 5..11 -> "Good morning"; in 12..17 -> "Good afternoon"; in 18..22 -> "Good evening"; else -> "Late night" }
    val top = remember(meta, apps) { vm.suggestions(8) }
    val rest = remember(apps, top) { apps.filter { it !in top } }
    Column(modifier.fillMaxSize().padding(top = 100.dp, bottom = 190.dp, start = 16.dp, end = 16.dp)) {
        Text(title, color = pal.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(if (top.isEmpty()) "Launch a few apps and this fills with your favourites." else "Your most used right now", color = pal.subtext, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { top.take(4).forEach { AppTile(it, vm, 66.dp) } }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { top.drop(4).take(4).forEach { AppTile(it, vm, 54.dp) } }
        Spacer(Modifier.height(18.dp))
        LazyColumn { items(rest.chunked(5)) { r -> Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { r.forEach { AppTile(it, vm, 48.dp, showLabel = false) } } } }
    }
}
