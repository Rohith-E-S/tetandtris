package com.rohith.tetandris.core

const val BOARD_WIDTH = 10
const val BOARD_HEIGHT = 20

typealias Cell = Int?

// Indexed [row][col], row 0 = bottom, matches TUI Stack.
class Stack(initial: List<List<Cell>> = List(BOARD_HEIGHT) { List(BOARD_WIDTH) { null } }) {
    val rows: MutableList<MutableList<Cell>> =
        initial.map { it.toMutableList() }.toMutableList()

    operator fun get(row: Int): MutableList<Cell> = rows[row]

    fun copy(): Stack = Stack(rows.map { it.toList() })

    fun lineClear(clearing: Set<Int>) {
        val kept = rows.filterIndexed { i, _ -> i !in clearing }
        val empty = List(clearing.size) { MutableList<Cell>(BOARD_WIDTH) { null } }
        rows.clear()
        // Kept rows drop to the bottom, empty rows are appended on top (TUI semantics).
        rows.addAll(kept.map { it.toMutableList() })
        rows.addAll(empty)
    }

    fun add(tetromino: Tetromino): Boolean {
        for ((x, y) in tetromino.geometry.shape) {
            if (y > BOARD_HEIGHT - 1) return false
            rows[y][x] = tetromino.variant.ordinal
        }
        return true
    }
}
