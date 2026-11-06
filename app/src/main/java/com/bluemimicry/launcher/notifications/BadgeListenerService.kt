package com.bluemimicry.launcher.notifications

import android.app.Notification
import android.app.PendingIntent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class NotificationSummary(val pkg: String, val title: String, val text: String, val postedAt: Long)

data class NotifItem(
    val key: String, val pkg: String, val title: String, val text: String, val time: Long,
    val intent: PendingIntent?, val actions: List<Pair<String, PendingIntent?>>, val clearable: Boolean, val group: String?,
)

/** In-memory only. Notification content is never written to disk or sent anywhere. */
object BadgeStore {
    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val counts: StateFlow<Map<String, Int>> = _counts
    private val _latest = MutableStateFlow<NotificationSummary?>(null)
    val latest: StateFlow<NotificationSummary?> = _latest
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected
    private val _active = MutableStateFlow<List<NotifItem>>(emptyList())
    val active: StateFlow<List<NotifItem>> = _active
    private val _history = MutableStateFlow<List<NotifItem>>(emptyList())
    /** Last 100 notifications seen this process lifetime (cleared when the process dies). */
    val history: StateFlow<List<NotifItem>> = _history

    @Volatile internal var service: BadgeListenerService? = null

    internal fun set(counts: Map<String, Int>) { _counts.value = counts }
    internal fun setLatest(n: NotificationSummary?) { _latest.value = n }
    internal fun setConnected(v: Boolean) { _connected.value = v }
    internal fun setActive(l: List<NotifItem>) { _active.value = l }
    internal fun addHistory(i: NotifItem) { _history.value = (listOf(i) + _history.value.filter { it.key != i.key }).take(100) }

    fun dismiss(key: String) { service?.cancelNotification(key) }
    fun dismissAll() { service?.cancelAllNotifications() }
}

/** Real badges + notification cards. Active only after the user grants Notification Access in system settings. */
class BadgeListenerService : NotificationListenerService() {

    override fun onListenerConnected() { BadgeStore.service = this; BadgeStore.setConnected(true); recount() }
    override fun onListenerDisconnected() { BadgeStore.service = null; BadgeStore.setConnected(false); BadgeStore.set(emptyMap()); BadgeStore.setActive(emptyList()) }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        recount()
        if (sbn.isOngoing) return
        val item = toItem(sbn)
        BadgeStore.addHistory(item)
        BadgeStore.setLatest(NotificationSummary(sbn.packageName, item.title, item.text, sbn.postTime))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) = recount()

    private fun toItem(s: StatusBarNotification): NotifItem {
        val e = s.notification.extras
        return NotifItem(
            key = s.key, pkg = s.packageName,
            title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            text = (e.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: e.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty(),
            time = s.postTime, intent = s.notification.contentIntent,
            actions = s.notification.actions?.map { (it.title?.toString() ?: "") to it.actionIntent } ?: emptyList(),
            clearable = s.isClearable, group = s.notification.group,
        )
    }

    private fun recount() {
        val active = try { activeNotifications } catch (_: Exception) { null } ?: return
        val shown = active.filter { !it.isOngoing && (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 }
        BadgeStore.set(shown.groupingBy { it.packageName }.eachCount())
        BadgeStore.setActive(shown.sortedByDescending { it.postTime }.map(::toItem))
    }
}
