package com.bluemimicry.launcher.engines.gl

import android.graphics.*
import androidx.compose.ui.graphics.asAndroidBitmap
import com.bluemimicry.launcher.data.AppInfo
import com.bluemimicry.launcher.data.AppRepository
import com.bluemimicry.launcher.data.IconStyle

const val ATLAS_GRID = 8
const val ATLAS_CELL = 128
const val ATLAS_MAX = ATLAS_GRID * ATLAS_GRID

/**
 * Packs real app icons (+ real badge counts) into one 1024x1024 texture so the whole 3D scene
 * draws with a single texture bind. Capped at 64 icons; callers choose which apps get a slot.
 */
object AtlasBuilder {
    fun build(
        apps: List<AppInfo>, repo: AppRepository, style: IconStyle,
        tileColor: Int, badges: Map<String, Int>,
    ): Bitmap {
        val bmp = Bitmap.createBitmap(ATLAS_GRID * ATLAS_CELL, ATLAS_GRID * ATLAS_CELL, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val tile = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tileColor }
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(40, 0, 0, 0) }
        val ip = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val red = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF3B30") }
        val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = 26f; typeface = Typeface.DEFAULT_BOLD }
        val pad = 8f
        for ((i, a) in apps.take(ATLAS_MAX).withIndex()) {
            val x = (i % ATLAS_GRID) * ATLAS_CELL.toFloat(); val y = (i / ATLAS_GRID) * ATLAS_CELL.toFloat()
            val box = RectF(x + pad, y + pad, x + ATLAS_CELL - pad, y + ATLAS_CELL - pad)
            val rad = when (style) {
                IconStyle.CIRCLE -> box.width() / 2f; IconStyle.SQUARE -> 6f; IconStyle.SQUIRCLE -> box.width() * 0.36f
                else -> box.width() * 0.27f
            }
            val bare = style == IconStyle.MINIMAL || style == IconStyle.TRANSPARENT
            if (!bare) {
                c.drawRoundRect(RectF(box.left + 3, box.top + 5, box.right + 3, box.bottom + 5), rad, rad, shadow)
                c.drawRoundRect(box, rad, rad, tile)
            }
            val inner = if (bare) 0f else box.width() * 0.16f
            val icon = repo.icon(a, 192).asAndroidBitmap()
            c.drawBitmap(icon, null, RectF(box.left + inner, box.top + inner, box.right - inner, box.bottom - inner), ip)
            val n = badges[a.pkg] ?: 0
            if (n > 0) {
                val cx = box.right - 14f; val cy = box.top + 14f
                c.drawCircle(cx, cy, 15f, red)
                c.drawText(if (n > 99) "99+" else "$n", cx, cy + 9f, txt.apply { textSize = if (n > 99) 17f else 24f })
            }
        }
        return bmp
    }
}
