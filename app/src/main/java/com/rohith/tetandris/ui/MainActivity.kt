package com.rohith.tetandris.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rohith.tetandris.core.GameEngine
import com.rohith.tetandris.core.GameMode
import kotlinx.coroutines.delay

private val Bg = Color(0xFF0A0E14)
private val Ink = Color(0xFFE6E6E6)
private val Muted = Color(0xFF8B93A5)
private val Faint = Color(0xFF3A4150)
private val Surface = Color(0xFF141B28)
private val Slot = Color(0xFF1A2230)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            GameScreen()
        }
    }
}

@Composable
fun GameScreen(vm: GameViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val textMeasurer = rememberTextMeasurer()
    val canvasSize = remember { mutableStateOf(IntSize.Zero) }
    val haptics = LocalHapticFeedback.current

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                vm.autoPause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Back always means "step back one level", never "lose the run silently".
    BackHandler(enabled = state.showHelp || (state.started && !state.engine.ended && !state.paused)) {
        when {
            state.showHelp -> vm.dismissHelp()
            state.started && !state.engine.ended && !state.paused -> vm.pause()
        }
    }

    // Game loop always runs; tickFrame() no-ops when paused/unstarted/help.
    LaunchedEffect(Unit) {
        while (true) {
            vm.tickFrame()
            delay(33)
        }
    }

    val showButtons = state.controlMode != ControlMode.GESTURES
    val ended = state.engine.ended

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasSize.value = it }
                    .tuiGestures(
                        vm = vm,
                        cellHeightPx = {
                            val w = canvasSize.value.width
                            val h = canvasSize.value.height
                            if (w > 0 && h > 0) TuiRenderer.cellFor(w.toFloat(), h.toFloat()) else 30f
                        }
                    )
            ) {
                drawGame(state, textMeasurer)
            }

            // Corner controls, folded into the HUD's empty corners (vertically
            // centered in the HUD band) so no top bar steals board space.
            if (state.started && !ended && !state.showHelp) {
                CornerButton(
                    label = if (state.paused) "\u25B6" else "II",
                    modifier = Modifier.align(Alignment.TopStart).padding(top = 18.dp),
                    tint = Ink
                ) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (state.paused) vm.resume() else vm.pause()
                }
                CornerButton(
                    label = "?",
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 18.dp),
                    tint = Muted
                ) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.openHelp()
                }
            }

            // Explicit, tappable states — no "tap anywhere" guessing.
            when {
                state.showHelp -> HelpCard(onGotIt = { vm.dismissHelp() })
                !state.started -> StartCard(
                    mode = state.mode,
                    startLevel = state.startLevel,
                    best = state.bestScore,
                    bestSprintMs = state.bestSprintMs,
                    controlMode = state.controlMode,
                    onMode = { vm.setMode(it) },
                    onLevel = { vm.setStartLevel(it) },
                    onControlMode = { vm.cycleControlMode() },
                    onStart = { vm.startGame() },
                    onHowTo = { vm.openHelp() }
                )
                ended -> GameOverCard(
                    engine = state.engine,
                    best = state.bestScore,
                    bestSprintMs = state.bestSprintMs,
                    onRetry = { vm.restart() },
                    onMenu = { vm.toMenu() },
                    onHowTo = { vm.openHelp() }
                )
                state.paused && state.countdown == null -> PauseCard(
                    engine = state.engine,
                    controlMode = state.controlMode,
                    onContinue = { vm.resume() },
                    onHowTo = { vm.openHelp() },
                    onRestart = { vm.restart() },
                    onMenu = { vm.toMenu() },
                    onControlMode = { vm.cycleControlMode() }
                )
            }
        }

        // Optional on-screen buttons (default OFF; toggle from the menus).
        // Placed in otherwise-dead footer space; preserves the board.
        if (showButtons) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PadButton("◀", onDown = { vm.onDrag(-1) })
                PadButton("▶", onDown = { vm.onDrag(1) })
                PadButton("▼", onHold = { active -> vm.setSoftDropping(active) })
                PadButton("⟳", onDown = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    vm.onTap()
                })
                PadButton("H", onDown = { vm.onLongPress() })
            }
        }
    }
}

