// Modified by nmdft on 2026-10-17; see NOTICE.md for fork changes and license.
package com.itesaurabh.deskflowclient

import android.view.InputDevice
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.view.inputmethod.InputMethodManager
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class PhysicalKeyboardTest {
    @Test fun actualAndroidHardwareEvents() {
        Thread.sleep(6000) // Launcher brings app to foreground on MIUI during this pause.
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val keyboard=PhysicalKeyboard(context)
        val events=CopyOnWriteArrayList<KeyEvent>()
        try {
            assertTrue("Run keyboard launcher first", keyboard.configured)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val view=View(activity).apply {
                        isFocusableInTouchMode=true
                        setOnKeyListener { _,_,event -> events.add(KeyEvent(event));true }
                    }
                    activity.setContentView(view);view.requestFocus()
                }
                keyboard.enter()
                Thread.sleep(1500)
                assertTrue("UHID device not registered", InputDevice.getDeviceIds().any {
                    InputDevice.getDevice(it)?.name?.startsWith("Deskflow Physical Keyboard") == true
                })
                fun tap(id:Int, mask:Int, button:Int) {
                    keyboard.key(id,mask,button,true);Thread.sleep(80)
                    keyboard.key(id,mask,button,false);Thread.sleep(80)
                }
                tap('a'.code,0,1)
                keyboard.key(0xEFE3,2,2,true)
                tap('c'.code,2,3)
                keyboard.key(0xEFE3,0,2,false)
                tap(0xEFBE,0,4)
                tap(0xEFB2,0,5)
                tap(0xEF8D,0x2000,6)
                keyboard.key('b'.code,0x2000,7,true)
                Thread.sleep(1000)
                keyboard.release()
                Thread.sleep(400)
                assertTrue("letter down",events.any { it.keyCode==KeyEvent.KEYCODE_A && it.action==KeyEvent.ACTION_DOWN })
                assertTrue("letter up",events.any { it.keyCode==KeyEvent.KEYCODE_A && it.action==KeyEvent.ACTION_UP })
                assertTrue("ctrl combo",events.any { it.keyCode==KeyEvent.KEYCODE_C && it.isCtrlPressed })
                assertTrue("F1",events.any { it.keyCode==KeyEvent.KEYCODE_F1 })
                assertTrue("keypad 2",events.any { it.keyCode==KeyEvent.KEYCODE_NUMPAD_2 })
                assertTrue("keypad enter",events.any { it.keyCode==KeyEvent.KEYCODE_NUMPAD_ENTER })
                assertTrue("native repeat",events.any { it.keyCode==KeyEvent.KEYCODE_B && it.repeatCount>0 })
                assertTrue("leave releases key",events.any { it.keyCode==KeyEvent.KEYCODE_B && it.action==KeyEvent.ACTION_UP })
                assertTrue("physical source",events.filter { it.keyCode==KeyEvent.KEYCODE_A }.all { it.deviceId>0 })
                lateinit var digits: EditText
                scenario.onActivity { activity ->
                    digits=EditText(activity)
                    digits.inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    activity.setContentView(digits)
                    digits.requestFocus()
                }
                Thread.sleep(400)
                for (id in 0xEFB0..0xEFB9) tap(id,0,id)
                Thread.sleep(400)
                scenario.onActivity { assertEquals("0123456789", digits.text.toString()) }

                lateinit var field: EditText
                scenario.onActivity { activity ->
                    field=EditText(activity)
                    field.inputType=android.text.InputType.TYPE_CLASS_TEXT
                    activity.setContentView(field)
                    field.requestFocus()
                    (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                        .showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
                }
                Thread.sleep(600)
                for (ch in "nihao ") tap(ch.code,0,ch.code)
                Thread.sleep(600)
                var text=""
                scenario.onActivity { text=field.text.toString() }
                if (text.none { it in '\u4e00'..'\u9fff' }) {
                    scenario.onActivity { field.setText("") }
                    tap(0xEFE1,1,90) // Sogou physical keyboard: Shift switches Chinese/English.
                    for (ch in "nihao ") tap(ch.code,0,ch.code)
                    Thread.sleep(600)
                    scenario.onActivity { text=field.text.toString() }
                }
                android.util.Log.i("PhysicalKeyboardTest", "Phone IME result: $text")
                assertTrue("Phone IME did not commit Chinese: $text", text.any { it in '\u4e00'..'\u9fff' })

            }
        } finally { keyboard.close() }
    }
    @Test fun deskflowWirePreservesPressRepeatAndRelease() {
        val seen = CopyOnWriteArrayList<String>()
        val latch = java.util.concurrent.CountDownLatch(3)
        val injector = object : Injector {
            override val screenWidth=1080
            override val screenHeight=2400
            override fun enter(x:Int,y:Int) {}
            override fun leave() {}
            override fun moveTo(x:Int,y:Int) {}
            override fun moveBy(dx:Int,dy:Int) {}
            override fun mouseDown(button:Int) {}
            override fun mouseUp(button:Int) {}
            override fun wheel(dx:Int,dy:Int) {}
            override fun keyDown(id:Int,mask:Int) { error("Lost physical button") }
            override fun keyDown(id:Int,mask:Int,button:Int) { seen.add("D:$id:$mask:$button");latch.countDown() }
            override fun keyUp(id:Int,mask:Int,button:Int) { seen.add("U:$id:$mask:$button");latch.countDown() }
            override fun keyRepeat(id:Int,mask:Int,button:Int,count:Int) { seen.add("R:$id:$mask:$button:$count");latch.countDown() }
        }
        java.net.ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout=3000
            val client=DeskflowClient("127.0.0.1",server.localPort,"wire-test",injector,null)
            client.start()
            try {
                server.accept().use { peer ->
                    peer.soTimeout=3000
                    val input=java.io.DataInputStream(peer.getInputStream())
                    val output=java.io.DataOutputStream(peer.getOutputStream())
                    fun send(packet:Packet) = Protocol.writePacket(output,packet.bytes())
                    send(Packet().raw("Synergy".toByteArray()).i2(1).i2(8))
                    Protocol.readPacket(input)
                    send(Packet().code("QINF"))
                    assertEquals("DINF", Reader(Protocol.readPacket(input)).code())
                    send(Packet().code("CIAK"))
                    send(Packet().code("DKDN").i2(65).i2(1).i2(42))
                    send(Packet().code("DKRP").i2(65).i2(1).i2(3).i2(42))
                    send(Packet().code("DKUP").i2(97).i2(0).i2(42))
                    assertTrue(latch.await(3,java.util.concurrent.TimeUnit.SECONDS))
                    assertEquals(listOf("D:65:1:42","R:65:1:42:3","U:97:0:42"),seen.toList())
                }
            } finally { client.disconnect();client.join(3000) }
        }
    }

    @Test fun unavailableBrokerFallsBackWithoutAdb() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val endpoint=java.io.File(context.filesDir,"keyboard-endpoint")
        val before=endpoint.readText()
        val port=java.net.ServerSocket(0).use { it.localPort }
        val keyboard=PhysicalKeyboard(context)
        val fallback=java.util.concurrent.CountDownLatch(1)
        try {
            endpoint.writeText("$port:unavailable-test")
            keyboard.key('a'.code,0,1,true) { fallback.countDown() }
            assertTrue(fallback.await(4,java.util.concurrent.TimeUnit.SECONDS))
            assertFalse(keyboard.ready)
        } finally { keyboard.close();endpoint.writeText(before) }
    }

}
