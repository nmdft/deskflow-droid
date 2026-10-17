// Modified by nmdft on 2026-10-17; see NOTICE.md for fork changes and license.
package com.itesaurabh.deskflowclient

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ClipboardWireTest {
    private fun receive(text: String, sequence: Int = 7): String? {
        val assembly = ClipboardAssembly()
        var result: String? = null
        for (packet in ClipboardWire.packets(text, sequence).drop(1)) {
            val r = Reader(packet)
            assertEquals("DCLP", r.code())
            assembly.receive(r)?.let { result = it }
        }
        return result
    }
    @Test fun unicodeAndNewlinesRoundTrip() { assertEquals("你好\nMac → 手机😀", receive("你好\nMac → 手机😀")) }
    @Test fun emptyClipboardRoundTrip() { assertEquals("", receive("")) }
    @Test fun multipleChunksRoundTrip() { val text = "中文abc\n".repeat(20000); assertEquals(text, receive(text)) }
    @Test fun matchesUpstreamTextSerialization() {
        assertArrayEquals(byteArrayOf(0,0,0,1,0,0,0,0,0,0,0,2,104,105), ClipboardWire.encode("hi"))
    }
    @Test fun ownershipAndSequenceAreBigEndian() {
        val r = Reader(ClipboardWire.packets("x", 0x10203040)[0])
        assertEquals("CCLP", r.code()); assertEquals(0, r.i1()); assertEquals(0x10203040, r.i4())
    }
    @Test(expected = IOException::class) fun rejectsDataWithoutStart() {
        ClipboardAssembly().receive(Reader(Packet().i1(0).i4(1).i1(2).blob(byteArrayOf(1)).bytes()))
    }
    @Test fun oversizeTransferIsIgnoredWithoutBreakingInputConnection() {
        val a=ClipboardAssembly()
        assertNull(a.receive(Reader(Packet().i1(0).i4(1).i1(1).str("99999999").bytes())))
        assertNull(a.receive(Reader(Packet().i1(0).i4(1).i1(2).str("discard").bytes())))
        assertNull(a.receive(Reader(Packet().i1(0).i4(1).i1(3).blob(byteArrayOf()).bytes())))
        var result:String?=null
        for (packet in ClipboardWire.packets("still works",2).drop(1)) {
            val r=Reader(packet);r.code();a.receive(r)?.let { result=it }
        }
        assertEquals("still works",result)
    }
    @Test(expected = IOException::class) fun rejectsSequenceChange() {
        val a = ClipboardAssembly()
        a.receive(Reader(Packet().i1(0).i4(1).i1(1).str("12").bytes()))
        a.receive(Reader(Packet().i1(0).i4(2).i1(2).blob(byteArrayOf()).bytes()))
    }
    @Test(expected = IOException::class) fun rejectsIncompleteEnd() {
        val a = ClipboardAssembly()
        a.receive(Reader(Packet().i1(0).i4(1).i1(1).str("12").bytes()))
        a.receive(Reader(Packet().i1(0).i4(1).i1(3).blob(byteArrayOf()).bytes()))
    }
    @Test(expected = IOException::class) fun rejectsNegativeBlobLength() { Reader(Packet().i4(-1).bytes()).blob() }
    @Test(expected = IllegalArgumentException::class) fun boundsOutgoingText() { ClipboardWire.encode("x".repeat(ClipboardWire.LIMIT)) }
    @Test fun skipHtmlOnlyClipboard() { assertNull(ClipboardWire.decode(Packet().i4(1).i4(1).str("<b>x</b>").bytes())) }
    @Test fun resetDropsPartialTransfer() {
        val a = ClipboardAssembly()
        a.receive(Reader(Packet().i1(0).i4(1).i1(1).str("12").bytes()))
        a.reset()
        try { a.receive(Reader(Packet().i1(0).i4(1).i1(3).blob(byteArrayOf()).bytes())); fail() }
        catch (_: IOException) { }
    }
}
