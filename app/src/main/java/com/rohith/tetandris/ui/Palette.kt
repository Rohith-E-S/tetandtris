package com.rohith.tetandris.ui

import androidx.compose.ui.graphics.Color
import com.rohith.tetandris.core.TetrominoVariant

// xterm-256 palette values used by the TUI
object Palette {
    val I = Color(0xFF00FFFF) // AnsiValue(51) cyan
    val J = Color(0xFF0087FF) // AnsiValue(33) blue
    val L = Color(0xFFFF5F00) // AnsiValue(202) orange
    val O = Color(0xFFFFD700) // AnsiValue(226) yellow
    val S = Color(0xFF00D700) // AnsiValue(40) green
    val T = Color(0xFFD75FAF) // AnsiValue(165) magenta
    val Z = Color(0xFFFF0000) // AnsiValue(196) red
    val White = Color(0xFFFFFFFF)
    val Faint = Color(0xFF3A4150) // faint '.' grid dots
    val Frame = Color(0xFFE6E6E6)

    fun variant(v: TetrominoVariant): Color = when (v) {
        TetrominoVariant.I -> I
        TetrominoVariant.J -> J
        TetrominoVariant.L -> L
        TetrominoVariant.O -> O
        TetrominoVariant.S -> S
        TetrominoVariant.T -> T
        TetrominoVariant.Z -> Z
    }
}
