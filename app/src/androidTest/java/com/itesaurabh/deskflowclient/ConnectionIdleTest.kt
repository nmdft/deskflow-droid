// Modified by nmdft on 2026-10-31; see NOTICE.md.
package com.itesaurabh.deskflowclient

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import org.junit.Assert.*
import org.junit.Test

/** Loopback protocol tests; no accessibility grants, saved settings or clipboard access. */
class ConnectionIdleTest {
    private class Backend : Injector {
        override val screenWidth = 1080
        override val screenHeight = 2400
        override fun enter(x: Int, y: Int) = Unit
        override fun leave() = Unit
        override fun moveTo(x: Int, y: Int) = Unit
        override fun moveBy(dx: Int, dy: Int) = Unit
        override fun mouseDown(button: Int) = Unit
        override fun mouseUp(button: Int) = Unit
        override fun wheel(dx: Int, dy: Int) = Unit
        override fun keyDown(id: Int, mask: Int) = Unit
    }

    @Test fun idleClipboardBlocksAndDisconnectStopsSender() {
        ServerSocket(0).use { server ->
            server.soTimeout = 5000
            val client = DeskflowClient("127.0.0.1", server.localPort, "fixture", Backend(), null)
            try {
                client.start()
                server.accept().use { peer ->
                    peer.soTimeout = 5000
                    val input = DataInputStream(peer.getInputStream())
                    val out = DataOutputStream(peer.getOutputStream())
                    Protocol.writePacket(out, "Barrier".toByteArray() + byteArrayOf(0, 1, 0, 8))
                    Protocol.readPacket(input)
                    Protocol.writePacket(out, "QINF".toByteArray())
                    assertEquals("DINF", Reader(Protocol.readPacket(input)).code())
                    Thread.sleep(200)
                    val sender = Thread.getAllStackTraces().keys.single { it.name == "deskflow-clipboard" && it.isAlive }
                    assertEquals(Thread.State.WAITING, sender.state)
                    client.disconnect()
                    client.join(5000)
                    sender.join(2000)
                    assertFalse(client.isAlive)
                    assertFalse(sender.isAlive)
                }
            } finally { client.disconnect(); client.join(5000) }
        }
    }

}