@Composable
private fun CornerButton(
    label: String,
    modifier: Modifier = Modifier,
    tint: Color,
    onTap: () -> Unit
) {
    TextButton(onClick = onTap, modifier = modifier) {
        Text(
            label,
            color = tint,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
    }
}

@Composable
private fun OverlayScrim() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x990A0E14))
    )
}

@Composable
private fun MenuCard(
    title: String,
    subtitle: String? = null,
    body: (@Composable () -> Unit)? = null,
    actions: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(max = 360.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (subtitle != null) {
                    Text(subtitle, color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                }
                if (body != null) {
                    Box(modifier = Modifier.padding(top = 12.dp)) { body() }
                }
                Column(modifier = Modifier.padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    actions()
                }
            }
        }
    }
}

private val HowToLines = listOf(
    "Tap — rotate counter-clockwise",
    "Drag sideways — move",
    "Drag straight down — soft drop",
    "Double tap — hold a piece for later",
    "",
    "Sprint — clear 40 lines as fast as you can",
    "Ultra — score as much as you can in 2:00"
)

@Composable
private fun HelpCard(onGotIt: () -> Unit) {
    OverlayScrim()
    MenuCard(
        title = "How to play",
        body = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HowToLines.forEach { line ->
                    Text(
                        line,
                        color = if (line.isEmpty()) Color.Transparent else Ink,
                        fontSize = 13.sp
                    )
                }
            }
        },
        actions = {
            Button(onClick = onGotIt) { Text("Got it") }
        }
    )
}

