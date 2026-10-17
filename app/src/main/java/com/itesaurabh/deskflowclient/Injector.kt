// Modified by nmdft on 2026-10-17; see NOTICE.md for fork changes and license.
package com.itesaurabh.deskflowclient

/**
 * Everything the protocol layer needs from an input backend. The accessibility implementation is
 * the only one today; a Shizuku implementation drops in here without touching DeskflowClient.
 */
interface Injector {
    val screenWidth: Int
    val screenHeight: Int
    fun screenSize(): Pair<Int, Int> = screenWidth to screenHeight

    /** Server cursor crossed onto this screen at the given absolute position. */
    fun enter(x: Int, y: Int)

    /** Server cursor left; release anything still held. */
    fun leave()

    fun moveTo(x: Int, y: Int)
    fun moveBy(dx: Int, dy: Int)
    fun mouseDown(button: Int)
    fun mouseUp(button: Int)

    /** Wheel deltas, 120 units per notch. Positive y is a scroll away from the user. */
    fun wheel(dx: Int, dy: Int)

    fun clipboard(text: String) {}

    fun keyDown(id: Int, mask: Int)
    fun keyDown(id: Int, mask: Int, button: Int) = keyDown(id, mask)
    fun keyUp(id: Int, mask: Int, button: Int) {}
    fun keyRepeat(id: Int, mask: Int, button: Int, count: Int) {
        repeat(count) { keyDown(id, mask, button) }
    }
}
