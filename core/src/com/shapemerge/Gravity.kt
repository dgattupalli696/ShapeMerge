package com.shapemerge

import com.badlogic.gdx.math.MathUtils

/** Per-level gravity direction. Unit vector; magnitude applied by the game. */
enum class Gravity(val dx: Float, val dy: Float, val label: String) {
    ZERO(0f, 0f, "ZERO-G"),
    UP(0f, 1f, "GRAVITY UP"),
    LEFT(-1f, 0f, "GRAVITY LEFT"),
    RIGHT(1f, 0f, "GRAVITY RIGHT");

    companion object {
        private val NONZERO = arrayOf(LEFT, RIGHT, UP)

        /** Gravity is a one-level event on every 5th level (5, 10, 15, ...). */
        fun isGravityLevel(level: Int): Boolean = level >= 5 && level % 5 == 0

        /** A random non-zero gravity direction, optionally different from [avoid]. */
        fun randomNonZero(avoid: Gravity?): Gravity {
            var g = NONZERO[MathUtils.random(NONZERO.size - 1)]
            if (avoid != null) {
                var guard = 0
                while (g == avoid && guard++ < 8) g = NONZERO[MathUtils.random(NONZERO.size - 1)]
            }
            return g
        }
    }
}
