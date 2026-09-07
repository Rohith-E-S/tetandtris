package com.rohith.tetandris.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

/**
 * Single-loop gesture scheme (one await, raw deltas — no stacked waits):
 *  - single tap: rotate counter-clockwise
 *  - double tap: hold (swap with the held piece)
 *  - horizontal drag: move, one step per ~0.6 board-cell dragged
 *  - straight-down drag (vertical clearly dominates): soft drop while held down.
 *    Diagonal drags ignore the vertical component so the piece doesn't speed up
 *    while the player is angling their finger toward a column.
 */
fun Modifier.tuiGestures(
    vm: GameViewModel,
    cellHeightPx: () -> Float
): Modifier = this.pointerInput(Unit) {
    val mainHandler = Handler(Looper.getMainLooper())
    var lastTapTime = 0L
    var pendingTapGen = 0
    val tapWindowMs = 300L
    val touchSlop = viewConfiguration.touchSlop

    awaitEachGesture {
        val down = awaitFirstDown()
        val downX = down.position.x
        val downY = down.position.y

        var accX = 0f
        var totalDx = 0f
        var totalDy = 0f
        var movedSteps = false
        val cellH = cellHeightPx().coerceAtLeast(8f)
        val stepPx = maxOf(cellH * 0.6f, 12f)
        vm.setSoftDropping(false)

        var gestureDone = false
        while (!gestureDone) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: continue

            if (!change.pressed) {
                // Finger lifted — end of gesture.
                change.consume()
                gestureDone = true
                continue
            }

            // Raw delta since last event (ignores consumption bookkeeping).
            // Both axes feed the tap-vs-drag classifier: a straight-down
            // soft-drop drag must NOT look like a tap on release.
            val dx = change.position.x - change.previousPosition.x
            val dy = change.position.y - change.previousPosition.y
            totalDx += dx
            totalDy += dy

            accX += dx
            val steps = (accX / stepPx).toInt()
            if (steps != 0) {
                vm.onDrag(steps)
                accX -= steps * stepPx
                movedSteps = true
            }

            // Soft drop only when the drag is clearly vertical. A small horizontal
            // component (diagonal aiming toward a column) must not engage soft drop —
            // the horizontal move wins, the piece doesn't speed up its fall.
            val vertical = change.position.y - downY
            val horizontal = change.position.x - downX
            val isStraightDown = vertical > cellH * 1.2f &&
                abs(vertical) > abs(horizontal) * 1.5f &&
                abs(horizontal) < cellH * 0.4f
            vm.setSoftDropping(isStraightDown)
            change.consume()
        }

        vm.setSoftDropping(false)

        val totalDist = maxOf(abs(totalDx), abs(totalDy))

        if (!movedSteps && totalDist < touchSlop) {
            // Pure tap. Single tap is held until the double-tap window closes:
            // a second tap means hold INSTEAD of a rotate, never both.
            val now = System.currentTimeMillis()
            if (now - lastTapTime < tapWindowMs) {
                lastTapTime = 0L
                pendingTapGen++ // cancel the withheld single tap
                vm.onDoubleTap()
            } else {
                lastTapTime = now
                val s = vm.state.value
                if (!s.started || s.paused || s.showHelp || s.engine.ended) {
                    vm.onTap()
                } else {
                    val gen = ++pendingTapGen
                    mainHandler.postDelayed({
                        if (pendingTapGen == gen) vm.onTap()
                    }, tapWindowMs)
                }
            }
        }
    }
}
