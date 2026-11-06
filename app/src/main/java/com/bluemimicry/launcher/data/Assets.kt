package com.bluemimicry.launcher.data

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Typeface as ComposeTypeface
import java.io.File

object Assets {
    /** Copies a picked document into private storage so it survives permission loss. Returns the file path. */
    fun import(ctx: Context, uri: Uri, subdir: String, name: String): String? = runCatching {
        val dir = File(ctx.filesDir, subdir).apply { mkdirs() }
        val out = File(dir, name)
        ctx.contentResolver.openInputStream(uri)?.use { i -> out.outputStream().use { o -> i.copyTo(o) } } ?: return null
        out.absolutePath
    }.getOrNull()

    fun fontFamily(name: String, customPath: String?): FontFamily {
        if (name == "Custom" && customPath != null) {
            runCatching { return FontFamily(ComposeTypeface(Typeface.createFromFile(customPath))) }
        }
        return when (name) {
            "Serif" -> FontFamily.Serif
            "Mono" -> FontFamily.Monospace
            "Cursive" -> FontFamily.Cursive
            "Condensed" -> FontFamily(ComposeTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL)))
            "Light" -> FontFamily(ComposeTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL)))
            "Medium" -> FontFamily(ComposeTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)))
            else -> FontFamily.SansSerif
        }
    }

    val builtInFonts = listOf("Sans", "Light", "Medium", "Condensed", "Serif", "Mono", "Cursive")
}
