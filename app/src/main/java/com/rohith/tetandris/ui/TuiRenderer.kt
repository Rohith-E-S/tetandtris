package com.rohith.tetandris.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rohith.tetandris.core.BOARD_HEIGHT
import com.rohith.tetandris.core.BOARD_WIDTH
import com.rohith.tetandris.core.LOCK_DURATION_MS
import com.rohith.tetandris.core.LINE_CLEAR_DURATION_MS
import com.rohith.tetandris.core.TetrominoVariant

/**
 * Adaptive TUI renderer.
 *
 * Portrait (tall): vertical bands — HUD (HOLD | SCORE | NEXT), board as large
 * as the width allows, then progress bar + banner slot.
 *
 * Wide (landscape / tablet): board takes the left, a stats column sits to its
 * right (hold, next queue, score, clock, records) — the board gets far bigger
 * than any portrait layout could give.
 *
 * Overlays (help/pause/game-over/countdown) dim only the board band.
 */
class TuiRenderer(private val textMeasurer: TextMeasurer) {

    data class Layout(
        val wide: Boolean,
        val cell: Float,
        val boardX: Float,
        val boardY: Float,
        val boardW: Float,
        val boardH: Float,
        // portrait HUD band
        val hudTop: Float,
        val hudH: Float,
        val colW: Float,
        // wide side panel
        val panelX: Float,
        val panelW: Float,
        val pad: Float,
        val textPx: Float,
        val smallPx: Float,
        val tinyPx: Float
    ) {
        val boardBottom: Float get() = boardY + boardH
        val subY: Float get() = boardBottom + pad * 0.55f
    }

    private val textCache = HashMap<Triple<String, Float, Boolean>, TextLayoutResult>()

    private fun measure(s: String, style: TextStyle, bold: Boolean): TextLayoutResult {
        // Cache must include font size — otherwise a string measured small gets
        // reused at a large size and every centered line stacks on top of itself.
        val key = Triple(s, style.fontSize.value, bold)
        return textCache.getOrPut(key) {
            textMeasurer.measure(
                AnnotatedString(s),
                if (bold) style.copy(fontWeight = FontWeight.Bold) else style,
                maxLines = 1,
                softWrap = false
            )
        }
    }

    companion object {
        private const val TRAIL_MS = 130f

        fun layoutFor(widthPx: Float, heightPx: Float): Layout {
            val pad = minOf(widthPx, heightPx) * 0.04f
            return if (widthPx > heightPx * 1.12f) {
                val panelW = maxOf(widthPx * 0.22f, 240f).coerceAtMost(widthPx * 0.32f)
                val cell = minOf(
                    (heightPx - pad * 2f) / 20f,
                    (widthPx - pad * 2f - panelW - pad) / 10f
                ).coerceAtLeast(4f)
                val boardW = cell * 10f
                val boardH = cell * 20f
                val groupW = boardW + pad + panelW
                val x0 = (widthPx - groupW) / 2f
                Layout(
                    wide = true, cell = cell,
                    boardX = x0, boardY = (heightPx - boardH) / 2f,
                    boardW = boardW, boardH = boardH,
                    hudTop = 0f, hudH = 0f, colW = 0f,
                    panelX = x0 + boardW + pad, panelW = panelW,
                    pad = pad,
                    textPx = (cell * 0.5f).coerceIn(13f, 28f),
                    smallPx = (cell * 0.4f).coerceIn(11f, 22f),
                    tinyPx = (cell * 0.32f).coerceIn(10f, 17f)
                )
            } else {
                val hudH = heightPx * 0.115f
                val subH = maxOf(heightPx * 0.03f, 30f)
                val cell = minOf(
                    (widthPx - pad * 2f) / 10f,
                    (heightPx - hudH - subH - pad * 3f) / 20f
                ).coerceAtLeast(4f)
                val boardW = cell * 10f
                val boardH = cell * 20f
                Layout(
                    wide = false, cell = cell,
                    boardX = (widthPx - boardW) / 2f,
                    boardY = pad + hudH + pad * 0.4f,
                    boardW = boardW, boardH = boardH,
                    hudTop = pad * 0.4f, hudH = hudH, colW = widthPx / 3f,
                    panelX = 0f, panelW = 0f,
                    pad = pad,
                    textPx = (cell * 0.46f).coerceIn(12f, 26f),
                    smallPx = (cell * 0.36f).coerceIn(10f, 20f),
                    tinyPx = (cell * 0.3f).coerceIn(9f, 16f)
                )
            }
        }

        /** Board cell size for given canvas — used for gesture tuning. */
        fun cellFor(widthPx: Float, heightPx: Float): Float {
            if (widthPx <= 0 || heightPx <= 0) return 20f
            return layoutFor(widthPx, heightPx).cell
        }
    }

