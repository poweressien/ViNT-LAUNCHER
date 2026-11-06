package com.bluemimicry.launcher.ui.components

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
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
import com.bluemimicry.launcher.ui.theme.LocalPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

fun hasUsageAccess(ctx: Context): Boolean = try {
    val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName) == AppOpsManager.MODE_ALLOWED
} catch (_: Exception) { false }

private data class Usage(val totalMs: Long, val top: List<Pair<String, Long>>)

private suspend fun loadUsage(ctx: Context, repo: AppRepository): Usage = withContext(Dispatchers.IO) {
    val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val start = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val stats = usm.queryAndAggregateUsageStats(start, System.currentTimeMillis())
    val byPkg = repo.apps.value.groupBy { it.pkg }
    val rows = stats.values
        .filter { it.totalTimeInForeground > 60_000 && byPkg.containsKey(it.packageName) && it.packageName != ctx.packageName }
        .sortedByDescending { it.totalTimeInForeground }
    Usage(
        totalMs = rows.sumOf { it.totalTimeInForeground },
        top = rows.take(3).map { byPkg.getValue(it.packageName).first().label to it.totalTimeInForeground },
    )
}

private fun fmt(ms: Long): String {
    val m = ms / 60_000
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
}

/** Real screen time from UsageStatsManager. Without Usage Access it offers the grant flow instead of fake numbers. */
@Composable
fun ScreenTimeWidget(repo: AppRepository, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val pal = LocalPalette.current
    var granted by remember { mutableStateOf(hasUsageAccess(ctx)) }
    var usage by remember { mutableStateOf<Usage?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    val apps by repo.apps.collectAsState()

    LaunchedEffect(tick, apps.size) {
        granted = hasUsageAccess(ctx)
        if (granted && apps.isNotEmpty()) usage = loadUsage(ctx, repo)
    }

    Column(modifier.background(pal.panel, RoundedCornerShape(22.dp)).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Screen time", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.Refresh, "Refresh", tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(20.dp).clickable { tick++ })
        }
        if (!granted) {
            Spacer(Modifier.height(10.dp))
            Text("Usage access is off", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Grant access", color = pal.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable {
                    ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
            )
        } else {
            val u = usage
            Spacer(Modifier.height(8.dp))
            Text(if (u == null) "…" else fmt(u.totalMs), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            val tints = listOf(Color(0xFFF1F3F9), Color(0xFF9EA3C0), Color(0xFFE3E6F7))
            u?.top?.forEachIndexed { i, (label, ms) ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        fmt(ms), color = Color(0xFF14141A), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(tints[i % 3], RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 3.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
