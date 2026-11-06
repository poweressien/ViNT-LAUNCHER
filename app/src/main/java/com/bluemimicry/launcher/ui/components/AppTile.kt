package com.bluemimicry.launcher.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemimicry.launcher.LauncherViewModel
import com.bluemimicry.launcher.data.AppInfo
import com.bluemimicry.launcher.ui.theme.LocalPalette

/** Icon + optional label + long-press menu. The standard tappable app everywhere in the launcher. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTile(
    app: AppInfo,
    vm: LauncherViewModel,
    size: Dp,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    labelFont: FontFamily = FontFamily.SansSerif,
    fontScale: Float = 1f,
    glyphSize: Dp? = null,
    enabled: Boolean = true,
    onTap: (() -> Unit)? = null,
) {
    val settings by vm.settings.collectAsState()
    val badges by vm.badges.collectAsState()
    val meta by vm.meta.collectAsState()
    val pal = LocalPalette.current
    var menu by remember { mutableStateOf(false) }
    val scale = meta[app.key]?.iconSizeScale ?: 1f

    Box(modifier) {
        Column(
            Modifier.combinedClickable(
                enabled = enabled,
                onClick = { if (onTap != null) onTap() else vm.launch(app) },
                onLongClick = { menu = true },
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppIcon(app, vm.repo, settings.iconStyle, size * scale, badges[app.pkg] ?: 0, glyphSize = glyphSize?.times(scale))
            if (showLabel && settings.showLabels) {
                Spacer(Modifier.height(4.dp))
                Text(
                    vm.labelOf(app), color = pal.text, fontSize = (12 * fontScale).sp, fontWeight = FontWeight.SemiBold,
                    fontFamily = labelFont, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = size + 16.dp),
                )
            }
        }
        if (menu) AppMenu(app, vm) { menu = false }
    }
}
