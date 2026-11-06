package com.bluemimicry.launcher.engines

import androidx.compose.animation.core.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import com.bluemimicry.launcher.LauncherViewModel
import com.bluemimicry.launcher.ui.components.AppTile
import kotlinx.coroutines.delay
import kotlin.math.ceil

/**
 * MODE D — apps swap grid slots every N seconds (default 4) with animated slides.
 * Safety: taps are disabled while a swap animation is running and for a short settle window after,
 * so an icon moving under the finger can never launch the wrong app. Pinned apps never move.
 * Preserves identity and badges because each tile is keyed by the real app key.
 */
@Composable
fun ShuffleEngine(vm: LauncherViewModel, modifier: Modifier = Modifier) {
    val settings by vm.settings.collectAsState()
    val apps by vm.visibleApps.collectAsState()
    val meta by vm.meta.collectAsState()
    val density = LocalDensity.current

    // slot order: list of app keys, index = grid slot
    var order by remember(apps) { mutableStateOf(apps.map { it.key }) }
    var settling by remember { mutableStateOf(false) }

    LaunchedEffect(apps, settings.shuffleSeconds, settings.shufflePaused, settings.reducedMotion) {
        if (settings.shufflePaused) return@LaunchedEffect
        while (true) {
            delay(settings.shuffleSeconds.coerceIn(1, 60) * 1000L)
            val pinned = meta.filter { it.value.pinned }.keys
            val movable = order.withIndex().filter { it.value !in pinned }
            val shuffledKeys = movable.map { it.value }.shuffled()
            val next = order.toMutableList()
            movable.forEachIndexed { i, iv -> next[iv.index] = shuffledKeys[i] }
            settling = true
            order = next
            delay(settings.animDurationMs + 250L)
            settling = false
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().padding(top = 110.dp, bottom = 200.dp)) {
        val cols = 4
        val cellW = maxWidth / cols
        val cellH = 92.dp
        val byKey = remember(apps) { apps.associateBy { it.key } }
        val rows = ceil(order.size / cols.toFloat()).toInt()
        val scrollMax = (cellH * rows - maxHeight).coerceAtLeast(0.dp)
        var scroll by remember { mutableFloatStateOf(0f) }
        val cellWPx = with(density) { cellW.roundToPx() }
        val cellHPx = with(density) { cellH.roundToPx() }

        Box(
            Modifier.fillMaxSize().androidScroll { d -> scroll = (scroll - d).coerceIn(0f, with(density) { scrollMax.toPx() }) },
        ) {
            for ((slot, k) in order.withIndex()) {
                val app = byKey[k] ?: continue
                key(k) {
                    val target = IntOffset((slot % cols) * cellWPx, (slot / cols) * cellHPx - scroll.toInt())
                    val anim: FiniteAnimationSpec<IntOffset> =
                        if (settings.reducedMotion) snap()
                        else if (settings.spring) spring(dampingRatio = 0.7f, stiffness = 180f)
                        else tween(settings.animDurationMs)
                    val pos by animateIntOffsetAsState(target, anim, label = "slot")
                    AppTile(
                        app, vm, 54.dp, enabled = !settling,
                        modifier = Modifier.offset { pos }.width(cellW),
                    )
                }
            }
        }
    }
}
