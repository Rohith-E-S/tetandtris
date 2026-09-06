package com.rohith.tetandris.ui

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer

fun DrawScope.drawGame(ui: UiState, measurer: TextMeasurer) {
    TuiRenderer(measurer).drawGame(this, ui, Palette.White)
}
