package com.bluemimicry.launcher.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.bluemimicry.launcher.LauncherViewModel
import com.bluemimicry.launcher.data.WidgetEntity
import com.bluemimicry.launcher.ui.theme.LocalPalette
import kotlin.math.roundToInt

const val REQ_BIND = 7001
const val REQ_CONFIGURE = 7002

/** Owns the AppWidgetHost for the activity and runs the bind → configure → place flow. */
class WidgetController(private val activity: Activity, private val onReady: (Int, AppWidgetProviderInfo) -> Unit) {
    val manager: AppWidgetManager = AppWidgetManager.getInstance(activity)
    val host = AppWidgetHost(activity, 1024)
    private var pendingId = -1

    fun start() = runCatching { host.startListening() }
    fun stop() = runCatching { host.stopListening() }

    fun providers(): List<AppWidgetProviderInfo> =
        manager.installedProviders.sortedBy { it.loadLabel(activity.packageManager).lowercase() }

    fun add(info: AppWidgetProviderInfo) {
        val id = host.allocateAppWidgetId(); pendingId = id
        if (manager.bindAppWidgetIdIfAllowed(id, info.provider)) afterBind(id, info)
        else activity.startActivityForResult(
            Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider), REQ_BIND,
        )
    }

    private fun afterBind(id: Int, info: AppWidgetProviderInfo) {
        if (info.configure != null) {
            runCatching { host.startAppWidgetConfigureActivityForResult(activity, id, 0, REQ_CONFIGURE, null) }
                .onFailure { onReady(id, info) }
        } else onReady(id, info)
    }

    fun handleResult(request: Int, result: Int) {
        if (request != REQ_BIND && request != REQ_CONFIGURE) return
        val id = pendingId
        if (id < 0) return
        if (result != Activity.RESULT_OK) { host.deleteAppWidgetId(id); pendingId = -1; return }
        val info = manager.getAppWidgetInfo(id) ?: return
        if (request == REQ_BIND) afterBind(id, info) else { onReady(id, info); pendingId = -1 }
    }

    fun remove(id: Int) = runCatching { host.deleteAppWidgetId(id) }
}

val LocalWidgetController = compositionLocalOf<WidgetController?> { null }

@Composable
fun WidgetPicker(controller: WidgetController, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val list = remember { controller.providers() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a widget") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(list) { p ->
                    Text(
                        "${p.loadLabel(ctx.packageManager)}  ·  ${p.provider.packageName.substringAfterLast('.')}",
                        Modifier.fillMaxWidth().clickable { controller.add(p); onDismiss() }.padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

/** Hosted widgets placed on the widgets page. Edit mode: drag to move, − / + to resize, × to remove. */
@Composable
fun HostedWidgets(vm: LauncherViewModel, editing: Boolean, pageW: Float, pageH: Float, widgets: List<WidgetEntity>) {
    val controller = LocalWidgetController.current ?: return
    val ctx = LocalContext.current
    val pal = LocalPalette.current
    val density = LocalDensity.current
    widgets.forEach { w ->
        key(w.appWidgetId) {
            val info = remember(w.appWidgetId) { controller.manager.getAppWidgetInfo(w.appWidgetId) } ?: return@key
            var drag by remember(w.x, w.y) { mutableStateOf(Offset.Zero) }
            val wDp = with(density) { (w.w * pageW).toDp() }; val hDp = with(density) { (w.h * pageH).toDp() }
            Box(
                Modifier.offset { IntOffset((w.x * pageW + drag.x).roundToInt(), (w.y * pageH + drag.y).roundToInt()) }
                    .size(wDp, hDp)
                    .pointerInput(editing, w.appWidgetId) {
                        if (editing) detectDragGestures(
                            onDragEnd = { vm.moveWidget(w, w.x + drag.x / pageW, w.y + drag.y / pageH) },
                            onDrag = { c, d -> c.consume(); drag += d },
                        )
                    },
            ) {
                AndroidView(factory = {
                    controller.host.createView(ctx, w.appWidgetId, info).apply { setAppWidget(w.appWidgetId, info) }
                }, update = { v ->
                    v.updateAppWidgetSize(null, wDp.value.toInt(), hDp.value.toInt(), wDp.value.toInt(), hDp.value.toInt())
                }, modifier = Modifier.matchParentSize())
                if (editing) Row(Modifier.align(Alignment.TopEnd)) {
                    Text("−", color = pal.accent, modifier = Modifier.clickable { vm.resizeWidget(w, 0.9f) }.padding(8.dp))
                    Text("+", color = pal.accent, modifier = Modifier.clickable { vm.resizeWidget(w, 1.1f) }.padding(8.dp))
                    Text("×", color = pal.badge, modifier = Modifier.clickable { controller.remove(w.appWidgetId); vm.removeWidget(w) }.padding(8.dp))
                }
            }
        }
    }
}
