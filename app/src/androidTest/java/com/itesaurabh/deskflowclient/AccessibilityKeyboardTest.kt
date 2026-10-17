// Modified by nmdft on 2026-10-17; see NOTICE.md for fork changes and license.
package com.itesaurabh.deskflowclient

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.provider.Settings
import android.text.InputType
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class AccessibilityKeyboardTest {
    @Test fun editingThroughProductionServiceWithOriginalIme() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val ime=Settings.Secure.getString(context.contentResolver,Settings.Secure.DEFAULT_INPUT_METHOD)
        assertFalse(ime.orEmpty().endsWith("DeskflowIme"))
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
        var captured=false
        lateinit var field:EditText
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                previousClip=activity.getSystemService(ClipboardManager::class.java).primaryClip;captured=true
                field=EditText(activity).apply { inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE }
                activity.setContentView(field);field.requestFocus()
                activity.getSystemService(InputMethodManager::class.java).showSoftInput(field,0)
            }
            try {
                var connected=false
                repeat(30) {
                    instrumentation.runOnMainSync { connected=service.inputMethod?.currentInputConnection!=null }
                    if(!connected) Thread.sleep(200)
                }
                assertTrue("No accessibility editor connection",connected)
                fun tap(id:Int,mask:Int=0) {
                    service.keyDown(id,mask,id);service.keyUp(id,mask,id);Thread.sleep(160)
                }
                fun text():String { var result="";scenario.onActivity { result=field.text.toString() };return result }
                service.enter(10,10);Thread.sleep(200)
                for(ch in "hello") tap(ch.code)
                assertEquals("hello",text())
                tap(KeyId.BACKSPACE);assertEquals("hell",text())
                tap('a'.code,KeyModifier.SUPER);tap('c'.code,KeyModifier.SUPER)
                scenario.onActivity { assertEquals("hell",it.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text.toString()) }
                tap('x'.code,KeyModifier.CONTROL);assertEquals("",text())
                tap('v'.code,KeyModifier.CONTROL);assertEquals("hell",text())
                tap(KeyId.LEFT,KeyModifier.SHIFT)
                scenario.onActivity { assertEquals(4,field.selectionStart);assertEquals(3,field.selectionEnd) }
                tap('a'.code,KeyModifier.CONTROL);tap(KeyId.BACKSPACE)
                for(ch in "你好1") tap(ch.code)
                assertEquals("你好1",text())
                service.clipboard("Mac中文😀\n剪贴板");Thread.sleep(200)
                tap('a'.code,KeyModifier.CONTROL);tap('v'.code,KeyModifier.CONTROL)
                assertEquals("Mac中文😀\n剪贴板",text())
                service.leave();Thread.sleep(200);tap('x'.code)
                assertEquals("Mac中文😀\n剪贴板",text())
                service.enter(10,10);Thread.sleep(100)
                service.keyRepeat('z'.code,0,99,3);Thread.sleep(400)
                assertEquals("Mac中文😀\n剪贴板zzz",text())
                assertEquals(ime,Settings.Secure.getString(context.contentResolver,Settings.Secure.DEFAULT_INPUT_METHOD))
            } finally {
                service.leave()
                instrumentation.runOnMainSync {
                    if(captured) {
                        val manager=context.getSystemService(ClipboardManager::class.java)
                        previousClip?.let { manager.setPrimaryClip(it) } ?: manager.clearPrimaryClip()
                    }
                }
            }
        }
    }
}
