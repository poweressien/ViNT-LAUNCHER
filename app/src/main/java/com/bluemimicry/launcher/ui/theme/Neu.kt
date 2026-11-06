package com.bluemimicry.launcher.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bluemimicry.launcher.data.ThemeId

/** Every colour any screen needs, per theme. Screens never hard-code colours. */
data class Palette(
    val bg: Color, val bgBottom: Color,
    val surface: Color,      // container fill
    val tile: Color,         // icon tile fill
    val highlight: Color,    // soft white light
    val shadow: Color,       // soft dark shadow
    val text: Color, val subtext: Color,
    val accent: Color, val accentSoft: Color,
    val badge: Color = Color(0xFFFF3B30),
    val darkSystemIcons: Boolean = true,
    val panel: Color,        // dark widget (screen time, island)
)

fun paletteFor(id: ThemeId, top: Color? = null, bottom: Color? = null): Palette = when (id) {
    ThemeId.BLUE_MIMICRY -> Palette(
        bg = top ?: Color(0xFFD2D2D2), bgBottom = bottom ?: Color(0xFFC4C4C4),
        surface = Color(0xFFDCDCDC), tile = Color(0xFFE2E2E2),
        highlight = Color(0xFFFFFFFF), shadow = Color(0xFF000000),
        text = Color(0xFF16161A), subtext = Color(0xFF55555C),
        accent = Color(0xFF6FBDED), accentSoft = Color(0xFFA9D3F0), panel = Color(0xFF121216),
    )
    ThemeId.IOS -> Palette(
        bg = Color(0xFFF2F2F7), bgBottom = Color(0xFFE5E5EA), surface = Color(0xFFFFFFFF), tile = Color(0xFFFFFFFF),
        highlight = Color(0xFFFFFFFF), shadow = Color(0xFF6B7280), text = Color(0xFF000000), subtext = Color(0xFF8E8E93),
        accent = Color(0xFF007AFF), accentSoft = Color(0xFFB3D4FF), panel = Color(0xFF000000),
    )
    ThemeId.NEUMORPHISM -> Palette(
        bg = Color(0xFFE6E9EF), bgBottom = Color(0xFFE0E4EB), surface = Color(0xFFE6E9EF), tile = Color(0xFFE6E9EF),
        highlight = Color(0xFFFFFFFF), shadow = Color(0xFF8A93A8), text = Color(0xFF2B2F3A), subtext = Color(0xFF6B7280),
        accent = Color(0xFF5B8DEF), accentSoft = Color(0xFFBBD0F7), panel = Color(0xFF1B1E27),
    )
    ThemeId.MINIMAL_WHITE -> Palette(
        bg = Color(0xFFFFFFFF), bgBottom = Color(0xFFF7F7F7), surface = Color(0xFFF6F6F6), tile = Color(0xFFFFFFFF),
        highlight = Color(0xFFFFFFFF), shadow = Color(0xFF9CA3AF), text = Color(0xFF111111), subtext = Color(0xFF777777),
        accent = Color(0xFF111111), accentSoft = Color(0xFFDDDDDD), panel = Color(0xFF111111),
    )
    ThemeId.AMOLED -> Palette(
        bg = Color(0xFF000000), bgBottom = Color(0xFF000000), surface = Color(0xFF0E0E10), tile = Color(0xFF17171A),
        highlight = Color(0xFF2A2A30), shadow = Color(0xFF000000), text = Color(0xFFF2F2F5), subtext = Color(0xFF9A9AA3),
        accent = Color(0xFF6FBDED), accentSoft = Color(0xFF244A63), panel = Color(0xFF111114), darkSystemIcons = false,
    )
    ThemeId.FUTURISTIC -> Palette(
        bg = Color(0xFF0B1020), bgBottom = Color(0xFF05070F), surface = Color(0xFF131A33), tile = Color(0xFF1A2342),
        highlight = Color(0xFF2B3A6B), shadow = Color(0xFF000000), text = Color(0xFFE6ECFF), subtext = Color(0xFF8C9AC7),
        accent = Color(0xFF7C5CFF), accentSoft = Color(0xFF3B2F85), panel = Color(0xFF05070F), darkSystemIcons = false,
    )
    ThemeId.HOLOGRAPHIC -> Palette(
        bg = Color(0xFFE9E4FF), bgBottom = Color(0xFFD4F1FF), surface = Color(0xFFF3EFFF), tile = Color(0xFFF8F5FF),
        highlight = Color(0xFFFFFFFF), shadow = Color(0xFF7E73B8), text = Color(0xFF2A2350), subtext = Color(0xFF6E64A6),
        accent = Color(0xFFB36BFF), accentSoft = Color(0xFFD9C2FF), panel = Color(0xFF1E1840),
    )
    ThemeId.GLASS -> Palette(
        bg = Color(0xFF9DB4D6), bgBottom = Color(0xFF6B84AE), surface = Color(0x66FFFFFF), tile = Color(0x80FFFFFF),
        highlight = Color(0xFFFFFFFF), shadow = Color(0xFF1D2B4A), text = Color(0xFFFFFFFF), subtext = Color(0xFFDCE6F7),
        accent = Color(0xFFFFFFFF), accentSoft = Color(0x55FFFFFF), panel = Color(0xCC0F1830), darkSystemIcons = false,
    )
    ThemeId.COLORFUL -> Palette(
        bg = Color(0xFFFFE9D6), bgBottom = Color(0xFFFFD1E8), surface = Color(0xFFFFF4EA), tile = Color(0xFFFFFFFF),
        highlight = Color(0xFFFFFFFF), shadow = Color(0xFFB4637A), text = Color(0xFF3A1F2B), subtext = Color(0xFF8A5A6B),
        accent = Color(0xFFFF6B9A), accentSoft = Color(0xFFFFB3CC), panel = Color(0xFF2B1220),
    )
}

