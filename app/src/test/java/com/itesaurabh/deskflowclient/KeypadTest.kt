// Modified by nmdft on 2026-10-17; see NOTICE.md for fork changes and license.
package com.itesaurabh.deskflowclient

import org.junit.Assert.assertEquals
import org.junit.Test

class KeypadTest {
    @Test fun synergyKeypadDigitsBecomeText() {
        assertEquals("0123456789", (0xEFB0..0xEFB9).map { KeyId.normalize(it).toChar() }.joinToString(""))
        assertEquals('.'.code, KeyId.normalize(0xEFAE))
        assertEquals(KeyId.RETURN, KeyId.normalize(0xEF8D))
        assertEquals(KeyId.LEFT, KeyId.normalize(0xEF96))
        assertEquals('中'.code, KeyId.normalize('中'.code))
    }
}
