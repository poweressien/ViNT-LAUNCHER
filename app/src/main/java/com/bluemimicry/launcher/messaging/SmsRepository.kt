package com.bluemimicry.launcher.messaging

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Conversation(val threadId: Long, val address: String, val name: String, val snippet: String, val date: Long, val unread: Int, val pinned: Boolean)
data class Message(val id: Long, val body: String, val date: Long, val outgoing: Boolean)

/** Thin wrapper over the Telephony provider. Everything here needs the runtime SMS permissions to be granted. */
class SmsRepository(private val ctx: Context) {
    private val pins get() = ctx.getSharedPreferences("sms_pins", Context.MODE_PRIVATE)

    fun hasRead() = granted(Manifest.permission.READ_SMS)
    fun hasSend() = granted(Manifest.permission.SEND_SMS)
    fun hasContacts() = granted(Manifest.permission.READ_CONTACTS)
    private fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    fun isDefaultSmsApp() = Telephony.Sms.getDefaultSmsPackage(ctx) == ctx.packageName

    fun togglePin(threadId: Long) {
        val s = pins.getStringSet("p", emptySet())!!.toMutableSet()
        if (!s.add(threadId.toString())) s.remove(threadId.toString())
        pins.edit().putStringSet("p", s).apply()
    }

    private fun contactName(address: String): String? {
        if (!hasContacts() || address.isBlank()) return null
        return runCatching {
            ctx.contentResolver.query(Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address)), arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull()
    }

    suspend fun conversations(): List<Conversation> = withContext(Dispatchers.IO) {
        if (!hasRead()) return@withContext emptyList()
        val pinned = pins.getStringSet("p", emptySet())!!
        val seen = LinkedHashMap<Long, Conversation>(); val unread = HashMap<Long, Int>()
        runCatching {
            ctx.contentResolver.query(
                Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.READ, Telephony.Sms.TYPE),
                null, null, "${Telephony.Sms.DATE} DESC",
            )?.use { c ->
                val names = HashMap<String, String?>()
                while (c.moveToNext()) {
                    val tid = c.getLong(0); val addr = c.getString(1).orEmpty()
                    if (c.getInt(4) == 0 && c.getInt(5) == Telephony.Sms.MESSAGE_TYPE_INBOX) unread[tid] = (unread[tid] ?: 0) + 1
                    if (tid !in seen) seen[tid] = Conversation(tid, addr, names.getOrPut(addr) { contactName(addr) } ?: addr, c.getString(2).orEmpty(), c.getLong(3), 0, tid.toString() in pinned)
                }
            }
        }
        seen.values.map { it.copy(unread = unread[it.threadId] ?: 0) }.sortedWith(compareByDescending<Conversation> { it.pinned }.thenByDescending { it.date })
    }

    suspend fun messages(threadId: Long): List<Message> = withContext(Dispatchers.IO) {
        if (!hasRead()) return@withContext emptyList()
        val out = ArrayList<Message>()
        runCatching {
            ctx.contentResolver.query(
                Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms._ID, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE),
                "${Telephony.Sms.THREAD_ID} = ?", arrayOf(threadId.toString()), "${Telephony.Sms.DATE} ASC",
            )?.use { c -> while (c.moveToNext()) out += Message(c.getLong(0), c.getString(1).orEmpty(), c.getLong(2), c.getInt(3) != Telephony.Sms.MESSAGE_TYPE_INBOX) }
            markRead(threadId)
        }
        out
    }

    private fun markRead(threadId: Long) {
        if (!isDefaultSmsApp()) return // only the default SMS app may modify the provider
        runCatching {
            ctx.contentResolver.update(Telephony.Sms.CONTENT_URI, ContentValues().apply { put(Telephony.Sms.READ, 1) }, "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0", arrayOf(threadId.toString()))
        }
    }

    /** Sends through the platform SmsManager. When we are the default SMS app we also record it in the provider. */
    suspend fun send(address: String, body: String): Boolean = withContext(Dispatchers.IO) {
        if (!hasSend() || address.isBlank() || body.isBlank()) return@withContext false
        runCatching {
            val sm = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java) else @Suppress("DEPRECATION") SmsManager.getDefault()
            val parts = sm.divideMessage(body)
            if (parts.size > 1) sm.sendMultipartTextMessage(address, null, parts, null, null) else sm.sendTextMessage(address, null, body, null, null)
            if (isDefaultSmsApp()) ctx.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address); put(Telephony.Sms.BODY, body); put(Telephony.Sms.DATE, System.currentTimeMillis()); put(Telephony.Sms.READ, 1)
            })
            true
        }.getOrDefault(false)
    }
}
