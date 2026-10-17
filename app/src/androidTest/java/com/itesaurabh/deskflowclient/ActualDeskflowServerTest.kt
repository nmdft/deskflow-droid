// Modified by nmdft on 2026-10-17; see NOTICE.md for fork changes and license.
package com.itesaurabh.deskflowclient

import android.content.Context
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Connection compatibility only, not an interactive keyboard/clipboard acceptance test. */
class ActualDeskflowServerTest {
    @Test fun savedTlsConnectionToUnmodifiedMacServer() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val prefs=context.getSharedPreferences("deskflow",Context.MODE_PRIVATE)
        val ime=Settings.Secure.getString(context.contentResolver,Settings.Secure.DEFAULT_INPUT_METHOD)
        assertTrue("Existing TLS setting must be retained",prefs.getBoolean("tls",false))
        Thread.sleep(6000)
        var service:InjectorService?=null
        repeat(30) {
            if(service==null) {
                instrumentation.runOnMainSync { service=InjectorService.instance }
                if(service==null) Thread.sleep(200)
            }
        }
        val access=requireNotNull(service)
        try {
            instrumentation.runOnMainSync {
                access.connect(requireNotNull(prefs.getString("host",null)),
                    prefs.getString("port","24800")!!.toInt(),
                    requireNotNull(prefs.getString("name",null)),true)
            }
            var connected=false
            for(i in 0 until 100) {
                Thread.sleep(150)
                instrumentation.runOnMainSync { connected=ClientState.phase==ClientState.Phase.Connected }
                if(connected) break
            }
            assertTrue("Saved TLS connection to actual Mac server failed",connected)
            assertEquals(ime,Settings.Secure.getString(context.contentResolver,Settings.Secure.DEFAULT_INPUT_METHOD))
        } finally { instrumentation.runOnMainSync { access.disconnect() } }
    }
}
