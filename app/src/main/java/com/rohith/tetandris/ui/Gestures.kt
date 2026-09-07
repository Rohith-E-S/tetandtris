package com.rohith.tetandris.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

/**
 * Single-loop gesture scheme (one await, raw deltas — no stacked waits):
 *  - single-finger tap: rotate counter-clockwise (fires on lift, no delay)
 *  - two-finger tap: hold (swap with the held piece)
 *  - horizontal drag: move, one step per ~0.6 board-cell dragged
 *  - straight-down drag (vertical clearly dominates): soft drop while held down.
 *    Diagonal drags ignore the vertical component so the piece doesn't speed up
 *    while the player is angling their finger toward a column.
 */
fun Modifier.tuiGestures(
    vm: GameViewModel,
    cellHeightPx: () -> Float
): Modifier = this.pointerInput(Unit) {
    val touchSlop = viewConfiguration.touchSlop
    // Both fingers of a two-finger tap must be back up within this long after
    // the first touch-down. Resting two fingers on the screen and lifting later
    // intentionally does nothing.
    val twoFingerTapTimeoutMs = 500L

    awaitEachGesture {
        val down = awaitFirstDown()
        val downTime = System.currentTimeMillis()
        val downX = down.position.x
        val downY = down.position.y

        var accX = 0f
        var totalDx = 0f
        var totalDy = 0f
        var totalDx2 = 0f
        var totalDy2 = 0f
        var movedSteps = false
        var twoFinger = false
        var firstUp = false
        val cellH = cellHeightPx().coerceAtLeast(8f)
        val stepPx = maxOf(cellH * 0.6f, 12f)
        vm.setSoftDropping(false)

        var gestureDone = false
        while (!gestureDone) {
            val event = awaitPointerEvent()
            val first = event.changes.firstOrNull { it.id == down.id }
            if (first != null) {
                if (!first.pressed) {
                    // First finger lifted — the gesture ends once the
                    // second finger (if any) lifts too.
                    first.consume()
                    firstUp = true
                } else {
                    // Raw delta since last event (ignores consumption bookkeeping).
                    // Both axes feed the tap-vs-drag classifier: a straight-down
                    // soft-drop drag must NOT look like a tap on release.
                    val dx = first.position.x - first.previousPosition.x
                    val dy = first.position.y - first.previousPosition.y
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
                    val vertical = first.position.y - downY
                    val horizontal = first.position.x - downX
                    val isStraightDown = vertical > cellH * 1.2f &&
                        abs(vertical) > abs(horizontal) * 1.5f &&
                        abs(horizontal) < cellH * 0.4f
                    vm.setSoftDropping(isStraightDown)
                    first.consume()
                }
            }

            // A second finger joining in turns this into a two-finger-tap
            // candidate. Its travel feeds the tap-vs-drag classifier just like
            // the first finger's, so real movement by either finger disqualifies
            // the tap and an in-progress drag keeps winning.
            for (change in event.changes) {
                if (change.id == down.id) continue
                if (change.pressed) {
                    twoFinger = true
                    totalDx2 += change.position.x - change.previousPosition.x
                    totalDy2 += change.position.y - change.previousPosition.y
                }
                change.consume()
            }

            if (firstUp && event.changes.none { it.pressed }) {
                gestureDone = true
            }
        }

        vm.setSoftDropping(false)

        val totalDist = maxOf(abs(totalDx), abs(totalDy), abs(totalDx2), abs(totalDy2))

        if (!movedSteps && totalDist < touchSlop) {
            if (twoFinger) {
                // Two-finger tap = hold — but only when quick.
                if (System.currentTimeMillis() - downTime < twoFingerTapTimeoutMs) {
                    vm.onTwoFingerTap()
                }
            } else {
                // Single-finger tap = rotate. There is no double-tap to wait for
                // anymore, so it fires immediately on lift.
                vm.onTap()
            }
        }
    }
}
