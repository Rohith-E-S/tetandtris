package com.rohith.tetandris.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BagTest {
    @Test
    fun `first 7 pieces form one full set`() {
        val bag = Bag(seed = 1)
        val seen = mutableSetOf<TetrominoVariant>()
        repeat(7) { seen.add(bag.getNext().variant) }
        assertEquals(TetrominoVariant.entries.toSet(), seen)
    }

    @Test
    fun `every window of 7 pieces has all variants`() {
        val bag = Bag(seed = 42)
        repeat(10) {
            val seen = mutableSetOf<TetrominoVariant>()
            repeat(7) { seen.add(bag.getNext().variant) }
            assertEquals(TetrominoVariant.entries.toSet(), seen)
        }
    }

    @Test
    fun `next queue shows 3 pieces`() {
        val bag = Bag(seed = 7)
        assertEquals(3, bag.next.size)
    }
}

class RotationTest {
    @Test
    fun `T piece rotates 4x back to original`() {
        val t = Tetromino.new(TetrominoVariant.T)
        t.geometry.transform(3, 10)
        val original = t.geometry.shape.toSet()
        repeat(4) { t.geometry.rotate(true) }
        assertEquals(original, t.geometry.shape.toSet())
        assertEquals(CardinalDirection.North, t.geometry.direction)
    }

    @Test
    fun `I piece roundtrip`() {
        val i = Tetromino.new(TetrominoVariant.I)
        i.geometry.transform(3, 10)
        val original = i.geometry.shape.toSet()
        repeat(4) { i.geometry.rotate(true) }
        assertEquals(original, i.geometry.shape.toSet())
        assertEquals(CardinalDirection.North, i.geometry.direction)
    }

    @Test
    fun `rotate near wall kicks piece back inside`() {
        val engine = GameEngine(startLevel = 0, seed = 1)
        repeat(10) {
            if (!engine.falling.hittingRight(engine.stack)) {
                engine.shift(ShiftDirection.Right, now = 0)
            }
        }
        val xMax = engine.falling.geometry.shape.maxOf { it.first }
        assertEquals(BOARD_WIDTH - 1, xMax)
        engine.rotate(RotationDirection.Clockwise, now = 0)
        engine.falling.geometry.shape.forEach { (x, y) ->
            assertTrue("x=$x y=$y", x in 0 until BOARD_WIDTH && y in 0 until BOARD_HEIGHT)
        }
        assertNotNull(engine.ghost)
    }
}

class LineClearTest {
    @Test
    fun `full row is detected and cleared`() {
        val stack = Stack()
        for (x in 0 until BOARD_WIDTH) stack[0][x] = 5
        stack.lineClear(setOf(0))
        assertTrue(stack[0].all { it == null })
        assertEquals(BOARD_HEIGHT, stack.rows.size)
    }

    @Test
    fun `cleared bottom row shifts stack down`() {
        val stack = Stack()
        stack[1][3] = 4
        for (x in 0 until BOARD_WIDTH) stack[0][x] = 5
        stack.lineClear(setOf(0))
        assertEquals(4, stack[0][3])
        assertNull(stack[1][3])
    }

    @Test
    fun `middle row clear preserves ordering`() {
        val stack = Stack()
        stack[0][0] = 1
        stack[2][0] = 2
        stack[4][0] = 3
        for (x in 0 until BOARD_WIDTH) stack[3][x] = 6
        stack.lineClear(setOf(3))
        // Rows below the cleared row keep their position
        assertEquals(1, stack[0][0])
        assertEquals(2, stack[2][0])
        // Rows above shift down by one
        assertEquals(3, stack[3][0])
        assertTrue(stack[4].all { it == null })
    }

    @Test
    fun `multiple rows clear at once`() {
        val stack = Stack()
        for (x in 0 until BOARD_WIDTH) {
            stack[0][x] = 1
            stack[1][x] = 2
        }
        stack[2][5] = 9
        stack.lineClear(setOf(0, 1))
        assertEquals(9, stack[0][5])
        assertTrue(stack[1].all { it == null })
    }
}

class ScoreTest {
    @Test
    fun `level increases every 10 lines`() {
        val score = Score(startLevel = 0)
        repeat(3) { score.scoreClear(ClearKind.Tetris) } // 12 lines
        assertEquals(1, score.level)
        assertEquals(12, score.lines)
    }

    @Test
    fun `single at level 1 scores 100 with no combo bonus`() {
        val score = Score(startLevel = 1)
        score.scoreClear(ClearKind.Single)
        assertEquals(100L, score.score)
    }

    @Test
    fun `combo increments and adds bonus`() {
        val score = Score(startLevel = 1)
        score.scoreClear(ClearKind.Single) // combo 0 -> +0
        assertEquals(100L, score.score)
        score.scoreClear(ClearKind.Single) // combo 1 -> +50*1*1
        assertEquals(100L + 100L + 50L, score.score)
    }

