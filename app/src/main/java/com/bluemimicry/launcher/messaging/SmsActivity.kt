package com.bluemimicry.launcher.messaging

import android.Manifest
import android.app.role.RoleManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.rememberCoroutineScope
import com.bluemimicry.launcher.LauncherViewModel
import com.bluemimicry.launcher.ui.theme.BmTheme
import com.bluemimicry.launcher.ui.theme.LocalPalette
import com.bluemimicry.launcher.ui.theme.neuRaised
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** SMS companion with a messaging theme that follows the launcher theme. Plain SMS only (no MMS). */
class SmsActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = SmsRepository(this)
        // sms:/smsto: deep links open a compose screen for that number
        val startAddress = intent?.data?.takeIf { it.scheme == "sms" || it.scheme == "smsto" }?.schemeSpecificPart?.substringBefore('?')
        setContent {
            val s by vm.settings.collectAsState()
            BmTheme(s.theme, s.gradientTop.let { Color(it) }, Color(s.gradientBottom), s.shadowIntensity) { SmsApp(repo, startAddress) }
        }
    }
}

@Composable
private fun SmsApp(repo: SmsRepository, startAddress: String?) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val pal = LocalPalette.current
    val scope = rememberCoroutineScope()
    var perms by remember { mutableStateOf(repo.hasRead() && repo.hasSend()) }
    var convos by remember { mutableStateOf<List<Conversation>>(emptyList()) }
    var open by remember { mutableStateOf<Conversation?>(null) }
    var compose by remember { mutableStateOf(startAddress) }
    var query by remember { mutableStateOf("") }
    var unreadOnly by remember { mutableStateOf(false) }
    var isDefault by remember { mutableStateOf(repo.isDefaultSmsApp()) }

    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms = repo.hasRead() && repo.hasSend() }
    val role = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { isDefault = repo.isDefaultSmsApp() }
    fun reload() = scope.launch { convos = repo.conversations() }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, perms) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) { isDefault = repo.isDefaultSmsApp(); if (perms) reload() } }
        owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) }
    }
    BackHandler(open != null || compose != null) { open = null; compose = null; reload() }

    Column(Modifier.fillMaxSize().background(pal.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        when {
            !perms -> Column(Modifier.padding(24.dp)) {
                Text("Messages", color = pal.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("To show and send SMS, Android needs your permission to read and send text messages. Contacts are optional and only used to show names.", color = pal.subtext, modifier = Modifier.padding(vertical = 12.dp))
                TextButton({ ask.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_CONTACTS)) }) { Text("Allow") }
            }
            open != null || compose != null -> ThreadScreen(repo, open, compose, pal.text.let { pal }, onBack = { open = null; compose = null; reload() })
            else -> {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Messages", color = pal.text, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton({ compose = "" }) { Text("New") }
                }
                if (!isDefault && Build.VERSION.SDK_INT >= 29) Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Make this your default SMS app to receive messages here and mark them read.", color = pal.subtext, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    TextButton({ ctx.getSystemService(RoleManager::class.java).let { if (it.isRoleAvailable(RoleManager.ROLE_SMS)) role.launch(it.createRequestRoleIntent(RoleManager.ROLE_SMS)) } }) { Text("Set") }
                }
                OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text("Search messages and people") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { FilterChip(unreadOnly, { unreadOnly = !unreadOnly }, { Text("Unread") }) }
                val shown = remember(convos, query, unreadOnly) { convos.filter { (!unreadOnly || it.unread > 0) && (query.isBlank() || it.name.contains(query, true) || it.snippet.contains(query, true) || it.address.contains(query)) } }
                LazyColumn(Modifier.weight(1f)) {
                    items(shown, key = { it.threadId }) { c ->
                        Row(
                            Modifier.fillMaxWidth().clickable { open = c }.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text((if (c.pinned) "📌 " else "") + c.name, color = pal.text, fontWeight = if (c.unread > 0) FontWeight.Bold else FontWeight.Medium, fontSize = 16.sp, maxLines = 1)
                                Text(c.snippet, color = pal.subtext, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (c.unread > 0) com.bluemimicry.launcher.ui.components.Badge(c.unread)
                            Text(if (c.pinned) "Unpin" else "Pin", color = pal.accent, fontSize = 12.sp, modifier = Modifier.clickable { repo.togglePin(c.threadId); reload() }.padding(start = 12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreadScreen(repo: SmsRepository, convo: Conversation?, newAddress: String?, pal: com.bluemimicry.launcher.ui.theme.Palette, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf(convo?.address ?: newAddress.orEmpty()) }
    var msgs by remember { mutableStateOf<List<Message>>(emptyList()) }
    var text by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    val df = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    fun load() = scope.launch { convo?.let { msgs = repo.messages(it.threadId) } }
    LaunchedEffect(convo) { load() }
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) list.scrollToItem(msgs.lastIndex) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onBack) { Text("‹ Back") }
            if (convo != null) Text(convo.name, color = pal.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            else OutlinedTextField(address, { address = it }, singleLine = true, placeholder = { Text("Phone number") }, modifier = Modifier.weight(1f))
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp), state = list, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(msgs, key = { it.id }) { m ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.outgoing) Arrangement.End else Arrangement.Start) {
                    Column(
                        Modifier.widthIn(max = 280.dp).background(if (m.outgoing) pal.accent else pal.surface, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(m.body, color = if (m.outgoing) Color.White else pal.text, fontSize = 15.sp)
                        Text(df.format(Date(m.date)), color = (if (m.outgoing) Color.White else pal.subtext).copy(alpha = 0.7f), fontSize = 10.sp)
                    }
                }
            }
        }
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(text, { text = it }, placeholder = { Text("Message") }, modifier = Modifier.weight(1f), maxLines = 4)
            TextButton({
                val t = text
                scope.launch { if (repo.send(address, t)) { text = ""; if (convo != null) { kotlinx.coroutines.delay(400); load() } } }
            }, enabled = text.isNotBlank() && address.isNotBlank()) { Text("Send") }
        }
    }
}
