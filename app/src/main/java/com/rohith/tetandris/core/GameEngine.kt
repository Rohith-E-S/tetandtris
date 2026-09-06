package com.rohith.tetandris.core

const val LOCK_RESET_LIMIT = 15
const val LOCK_DURATION_MS = 500L
const val LINE_CLEAR_DURATION_MS = 125L
const val SOFT_DROP_INTERVAL_MS = 50L
const val SPRINT_TARGET_LINES = 40
const val ULTRA_DURATION_MS = 120_000L

enum class GameMode(val label: String) {
    Marathon("Marathon"), Sprint("Sprint"), Ultra("Ultra")
}

private val JLSTZ_OFFSETS = arrayOf(
    arrayOf(0 to 0, 0 to 0, 0 to 0, 0 to 0, 0 to 0),
    arrayOf(0 to 0, 1 to 0, 1 to -1, 0 to 2, 1 to 2),
    arrayOf(0 to 0, 0 to 0, 0 to 0, 0 to 0, 0 to 0),
    arrayOf(0 to 0, -1 to 0, -1 to -1, 0 to 2, -1 to 2),
)

private val I_OFFSETS = arrayOf(
    arrayOf(0 to 0, -1 to 0, 2 to 0, -1 to 0, 2 to 0),
    arrayOf(-1 to 0, 0 to 0, 0 to 0, 0 to 1, 0 to -2),
    arrayOf(-1 to 1, 1 to 1, -2 to 1, 1 to 0, -2 to 0),
    arrayOf(0 to 1, 0 to 1, 0 to 1, 0 to -1, 0 to 2),
)

private val O_OFFSETS = arrayOf(
    arrayOf(0 to 0, 0 to 0, 0 to 0, 0 to 0, 0 to 0),
    arrayOf(0 to -1, 0 to 0, 0 to 0, 0 to 0, 0 to 0),
    arrayOf(-1 to -1, 0 to 0, 0 to 0, 0 to 0, 0 to 0),
    arrayOf(-1 to 0, 0 to 0, 0 to 0, 0 to 0, 0 to 0),
)

fun dropIntervalMs(level: Int): Long {
    val lvl = maxOf(level, 1)
    val dropRate = Math.pow(0.8 - (lvl - 1) * 0.007, (lvl - 1).toDouble())
    return (dropRate * 1000).toLong().coerceAtLeast(1L)
}

