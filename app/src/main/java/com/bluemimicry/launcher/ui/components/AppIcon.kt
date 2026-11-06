package com.bluemimicry.launcher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemimicry.launcher.data.AppInfo
import com.bluemimicry.launcher.data.AppRepository
import com.bluemimicry.launcher.data.IconStyle
import com.bluemimicry.launcher.ui.theme.LocalPalette
import com.bluemimicry.launcher.ui.theme.LocalShadowScale
import com.bluemimicry.launcher.ui.theme.neuRaised

fun iconShape(style: IconStyle, size: Dp): Shape = when (style) {
    IconStyle.CIRCLE -> CircleShape
    IconStyle.SQUIRCLE -> RoundedCornerShape(size * 0.36f)
    IconStyle.SQUARE -> RoundedCornerShape(size * 0.06f)
    else -> RoundedCornerShape(size * 0.27f)
}

/** Real installed-app icon on a raised tile, with an optional real notification badge. */
@Composable
fun AppIcon(
    app: AppInfo,
    repo: AppRepository,
    style: IconStyle,
    size: Dp,
    badge: Int = 0,
    modifier: Modifier = Modifier,
    glyphSize: Dp? = null,
) {
    val pal = LocalPalette.current
    val shadowScale = LocalShadowScale.current
    val px = with(LocalDensity.current) { (size * 1.5f).roundToPx() }.coerceIn(48, 512)
    val ver by repo.iconVersion.collectAsState()
    val bmp = remember(app.key, px, ver) { repo.icon(app, px) }
    val shape = iconShape(style, size)
    val bare = style == IconStyle.MINIMAL || style == IconStyle.TRANSPARENT
    val inner = glyphSize ?: if (bare) size else size * 0.68f

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        val tileMod = when (style) {
            IconStyle.NEUMORPHIC, IconStyle.CIRCLE, IconStyle.SQUIRCLE, IconStyle.ROUNDED, IconStyle.SQUARE ->
                Modifier.size(size).neuRaised(shape, size * 0.27f, size * 0.14f, pal.tile, pal.highlight, pal.shadow, shadowScale)
            IconStyle.RAISED3D ->
                Modifier.size(size).neuRaised(shape, size * 0.27f, size * 0.24f, pal.tile, pal.highlight, pal.shadow, shadowScale * 1.5f)
            IconStyle.GLASS ->
                Modifier.size(size).clip(shape).background(Color.White.copy(alpha = 0.28f)).border(1.dp, Color.White.copy(alpha = 0.6f), shape)
            else -> Modifier.size(size)
        }
        Box(tileMod, contentAlignment = Alignment.Center) {
            Image(bmp, contentDescription = app.label, modifier = Modifier.size(inner).clip(if (bare) shape else RoundedCornerShape(inner * 0.22f)), contentScale = ContentScale.Fit)
        }
        if (badge > 0) Badge(badge, Modifier.align(Alignment.TopEnd).offset(x = size * 0.14f, y = -size * 0.14f))
    }
}

@Composable
fun Badge(count: Int, modifier: Modifier = Modifier) {
    val pal = LocalPalette.current
    Box(
        modifier.defaultMinSize(minWidth = 20.dp, minHeight = 20.dp).clip(CircleShape).background(pal.badge).padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (count > 99) "99+" else "$count", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
