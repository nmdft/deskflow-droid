// Modified by nmdft on 2026-10-17; see NOTICE.md for fork changes and license.
package com.itesaurabh.deskflowclient

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Capability probe only. Does not select our IME or change the shipping keyboard route.
 * Before running: unlock phone, select the old IME in Chinese mode, keep screen on.
 */
class AccessibilityImeProbeTest {
    @Test fun oldImeCompositionThroughAccessibility() {
        assumeTrue("Accessibility input connection requires Android 13", Build.VERSION.SDK_INT >= 33)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val oldIme = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        assertFalse("Select Doubao/Sogou, not Deskflow IME", oldIme.orEmpty().endsWith("DeskflowIme"))
        Thread.sleep(6000) // MIUI foreground launcher and accessibility rebind during this pause.
        var service: InjectorService? = null
        repeat(30) {
            if (service == null) {
                instrumentation.runOnMainSync { service = InjectorService.instance }
                if (service == null) Thread.sleep(200)
            }
        }
        val access = requireNotNull(service) { "Enable and bind Deskflow accessibility service" }
        var oldFlags = 0
        var previousClip: ClipData? = null
        var clipboardCaptured = false
        instrumentation.runOnMainSync {
            val info = access.serviceInfo
            oldFlags = info.flags
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_INPUT_METHOD_EDITOR
            access.serviceInfo = info
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var field: EditText
                scenario.onActivity { activity ->
                    activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    previousClip = activity.getSystemService(ClipboardManager::class.java).primaryClip
                    clipboardCaptured = true
                    field = EditText(activity).apply {
                        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    }
                    activity.setContentView(field)
                    field.requestFocus()
                    activity.getSystemService(InputMethodManager::class.java).showSoftInput(field, 0)
                }
                var connected = false
                repeat(30) {
                    instrumentation.runOnMainSync { connected = access.inputMethod?.currentInputConnection != null }
                    if (!connected) Thread.sleep(200)
                }
                assertTrue("Accessibility input connection did not bind", connected)
                fun send(action: (android.accessibilityservice.InputMethod.AccessibilityInputConnection) -> Unit) {
                    instrumentation.runOnMainSync { action(requireNotNull(access.inputMethod?.currentInputConnection)) }
                    Thread.sleep(180)
                }
                fun text(): String {
                    var value = ""
                    scenario.onActivity { value = field.text.toString() }
                    return value
                }
                // Direct editor operations may work even when old IME composition does not.
                send { it.commitText("probe", 1, null) }
                assertEquals("probe", text())
                android.util.Log.i("AccessibilityProbe", "Direct commitText passed; original IME retained")
                send { it.performContextMenuAction(android.R.id.selectAll) }
                send { it.performContextMenuAction(android.R.id.copy) }
                scenario.onActivity {
                    assertEquals("probe", it.getSystemService(ClipboardManager::class.java)
                        .primaryClip?.getItemAt(0)?.text?.toString())
                }
                android.util.Log.i("AccessibilityProbe", "Select-all and copy passed")
                send { it.performContextMenuAction(android.R.id.cut) }
                assertEquals("", text())
                send { it.performContextMenuAction(android.R.id.paste) }
                assertEquals("probe", text())
                send { it.performContextMenuAction(android.R.id.selectAll) }
                send { it.commitText("", 1, null) }
                assertEquals("", text())
                android.util.Log.i("AccessibilityProbe", "Cut and paste passed")
                // Use hardware-like events to test the strongest plausible interpretation,
                // not commitText, which cannot exercise the old IME's pinyin processing.
                for (event in requireNotNull(KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
                    .getEvents("nihao ".toCharArray()))) {
                    send {
                        val now = SystemClock.uptimeMillis()
                        it.sendKeyEvent(KeyEvent(now, now, event.action, event.keyCode, 0,
                            event.metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, event.scanCode,
                            0, android.view.InputDevice.SOURCE_KEYBOARD))
                    }
                }
                Thread.sleep(500)
                android.util.Log.i("AccessibilityProbe", "Generated nihao-space result: ${text()}")
                assertEquals("Probe must not change the old IME", oldIme,
                    Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD))
                assertEquals("Direct editing passed, but old IME did not compose nihao to 你好; " +
                    "confirm old IME Chinese mode before interpreting this failure", "你好", text())
            }
        } finally {
            instrumentation.runOnMainSync {
                val info = access.serviceInfo
                info.flags = oldFlags
                access.serviceInfo = info
                if (clipboardCaptured) {
                    val manager = context.getSystemService(ClipboardManager::class.java)
                    previousClip?.let { manager.setPrimaryClip(it) } ?: manager.clearPrimaryClip()
                }
            }
        }
    }
}