val LocalPalette = compositionLocalOf { paletteFor(ThemeId.BLUE_MIMICRY) }
val LocalShadowScale = compositionLocalOf { 1f }

@Composable
fun BmTheme(id: ThemeId, top: Color? = null, bottom: Color? = null, shadowScale: Float = 1f, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalPalette provides paletteFor(id, top, bottom),
        LocalShadowScale provides shadowScale,
        content = content,
    )
}

/**
 * Soft neumorphic raised surface, hardware-safe: stacked translucent rounded rects instead of
 * blur masks, so it renders identically on every GPU/OEM and stays cheap during animation.
 * Light source is top-left: white highlight up-left, grey shadow down-right.
 */
fun Modifier.neuRaised(
    shape: Shape = RoundedCornerShape(20.dp),
    radius: Dp = 20.dp,
    depth: Dp = 6.dp,
    fill: Color,
    highlight: Color,
    shadow: Color,
    scale: Float = 1f,
): Modifier = this
    .drawBehind {
        val d = depth.toPx() * scale
        if (d > 0.5f) {
            val steps = 6
            val r = radius.toPx()
            for (i in 1..steps) {
                val t = i / steps.toFloat()
                val grow = d * t
                val a = (1f - t) * (1f - t)
                // dark, offset down-right
                drawRoundRect(
                    color = shadow.copy(alpha = (0.07f * a).coerceIn(0f, 1f)),
                    topLeft = Offset(-grow * 0.2f + d * 0.45f, -grow * 0.2f + d * 0.6f),
                    size = Size(size.width + grow * 0.4f, size.height + grow * 0.4f),
                    cornerRadius = CornerRadius(r + grow * 0.2f),
                )
                // light, offset up-left
                drawRoundRect(
                    color = highlight.copy(alpha = (0.16f * a).coerceIn(0f, 1f)),
                    topLeft = Offset(-grow * 0.2f - d * 0.45f, -grow * 0.2f - d * 0.45f),
                    size = Size(size.width + grow * 0.4f, size.height + grow * 0.4f),
                    cornerRadius = CornerRadius(r + grow * 0.2f),
                )
            }
        }
    }
    .background(
        Brush.linearGradient(listOf(lighten(fill, 0.06f), fill, darken(fill, 0.04f))),
        shape,
    )
    .border(0.8.dp, highlight.copy(alpha = 0.55f), shape)

fun lighten(c: Color, f: Float) = Color(
    (c.red + (1f - c.red) * f).coerceIn(0f, 1f), (c.green + (1f - c.green) * f).coerceIn(0f, 1f),
    (c.blue + (1f - c.blue) * f).coerceIn(0f, 1f), c.alpha,
)

fun darken(c: Color, f: Float) = Color(
    (c.red * (1f - f)).coerceIn(0f, 1f), (c.green * (1f - f)).coerceIn(0f, 1f),
    (c.blue * (1f - f)).coerceIn(0f, 1f), c.alpha,
)
