package com.rohith.tetandris.core

enum class ClearKind {
    PerfectClear, Single, Double, Triple, Tetris, TSpinSingle, TSpinDouble, TSpinTriple;

    companion object {
        fun fromState(numCleared: Int, perfectClear: Boolean, isTSpin: Boolean): ClearKind =
            when {
                perfectClear -> PerfectClear
                numCleared == 1 && isTSpin -> TSpinSingle
                numCleared == 2 && isTSpin -> TSpinDouble
                numCleared == 3 && isTSpin -> TSpinTriple
                numCleared == 1 -> Single
                numCleared == 2 -> Double
                numCleared == 3 -> Triple
                numCleared == 4 -> Tetris
                else -> throw IllegalStateException("Invalid clear type")
            }
    }

    val lineClearCount: Int
        get() = when (this) {
            Single, TSpinSingle -> 1
            Double, TSpinDouble -> 2
            Triple, TSpinTriple -> 3
            Tetris, PerfectClear -> 4
        }

    /** Clears that can extend a back-to-back chain (guideline: Tetris and T-spins). */
    val isB2bQualified: Boolean
        get() = this == Tetris || this == TSpinSingle || this == TSpinDouble || this == TSpinTriple
}

class Score(startLevel: Int) {
    val startLevel = startLevel
    var level: Int = startLevel
        private set
    var lines: Int = 0
        private set
    var score: Long = 0
    var combo: Int = -1
        private set

    fun scoreClear(clearType: ClearKind, backToBack: Boolean = false) {
        val lineClearCount = clearType.lineClearCount
        lines += lineClearCount
        level = startLevel + lines / 10
        combo += 1
        val base = when (clearType) {
            ClearKind.PerfectClear -> when (lineClearCount) {
                1 -> 800L; 2 -> 1200L; 3 -> 1800L; 4 -> 2000L; else -> 0L
            }
            ClearKind.Single -> 100L
            ClearKind.Double -> 300L
            ClearKind.Triple -> 500L
            ClearKind.Tetris -> 800L
            ClearKind.TSpinSingle -> 800L
            ClearKind.TSpinDouble -> 1200L
            ClearKind.TSpinTriple -> 1600L
        }
        // B2B chain multiplies the base by 1.5 (integer-safe: x3 then /2).
        score += base * level * (if (backToBack) 3L else 2L) / 2L
        score += 50L * combo * level
    }

    fun resetCombo() {
        combo = -1
    }
}
