// Modified by nmdft on 2026-10-17; see NOTICE.md for fork changes and license.
package com.itesaurabh.deskflowclient

import org.junit.Assert.*
import org.junit.Test

class TextNavigationTest {
    @Test fun shiftLeftPreservesAnchor() {
        assertEquals(4 to 3,TextNavigation.move("hell",4,4,KeyId.LEFT,KeyModifier.SHIFT))
        assertEquals(4 to 2,TextNavigation.move("hell",4,3,KeyId.LEFT,KeyModifier.SHIFT))
    }
    @Test fun arrowsCollapseSelection() {
        assertEquals(1 to 1,TextNavigation.move("abcd",1,3,KeyId.LEFT,0))
        assertEquals(3 to 3,TextNavigation.move("abcd",3,1,KeyId.RIGHT,0))
    }
    @Test fun emojiNotSplit() {
        assertEquals(1 to 1,TextNavigation.move("a😀b",3,3,KeyId.LEFT,0))
        assertEquals(3 to 3,TextNavigation.move("a😀b",1,1,KeyId.RIGHT,0))
    }
    @Test fun wordNavigation() {
        assertEquals(6 to 6,TextNavigation.move("hello world",11,11,KeyId.LEFT,KeyModifier.CONTROL))
        assertEquals(6 to 6,TextNavigation.move("hello world",0,0,KeyId.RIGHT,KeyModifier.CONTROL))
    }
    @Test fun shiftHomeEnd() {
        assertEquals(2 to 0,TextNavigation.move("abc",2,2,KeyId.HOME,KeyModifier.SHIFT))
        assertEquals(2 to 3,TextNavigation.move("abc",2,2,KeyId.END,KeyModifier.SHIFT))
    }
    @Test fun verticalMovement() {
        assertEquals(1 to 1,TextNavigation.move("abc\nx\nlong",5,5,KeyId.UP,0))
        assertEquals(5 to 5,TextNavigation.move("abc\nx\nlong",2,2,KeyId.DOWN,0))
    }
    @Test fun boundedEmptyField() {
        assertEquals(0 to 0,TextNavigation.move("",0,0,KeyId.LEFT,KeyModifier.SHIFT))
        assertEquals(0 to 0,TextNavigation.move("",0,0,KeyId.DOWN,0))
    }
}
