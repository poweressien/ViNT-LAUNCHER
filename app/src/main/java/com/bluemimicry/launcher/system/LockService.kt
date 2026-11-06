package com.bluemimicry.launcher.system

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * The only supported non-device-admin way to lock the screen from a launcher (API 28+).
 * It reads no screen content: the config declares no event types and no content retrieval.
 * It exists solely so the "Lock screen" gesture can call GLOBAL_ACTION_LOCK_SCREEN.
 */
class LockService : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    companion object {
        @Volatile var instance: LockService? = null
        fun lock(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) ?: false
    }
}
