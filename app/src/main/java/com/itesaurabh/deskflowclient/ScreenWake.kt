// Modified by nmdft on 2026-10-17; see NOTICE.md for fork changes and license.
package com.itesaurabh.deskflowclient

import android.content.Context
import android.os.PowerManager

/** A short screen wake on entry, not a permanent CPU/network/screen keep-alive. */
class ScreenWake(context: Context) {
    private val prefs = context.getSharedPreferences("deskflow", Context.MODE_PRIVATE)
    private val power = context.getSystemService(PowerManager::class.java)
    @Suppress("DEPRECATION") // Supported on the target K40/Android13; no foreground activity takeover.
    private val lock = power.newWakeLock(
        PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
        "Deskflow:mouse-entry"
    ).apply { setReferenceCounted(false) }

    fun onMouseEnter() {
        if (!prefs.getBoolean("wake_on_mouse_entry", true) || power.isInteractive) return
        try {
            lock.acquire(1500) // Automatically releases, including when the process is interrupted.
            Logs.add("Mouse entered: requested screen wake")
        } catch (e: SecurityException) {
            Logs.warn("Screen wake denied by system: ${e.message}")
        }
    }

    fun close() { if (lock.isHeld) lock.release() }
}