class GameEngine(
    val mode: GameMode = GameMode.Marathon,
    startLevel: Int = 1,
    seed: Long = System.nanoTime()
) {
    private val bag = Bag(seed)
    val stack = Stack()
    val score = Score(startLevel)

    var falling: Tetromino = bag.getNext()
        private set
    var ghost: Tetromino? = null
        private set
    var holding: Tetromino? = null
        private set
    val next: List<Tetromino> get() = bag.next

    var clearing: Set<Int> = emptySet()
        private set
    var canHold = true
        private set
    /** Game finished for any reason (top-out, sprint complete, time up). */
    var ended = false
        private set
    /** Game finished by reaching the mode's goal (sprint). */
    var won = false
        private set
    val lost: Boolean get() = ended && !won
    var locking = false
        private set
    var lockResetCount = 0
        private set
    /** Last line-clear / special event for UI banners, e.g. "TETRIS! +800". */
    var lastEventMessage: String? = null
        private set
    var lastEventAtMs: Long = 0L
        private set

    // Run stats — clock only advances while update() runs, so pausing freezes it.
    var elapsedMs = 0L
        private set
    var pieces = 0
        private set
    var tetrises = 0
        private set
    var tSpins = 0
        private set
    var maxCombo = 0
        private set
    var b2bChains = 0
        private set
    var b2bActive = false
        private set

    /** Cells vacated by the last hard drop (column to rows), for the fade trail. */
    var dropTrailCells: List<Pair<Int, Int>> = emptyList()
        private set
    var dropTrailAtMs = 0L
        private set

    private var lastActionWasRotate = false
    private var pendingTSpin = false

    var softDropActive = false

    var lockDeadline: Long = Long.MAX_VALUE
        private set
    var lineClearDeadline: Long = Long.MAX_VALUE
        private set
    private var dropAccumulatorMs = 0L

    init {
        falling.startPosTransform(stack)
        updateGhost()
    }

    /** Test hook: force the falling piece. */
    fun debugSpawn(t: Tetromino) {
        falling = t
        locking = false
        lockResetCount = 0
        lockDeadline = Long.MAX_VALUE
        updateGhost()
    }

    private fun resetLockTimer(now: Long) {
        if (lockResetCount < LOCK_RESET_LIMIT) {
            lockDeadline = now + LOCK_DURATION_MS
        }
    }

    private fun updateGhost() {
        val g = falling.duplicate()
        while (!g.hittingBottom(stack)) {
            g.geometry.transform(0, -1)
        }
        ghost = if (g.overlapping(stack)) null else g
    }

    fun tSpinCheck(): Boolean {
        if (!lastActionWasRotate || falling.variant != TetrominoVariant.T) return false
        val (cx, cy) = falling.geometry.center
        val corners = listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
        val occupied = corners.count { (dx, dy) ->
            val x = cx + dx
            val y = cy + dy
            x !in 0 until BOARD_WIDTH || y !in 0 until BOARD_HEIGHT || stack[y][x] != null
        }
        return occupied >= 3
    }

    private fun markClear(now: Long) {
        val marked = mutableSetOf<Int>()
        for (i in 0 until BOARD_HEIGHT) {
            if (stack[i].all { it != null }) marked.add(i)
        }
        clearing = marked
        if (marked.isEmpty()) {
            score.resetCombo()
        } else {
            lineClearDeadline = now + LINE_CLEAR_DURATION_MS
        }
    }

    private fun lineClear(now: Long) {
        val numCleared = clearing.size
        val perfectClear = stack.rows.flatten().all { it == null }
        val isTSpin = pendingTSpin
        stack.lineClear(clearing)
        val clearKind = ClearKind.fromState(numCleared, perfectClear, isTSpin)
        val b2b = clearKind.isB2bQualified && b2bActive
        val before = score.score
        score.scoreClear(clearKind, b2b)
        val gained = score.score - before
        if (clearKind == ClearKind.Tetris) tetrises++
        if (isTSpin) tSpins++
        if (clearKind.isB2bQualified) {
            if (b2bActive) b2bChains++
            b2bActive = true
        } else {
            b2bActive = false
        }
        if (score.combo > maxCombo) maxCombo = score.combo
        val prefix = if (b2b) "B2B " else ""
        lastEventMessage = when (clearKind) {
            ClearKind.Tetris -> "${prefix}Tetris +$gained"
            ClearKind.TSpinSingle -> "${prefix}T-spin +$gained"
            ClearKind.TSpinDouble -> "${prefix}T-spin double +$gained"
            ClearKind.TSpinTriple -> "${prefix}T-spin triple +$gained"
            ClearKind.PerfectClear -> "Perfect clear +$gained"
            ClearKind.Triple -> "Triple +$gained"
            ClearKind.Double -> "Double +$gained"
            ClearKind.Single -> if (score.combo > 0) "Combo ×${score.combo} +$gained" else null
        }
        lastEventAtMs = now
        updateGhost()
        clearing = emptySet()
        lineClearDeadline = Long.MAX_VALUE
        pendingTSpin = false
        if (mode == GameMode.Sprint && score.lines >= SPRINT_TARGET_LINES) {
            ended = true
            won = true
        }
    }

    private fun place(now: Long): Boolean {
        if (!falling.hittingBottom(stack)) return true

        pendingTSpin = tSpinCheck()

        if (!stack.add(falling)) {
            ended = true
            return false
        }
        pieces++

        markClear(now)

        val nextPiece = bag.getNext()
        nextPiece.startPosTransform(stack)
        falling = nextPiece
        locking = false
        canHold = true
        lockResetCount = 0
        lockDeadline = Long.MAX_VALUE
        dropAccumulatorMs = 0

        if (falling.overlapping(stack)) {
            ended = true
            return false
        }

        updateGhost()
        lastActionWasRotate = false
        return true
    }

    fun shift(direction: ShiftDirection, now: Long) {
        if (lockResetCount == LOCK_RESET_LIMIT) {
            if (!place(now)) return
        }
        when (direction) {
            ShiftDirection.Left ->
                if (!falling.hittingLeft(stack)) falling.geometry.transform(-1, 0)
            ShiftDirection.Right ->
                if (!falling.hittingRight(stack)) falling.geometry.transform(1, 0)
        }
        updateGhost()
        lockResetCount++
        resetLockTimer(now)
        lastActionWasRotate = false
    }

    fun rotate(direction: RotationDirection, now: Long) {
        val rotated = falling.duplicate()
        rotated.geometry.rotate(direction == RotationDirection.Clockwise)

        val offsetTable = when (falling.variant) {
            TetrominoVariant.I -> I_OFFSETS
            TetrominoVariant.O -> O_OFFSETS
            else -> JLSTZ_OFFSETS
        }

        for (i in 0 until offsetTable[0].size) {
            val offsetX = offsetTable[rotated.geometry.direction.ordinal][i].first -
                offsetTable[falling.geometry.direction.ordinal][i].first
            val offsetY = offsetTable[rotated.geometry.direction.ordinal][i].second -
                offsetTable[falling.geometry.direction.ordinal][i].second

            rotated.geometry.transform(-offsetX, -offsetY)

            if (!rotated.overlapping(stack)) {
                falling = rotated
                lockResetCount++
                updateGhost()
                resetLockTimer(now)
                lastActionWasRotate = true
                return
            }
            rotated.geometry.transform(offsetX, offsetY)
        }
    }

    fun hold(now: Long) {
        if (!canHold) return
        val swapVariant = holding?.variant ?: bag.getNext().variant
        val swap = Tetromino.new(swapVariant)
        swap.startPosTransform(stack)
        holding = Tetromino.new(falling.variant)
        falling = swap
        canHold = false
        lockResetCount = 0
        lockDeadline = Long.MAX_VALUE
        dropAccumulatorMs = 0
        locking = falling.hittingBottom(stack)
        updateGhost()
        lastActionWasRotate = false
    }

    /** Gravity drop: move down, resetting lock state (mirrors TUI drop()). */
    private fun gravityDrop(now: Long, awardScore: Boolean) {
        if (!falling.hittingBottom(stack)) {
            falling.geometry.transform(0, -1)
            if (awardScore) score.score += 1
            lockResetCount = 0
            resetLockTimer(now)
        }
        locking = falling.hittingBottom(stack)
    }

    fun hardDrop(now: Long) {
        val beforeByCol = falling.geometry.shape.groupBy({ it.first }, { it.second })
        while (!falling.hittingBottom(stack)) {
            falling.geometry.transform(0, -1)
            score.score += 2
        }
        val afterByCol = falling.geometry.shape.groupBy({ it.first }, { it.second })
        dropTrailCells = buildList {
            beforeByCol.forEach { (x, ys) ->
                val beforeTop = ys.max()
                val afterTop = afterByCol[x]?.max() ?: beforeTop
                for (y in (afterTop + 1)..beforeTop) add(x to y)
            }
        }
        dropTrailAtMs = now
        place(now)
        lastActionWasRotate = false
    }

    /** Advances game state. Returns true when a visual change occurred. */
    fun update(deltaMs: Long, now: Long): Boolean {
        if (ended) return false

        elapsedMs += deltaMs
        if (mode == GameMode.Ultra && elapsedMs >= ULTRA_DURATION_MS) {
            ended = true
            return true
        }

        if (clearing.isNotEmpty()) {
            if (now >= lineClearDeadline) {
                lineClear(now)
                return true
            }
            return false
        }

        // Gravity
        dropAccumulatorMs += deltaMs
        val gravityInterval = dropIntervalMs(score.level)
        val interval = if (softDropActive) minOf(gravityInterval, SOFT_DROP_INTERVAL_MS) else gravityInterval
        var moved = false
        while (dropAccumulatorMs >= interval) {
            dropAccumulatorMs -= interval
            if (!falling.hittingBottom(stack)) {
                gravityDrop(now, awardScore = softDropActive)
                moved = true
            } else {
                break
            }
        }

        // Resting: arm lock timer once
        if (falling.hittingBottom(stack)) {
            val wasLocking = locking
            locking = true
            if (lockDeadline == Long.MAX_VALUE) {
                resetLockTimer(now)
            }
            // Lock delay expiry
            if (now >= lockDeadline) {
                place(now)
                return true
            }
            if (!wasLocking) moved = true
        } else {
            if (locking) moved = true
            locking = false
        }
        return moved
    }

    private fun Tetromino.duplicate(): Tetromino = copy(
        geometry = Geometry(geometry.shape.map { it }, geometry.center, geometry.direction)
    )
}