    @Test
    fun `combo resets on no clear`() {
        val score = Score(startLevel = 0)
        score.scoreClear(ClearKind.Single)
        assertEquals(0, score.combo)
        score.resetCombo()
        assertEquals(-1, score.combo)
    }

    @Test
    fun `tetris at level 1 scores 800`() {
        val score = Score(startLevel = 1)
        score.scoreClear(ClearKind.Tetris)
        assertEquals(800L, score.score)
    }

    @Test
    fun `t-spin double scores 1200 times level`() {
        val score = Score(startLevel = 2)
        score.scoreClear(ClearKind.TSpinDouble)
        assertEquals(1200L * 2, score.score)
    }

    @Test
    fun `back-to-back tetris scores 1_5x base`() {
        val score = Score(startLevel = 1)
        score.scoreClear(ClearKind.Tetris, backToBack = true)
        assertEquals(1200L, score.score)
    }

    @Test
    fun `b2b only applies to tetris and t-spins`() {
        assertTrue(ClearKind.Tetris.isB2bQualified)
        assertTrue(ClearKind.TSpinSingle.isB2bQualified)
        assertTrue(ClearKind.TSpinTriple.isB2bQualified)
        assertFalse(ClearKind.Single.isB2bQualified)
        assertFalse(ClearKind.Double.isB2bQualified)
        assertFalse(ClearKind.Triple.isB2bQualified)
    }
}

class GravityTest {
    @Test
    fun `gravity curve matches guideline`() {
        assertTrue(dropIntervalMs(5) < dropIntervalMs(1))
        assertTrue(dropIntervalMs(10) < dropIntervalMs(5))
        assertTrue(dropIntervalMs(20) < dropIntervalMs(10))
    }

    @Test
    fun `level 1 drop rate is about 1 second`() {
        val ms = dropIntervalMs(1)
        assertTrue("was $ms", ms in 990..1010)
    }
}

class EngineTest {
    @Test
    fun `hard drop places piece and spawns next`() {
        val engine = GameEngine(startLevel = 0, seed = 3)
        val firstVariant = engine.falling.variant
        engine.hardDrop(now = 0)
        // A block of the piece now exists in the stack
        assertTrue(engine.stack.rows.flatten().count { it != null } >= 4)
        // New falling piece is different from the dropped one (or same variant from bag)
        if (firstVariant != TetrominoVariant.O) {
            assertTrue(engine.falling.geometry.shape.any { it.second > 15 })
        }
        assertNotNull(engine.ghost)
    }

    @Test
    fun `ghost tracks falling piece column`() {
        val engine = GameEngine(startLevel = 0, seed = 5)
        val ghostX = engine.ghost!!.geometry.shape.map { it.first }.toSet()
        val fallingX = engine.falling.geometry.shape.map { it.first }.toSet()
        assertEquals(fallingX, ghostX)
    }

    @Test
    fun `hold swaps piece once then locks`() {
        val engine = GameEngine(startLevel = 0, seed = 9)
        val original = engine.falling.variant
        engine.hold(now = 0)
        assertFalse(engine.canHold)
        val held = engine.falling.variant
        engine.hold(now = 0)
        assertEquals(held, engine.falling.variant)
        assertEquals(original, engine.holding!!.variant)
    }

    @Test
    fun `update ticks gravity after full interval`() {
        val engine = GameEngine(startLevel = 1, seed = 11)
        val y0 = engine.falling.geometry.shape.minOf { it.second }
        engine.update(500, now = 500)
        val y1 = engine.falling.geometry.shape.minOf { it.second }
        assertEquals(y0, y1) // half interval: no drop yet
        engine.update(600, now = 1100)
        val y2 = engine.falling.geometry.shape.minOf { it.second }
        assertTrue("y0=$y0 y2=$y2", y2 < y0)
    }

    @Test
    fun `line clear flashes then collapses`() {
        val engine = GameEngine(startLevel = 1, seed = 13)
        // Build a stack with exactly one gap in the bottom row
        for (x in 0 until BOARD_WIDTH) {
            if (x != 4) engine.stack[0][x] = 0
        }
        // Spawn a vertical I positioned over the gap
        val v = Tetromino.new(TetrominoVariant.I)
        v.geometry.rotate(true)
        v.geometry.transform(4 - v.geometry.shape.minOf { p -> p.first }, 19 - v.geometry.shape.minOf { p -> p.second })
        engine.debugSpawn(v)
        engine.hardDrop(now = 0)
        // Bottom row should now be full and flagged for clearing
        assertTrue(engine.clearing.isNotEmpty())
        // After flash duration, update collapses it
        engine.update(10, now = 200)
        assertTrue(engine.clearing.isEmpty())
        // Only the I column remains above the cleared row
        assertEquals((0 until BOARD_WIDTH).map { if (it == 4) 0 else null }, engine.stack[0].toList())
        assertTrue(engine.score.score >= 100L) // single at level 1 + hard drop points
        assertEquals(1, engine.score.lines)
    }

