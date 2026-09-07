package com.rohith.tetandris.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.rohith.tetandris.core.GameEngine
import com.rohith.tetandris.core.GameMode
import com.rohith.tetandris.core.RotationDirection
import com.rohith.tetandris.core.ShiftDirection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ControlMode { GESTURES, BUTTONS, BOTH }

data class UiState(
    val engine: GameEngine,
    val frame: Long,
    val started: Boolean = false,
    val paused: Boolean = false,
    val showHelp: Boolean = false,
    val controlMode: ControlMode = ControlMode.GESTURES,
    /** Mode + level applied to the next game started. */
    val mode: GameMode = GameMode.Marathon,
    val startLevel: Int = 1,
    /** Records for the selected mode (sprint's "best" is a time, in bestSprintMs). */
    val bestScore: Long = 0L,
    val bestLines: Int = 0,
    val bestSprintMs: Long = 0L,
    /** Seconds remaining before resume, while counting down. */
    val countdown: Int? = null,
    val banner: String? = null
)

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("tetandris", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        UiState(
            engine = GameEngine(),
            frame = 0,
            started = false,
            paused = false,
            showHelp = !prefs.getBoolean("seen_tutorial", false),
            controlMode = runCatching {
                ControlMode.valueOf(prefs.getString("control_mode", "GESTURES") ?: "GESTURES")
            }.getOrDefault(ControlMode.GESTURES),
            mode = runCatching {
                GameMode.valueOf(prefs.getString("mode", "Marathon") ?: "Marathon")
            }.getOrDefault(GameMode.Marathon),
            startLevel = prefs.getInt("start_level", 1).coerceIn(1, 15),
            bestScore = 0L,
            bestLines = 0,
            bestSprintMs = 0L
        ).let { s ->
            s.copy(bestScore = bestScoreFor(s.mode), bestLines = prefs.getInt("best_lines", 0), bestSprintMs = prefs.getLong(BEST_SPRINT_MS, 0L))
        }
    )
    val state: StateFlow<UiState> = _state

    private var lastFrameTime = System.nanoTime()
    private var lastSeenEventAt = 0L
    private var countdownDeadlineMs = 0L

    /** Advances game state. Emits only when something visibly changed. */
    fun tickFrame() {
        val s = _state.value
        val nowMs = System.nanoTime() / 1_000_000
        if (s.countdown != null) {
            if (nowMs >= countdownDeadlineMs) {
                val next = s.countdown!! - 1
                if (next <= 0) {
                    _state.value = s.copy(countdown = null, paused = false, frame = s.frame + 1)
                    lastFrameTime = System.nanoTime()
                } else {
                    countdownDeadlineMs = nowMs + COUNTDOWN_STEP_MS
                    _state.value = s.copy(countdown = next, frame = s.frame + 1)
                }
            }
            return
        }
        if (!s.started || s.paused || s.showHelp) {
            lastFrameTime = System.nanoTime()
            // Expire banner even while paused so it doesn't stick.
            if (s.banner != null) emit()
            return
        }
        val engine = s.engine
        if (engine.ended) {
            persistBestIfNeeded()
            return
        }
        val nowNs = System.nanoTime()
        val deltaMs = (nowNs - lastFrameTime) / 1_000_000
        lastFrameTime = nowNs
        val changed = engine.update(deltaMs.coerceAtMost(100), nowMs)
        val newBanner = if (engine.lastEventAtMs != lastSeenEventAt && engine.lastEventMessage != null) {
            lastSeenEventAt = engine.lastEventAtMs
            engine.lastEventMessage
        } else if (s.banner != null && nowMs - lastSeenEventAt > 1200) {
            null
        } else {
            s.banner
        }
        if (engine.ended) persistBestIfNeeded()
        if (changed || newBanner != s.banner) {
            _state.value = s.copy(banner = newBanner, frame = s.frame + 1)
        } else if (engine.locking) {
            // Re-emit for lock-delay progress bar animation.
            _state.value = s.copy(frame = s.frame + 1)
        }
    }

    fun setMode(mode: GameMode) {
        val s = _state.value
        if (s.started && !s.engine.ended) return
        prefs.edit().putString("mode", mode.name).apply()
        _state.value = s.copy(
            mode = mode,
            bestScore = bestScoreFor(mode),
            bestLines = if (mode == GameMode.Marathon) prefs.getInt("best_lines", 0) else 0,
            bestSprintMs = if (mode == GameMode.Sprint) prefs.getLong(BEST_SPRINT_MS, 0L) else 0L,
            frame = s.frame + 1
        )
    }

    fun setStartLevel(level: Int) {
        val s = _state.value
        if (s.started && !s.engine.ended) return
        val coerced = level.coerceIn(1, 15)
        prefs.edit().putInt("start_level", coerced).apply()
        _state.value = s.copy(startLevel = coerced, frame = s.frame + 1)
    }

    fun startGame() {
        val s = _state.value
        markSeen()
        _state.value = freshState(s, started = true)
        lastFrameTime = System.nanoTime()
    }

    fun toMenu() {
        persistBestIfNeeded()
        _state.value = freshState(_state.value, started = false)
        lastFrameTime = System.nanoTime()
    }

    private fun freshState(s: UiState, started: Boolean): UiState {
        val mode = s.mode
        val level = if (mode == GameMode.Sprint) 1 else s.startLevel
        return UiState(
            engine = GameEngine(mode = mode, startLevel = level),
            frame = 0,
            started = started,
            paused = false,
            showHelp = false,
            controlMode = s.controlMode,
            mode = mode,
            startLevel = level,
            bestScore = bestScoreFor(mode),
            bestLines = if (mode == GameMode.Marathon) prefs.getInt("best_lines", 0) else 0,
            bestSprintMs = if (mode == GameMode.Sprint) prefs.getLong(BEST_SPRINT_MS, 0L) else 0L,
            banner = null
        )
    }

    fun dismissHelp() {
        markSeen()
        val s = _state.value
        // Closing help returns to where you were: first-run starts the game,
        // otherwise paused stays paused — never silently resume.
        _state.value = s.copy(
            showHelp = false,
            started = if (s.started) s.started else true,
            paused = s.paused,
            frame = s.frame + 1
        )
        lastFrameTime = System.nanoTime()
    }

    fun openHelp() {
        val s = _state.value
        _state.value = s.copy(showHelp = true, frame = s.frame + 1)
        lastFrameTime = System.nanoTime()
    }

    fun toggleHelp() {
        val s = _state.value
        if (s.showHelp) dismissHelp() else openHelp()
    }

    fun pause() {
        val s = _state.value
        if (!s.started || s.engine.ended || s.countdown != null) return
        _state.value = s.copy(paused = true, frame = s.frame + 1)
    }

    fun resume() {
        val s = _state.value
        if (s.countdown != null) return
        if (!s.started || s.engine.ended) return
        // Resume through a short countdown so hands can get back into position.
        _state.value = s.copy(paused = true, showHelp = false, countdown = COUNTDOWN_STEPS, frame = s.frame + 1)
        countdownDeadlineMs = System.nanoTime() / 1_000_000 + COUNTDOWN_STEP_MS
        lastFrameTime = System.nanoTime()
    }

    fun autoPause() {
        val s = _state.value
        if (s.countdown != null) {
            // Cancel an in-flight countdown; user resumes manually.
            _state.value = s.copy(countdown = null, frame = s.frame + 1)
            return
        }
        if (s.started && !s.paused && !s.engine.ended) {
            _state.value = s.copy(paused = true, frame = s.frame + 1)
        }
    }

    fun cycleControlMode() {
        val s = _state.value
        val next = when (s.controlMode) {
            ControlMode.GESTURES -> ControlMode.BOTH
            ControlMode.BOTH -> ControlMode.BUTTONS
            ControlMode.BUTTONS -> ControlMode.GESTURES
        }
        prefs.edit().putString("control_mode", next.name).apply()
        _state.value = s.copy(controlMode = next, frame = s.frame + 1)
    }

    /**
     * Tap always rotates the falling piece counter-clockwise. Handles
     * start / help / pause / game-over first.
     */
    fun onTap() {
        val s = _state.value
        if (s.showHelp) {
            dismissHelp()
            return
        }
        if (s.countdown != null) return
        if (!s.started) {
            startGame()
            return
        }
        if (s.paused) {
            resume()
            return
        }
        val engine = s.engine
        if (engine.ended) {
            restart()
            return
        }
        engine.rotate(RotationDirection.CounterClockwise, now())
        emit()
    }

    /**
     * Two-finger tap holds the falling piece (swap with the held piece).
     * Ignored outside active play.
     */
    fun onTwoFingerTap() {
        val s = _state.value
        if (!s.started || s.paused || s.showHelp || s.countdown != null || s.engine.ended) return
        s.engine.hold(now())
        emit()
    }

    fun onLongPress() {
        val s = _state.value
        if (!s.started || s.paused || s.showHelp || s.countdown != null || s.engine.ended) return
        s.engine.hold(now())
        emit()
    }

    fun onDrag(dxCells: Int) {
        val s = _state.value
        if (!s.started || s.paused || s.showHelp || s.countdown != null || s.engine.ended) return
        repeat(kotlin.math.abs(dxCells)) {
            s.engine.shift(if (dxCells > 0) ShiftDirection.Right else ShiftDirection.Left, now())
        }
        emit()
    }

    fun setSoftDropping(active: Boolean) {
        _state.value.engine.softDropActive = active
    }

    fun restart() {
        persistBestIfNeeded()
        _state.value = freshState(_state.value, started = true)
        lastSeenEventAt = 0L
        lastFrameTime = System.nanoTime()
    }

    private fun bestScoreFor(mode: GameMode): Long =
        prefs.getLong("best_score_${mode.name}", 0L)

    private fun persistBestIfNeeded() {
        val s = _state.value
        val engine = s.engine
        val score = engine.score.score
        val lines = engine.score.lines
        val edits = prefs.edit()
        var changed = false
        if (score > bestScoreFor(s.mode)) {
            edits.putLong("best_score_${s.mode.name}", score)
            changed = true
        }
        if (s.mode == GameMode.Marathon && lines > s.bestLines) {
            edits.putInt("best_lines", lines)
            changed = true
        }
        if (s.mode == GameMode.Sprint && engine.won) {
            val best = prefs.getLong(BEST_SPRINT_MS, 0L)
            if (best == 0L || engine.elapsedMs < best) {
                edits.putLong(BEST_SPRINT_MS, engine.elapsedMs)
                changed = true
            }
        }
        if (changed) {
            edits.apply()
            _state.value = s.copy(
                bestScore = maxOf(score, bestScoreFor(s.mode)),
                bestLines = maxOf(lines, s.bestLines),
                bestSprintMs = prefs.getLong(BEST_SPRINT_MS, 0L)
            )
        }
    }

    private fun markSeen() {
        if (!prefs.getBoolean("seen_tutorial", false)) {
            prefs.edit().putBoolean("seen_tutorial", true).apply()
        }
    }

    private fun emit() {
        val s = _state.value
        // Surface fresh engine events as banners.
        val engine = s.engine
        val banner = if (engine.lastEventAtMs != lastSeenEventAt && engine.lastEventMessage != null) {
            lastSeenEventAt = engine.lastEventAtMs
            engine.lastEventMessage
        } else {
            s.banner
        }
        _state.value = s.copy(banner = banner, frame = s.frame + 1)
    }

    private fun now() = System.nanoTime() / 1_000_000

    companion object {
        private const val BEST_SPRINT_MS = "best_sprint_ms"
        const val COUNTDOWN_STEPS = 3
        const val COUNTDOWN_STEP_MS = 650L
    }
}
