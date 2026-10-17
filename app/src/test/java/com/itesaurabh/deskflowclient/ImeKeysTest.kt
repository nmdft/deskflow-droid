// Modified by nmdft on 2026-10-17; see NOTICE.md for fork changes and license.
package com.itesaurabh.deskflowclient

import org.junit.Assert.*
import org.junit.Test

class ImeKeysTest {
    @Test fun commandAndControlEditActions() {
        for (mask in listOf(KeyModifier.CONTROL, KeyModifier.META, KeyModifier.SUPER)) {
            assertEquals(16908319, ImeKeys.editAction('A'.code, mask))
            assertEquals(16908321, ImeKeys.editAction('c'.code, mask))
            assertEquals(16908320, ImeKeys.editAction('x'.code, mask))
            assertEquals(16908322, ImeKeys.editAction('v'.code, mask))
        }
    }
    @Test fun ordinaryLetterNotShortcut() { assertNull(ImeKeys.editAction('c'.code, 0)) }
    @Test fun altDoesNotBecomeCopy() { assertNull(ImeKeys.editAction('c'.code, KeyModifier.CONTROL or KeyModifier.ALT)) }
    @Test fun navigationAndKeypad() {
        assertEquals(21, ImeKeys.code(KeyId.LEFT)); assertEquals(67, ImeKeys.code(KeyId.BACKSPACE))
        assertEquals(9, ImeKeys.code(0xEFB2)); assertEquals(66, ImeKeys.code(0xEF8D))
    }
    @Test fun unknownShortcutNotFakeHardware() { assertNull(ImeKeys.code(0xEEEE)) }
}