    @Test
    fun `game over when stack reaches top`() {
        val engine = GameEngine(startLevel = 0, seed = 2)
        for (y in 16..19) {
            for (x in 0 until BOARD_WIDTH) engine.stack[y][x] = 1
        }
        engine.hardDrop(now = 0)
        assertTrue(engine.lost)
    }

    @Test
    fun `shift moves piece and respects walls`() {
        val engine = GameEngine(startLevel = 0, seed = 15)
        val x0 = engine.falling.geometry.shape.minOf { it.first }
        engine.shift(ShiftDirection.Left, now = 0)
        val x1 = engine.falling.geometry.shape.minOf { it.first }
        assertTrue(x1 < x0)
        // Keep shifting left until blocked
        repeat(20) { engine.shift(ShiftDirection.Left, now = 0) }
        assertEquals(0, engine.falling.geometry.shape.minOf { it.first })
    }

    @Test
    fun `hard drop records a fading trail`() {
        val engine = GameEngine(startLevel = 1, seed = 17)
        engine.hardDrop(now = 0)
        assertTrue(engine.dropTrailCells.isNotEmpty())
        assertTrue(engine.dropTrailAtMs >= 0)
        engine.dropTrailCells.forEach { (x, y) ->
            assertTrue(x in 0 until BOARD_WIDTH && y in 0 until BOARD_HEIGHT)
        }
    }

    @Test
    fun `pieces counter increments per placement`() {
        val engine = GameEngine(startLevel = 1, seed = 19)
        assertEquals(0, engine.pieces)
        engine.hardDrop(now = 0)
        assertEquals(1, engine.pieces)
        engine.hardDrop(now = 1000)
        assertEquals(2, engine.pieces)
    }

    @Test
    fun `clock advances with update and not on its own`() {
        val engine = GameEngine(startLevel = 1, seed = 21)
        assertEquals(0L, engine.elapsedMs)
        engine.update(250, now = 250)
        assertEquals(250L, engine.elapsedMs)
        engine.update(0, now = 250)
        assertEquals(250L, engine.elapsedMs)
    }

    @Test
    fun `sprint ends in a win at 40 lines`() {
        val engine = GameEngine(mode = GameMode.Sprint, startLevel = 1, seed = 23)
        var nowMs = 0L
        repeat(10) {
            // Nine filled columns, rows 0..15 (headroom above for spawning) —
            // a vertical I in column 0 clears 4 rows.
            for (y in 0..15) for (x in 1 until BOARD_WIDTH) engine.stack[y][x] = 1
            val v = Tetromino.new(TetrominoVariant.I)
            v.geometry.rotate(true)
            v.geometry.transform(
                0 - v.geometry.shape.minOf { it.first },
                3 - v.geometry.shape.minOf { it.second }
            )
            engine.debugSpawn(v)
            engine.hardDrop(now = nowMs)
            nowMs += LINE_CLEAR_DURATION_MS + 10
            engine.update(10, now = nowMs)
        }
        assertEquals(SPRINT_TARGET_LINES, engine.score.lines)
        assertTrue(engine.ended)
        assertTrue(engine.won)
        assertFalse(engine.lost)
    }

    @Test
    fun `ultra ends when the clock runs out`() {
        val engine = GameEngine(mode = GameMode.Ultra, startLevel = 1, seed = 25)
        assertFalse(engine.ended)
        engine.update(ULTRA_DURATION_MS + 10, now = ULTRA_DURATION_MS + 10)
        assertTrue(engine.ended)
        assertFalse(engine.won)
        assertTrue(engine.lost)
    }

    @Test
    fun `marathon never wins on its own`() {
        val engine = GameEngine(mode = GameMode.Marathon, startLevel = 1, seed = 27)
        engine.update(ULTRA_DURATION_MS * 3, now = ULTRA_DURATION_MS * 3)
        assertFalse(engine.ended)
    }

    @Test
    fun `b2b banner prefixes a chained tetris`() {
        val engine = GameEngine(startLevel = 1, seed = 29)
        var nowMs = 0L
        // Fill rows 0..15 col 1..9 twice (headroom above for spawning); each
        // vertical I in col 0 clears 4 lines, so two clears chain a B2B.
        repeat(2) {
            for (y in 0..15) for (x in 1 until BOARD_WIDTH) engine.stack[y][x] = 1
            val v = Tetromino.new(TetrominoVariant.I)
            v.geometry.rotate(true)
            v.geometry.transform(
                0 - v.geometry.shape.minOf { it.first },
                3 - v.geometry.shape.minOf { it.second }
            )
            engine.debugSpawn(v)
            engine.hardDrop(now = nowMs)
            nowMs += LINE_CLEAR_DURATION_MS + 10
            engine.update(10, now = nowMs)
        }
        assertTrue(engine.b2bActive)
        assertEquals(1, engine.b2bChains)
        assertTrue(engine.lastEventMessage?.startsWith("B2B") == true)
    }
}
