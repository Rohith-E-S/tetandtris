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
 *  - single tap: rotate (left third of the screen = counter-clockwise, rest = clockwise)
 *  - double tap / fast flick down: hard drop
 *  - touch-and-hold still (~450ms): hold (any move before that cancels it)
 *  - horizontal drag: move, one step per ~0.6 board-cell dragged
 *  - vertical drag: soft drop while held down
 */
fun Modifier.tuiGestures(
    vm: GameViewModel,
    cellHeightPx: () -> Float
): Modifier = this.pointerInput(Unit) {
    val mainHandler = Handler(Looper.getMainLooper())
    var lastTapTime = 0L
    var pendingTapGen = 0
    val tapWindowMs = 300L
    val longPressTimeout = (viewConfiguration.longPressTimeoutMillis + 50).toLong()
    val touchSlop = viewConfiguration.touchSlop

    awaitEachGesture {
        val down = awaitFirstDown()
        val downTime = System.currentTimeMillis()
        val downX = down.position.x
        val downY = down.position.y
        val tapXFrac = downX / size.width.coerceAtLeast(1)

        var accX = 0f
        var totalDx = 0f
        var totalDy = 0f
        var holdFired = false
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
            val dx = change.position.x - change.previousPosition.x
            val dy = change.position.y - change.previousPosition.y
            totalDx += dx
            totalDy += dy
            val elapsed = System.currentTimeMillis() - downTime
            val dist = maxOf(abs(totalDx), abs(totalDy))

            // Hold fires only while the finger is still held in place.
            // Any real movement beforehand cancels it — dragging always wins.
            if (!holdFired && !movedSteps && elapsed >= longPressTimeout && dist < touchSlop) {
                holdFired = true
                vm.onLongPress()
                change.consume()
                continue
            }
            if (holdFired) {
                // Rest of a hold gesture is consumed; wait for lift.
                change.consume()
                continue
            }

            accX += dx
            val steps = (accX / stepPx).toInt()
            if (steps != 0) {
                vm.onDrag(steps)
                accX -= steps * stepPx
                movedSteps = true
            }

            val vertical = change.position.y - downY
            val horizontal = change.position.x - downX
            vm.setSoftDropping(vertical > cellH * 1.2f && abs(vertical) > abs(horizontal))
            change.consume()
        }

        vm.setSoftDropping(false)
        if (holdFired) return@awaitEachGesture

        val totalDist = maxOf(abs(totalDx), abs(totalDy))
        val duration = System.currentTimeMillis() - downTime

        if (!movedSteps && totalDist < touchSlop) {
            // Pure tap. Single tap is held until the double-tap window closes:
            // a second tap means hard drop INSTEAD of a rotate, never both.
            val now = System.currentTimeMillis()
            if (now - lastTapTime < tapWindowMs) {
                lastTapTime = 0L
                pendingTapGen++ // cancel the withheld single tap
                vm.onDoubleTap()
            } else {
                lastTapTime = now
                val s = vm.state.value
                if (!s.started || s.paused || s.showHelp || s.engine.ended) {
                    // No double-tap meaning outside active play — act at once.
                    vm.onTap(tapXFrac)
                } else {
                    val gen = ++pendingTapGen
                    mainHandler.postDelayed({
                        if (pendingTapGen == gen) vm.onTap(tapXFrac)
                    }, tapWindowMs)
                }
            }
        } else if (movedSteps && totalDy > cellH * 2f && duration < 350 &&
            totalDy / duration.coerceAtLeast(1) > cellH / 120f &&
            abs(totalDy) > abs(totalDx) * 1.2f
        ) {
            // Fast downward flick = hard drop.
            vm.onFlickDown()
        }
    }
}
