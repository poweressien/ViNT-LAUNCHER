package com.bluemimicry.launcher.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bluemimicry.launcher.ui.theme.LocalPalette

@Composable
fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, display: (Float) -> String = { "%.2f".format(it) }) {
    val pal = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row { Text(label, color = pal.text, fontSize = 13.sp, modifier = Modifier.weight(1f)); Text(display(value), color = pal.subtext, fontSize = 12.sp) }
        Slider(value, onChange, valueRange = range)
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, hint: String? = null, onChange: (Boolean) -> Unit) {
    val pal = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = pal.text, fontSize = 14.sp)
            if (hint != null) Text(hint, color = pal.subtext, fontSize = 11.sp)
        }
        Switch(checked, onChange)
    }
}

@Composable
fun <T> ChipRow(label: String, options: List<T>, selected: T, name: (T) -> String, onPick: (T) -> Unit) {
    val pal = LocalPalette.current
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, color = pal.subtext, fontSize = 12.sp)
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(options.size) { i ->
                val o = options[i]
                FilterChip(selected = o == selected, onClick = { onPick(o) }, label = { Text(name(o)) })
            }
        }
    }
}

@Composable
fun DropdownRow(label: String, current: String, options: List<Pair<String, String>>, onPick: (String) -> Unit) {
    val pal = LocalPalette.current
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = pal.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Box {
            Text(
                options.firstOrNull { it.first == current }?.second ?: current, color = pal.accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                modifier = Modifier.clickable { open = true }.background(pal.surface, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
            )
            DropdownMenu(open, { open = false }) {
                options.forEach { (k, v) -> DropdownMenuItem(text = { Text(v) }, onClick = { onPick(k); open = false }) }
            }
        }
    }
}

@Composable
fun ActionButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val pal = LocalPalette.current
    Text(
        text, color = pal.accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
        modifier = modifier.padding(vertical = 4.dp).background(pal.surface, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
fun Section(title: String) {
    Text(title, color = LocalPalette.current.text, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
}
