// Modified by nmdft on 2026-10-17; see NOTICE.md for fork changes and license.
package com.itesaurabh.deskflowclient

import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.PowerManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Uses isolated preferences so the user's saved connection and switch are unchanged. */
class ScreenWakeSettingsTest {
    @Test fun defaultEnabledAndSavedToggleAppliesWithoutRecreatingWakeHandler() {
        Thread.sleep(6000) // Allow the device harness to foreground the app on MIUI.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        val prefs = target.getSharedPreferences("wake-settings-test", Context.MODE_PRIVATE)
        val context = object : ContextWrapper(target) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = prefs
        }
        val power = target.getSystemService(PowerManager::class.java)
        val keyguard = target.getSystemService(KeyguardManager::class.java)
        val wake = ScreenWake(context)
        fun sleepScreen() {
            instrumentation.uiAutomation.executeShellCommand("input keyevent 223").close()
            repeat(30) { if (power.isInteractive) Thread.sleep(100) }
            assertFalse("Screen must be off before mouse entry", power.isInteractive)
            Thread.sleep(600) // Let the display transition finish before requesting a wake.
        }
        fun assertWakes(handler: ScreenWake) {
            val wasLocked = keyguard.isKeyguardLocked
            handler.onMouseEnter()
            repeat(30) { if (!power.isInteractive) Thread.sleep(100) }
            assertTrue("Enabled mouse entry must wake screen", power.isInteractive)
            if (wasLocked) assertTrue("Must preserve keyguard", keyguard.isKeyguardLocked)
            handler.close()
        }
        try {
            prefs.edit().clear().commit()
            sleepScreen()
            assertWakes(wake)
            prefs.edit().putBoolean("wake_on_mouse_entry", false).commit()
            sleepScreen()
            wake.onMouseEnter()
            Thread.sleep(1800)
            assertFalse("Disabled mouse entry must not wake screen", power.isInteractive)
            ScreenWake(context).also {
                try {
                    it.onMouseEnter()
                    Thread.sleep(300)
                    assertFalse("Saved off choice must survive a new handler", power.isInteractive)
                } finally { it.close() }
            }
            prefs.edit().putBoolean("wake_on_mouse_entry", true).commit()
            assertWakes(wake)
        } finally {
            wake.close()
            prefs.edit().clear().commit()
        }
    }
}
