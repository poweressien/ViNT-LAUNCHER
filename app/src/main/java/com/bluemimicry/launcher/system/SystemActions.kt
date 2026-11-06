package com.bluemimicry.launcher.system

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast

/** Every function here uses a supported public API or settings intent. Nothing silently changes protected settings. */
object SystemActions {

    private fun start(ctx: Context, i: Intent) {
        runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Toast.makeText(ctx, "Not available on this device", Toast.LENGTH_SHORT).show() }
    }

    fun wifi(ctx: Context) =
        start(ctx, Intent(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS))

    fun internet(ctx: Context) =
        start(ctx, Intent(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIRELESS_SETTINGS))

    fun bluetooth(ctx: Context) = start(ctx, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    fun airplane(ctx: Context) = start(ctx, Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
    fun display(ctx: Context) = start(ctx, Intent(Settings.ACTION_DISPLAY_SETTINGS))
    fun sound(ctx: Context) = start(ctx, Intent(Settings.ACTION_SOUND_SETTINGS))
    fun notificationAccess(ctx: Context) = start(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    fun usageAccess(ctx: Context) = start(ctx, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    fun accessibility(ctx: Context) = start(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    fun liveWallpaperPicker(ctx: Context) = start(ctx, Intent(android.app.WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
    fun defaultHomeSettings(ctx: Context) = start(ctx, Intent(Settings.ACTION_HOME_SETTINGS))
    fun batteryOptimisation(ctx: Context) = start(ctx, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    fun appDetails(ctx: Context) = start(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))

    /** Real torch toggle; needs no permission on any API level. Returns the new state, or null if unsupported. */
    private var torchOn = false
    fun toggleTorch(ctx: Context): Boolean? = try {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        if (id == null) null else { torchOn = !torchOn; cm.setTorchMode(id, torchOn); torchOn }
    } catch (_: Exception) { torchOn = false; null }

    fun volume(ctx: Context): Pair<Int, Int> {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return am.getStreamVolume(AudioManager.STREAM_MUSIC) to am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    }
    fun setVolume(ctx: Context, v: Int) {
        (ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager).setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
    }

    /** DND needs Notification-Policy access. If missing we send the user to grant it instead of failing silently. */
    fun toggleDnd(ctx: Context): Boolean? {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) { start(ctx, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)); return null }
        val on = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        return !on
    }
    fun dndOn(ctx: Context): Boolean =
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL

    /** Auto-rotate is a protected system setting: needs WRITE_SETTINGS. Without it we open Display settings. */
    fun toggleRotation(ctx: Context): Boolean? {
        if (!Settings.System.canWrite(ctx)) {
            start(ctx, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}"))); return null
        }
        val now = Settings.System.getInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0)
        Settings.System.putInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (now == 1) 0 else 1)
        return now != 1
    }

    /** Screen recording has no public API. Try the common OEM recorders, otherwise say so honestly. */
    fun screenRecord(ctx: Context) {
        val candidates = listOf(
            "com.miui.screenrecorder", "com.samsung.android.app.smartcapture", "com.google.android.apps.nbu.paisa.user.screenrecord",
        )
        val i = candidates.firstNotNullOfOrNull { ctx.packageManager.getLaunchIntentForPackage(it) }
        if (i != null) start(ctx, i) else Toast.makeText(ctx, "No screen recorder found. Use your device's quick settings.", Toast.LENGTH_LONG).show()
    }

    /** Pull down the real system notification shade (normal permission EXPAND_STATUS_BAR, reflection-based). */
    @Suppress("WrongConstant")
    fun expandSystemShade(ctx: Context, quickSettings: Boolean = false) {
        runCatching {
            val sb = ctx.getSystemService("statusbar")
            val cls = Class.forName("android.app.StatusBarManager")
            val name = if (quickSettings && Build.VERSION.SDK_INT >= 17) "expandSettingsPanel" else "expandNotificationsPanel"
            cls.getMethod(name).invoke(sb)
        }.onFailure { Toast.makeText(ctx, "This device blocks opening the system shade", Toast.LENGTH_SHORT).show() }
    }

    fun lockScreen(ctx: Context) {
        if (!LockService.lock()) {
            Toast.makeText(ctx, "Enable the Blue Mimicry lock service in Accessibility first", Toast.LENGTH_LONG).show()
            accessibility(ctx)
        }
    }
}
