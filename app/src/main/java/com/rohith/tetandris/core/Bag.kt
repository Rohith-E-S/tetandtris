package com.rohith.tetandris.core

import kotlin.random.Random

class Bag(seed: Long) {
    private val rng = Random(seed)
    private var rest: MutableList<Tetromino> = randBagGen()
    var next: MutableList<Tetromino> = rest.takeLast(3).toMutableList()

    init {
        repeat(3) { rest.removeAt(rest.size - 1) }
    }

    private fun randBagGen(): MutableList<Tetromino> {
        val bag = TetrominoVariant.entries.map { Tetromino.new(it) }.toMutableList()
        bag.shuffle(rng)
        return bag
    }

    fun getNext(): Tetromino {
        next.add(rest.removeAt(rest.size - 1))
        if (rest.isEmpty()) {
            rest = randBagGen()
        }
        return next.removeAt(0)
    }
}
