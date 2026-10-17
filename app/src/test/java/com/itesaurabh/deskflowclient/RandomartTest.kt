package com.itesaurabh.deskflowclient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RandomartTest {

    /** 9 rows of 17 cells, the field size Deskflow uses. The UI draws the frame. */
    @Test
    fun hasTheSameFieldAsDeskflow() {
        val rows = Randomart.of(ByteArray(32) { it.toByte() }).lines()

        assertEquals(9, rows.size)
        rows.forEach { assertEquals(17, it.length) }
    }

    /**
     * With nothing to walk, the bishop never leaves the centre, so the end marker lands on the
     * start marker and overwrites it. That pins the field size, the centre, and the marker order
     * all at once.
     */
    @Test
    fun emptyDigestMarksOnlyTheCentre() {
        val rows = Randomart.of(ByteArray(0)).lines()

        assertEquals('E', rows[4][8])
        assertEquals(1, rows.sumOf { row -> row.count { it != ' ' } })
    }

    /** The walk is bounded, so no byte pattern can escape the field. */
    @Test
    fun staysInsideTheFieldForEveryByte() {
        for (value in 0..255) {
            val rows = Randomart.of(ByteArray(64) { value.toByte() }).lines()

            assertEquals(9, rows.size)
            rows.forEach { assertEquals(17, it.length) }
            assertTrue("no end marker for byte $value", rows.any { it.contains('E') })
        }
    }

    /**
     * The server fingerprint from a real session, whose art was compared glyph by glyph against
     * the picture Deskflow drew in its own Local Fingerprints window on the desktop. That
     * comparison is what established the two implementations agree; this locks it in so a change
     * here cannot silently drift away from the desktop.
     */
    @Test
    fun matchesTheArtDeskflowDrawsForTheSameFingerprint() {
        val fingerprint = "8F:21:E7:33:85:CD:E2:94:10:59:CE:47:C9:8C:A3:67:" +
            "9D:91:83:4A:1E:49:28:1F:A3:FC:9F:A7:F7:30:A1:33"
        val digest = fingerprint.split(":").map { it.toInt(16).toByte() }.toByteArray()

        val expected = listOf(
            "     oo+.=.o     ",
            "  . + =++.B      ",
            " . + =.+oo.+     ",
            "  o . +.o*o      ",
            "   .  .oS +      ",
            "    .  B B       ",
            "     .E.O .      ",
            "      ooo=       ",
            "      .+ ..      "
        )

        assertEquals(expected, Randomart.of(digest).lines())
    }
}
