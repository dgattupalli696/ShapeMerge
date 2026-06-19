package com.shapemerge

import com.badlogic.gdx.math.MathUtils

/** Per-level gravity direction. Unit vector; magnitude applied by the game. */
enum class Gravity(val dx: Float, val dy: Float, val label: String) {
    ZERO(0f, 0f, "ZERO-G"),
    DOWN(0f, -1f, "GRAVITY DOWN"),
    UP(0f, 1f, "GRAVITY UP"),
    LEFT(-1f, 0f, "GRAVITY LEFT"),
    RIGHT(1f, 0f, "GRAVITY RIGHT");

    companion object {
        private val NONZERO = arrayOf(DOWN, LEFT, RIGHT, UP)

        /** Which 5-level gravity band a level falls in (0 = none, levels 1-4). */
        fun bandForLevel(level: Int): Int = if (level < 5) 0 else level / 5

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
