package com.bluemimicry.launcher.data

import android.os.UserHandle

/** One launchable activity discovered on the device through LauncherApps. Never hard-coded. */
data class AppInfo(
    val pkg: String,
    val activity: String,
    val label: String,
    val installTime: Long,
    val isSystem: Boolean,
    val category: Int,
    val user: UserHandle,
) {
    /** Stable identity used by every display engine and every database table. */
    val key: String get() = "$pkg/$activity"
}
