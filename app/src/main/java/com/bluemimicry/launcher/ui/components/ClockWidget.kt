package com.bluemimicry.launcher.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemimicry.launcher.ui.theme.LocalPalette
import com.bluemimicry.launcher.ui.theme.LocalShadowScale
import com.bluemimicry.launcher.ui.theme.neuRaised
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.PI
import kotlin.math.sin

/** Ticks once per second (or minute) and always lands on the boundary, so the clock never drifts. */
@Composable
fun rememberNow(everySecond: Boolean): State<Long> = produceState(System.currentTimeMillis(), everySecond) {
    while (true) {
        value = System.currentTimeMillis()
        val step = if (everySecond) 1000L else 60_000L
        delay(step - (value % step) + 5)
    }
}

/** Large rounded clock card with the flowing blue wave. Time and date update automatically. */
@Composable
fun ClockWidget(
    modifier: Modifier = Modifier,
    fontFamily: FontFamily = FontFamily.SansSerif,
    fontScale: Float = 1f,
    animate: Boolean = true,
    radiusDp: Float = 38f,
) {
    val pal = LocalPalette.current
    val now by rememberNow(everySecond = false)
    val cal = remember(now) { Calendar.getInstance().apply { timeInMillis = now } }
    val time = remember(now) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time) }
    val day = remember(now) { SimpleDateFormat("EEEE", Locale.getDefault()).format(cal.time) }
    val date = remember(now) { SimpleDateFormat("MM/dd", Locale.getDefault()).format(cal.time) }

    val phase = if (animate) {
        val t = rememberInfiniteTransition(label = "wave")
        t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "p").value
    } else 0f

    val shape = RoundedCornerShape(radiusDp.dp)
    Box(
        modifier
            .neuRaised(shape, radiusDp.dp, 12.dp, pal.surface, pal.highlight, pal.shadow, LocalShadowScale.current)
            .clip(shape)
    ) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width; val h = size.height
            fun wave(base: Float, amp: Float, ph: Float, tilt: Float): Path = Path().apply {
                moveTo(0f, h)
                lineTo(0f, base + tilt)
                val n = 48
                for (i in 0..n) {
                    val x = w * i / n
                    val y = base + tilt * (1f - 2f * i / n) + amp * sin(i / n.toFloat() * 2.4f * PI.toFloat() + ph)
                    lineTo(x, y)
                }
                lineTo(w, h); close()
            }
            drawPath(wave(h * 0.62f, h * 0.05f, phase, h * 0.14f), pal.accentSoft)
            drawPath(wave(h * 0.78f, h * 0.045f, phase * 1.3f + 1.2f, h * 0.10f), pal.accent)
            // atmospheric sun/moon disc
            drawCircle(Color.White.copy(alpha = 0.95f), radius = h * 0.13f, center = Offset(w * 0.77f, h * 0.26f))
        }
        Column(Modifier.padding(start = 28.dp, top = 22.dp, bottom = 22.dp)) {
            Text(time, style = TextStyle(fontFamily = fontFamily, fontSize = (64 * fontScale).sp, fontWeight = FontWeight.Medium, color = pal.text, letterSpacing = 1.sp))
            Text(day, style = TextStyle(fontFamily = fontFamily, fontSize = (38 * fontScale).sp, fontWeight = FontWeight.Medium, color = pal.text))
            Text(date, style = TextStyle(fontFamily = fontFamily, fontSize = (20 * fontScale).sp, color = pal.text, letterSpacing = 2.sp))
        }
    }
}

/** Optional floating digital clock with seconds, shown under the island (reference top area). */
@Composable
fun SecondsChip(modifier: Modifier = Modifier, fontFamily: FontFamily = FontFamily.Monospace) {
    val now by rememberNow(everySecond = true)
    val s = remember(now) { SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(now)) }
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFF7E7E7E)), contentAlignment = Alignment.Center) {
        Text(s, Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            style = TextStyle(fontFamily = fontFamily, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White))
    }
}
