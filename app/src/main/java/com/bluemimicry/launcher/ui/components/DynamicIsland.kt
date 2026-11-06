package com.bluemimicry.launcher.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemimicry.launcher.data.AppRepository
import com.bluemimicry.launcher.notifications.BadgeStore
import kotlinx.coroutines.delay

data class BatteryState(val percent: Int, val charging: Boolean)

@Composable
fun rememberBattery(): State<BatteryState> {
    val ctx = LocalContext.current
    val state = remember { mutableStateOf(BatteryState(100, false)) }
    DisposableEffect(ctx) {
        fun read(i: Intent?) {
            i ?: return
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            if (level >= 0) state.value = BatteryState(level * 100 / scale, st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL)
        }
        val r = object : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) = read(i) }
        read(ctx.registerReceiver(r, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
        onDispose { ctx.unregisterReceiver(r) }
    }
    return state
}

/**
 * Launcher-level Dynamic-Island-style pill. It lives INSIDE the launcher window; it does not and cannot
 * replace Android's system status bar. Shows the latest real notification (needs Notification Access),
 * otherwise charging state, otherwise a compact idle pill.
 */
@Composable
fun DynamicIsland(repo: AppRepository, onOpenApp: (String) -> Unit, modifier: Modifier = Modifier) {
    val latest by BadgeStore.latest.collectAsState()
    val battery by rememberBattery()
    var expanded by remember { mutableStateOf(false) }
    var showNotif by remember { mutableStateOf(false) }

    LaunchedEffect(latest?.postedAt) {
        if (latest != null) { showNotif = true; delay(6000); showNotif = false; expanded = false }
    }

    val n = latest
    Column(
        modifier.animateContentSize(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
            .background(Color.Black, RoundedCornerShape(if (expanded) 28.dp else 22.dp))
            .clickable { if (showNotif && n != null) expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = if (expanded) 14.dp else 8.dp)
            .widthIn(min = 120.dp, max = 330.dp),
    ) {
        when {
            showNotif && n != null -> {
                val label = repo.find(repo.apps.value.firstOrNull { it.pkg == n.pkg }?.key ?: "")?.label ?: n.pkg
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, color = Color(0xFF5B8DEF), fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(12.dp))
                    Text(n.title.ifBlank { "New" }, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (expanded) {
                    Spacer(Modifier.height(8.dp))
                    Text(n.text, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(10.dp))
                    Text("Open", color = Color(0xFF5B8DEF), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onOpenApp(n.pkg); expanded = false })
                }
            }
            battery.charging -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Charging", color = Color.White, fontSize = 13.sp)
                Spacer(Modifier.width(18.dp))
                Text("${battery.percent}%", color = Color(0xFF34C759), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            else -> Spacer(Modifier.height(10.dp).width(96.dp))
        }
    }
}
