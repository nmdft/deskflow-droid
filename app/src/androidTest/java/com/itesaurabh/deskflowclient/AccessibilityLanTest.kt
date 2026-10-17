// Modified by nmdft on 2026-10-17; see NOTICE.md for fork changes and license.
package com.itesaurabh.deskflowclient

import android.content.ClipData
import android.content.ClipboardManager
import android.provider.Settings
import android.text.InputType
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Exercises the production service over direct Wi-Fi to the Mac fixture, not adb forwarding. */
class AccessibilityLanTest {
    @Test fun productionWifiKeyboardAndClipboard() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val args=InstrumentationRegistry.getArguments()
        val ime=Settings.Secure.getString(context.contentResolver,Settings.Secure.DEFAULT_INPUT_METHOD)
        Thread.sleep(6000)
        var access:InjectorService?=null
        repeat(30) {
            if(access==null) {
                instrumentation.runOnMainSync { access=InjectorService.instance }
                if(access==null) Thread.sleep(200)
            }
        }
        val service=requireNotNull(access)
        var previousClip:ClipData?=null
        lateinit var field:EditText
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                previousClip=activity.getSystemService(ClipboardManager::class.java).primaryClip
                field=EditText(activity).apply { inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE }
                activity.setContentView(field);field.requestFocus()
                activity.getSystemService(InputMethodManager::class.java).showSoftInput(field,0)
            }
            try {
                Thread.sleep(700)
                instrumentation.runOnMainSync {
                    service.connect(requireNotNull(args.getString("fixture_host")),
                        requireNotNull(args.getString("fixture_port")).toInt(),"lan-test",false)
                }
                var result=""
                repeat(60) {
                    Thread.sleep(200)
                    scenario.onActivity { result=field.text.toString() }
                    if(result=="wifi你好") return@repeat
                }
                Thread.sleep(500)
                scenario.onActivity {
                    assertEquals("wifi你好",field.text.toString())
                    assertEquals("wifi你好",it.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text.toString())
                }
                assertEquals(ime,Settings.Secure.getString(context.contentResolver,Settings.Secure.DEFAULT_INPUT_METHOD))
            } finally {
                instrumentation.runOnMainSync {
                    service.disconnect();service.leave()
                    val manager=context.getSystemService(ClipboardManager::class.java)
                    previousClip?.let { manager.setPrimaryClip(it) } ?: manager.clearPrimaryClip()
                }
            }
        }
    }
}
