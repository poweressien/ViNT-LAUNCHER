package com.bluemimicry.launcher.engines

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bluemimicry.launcher.LauncherViewModel
import com.bluemimicry.launcher.data.AppInfo
import com.bluemimicry.launcher.data.PerfProfile
import com.bluemimicry.launcher.engines.gl.ATLAS_MAX
import com.bluemimicry.launcher.engines.gl.AtlasBuilder
import com.bluemimicry.launcher.engines.gl.GlConfig
import com.bluemimicry.launcher.engines.gl.ShapeGlView
import com.bluemimicry.launcher.ui.theme.LocalPalette

/**
 * MODE C — real OpenGL ES 3D shapes (cube, sphere, cylinder, ring, pyramid, multi-layer carousel).
 * Limit: one 8x8 atlas, so at most 64 apps are placed (36 on the Lightweight profile).
 * Which apps: pinned first, then most-launched, then alphabetical.
 */
@Composable
fun ShapesEngine(vm: LauncherViewModel, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val settings by vm.settings.collectAsState()
    val apps by vm.visibleApps.collectAsState()
    val meta by vm.meta.collectAsState()
    val badges by vm.badges.collectAsState()
    val pal = LocalPalette.current
    val owner = LocalLifecycleOwner.current

    val cap = if (settings.perf == PerfProfile.LIGHTWEIGHT) 36 else ATLAS_MAX
    val chosen: List<AppInfo> = remember(apps, meta, cap) {
        apps.sortedWith(compareByDescending<AppInfo> { meta[it.key]?.pinned == true }
            .thenByDescending { meta[it.key]?.launchCount ?: 0 }
            .thenBy { vm.labelOf(it).lowercase() }).take(cap)
    }
    val tile = pal.tile.toArgb()
    val iconVer by vm.repo.iconVersion.collectAsState()
    val atlas = remember(chosen, badges, settings.iconStyle, tile, iconVer) {
        AtlasBuilder.build(chosen, vm.repo, settings.iconStyle, tile, badges)
    }
    val currentChosen by rememberUpdatedState(chosen)

    val view = remember { ShapeGlView(ctx) { i -> currentChosen.getOrNull(i)?.let(vm::launch) } }

    DisposableEffect(owner, view) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_PAUSE) view.onPause() else if (e == Lifecycle.Event.ON_RESUME) view.onResume()
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs); view.onPause() }
    }

    AndroidView(
        factory = { view }, modifier = modifier,
        update = { v ->
            v.setAtlas(atlas, chosen.size)
            v.renderer.cfg = GlConfig(
                shape = settings.shape3d, fov = settings.fov, autoRotate = settings.autoRotate && !settings.reducedMotion,
                speed = settings.rotationSpeed, light = settings.lightIntensity, metallic = settings.shapeMetallic,
                glass = settings.shapeGlass, bodyColor = settings.shapeColor.toInt(), bg = settings.bg3d,
                fpsCap = settings.perf.fpsCap, sphereDetail = settings.perf.sphereDetail,
            )
        },
    )
}
