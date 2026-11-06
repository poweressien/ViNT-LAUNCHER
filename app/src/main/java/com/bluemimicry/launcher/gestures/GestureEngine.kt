package com.bluemimicry.launcher.gestures

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bluemimicry.launcher.data.LauncherSettings
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/** Actions a gesture can trigger. Stored by name in settings. */
enum class GestureAction(val label: String) {
    NONE("Nothing"), DRAWER("App drawer"), SEARCH("Search"), NOTIFICATIONS("Notification centre"),
    SYSTEM_SHADE("System notification shade"), CONTROL_CENTER("Control Center"), STUDIO("Customization Studio"),
    NEXT_MODE("Next display mode"), SHUFFLE("Shuffle apps now"), ROTATE_3D("Spin 3D environment"),
    FAVORITES("Open favorites"), EDIT("Edit home"), LOCK("Lock screen"), LOCK_COMPANION("Lock-screen companion"), MESSAGES("Messages"),
    ;
    companion object { fun of(s: String) = values().firstOrNull { it.name == s } ?: NONE }
}

/**
 * Touch gestures use the Initial pointer pass and never consume, so pagers, drags and the 3D view keep working.
 * To avoid stealing normal drags, vertical/horizontal swipes only count when they START in an edge zone:
 * top 18% (swipe down), bottom 22% (swipe up / left / right). Two-finger swipe and pinch work anywhere.
 */
fun Modifier.launcherGestures(
    settings: LauncherSettings, size: IntSize, pinchEnabled: Boolean, onAction: (GestureAction) -> Unit,
): Modifier = this
    .pointerInput(settings.gestureDoubleTap, settings.gestureLongPress) {
        detectTapGestures(
            onDoubleTap = { onAction(GestureAction.of(settings.gestureDoubleTap)) },
            onLongPress = { onAction(GestureAction.of(settings.gestureLongPress)) },
        )
    }
    .pointerInput(settings.gestureSwipeUp, settings.gestureSwipeDown, settings.gestureSwipeLeft, settings.gestureSwipeRight, settings.gestureTwoFinger, settings.gesturePinch, pinchEnabled, size) {
        val minSwipe = 90.dpToPx(this)
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val start = down.position
            var maxPointers = 1
            var startDist = 0f; var endDist = 0f
            var cum = Offset.Zero
            var last = start
            var cur: Offset
            do {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                val pts = ev.changes.filter { it.pressed }
                if (pts.size > maxPointers) maxPointers = pts.size
                if (pts.size >= 2) {
                    val d = hypot(pts[0].position.x - pts[1].position.x, pts[0].position.y - pts[1].position.y)
                    if (startDist == 0f) startDist = d
                    endDist = d
                }
                cur = pts.firstOrNull()?.position ?: last
                last = cur
            } while (ev.changes.any { it.pressed })
            cum = last - start
            val dx = cum.x; val dy = cum.y
            when {
                maxPointers >= 2 && startDist > 0f && abs(endDist - startDist) > 140f && pinchEnabled ->
                    onAction(GestureAction.of(settings.gesturePinch))
                maxPointers >= 2 && hypot(dx, dy) > minSwipe && abs(endDist - startDist) < 140f ->
                    onAction(GestureAction.of(settings.gestureTwoFinger))
                maxPointers == 1 && abs(dy) > minSwipe && abs(dy) > abs(dx) * 1.6f -> {
                    if (dy < 0 && start.y > size.height * 0.78f) onAction(GestureAction.of(settings.gestureSwipeUp))
                    if (dy > 0 && start.y < size.height * 0.18f) onAction(GestureAction.of(settings.gestureSwipeDown))
                }
                maxPointers == 1 && abs(dx) > minSwipe * 1.5f && abs(dx) > abs(dy) * 2f && start.y > size.height * 0.78f ->
                    onAction(GestureAction.of(if (dx < 0) settings.gestureSwipeLeft else settings.gestureSwipeRight))
            }
        }
    }

private fun Int.dpToPx(s: androidx.compose.ui.unit.Density): Float = this * s.density

/** Shake and tilt via the accelerometer; registered only while the activity is resumed (no background work). */
@Composable
fun MotionGestures(settings: LauncherSettings, onAction: (GestureAction) -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val shake = GestureAction.of(settings.gestureShake); val tilt = GestureAction.of(settings.gestureTilt)
    val cb by rememberUpdatedState(onAction)
    DisposableEffect(owner, shake, tilt) {
        if (shake == GestureAction.NONE && tilt == GestureAction.NONE) return@DisposableEffect onDispose {}
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return@DisposableEffect onDispose {}
        var lastFire = 0L
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val now = System.currentTimeMillis()
                if (now - lastFire < 1200) return
                val x = e.values[0]; val y = e.values[1]; val z = e.values[2]
                val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
                if (shake != GestureAction.NONE && g > 2.6f) { lastFire = now; cb(shake); return }
                // tilt: strong roll to the side while the phone is held upright
                if (tilt != GestureAction.NONE && abs(x) > 7.5f && abs(y) > 3f) { lastFire = now; cb(tilt) }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        val obs = LifecycleEventObserver { _, ev ->
            if (ev == Lifecycle.Event.ON_RESUME) sm.registerListener(l, acc, SensorManager.SENSOR_DELAY_UI)
            else if (ev == Lifecycle.Event.ON_PAUSE) sm.unregisterListener(l)
        }
        owner.lifecycle.addObserver(obs)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) sm.registerListener(l, acc, SensorManager.SENSOR_DELAY_UI)
        onDispose { owner.lifecycle.removeObserver(obs); sm.unregisterListener(l) }
    }
}
