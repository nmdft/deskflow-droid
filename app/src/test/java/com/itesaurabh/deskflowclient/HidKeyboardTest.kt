// Modified by nmdft on 2026-10-17; see NOTICE.md for fork changes and license.
package com.itesaurabh.deskflowclient

import org.junit.Assert.*
import org.junit.Test

class HidKeyboardTest {
    private val reports = mutableListOf<ByteArray>()
    private val keyboard = HidKeyboard { reports.add(it.clone()) }
    private fun held(usage: Int) = reports.last { it[0].toInt() == 1 }[1+usage/8].toInt() and (1 shl (usage%8)) != 0

    @Test fun keyUpUsesPhysicalButtonEvenWhenSymbolChanges() {
        keyboard.key('A'.code, 1, 12, true)
        assertTrue(held(4)); assertTrue(held(225))
        keyboard.key('a'.code, 0, 12, false)
        assertFalse(held(4)); assertFalse(held(225))
    }
    @Test fun moreThanSixKeysAndIndependentRelease() {
        for(i in 0..9) keyboard.key('a'.code+i, 0, i+1, true)
        for(i in 0..9) assertTrue(held(4+i))
        keyboard.key('e'.code,0,5,false)
        assertFalse(held(8));assertTrue(held(7));assertTrue(held(9))
    }
    @Test fun rightModifiersAndDisconnectRelease() {
        keyboard.key(0xEFE4,2,7,true)
        keyboard.key('c'.code,2,8,true)
        assertTrue(held(228));assertFalse(held(224))
        keyboard.key(0xE0AF,0,20,true)
        assertArrayEquals(byteArrayOf(2,0xe9.toByte(),0),reports.last())
        keyboard.reset()
        assertTrue(reports.last { it[0].toInt()==1 }.drop(1).all { it.toInt()==0 })
        assertArrayEquals(byteArrayOf(2,0,0),reports.last())
    }
    @Test fun keypadNavigationAndFunctionKeys() {
        assertEquals(89,HidKeyboard.usage(0xEFB1))
        assertEquals(88,HidKeyboard.usage(0xEF8D))
        assertEquals(115,HidKeyboard.usage(0xEFD5))
        assertEquals(95,HidKeyboard.usage(0xEF95))
        assertEquals(74,HidKeyboard.usage(0xEF50))
        assertFalse(keyboard.key('中'.code,0,5,true))
    }
    @Test fun controlCharacterEncodingStillProducesPhysicalLetter() {
        assertTrue(keyboard.key(3,2,40,true))
        assertTrue(held(6));assertTrue(held(224))
    }
    @Test fun lockStateIsSynchronizedWithoutRetogglingEveryKey() {
        keyboard.initializeLocks(0)
        keyboard.key('a'.code,0x2000,1,true)
        assertTrue(reports.any { it[0].toInt()==1 && it[1+83/8].toInt() and (1 shl (83%8)) != 0 })
        reports.clear()
        keyboard.key('b'.code,0x2000,2,true)
        assertFalse(reports.any { it[0].toInt()==1 && it[1+83/8].toInt() and (1 shl (83%8)) != 0 })
    }
    @Test fun numericKeypadWithoutNumLockMaskStillEnablesDigits() {
        keyboard.initializeLocks(0)
        keyboard.key(0xEFB2,0,9,true)
        assertTrue("numeric KeyID must enable NumLock", reports.any {
            it[0].toInt()==1 && it[1+83/8].toInt() and (1 shl (83%8)) != 0
        })
        assertTrue(held(90))
        reports.clear()
        keyboard.key('a'.code,0,10,true)
        assertFalse("missing source flag must not disable NumLock",reports.any {
            it[0].toInt()==1 && it[1+83/8].toInt() and (1 shl (83%8)) != 0
        })
    }

}
