// Modified by nmdft on 2026-10-17; see NOTICE.md for fork changes and license.
package com.itesaurabh.deskflowclient

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.os.PowerManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Direct LAN fixture sends heartbeats then CINN while the phone is screen-off. */
class MouseWakeLanTest {
    @Test fun screenOffWifiConnectionReceivesEntryAndWakes() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val args=InstrumentationRegistry.getArguments()
        val power=context.getSystemService(PowerManager::class.java)
        val keyguard=context.getSystemService(KeyguardManager::class.java)
        Thread.sleep(6000)
        var access:InjectorService?=null
        repeat(30) {
            if(access==null) {
                instrumentation.runOnMainSync { access=InjectorService.instance }
                if(access==null) Thread.sleep(200)
            }
        }
        val service=requireNotNull(access)
        try {
            instrumentation.runOnMainSync {
                service.connect(requireNotNull(args.getString("fixture_host")),
                    requireNotNull(args.getString("fixture_port")).toInt(),"wake-lan",false)
            }
            var connected=false
            for(i in 0 until 60) {
                Thread.sleep(100)
                instrumentation.runOnMainSync { connected=ClientState.phase==ClientState.Phase.Connected }
                if(connected) break
            }
            assertTrue("LAN fixture did not connect",connected)
            var didLock=false
            instrumentation.runOnMainSync { didLock=service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) }
            assertTrue(didLock)
            for(i in 0 until 30) { if(!power.isInteractive) break;Thread.sleep(100) }
            assertFalse(power.isInteractive)
            val locked=keyguard.isKeyguardLocked
            Thread.sleep(4500)
            assertFalse("No wake before enter event",power.isInteractive)
            for(i in 0 until 80) { if(power.isInteractive) break;Thread.sleep(100) }
            assertTrue("LAN mouse-enter event did not wake screen",power.isInteractive)
            if(locked) assertTrue("Wake must retain keyguard",keyguard.isKeyguardLocked)
            Thread.sleep(1700)
        } finally { instrumentation.runOnMainSync { service.disconnect();service.leave() } }
    }
}
