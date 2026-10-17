// Modified by nmdft on 2026-10-17; see NOTICE.md for fork changes and license.
package com.itesaurabh.deskflowclient

import org.junit.Assert.*
import org.junit.Test

class EditorTextTest {
    @Test fun selectionHandlesBothDirections() {
        assertEquals("你好", EditorText.selection("你好世界", 0, 2))
        assertEquals("你好", EditorText.selection("你好世界", 2, 0))
    }
    @Test fun emptyAndInvalidSelectionsNotPublished() {
        assertNull(EditorText.selection("secret", 0, 0))
        assertNull(EditorText.selection("secret", -1, 3))
        assertNull(EditorText.selection("secret", 1, 99))
    }
    @Test fun emojiDeletionDoesNotSplitSurrogatePair() {
        assertEquals(2 to 0, EditorText.deleteUnits("a😀b", 3, true))
        assertEquals(0 to 2, EditorText.deleteUnits("a😀b", 1, false))
    }
    @Test fun deleteAtEdgesIsNoOp() {
        assertEquals(0 to 0, EditorText.deleteUnits("", 0, true))
        assertEquals(0 to 0, EditorText.deleteUnits("abc", 3, false))
    }
    @Test fun passwordVariationsAreProtected() {
        for (type in listOf(0x81, 0x91, 0xe1, 0x12)) assertTrue(EditorSecurity.isPassword(type))
        assertFalse(EditorSecurity.isPassword(1)); assertFalse(EditorSecurity.isPassword(2))
    }
}
