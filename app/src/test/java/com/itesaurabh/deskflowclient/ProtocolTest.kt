package com.itesaurabh.deskflowclient

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class ProtocolTest {

    /** The example from ProtocolTypes.h: "Barrier" 1.8, client name "workstation". */
    @Test
    fun helloBackMatchesUpstreamExample() {
        val expected = "Barrier".toByteArray(Charsets.US_ASCII) +
            byteArrayOf(0, 1, 0, 8, 0, 0, 0, 0x0B) +
            "workstation".toByteArray(Charsets.US_ASCII)
        assertArrayEquals(expected, Protocol.helloBack("Barrier".toByteArray(Charsets.US_ASCII), 8, "workstation"))
    }

    @Test
    fun packetsAreLengthPrefixed() {
        val out = ByteArrayOutputStream()
        Protocol.writePacket(DataOutputStream(out), Protocol.KEEP_ALIVE)
        assertArrayEquals(byteArrayOf(0, 0, 0, 4) + "CALV".toByteArray(), out.toByteArray())

        val back = Protocol.readPacket(DataInputStream(ByteArrayInputStream(out.toByteArray())))
        assertEquals("CALV", Reader(back).code())
    }

    /** "CINN\x01\x90\x01\x2C\x00\x00\x00\x01\x00\x00" from the upstream docs: 400,300 seq 1. */
    @Test
    fun readsEnterMessage() {
        val r = Reader("CINN".toByteArray() + byteArrayOf(0x01, 0x90.toByte(), 0x01, 0x2C, 0, 0, 0, 1, 0, 0))
        assertEquals("CINN", r.code())
        assertEquals(400, r.i2())
        assertEquals(300, r.i2())
        assertEquals(1, r.i4())
        assertEquals(0, r.u2())
    }

    @Test
    fun readsNegativeRelativeMotion() {
        val r = Reader("DMRM".toByteArray() + byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x00, 0x07))
        assertEquals("DMRM", r.code())
        assertEquals(-5, r.i2())
        assertEquals(7, r.i2())
    }

    @Test
    fun infoCarriesSevenFields() {
        val body = Protocol.info(0, 0, 1080, 2400, 540, 1200)
        assertEquals(4 + 7 * 2, body.size)
        val r = Reader(body)
        assertEquals("DINF", r.code())
        assertEquals(0, r.i2())
        assertEquals(0, r.i2())
        assertEquals(1080, r.i2())
        assertEquals(2400, r.i2())
        assertEquals(0, r.i2())
        assertEquals(540, r.i2())
        assertEquals(1200, r.i2())
    }

    @Test
    fun controlKeysAreNotPrintable() {
        assertEquals(true, KeyId.isPrintable('a'.code))
        assertEquals(true, KeyId.isPrintable(0x20AC)) // euro sign
        assertEquals(false, KeyId.isPrintable(KeyId.BACKSPACE))
        assertEquals(false, KeyId.isPrintable(KeyId.RETURN))
        assertEquals(false, KeyId.isPrintable(KeyId.DELETE))
    }
}
