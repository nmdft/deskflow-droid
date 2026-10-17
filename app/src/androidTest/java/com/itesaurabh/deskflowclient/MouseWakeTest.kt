// Modified by nmdft on 2026-10-17; see NOTICE.md for fork changes and license.
package com.itesaurabh.deskflowclient

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.os.PowerManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Screen-off test drives the production service using Deskflow CINN; no shell wake command. */
class MouseWakeTest {
    @Test fun heartbeatDoesNotWakeButMouseEntryDoesAndKeepsKeyguard() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
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
        val locked=CountDownLatch(1)
        val checked=CountDownLatch(1)
        val entrySent=CountDownLatch(1)
        val end=CountDownLatch(1)
        val server=ServerSocket(0)
        server.soTimeout=15000
        var failure:Throwable?=null
        val peer=Thread {
            try {
                server.accept().use { socket ->
                    socket.soTimeout=15000
                    val input=DataInputStream(socket.getInputStream())
                    val out=DataOutputStream(socket.getOutputStream())
                    Protocol.writePacket(out,Packet().raw("Barrier".toByteArray()).i2(1).i2(8).bytes())
                    assertEquals("Barrier",String(Protocol.readPacket(input).copyOfRange(0,7)))
                    Protocol.writePacket(out,Packet().code("QINF").bytes())
                    assertEquals("DINF",Reader(Protocol.readPacket(input)).code())
                    Protocol.writePacket(out,Packet().code("CIAK").bytes())
                    assertTrue(locked.await(15,TimeUnit.SECONDS))
                    repeat(3) {
                        Protocol.writePacket(out,Protocol.KEEP_ALIVE)
                        assertEquals("CALV",Reader(Protocol.readPacket(input)).code())
                        Thread.sleep(400)
                    }
                    checked.countDown()
                    Thread.sleep(600)
                    Protocol.writePacket(out,Packet().code("CINN").i2(10).i2(10).i4(42).i2(0).bytes())
                    entrySent.countDown()
                    end.await(10,TimeUnit.SECONDS)
                }
            } catch(e:Throwable) { failure=e;checked.countDown();entrySent.countDown() }
        }.apply { start() }
        try {
            instrumentation.runOnMainSync { service.connect("127.0.0.1",server.localPort,"wake-test",false) }
            var connected=false
            for(i in 0 until 60) {
                Thread.sleep(100)
                instrumentation.runOnMainSync { connected=ClientState.phase==ClientState.Phase.Connected }
                if(connected) break
            }
            assertTrue("Fixture connection failed",connected)
            var didLock=false
            instrumentation.runOnMainSync { didLock=service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) }
            assertTrue("System refused test screen lock",didLock)
            for(i in 0 until 30) { if(!power.isInteractive) break;Thread.sleep(100) }
            assertFalse("Screen did not turn off",power.isInteractive)
            Thread.sleep(300)
            val keyguardWasLocked=keyguard.isKeyguardLocked
            locked.countDown()
            assertTrue(checked.await(10,TimeUnit.SECONDS));failure?.let { throw AssertionError(it) }
            assertFalse("Heartbeat must not wake screen",power.isInteractive)
            assertTrue(entrySent.await(5,TimeUnit.SECONDS));failure?.let { throw AssertionError(it) }
            for(i in 0 until 40) { if(power.isInteractive) break;Thread.sleep(100) }
            assertTrue("Mouse entry did not wake screen",power.isInteractive)
            if(keyguardWasLocked) assertTrue("Must not bypass keyguard",keyguard.isKeyguardLocked)
            Thread.sleep(1800)
            assertTrue("Screen should remain normally awake after timed wake lock release",power.isInteractive)
        } finally {
            end.countDown();locked.countDown()
            instrumentation.runOnMainSync { service.disconnect();service.leave() }
            server.close();peer.join(3000)
        }
    }
}