    fun drawGame(scope: DrawScope, ui: UiState, tint: Color) {
        scope.drawGameInternal(ui, tint)
    }

    private fun DrawScope.drawGameInternal(ui: UiState, tint: Color) {
        val engine = ui.engine
        val W = size.width
        val H = size.height
        val l = layoutFor(W, H)
        val cell = l.cell
        val bx = l.boardX
        val by = l.boardY
        val style = TextStyle(fontSize = l.textPx.sp, fontFamily = FontFamily.Monospace)
        val small = TextStyle(fontSize = l.smallPx.sp, fontFamily = FontFamily.Monospace)
        val tiny = TextStyle(fontSize = l.tinyPx.sp, fontFamily = FontFamily.Monospace)
        val hero = TextStyle(fontSize = (l.textPx * 1.15f).sp, fontFamily = FontFamily.Monospace)
        val frameW = (cell * 0.06f).coerceAtLeast(2f)
        val nowMs = System.nanoTime() / 1_000_000

        fun frameRect(r: Rect) {
            drawRect(Palette.Frame, topLeft = r.topLeft, size = Size(r.width, frameW))
            drawRect(Palette.Frame, topLeft = Offset(r.left, r.bottom - frameW), size = Size(r.width, frameW))
            drawRect(Palette.Frame, topLeft = r.topLeft, size = Size(frameW, r.height))
            drawRect(Palette.Frame, topLeft = Offset(r.right - frameW, r.top), size = Size(frameW, r.height))
        }

        fun txt(s: String, x: Float, y: Float, color: Color, st: TextStyle = style, bold: Boolean = false) {
            val layout = measure(s, st, bold)
            drawText(layout, color = color, topLeft = Offset(x, y))
        }

        fun txtCentered(s: String, cx: Float, y: Float, color: Color, st: TextStyle = style, bold: Boolean = false): Float {
            val layout = measure(s, st, bold)
            drawText(layout, color = color, topLeft = Offset(cx - layout.size.width / 2f, y))
            return layout.size.height.toFloat()
        }

        /** Draws a piece mini centered in a 4-wide × 3-tall mini-cell box. */
        fun drawMini(variant: TetrominoVariant, shape: List<Pair<Int, Int>>, cx: Float, cy: Float, m: Float, alpha: Float = 1f) {
            val ys = shape.map { it.second }
            val yMin = ys.min()
            val yMax = ys.max()
            val h = (yMax - yMin + 1) * m
            val ox = cx - 4f * m / 2f
            val oy = cy - h / 2f
            for ((x, y) in shape) {
                drawRect(
                    Palette.variant(variant).copy(alpha = alpha),
                    topLeft = Offset(ox + x * m, oy + (yMax - y) * m),
                    size = Size(m * 0.92f, m * 0.92f)
                )
            }
        }

        val labelColor = Color(0xFF8B93A5)
        val faintColor = Palette.Faint
        val holdShape = engine.holding?.geometry?.shape
        val holdVariant = engine.holding?.variant
        val holdAlpha = if (engine.canHold) 1f else 0.3f
        val timeLabel = formatClock(engine.elapsedMs)

        // ---------- 1. STATS (portrait band across top / wide column at right) ----------
        if (l.wide) {
            val px = l.panelX
            val pw = l.panelW
            val cx = px + pw / 2f
            val mini = minOf(cell * 0.42f, pw / 5.4f)
            var y = l.boardY
            y += txtCentered("Hold", cx, y, labelColor, small) + 8f
            if (holdVariant != null && holdShape != null) drawMini(holdVariant, holdShape, cx, y + mini * 1.5f, mini, holdAlpha)
            y += mini * 3f + 14f
            drawLine(faintColor.copy(alpha = 0.5f), Offset(px + pw * 0.18f, y), Offset(px + pw * 0.82f, y), 1.5f)
            y += 12f
            y += txtCentered("Next", cx, y, labelColor, small) + 10f
            engine.next.take(3).forEachIndexed { i, n ->
                val m = mini * if (i == 0) 1f else 0.72f
                val alpha = if (i == 0) 1f else 0.65f
                drawMini(n.variant, n.geometry.shape, cx, y + m * 1.5f, m, alpha)
                y += m * 3f + 10f
            }
            y += 6f
            drawLine(faintColor.copy(alpha = 0.5f), Offset(px + pw * 0.18f, y), Offset(px + pw * 0.82f, y), 1.5f)
            y += 14f
            y += txtCentered("Score", cx, y, labelColor, small) + 4f
            y += txtCentered("%,d".format(engine.score.score), cx, y, tint, hero, bold = true) + 6f
            y += txtCentered("Level ${engine.score.level} · ${engine.score.lines} lines", cx, y, labelColor, small) + 6f
            y += txtCentered("Time $timeLabel", cx, y, labelColor, small, bold = true) + 6f
            if (ui.bestScore > 0) {
                y += txtCentered("Best ${"%,d".format(ui.bestScore)}", cx, y, faintColor, small) + 6f
            }
            if (engine.score.combo > 0) {
                y += txtCentered("Combo ×${engine.score.combo}", cx, y, tint, small, bold = true) + 6f
            }
            if (engine.b2bActive) {
                txtCentered("B2B", cx, y, tint, small, bold = true)
            }
            // level progress, pinned to the panel bottom
            val intoLevel = engine.score.lines % 10
            val barW = pw * 0.64f
            val barY = l.boardBottom - 6f
            drawRect(faintColor.copy(alpha = 0.4f), topLeft = Offset(cx - barW / 2f, barY), size = Size(barW, 5f))
            drawRect(tint, topLeft = Offset(cx - barW / 2f, barY), size = Size(barW * (intoLevel / 10f), 5f))
        } else {
            val hudTop = l.hudTop
            val colW = l.colW
            drawLine(
                faintColor.copy(alpha = 0.6f),
                Offset(colW, hudTop), Offset(colW, hudTop + l.hudH),
                strokeWidth = 1.5f
            )
            drawLine(
                faintColor.copy(alpha = 0.6f),
                Offset(colW * 2, hudTop), Offset(colW * 2, hudTop + l.hudH),
                strokeWidth = 1.5f
            )
            // Hold (left third, centered)
            val holdCx = colW / 2f
            val holdLabelH = measure("Hold", small, false).size.height.toFloat()
            txtCentered("Hold", holdCx, hudTop, labelColor, small)
            if (holdVariant != null && holdShape != null) {
                drawMini(holdVariant, holdShape, holdCx, hudTop + holdLabelH + (l.hudH - holdLabelH) / 2f, cell * 0.4f, holdAlpha)
            }
            // Score (middle third) — label small, value is the hero
            val statCx = colW * 1.5f
            var sy = hudTop
            sy += txtCentered("Score", statCx, sy, labelColor, small) + 2f
            sy += txtCentered("%,d".format(engine.score.score), statCx, sy, tint, style, bold = true) + 3f
            sy += txtCentered(
                "Level ${engine.score.level} · ${engine.score.lines} lines",
                statCx, sy, labelColor, small
            ) + 3f
            if (engine.score.combo > 0) {
                txtCentered("Combo ×${engine.score.combo}", statCx, sy, tint, small, bold = true)
            } else if (ui.bestScore > 0) {
                txtCentered("Best ${"%,d".format(ui.bestScore)}", statCx, sy, faintColor, small)
            }
            // Next (right third) — slots sized from the real label height so
            // minis never ride up over the label.
            val nextCx = colW * 2.5f
            val nextLabelH = measure("Next", small, false).size.height.toFloat()
            txtCentered("Next", nextCx, hudTop, labelColor, small)
            val slotH = (l.hudH - nextLabelH - 12f) / 3f
            val mini = minOf(cell * 0.38f, slotH / 2.4f)
            engine.next.take(3).forEachIndexed { i, n ->
                val m = mini * if (i == 0) 1f else 0.72f
                val cy = hudTop + nextLabelH + 8f + i * slotH + slotH / 2f
                drawMini(n.variant, n.geometry.shape, nextCx, cy, m, if (i == 0) 1f else 0.65f)
            }
        }

        // ---------- 2. BOARD ----------
        val boardRect = Rect(bx, by, bx + l.boardW, by + l.boardH)
        frameRect(boardRect)

        // grid dots
        val dotR = cell * 0.045f
        for (row in 0 until BOARD_HEIGHT) {
            for (k in 0 until BOARD_WIDTH) {
                drawCircle(
                    Palette.Faint,
                    radius = dotR,
                    center = Offset(bx + k * cell + cell / 2f, by + (19 - row) * cell + cell / 2f)
                )
            }
        }

        fun cellRect(k: Int, stackRow: Int): Rect {
            val screenRow = 19 - stackRow
            return Rect(bx + k * cell, by + screenRow * cell, bx + (k + 1) * cell, by + (screenRow + 1) * cell)
        }

        // hard-drop trail (fades out in ~130ms)
        if (engine.dropTrailCells.isNotEmpty()) {
            val t = (nowMs - engine.dropTrailAtMs).coerceAtLeast(0L).toFloat()
            if (t < TRAIL_MS) {
                val alpha = 0.22f * (1f - t / TRAIL_MS)
                val tc = Palette.variant(engine.falling.variant)
                for ((x, y) in engine.dropTrailCells) {
                    if (y !in 0 until BOARD_HEIGHT) continue
                    val r = cellRect(x, y)
                    drawRect(tc.copy(alpha = alpha), topLeft = r.topLeft, size = r.size)
                }
            }
        }

        // stack
        for (stackRow in 0 until BOARD_HEIGHT) {
            for (k in 0 until BOARD_WIDTH) {
                val v = engine.stack[stackRow][k] ?: continue
                val r = cellRect(k, stackRow)
                val inset = r.deflate(cell * 0.04f)
                if (stackRow in engine.clearing) {
                    // flash fades over the clear window
                    val frac = ((engine.lineClearDeadline - nowMs).toFloat() / LINE_CLEAR_DURATION_MS).coerceIn(0f, 1f)
                    drawRect(Palette.variant(TetrominoVariant.entries[v]).copy(alpha = 0.45f), topLeft = inset.topLeft, size = inset.size)
                    drawRect(Palette.White.copy(alpha = 0.25f + 0.65f * frac), topLeft = inset.topLeft, size = inset.size)
                } else {
                    drawRect(Palette.variant(TetrominoVariant.entries[v]), topLeft = inset.topLeft, size = inset.size)
                }
            }
        }

        // ghost
        engine.ghost?.let { g ->
            val gc = Palette.variant(g.variant)
            for ((x, y) in g.geometry.shape) {
                if (y !in 0 until BOARD_HEIGHT) continue
                val r = cellRect(x, y).deflate(cell * 0.04f)
                drawRect(gc.copy(alpha = 0.28f), topLeft = r.topLeft, size = r.size)
                drawRect(gc.copy(alpha = 0.8f), topLeft = r.topLeft, size = r.size, style = Stroke(width = cell * 0.05f))
            }
        }

        // falling
        val fc = Palette.variant(engine.falling.variant)
        for ((x, y) in engine.falling.geometry.shape) {
            if (y !in 0 until BOARD_HEIGHT) continue
            val r = cellRect(x, y).deflate(cell * 0.04f)
            if (engine.locking) drawRect(fc.copy(alpha = 0.75f), topLeft = r.topLeft, size = r.size)
            else drawRect(fc, topLeft = r.topLeft, size = r.size)
        }

        // ---------- 3. SUB BAR (progress + banner slot) ----------
        val subY = l.subY
        // Progress toward next level. No label — HUD already shows level/lines.
        val intoLevel = engine.score.lines % 10
        val barW = l.boardW
        drawRect(faintColor.copy(alpha = 0.4f), topLeft = Offset(bx, subY), size = Size(barW, 5f))
        drawRect(fc, topLeft = Offset(bx, subY), size = Size(barW * (intoLevel / 10f), 5f))

        // Banner floats above the progress bar (with backdrop) so it never
        // collides with the LV line.
        ui.banner?.let { b ->
            val label = measure(b.take(24), small, true)
            val bw = label.size.width + 28f
            val bh = label.size.height + 14f
            val dx = bx + l.boardW / 2f - bw / 2f
            val dy = subY - bh - 8f
            drawRect(Color(0xE60A0E14), topLeft = Offset(dx, dy), size = Size(bw, bh))
            drawRect(
                Palette.White.copy(alpha = 0.7f),
                topLeft = Offset(dx, dy),
                size = Size(bw, bh),
                style = Stroke(width = 1.5f)
            )
            drawText(
                label, color = Palette.White,
                topLeft = Offset(dx + 14f, dy + (bh - label.size.height) / 2f)
            )
        }

        // lock bar (thin, under progress)
        if (engine.locking && engine.lockDeadline != Long.MAX_VALUE && !engine.ended) {
            val frac = (1f - (engine.lockDeadline - nowMs).toFloat() / LOCK_DURATION_MS).coerceIn(0f, 1f)
            drawRect(fc, topLeft = Offset(bx, subY - 4f), size = Size(barW * frac, 3f))
        }

        // ---------- OVERLAY DIM + countdown ----------
        if (ui.showHelp || !ui.started || engine.ended || ui.paused) {
            drawRect(Color(0xD60A0E14), topLeft = boardRect.topLeft, size = boardRect.size)
        }
        if (ui.countdown != null && !engine.ended) {
            drawRect(Color(0xB30A0E14), topLeft = boardRect.topLeft, size = boardRect.size)
            val big = TextStyle(fontSize = (cell * 2.6f).sp, fontFamily = FontFamily.Monospace)
            txtCentered(ui.countdown.toString(), bx + l.boardW / 2f, by + l.boardH / 2f - cell * 2.1f, Palette.White, big, bold = true)
            txtCentered("get ready", bx + l.boardW / 2f, by + l.boardH / 2f + cell * 1.4f, labelColor, small)
        }
    }

    private fun formatClock(ms: Long): String {
        val total = (ms.coerceAtLeast(0L) / 100L)
        val s = total / 10
        return "%d:%02d.%d".format(s / 60, s % 60, total % 10)
    }
}
