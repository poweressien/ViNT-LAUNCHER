package com.bluemimicry.launcher.ui.components

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Four-colour ring mark drawn with canvas arcs (no third-party logo asset is bundled). */
@Composable
private fun RingMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = Stroke(width = size.minDimension * 0.22f, cap = StrokeCap.Butt)
        val inset = s.width / 2
        val sz = Size(size.width - s.width, size.height - s.width)
        val o = Offset(inset, inset)
        drawArc(Color(0xFFEA4335), -40f, -80f, false, o, sz, style = s)
        drawArc(Color(0xFFFBBC05), -120f, -70f, false, o, sz, style = s)
        drawArc(Color(0xFF34A853), 170f, -90f, false, o, sz, style = s)
        drawArc(Color(0xFF4285F4), 80f, -100f, false, o, sz, style = s)
    }
}

/** Wide dark pill search bar. Tap opens the launcher's universal search; the mic starts a real voice web-search. */
@Composable
fun WideSearchBar(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    Row(
        modifier.height(60.dp).background(Color(0xFF3C3F44), RoundedCornerShape(30.dp)).clickable(onClick = onOpen).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RingMark(Modifier.size(28.dp))
        Spacer(Modifier.weight(1f))
        Icon(Icons.Filled.Search, "Search", tint = Color(0xFFE8EAED), modifier = Modifier.size(26.dp).clickable(onClick = onOpen))
        Spacer(Modifier.width(22.dp))
        Icon(Icons.Filled.Mic, "Voice search", tint = Color(0xFFE8EAED), modifier = Modifier.size(26.dp).clickable {
            val i = Intent(RecognizerIntent.ACTION_WEB_SEARCH).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { ctx.startActivity(i) }
        })
    }
}

/** Small floating "Search" pill that sits above the dock. */
@Composable
fun SearchPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.background(Color(0xFF55585D), RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Search", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
