package com.rohith.tetandris.core

data class Geometry(
    var shape: List<Pair<Int, Int>>,
    var center: Pair<Int, Int>,
    var direction: CardinalDirection
) {
    fun transform(dx: Int, dy: Int) {
        shape = shape.map { (it.first + dx) to (it.second + dy) }
        center = (center.first + dx) to (center.second + dy)
    }

    fun rotate(cw: Boolean) {
        val angle = if (cw) -Math.PI / 2 else Math.PI / 2
        val cos = Math.cos(angle)
        val sin = Math.sin(angle)
        shape = shape.map { (x, y) ->
            val rx = x - center.first
            val ry = y - center.second
            (Math.round(rx * cos - ry * sin) + center.first).toInt() to
                (Math.round(rx * sin + ry * cos) + center.second).toInt()
        }
        direction = if (cw) {
            CardinalDirection.entries[(direction.ordinal + 1) % 4]
        } else {
            CardinalDirection.entries[((direction.ordinal - 1) % 4 + 4) % 4]
        }
    }
}

enum class CardinalDirection { North, East, South, West }

enum class TetrominoVariant { I, J, L, O, S, T, Z }

enum class RotationDirection { Clockwise, CounterClockwise }

data class Tetromino(
    var geometry: Geometry,
    val variant: TetrominoVariant
) {
    fun clone(): Tetromino =
        copy(geometry = Geometry(geometry.shape.map { it }, geometry.center, geometry.direction))

    companion object {
        fun new(variant: TetrominoVariant): Tetromino = when (variant) {
            TetrominoVariant.I -> Tetromino(
                Geometry(listOf(0 to 1, 1 to 1, 2 to 1, 3 to 1), 1 to 1, CardinalDirection.North), variant
            )
            TetrominoVariant.J -> Tetromino(
                Geometry(listOf(1 to 1, 1 to 0, 2 to 0, 3 to 0), 2 to 0, CardinalDirection.North), variant
            )
            TetrominoVariant.L -> Tetromino(
                Geometry(listOf(1 to 0, 2 to 0, 3 to 0, 3 to 1), 2 to 0, CardinalDirection.North), variant
            )
            TetrominoVariant.O -> Tetromino(
                Geometry(listOf(1 to 0, 1 to 1, 2 to 0, 2 to 1), 1 to 0, CardinalDirection.North), variant
            )
            TetrominoVariant.S -> Tetromino(
                Geometry(listOf(1 to 0, 2 to 0, 2 to 1, 3 to 1), 2 to 0, CardinalDirection.North), variant
            )
            TetrominoVariant.T -> Tetromino(
                Geometry(listOf(1 to 0, 2 to 0, 2 to 1, 3 to 0), 2 to 0, CardinalDirection.North), variant
            )
            TetrominoVariant.Z -> Tetromino(
                Geometry(listOf(1 to 1, 2 to 1, 2 to 0, 3 to 0), 2 to 0, CardinalDirection.North), variant
            )
        }
    }

    fun startPosTransform(stack: Stack) {
        geometry.transform(3, 18)
        for (i in 17..19) {
            if (stack[i].any { it != null }) {
                geometry.transform(0, 1)
            }
        }
    }

    fun overlapping(stack: Stack): Boolean = geometry.shape.any { (x, y) ->
        x < 0 || y < 0 || x > BOARD_WIDTH - 1 || y > BOARD_HEIGHT - 1 || stack[y][x] != null
    }

    fun hittingBottom(stack: Stack): Boolean = geometry.shape.any { (x, y) ->
        y == 0 || (y < BOARD_HEIGHT && stack[y - 1][x] != null)
    }

    fun hittingLeft(stack: Stack): Boolean = geometry.shape.any { (x, y) ->
        x == 0 || (y < BOARD_HEIGHT && stack[y][x - 1] != null)
    }

    fun hittingRight(stack: Stack): Boolean = geometry.shape.any { (x, y) ->
        x == BOARD_WIDTH - 1 || (y < BOARD_HEIGHT && stack[y][x + 1] != null)
    }
}

enum class ShiftDirection { Left, Right }
