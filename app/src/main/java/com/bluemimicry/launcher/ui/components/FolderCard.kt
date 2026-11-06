package com.bluemimicry.launcher.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemimicry.launcher.LauncherViewModel
import com.bluemimicry.launcher.data.AppInfo
import com.bluemimicry.launcher.data.FolderEntity
import com.bluemimicry.launcher.ui.theme.LocalPalette
import com.bluemimicry.launcher.ui.theme.LocalShadowScale
import com.bluemimicry.launcher.ui.theme.neuRaised
import kotlin.math.roundToInt

/**
 * Floating neumorphic group: big apps in a grid, remaining apps packed into a small 2x2 cluster
 * (exactly the reference arrangement), title underneath, combined red badge on the corner.
 */
@Composable
fun FolderCard(
    folder: FolderEntity,
    members: List<AppInfo>,
    bigKeys: Set<String>,
    vm: LauncherViewModel,
    width: Dp,
    editing: Boolean,
    labelFont: FontFamily,
    fontScale: Float,
    onOpen: () -> Unit,
    onMove: (dxPx: Float, dyPx: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pal = LocalPalette.current
    val settings by vm.settings.collectAsState()
    val badges by vm.badges.collectAsState()
    var drag by remember(folder.x, folder.y) { mutableStateOf(Offset.Zero) }
    var renaming by remember { mutableStateOf(false) }

    val pad = 14.dp
    val cols = folder.cols.coerceIn(2, 5)
    val cell = (width - pad * 2) / cols
    val bigSize = cell * 0.80f
    val bigs = members.filter { it.key in bigKeys }.take(cols * 2)
    val smalls = members.filter { it.key !in bigKeys || it !in bigs }.take(4)
    val total = members.sumOf { badges[it.pkg] ?: 0 }

    Column(
        modifier.width(width)
            .offset { IntOffset(drag.x.roundToInt(), drag.y.roundToInt()) }
            .pointerInput(editing, folder.id) {
                if (editing) detectDragGestures(
                    onDragEnd = { onMove(drag.x, drag.y) },
                    onDrag = { c, d -> c.consume(); drag += d },
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            val shape = RoundedCornerShape(settings.containerRadius.dp)
            Column(
                Modifier.fillMaxWidth()
                    .neuRaised(shape, settings.containerRadius.dp, 8.dp, pal.surface.copy(alpha = settings.containerAlpha), pal.highlight, pal.shadow, LocalShadowScale.current)
                    .padding(pad),
            ) {
                // cells: bigs first, then one cluster cell
                val cells = bigs.size + if (smalls.isNotEmpty()) 1 else 0
                val rows = ((cells + cols - 1) / cols).coerceAtLeast(1)
                for (r in 0 until rows) {
                    Row {
                        for (c in 0 until cols) {
                            val idx = r * cols + c
                            Box(Modifier.size(cell), contentAlignment = Alignment.Center) {
                                when {
                                    idx < bigs.size -> AppTile(bigs[idx], vm, bigSize, showLabel = false, enabled = !editing)
                                    idx == bigs.size && smalls.isNotEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        smalls.chunked(2).forEach { pair ->
                                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                                pair.forEach { AppTile(it, vm, cell * 0.36f, showLabel = false, enabled = !editing) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (total > 0) Badge(total, Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-8).dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            folder.name, color = pal.text, fontSize = (13 * fontScale).sp, fontWeight = FontWeight.Bold, fontFamily = labelFont,
            modifier = Modifier.clickable { if (editing) renaming = true else onOpen() },
        )
        if (editing) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 4.dp)) {
                Text("−", color = pal.accent, fontSize = 22.sp, modifier = Modifier.clickable { vm.resizeFolder(folder, folder.w - 0.04f) })
                Text("+", color = pal.accent, fontSize = 22.sp, modifier = Modifier.clickable { vm.resizeFolder(folder, folder.w + 0.04f) })
                Text("Delete", color = pal.badge, fontSize = 14.sp, modifier = Modifier.clickable { vm.deleteFolder(folder) })
            }
        }
    }

    if (renaming) {
        var t by remember { mutableStateOf(folder.name) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename group") },
            text = { androidx.compose.material3.OutlinedTextField(t, { t = it }, singleLine = true) },
            confirmButton = { androidx.compose.material3.TextButton({ vm.renameFolder(folder, t.ifBlank { folder.name }); renaming = false }) { Text("Save") } },
        )
    }
}