@Composable
private fun ModePicker(current: GameMode, onSelect: (GameMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        GameMode.entries.forEach { m ->
            val selected = m == current
            Text(
                m.label,
                color = if (selected) Color.White else Muted,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) Slot else Color.Transparent)
                    .clickable { onSelect(m) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun LevelStepper(level: Int, onLevel: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Level", color = Muted, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
        TextButton(onClick = { onLevel(level - 1) }) {
            Text("−", color = Ink, fontFamily = FontFamily.Monospace, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            "$level",
            color = Ink,
            fontFamily = FontFamily.Monospace,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        TextButton(onClick = { onLevel(level + 1) }) {
            Text("+", color = Ink, fontFamily = FontFamily.Monospace, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ControlModeLine(mode: ControlMode, onCycle: () -> Unit) {
    Text(
        "Controls: ${mode.name.lowercase().replaceFirstChar { it.uppercase() }}",
        color = Muted,
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onCycle)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
private fun StartCard(
    mode: GameMode,
    startLevel: Int,
    best: Long,
    bestSprintMs: Long,
    controlMode: ControlMode,
    onMode: (GameMode) -> Unit,
    onLevel: (Int) -> Unit,
    onControlMode: () -> Unit,
    onStart: () -> Unit,
    onHowTo: () -> Unit
) {
    OverlayScrim()
    MenuCard(
        title = "Tetandris",
        subtitle = when {
            mode == GameMode.Sprint && bestSprintMs > 0 -> "Best ${formatTime(bestSprintMs)}"
            best > 0 -> "Best ${"%,d".format(best)}"
            else -> "A tiny Tetris"
        },
        body = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ModePicker(mode, onMode)
                if (mode != GameMode.Sprint) LevelStepper(startLevel, onLevel)
            }
        },
        actions = {
            Button(onClick = onStart) { Text("Start") }
            TextButton(onClick = onHowTo) {
                Text("How to play", color = Muted, fontSize = 14.sp)
            }
            ControlModeLine(controlMode, onControlMode)
        }
    )
}

@Composable
private fun PauseCard(
    engine: GameEngine,
    controlMode: ControlMode,
    onContinue: () -> Unit,
    onHowTo: () -> Unit,
    onRestart: () -> Unit,
    onMenu: () -> Unit,
    onControlMode: () -> Unit
) {
    OverlayScrim()
    MenuCard(
        title = "Paused",
        subtitle = "%,d points · %d lines".format(engine.score.score, engine.score.lines),
        body = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StatRow("Time", formatTime(engine.elapsedMs))
                StatRow("Pieces", "${engine.pieces}")
            }
        },
        actions = {
            Button(onClick = onContinue) { Text("Continue") }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onRestart) { Text("Restart", color = Muted, fontSize = 14.sp) }
                TextButton(onClick = onMenu) { Text("Menu", color = Muted, fontSize = 14.sp) }
                TextButton(onClick = onHowTo) { Text("How to play", color = Muted, fontSize = 14.sp) }
            }
            ControlModeLine(controlMode, onControlMode)
        }
    )
}

@Composable
private fun GameOverCard(
    engine: GameEngine,
    best: Long,
    bestSprintMs: Long,
    onRetry: () -> Unit,
    onMenu: () -> Unit,
    onHowTo: () -> Unit
) {
    val sprintWin = engine.mode == GameMode.Sprint && engine.won
    val hero = if (sprintWin) formatTime(engine.elapsedMs) else "%,d".format(engine.score.score)
    val newBest = when {
        sprintWin -> engine.elapsedMs <= bestSprintMs && bestSprintMs > 0
        engine.score.score > 0 && engine.score.score >= best -> true
        else -> false
    }
    OverlayScrim()
    MenuCard(
        title = when {
            engine.won -> "Sprint complete"
            engine.mode == GameMode.Ultra -> "Time's up"
            else -> "Game over"
        },
        subtitle = if (newBest) "New best" else null,
        body = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    hero,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (sprintWin) "%,d points".format(engine.score.score) else "Best ${"%,d".format(maxOf(best, engine.score.score))}",
                    color = Muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StatRow("Lines", "${engine.score.lines}")
                    StatRow("Level", "${engine.score.level}")
                    StatRow("Time", formatTime(engine.elapsedMs))
                    StatRow("Pieces", "${engine.pieces}")
                    StatRow("Tetrises", "${engine.tetrises}")
                    StatRow("T-spins", "${engine.tSpins}")
                    StatRow("Max combo", if (engine.maxCombo > 0) "×${engine.maxCombo}" else "—")
                    if (engine.b2bChains > 0) StatRow("B2B chains", "${engine.b2bChains}")
                }
            }
        },
        actions = {
            Button(onClick = onRetry) { Text("Play again") }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onMenu) { Text("Menu", color = Muted, fontSize = 14.sp) }
                TextButton(onClick = onHowTo) { Text("How to play", color = Muted, fontSize = 14.sp) }
            }
        }
    )
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, color = Muted, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        Text(
            value,
            color = Ink,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

private fun formatTime(ms: Long): String {
    val total = ms.coerceAtLeast(0L) / 100L
    val s = total / 10
    return "%d:%02d.%d".format(s / 60, s % 60, total % 10)
}

@Composable
private fun PadButton(
    label: String,
    onDown: (() -> Unit)? = null,
    onHold: ((Boolean) -> Unit)? = null
) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .padding(2.dp)
            .background(Slot, shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .let { m ->
                if (onHold != null) {
                    m.pointerHoldButton(onHold)
                } else {
                    m.clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDown?.invoke()
                    }
                }
            }
    ) {
        Text(
            label,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
    }
}

private fun Modifier.pointerHoldButton(onHold: (Boolean) -> Unit): Modifier =
    this.then(
        pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                onHold(true)
                var released = false
                while (!released) {
                    val event = awaitPointerEvent()
                    if (event.changes.all { !it.pressed }) released = true
                }
                onHold(false)
            }
        }
    )
