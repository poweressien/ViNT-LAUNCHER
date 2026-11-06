package com.bluemimicry.launcher.lockscreen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemimicry.launcher.LauncherViewModel
import com.bluemimicry.launcher.notifications.BadgeStore
import com.bluemimicry.launcher.system.SystemActions
import com.bluemimicry.launcher.ui.components.AppTile
import com.bluemimicry.launcher.ui.components.rememberBattery
import com.bluemimicry.launcher.ui.components.rememberNow
import com.bluemimicry.launcher.ui.theme.BmTheme
import com.bluemimicry.launcher.ui.theme.LocalPalette
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.PI
import kotlin.math.sin

/**
 * Lock-screen COMPANION. This is an ordinary app screen shown after the phone is unlocked
 * (or on demand). It is NOT Android's secure lock screen: it never appears above the keyguard,
 * never handles PINs/biometrics, and cannot bypass authentication. "Lock now" asks Android
 * to lock the real device (needs the optional accessibility lock service).
 */
class LockCompanionActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val s by vm.settings.collectAsState()
            BmTheme(s.theme, shadowScale = s.shadowIntensity) {
                CompanionScreen(vm, onExit = { finish() })
            }
        }
    }
}

private val STYLES = listOf("Wave", "Minimal", "Night")

@Composable
private fun CompanionScreen(vm: LauncherViewModel, onExit: () -> Unit) {
    val ctx = LocalContext.current
    val pal = LocalPalette.current
    val s by vm.settings.collectAsState()
    val now by rememberNow(everySecond = false)
    val battery by rememberBattery()
    val notifs by BadgeStore.active.collectAsState()
    val dock by vm.dock.collectAsState()
    val apps by vm.allApps.collectAsState()
    val sp = remember { ctx.getSharedPreferences("lockcomp", android.content.Context.MODE_PRIVATE) }
    var style by remember { mutableIntStateOf(sp.getInt("style", 0)) }
    var weather by remember { mutableStateOf<Weather?>(null) }
    var weatherDialog by remember { mutableStateOf(false) }
    var configured by remember { mutableStateOf(WeatherSource.isConfigured(ctx)) }

    LaunchedEffect(configured) { weather = if (configured) WeatherSource.fetch(ctx) else null }

    val night = style == 2
    val textColor = if (night) Color.White else pal.text
    val bg = when (style) { 2 -> Brush.verticalGradient(listOf(Color(0xFF0A0F1F), Color(0xFF050814))); 1 -> Brush.verticalGradient(listOf(pal.bg, pal.bgBottom)); else -> Brush.verticalGradient(listOf(Color(0xFFE6ECF6), Color(0xFFC6D8EC))) }
    val time = remember(now) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now)) }
    val date = remember(now) { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date(now)) }

    Box(Modifier.fillMaxSize().background(bg).pointerInput(Unit) { detectVerticalDragGestures { _, d -> if (d < -40f) onExit() } }) {
        if (style == 0) Waves(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.42f), s.reducedMotion)

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Companion screen — not the secure lock screen", color = textColor.copy(alpha = 0.55f), fontSize = 11.sp)
            Spacer(Modifier.height(36.dp))
            Text(date, color = textColor.copy(alpha = 0.75f), fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Text(time, color = textColor, fontSize = 96.sp, fontWeight = FontWeight.Light)

            Text(
                when {
                    !configured -> "Add weather"
                    weather == null -> "Weather unavailable"
                    else -> "${weather!!.tempC.toInt()}°C  ·  ${weather!!.description}"
                },
                color = textColor.copy(alpha = 0.8f), fontSize = 15.sp, modifier = Modifier.clickable { weatherDialog = true }.padding(8.dp),
            )

            if (battery.charging) ChargingRing(battery.percent, Modifier.padding(top = 10.dp).size(88.dp), s.reducedMotion, textColor)

            Spacer(Modifier.height(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (notifs.isEmpty()) Text(if (BadgeStore.connected.value) "No notifications" else "Grant notification access in Studio → System to see them here", color = textColor.copy(alpha = 0.55f), fontSize = 13.sp)
                notifs.take(4).forEach { n ->
                    Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = if (night) 0.08f else 0.55f), RoundedCornerShape(18.dp)).padding(12.dp)) {
                        Text(if (s.hideNotifContent) "Notification" else n.title, color = textColor, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                        Text(if (s.hideNotifContent) "Content hidden" else n.text, color = textColor.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 2)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                val byKey = apps.associateBy { it.key }
                dock.mapNotNull { byKey[it.key] }.take(4).forEach { AppTile(it, vm, 50.dp, showLabel = false) }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("Style: ${STYLES[style]}", color = textColor, fontSize = 13.sp, modifier = Modifier.clickable { style = (style + 1) % STYLES.size; sp.edit().putInt("style", style).apply() }.padding(8.dp))
                Text("Lock now", color = textColor, fontSize = 13.sp, modifier = Modifier.clickable { SystemActions.lockScreen(ctx) }.padding(8.dp))
                Text("Home ↑", color = textColor, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onExit).padding(8.dp))
            }
        }
    }

    if (weatherDialog) {
        var lat by remember { mutableStateOf("") }; var lon by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { weatherDialog = false },
            title = { Text("Weather source") },
            text = {
                Column {
                    Text("Uses Open-Meteo. Only the coordinates you enter below are sent over the internet. Nothing else.", fontSize = 13.sp)
                    OutlinedTextField(lat, { lat = it }, label = { Text("Latitude, e.g. 5.0377") }, singleLine = true)
                    OutlinedTextField(lon, { lon = it }, label = { Text("Longitude, e.g. 7.9128") }, singleLine = true)
                }
            },
            confirmButton = { TextButton({
                val la = lat.toDoubleOrNull(); val lo = lon.toDoubleOrNull()
                if (la != null && lo != null && la in -90.0..90.0 && lo in -180.0..180.0) { WeatherSource.configure(ctx, la, lo); configured = true; weatherDialog = false }
            }) { Text("I agree, use these") } },
            dismissButton = { TextButton({ WeatherSource.clear(ctx); configured = false; weather = null; weatherDialog = false }) { Text("Turn off") } },
        )
    }
}

@Composable
private fun Waves(modifier: Modifier, reduced: Boolean) {
    val phase = if (reduced) 0f else rememberInfiniteTransition(label = "w").animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(10000, easing = LinearEasing)), label = "p").value
    Canvas(modifier) {
        fun band(base: Float, amp: Float, ph: Float, c: Color) {
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, size.height); lineTo(0f, base)
                for (i in 0..40) { val x = size.width * i / 40; lineTo(x, base + amp * sin(i / 40f * 2.6f * PI.toFloat() + ph)) }
                lineTo(size.width, size.height); close()
            }
            drawPath(p, c)
        }
        band(size.height * 0.15f, 22f, phase, Color(0xFFD3E4F3)); band(size.height * 0.42f, 26f, phase * 1.2f + 1f, Color(0xFF6A8CAA)); band(size.height * 0.68f, 20f, phase * 0.8f + 2f, Color(0xFF071530))
    }
}

@Composable
private fun ChargingRing(percent: Int, modifier: Modifier, reduced: Boolean, color: Color) {
    val pulse = if (reduced) 1f else rememberInfiniteTransition(label = "c").animateFloat(0.85f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "pl").value
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val st = Stroke(10f, cap = StrokeCap.Round)
            drawArc(color.copy(alpha = 0.15f), 0f, 360f, false, style = st)
            drawArc(Color(0xFF34C759).copy(alpha = pulse), -90f, 360f * percent / 100f, false, style = st)
        }
        Text("$percent%", color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}
