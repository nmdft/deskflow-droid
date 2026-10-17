// Modified by nmdft on 2026-10-17; see NOTICE.md for fork changes and license.
package com.itesaurabh.deskflowclient

/** Deskflow sees the mounted device; Android receives coordinates in its current display frame. */
data class PointerSpace(val displayWidth: Int, val displayHeight: Int, val displayRotation: Int,
                        val mode: Int, val reversed: Boolean = false) {
    private val naturalWidth = if (displayRotation % 2 == 0) displayWidth else displayHeight
    private val naturalHeight = if (displayRotation % 2 == 0) displayHeight else displayWidth
    private val portraitRotation = if (naturalWidth <= naturalHeight) 0 else 1
    val mountRotation = if (mode == -1) displayRotation else
        (portraitRotation + mode + if (reversed) 2 else 0) % 4
    val width = if (mountRotation % 2 == 0) naturalWidth else naturalHeight
    val height = if (mountRotation % 2 == 0) naturalHeight else naturalWidth
    val turns = (displayRotation - mountRotation + 4) % 4
    val cursorAngle get() = -90f * turns

    fun toDisplay(x: Float, y: Float): Pair<Float, Float> = when (turns) {
        1 -> y to (width - 1f - x)
        2 -> (width - 1f - x) to (height - 1f - y)
        3 -> (height - 1f - y) to x
        else -> x to y
    }
}
