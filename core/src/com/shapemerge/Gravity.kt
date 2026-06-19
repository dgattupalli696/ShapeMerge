package com.shapemerge

/** Per-level gravity direction. Unit vector; magnitude applied by the game. */
enum class Gravity(val dx: Float, val dy: Float, val label: String) {
    ZERO(0f, 0f, "ZERO-G"),
    DOWN(0f, -1f, "GRAVITY DOWN"),
    UP(0f, 1f, "GRAVITY UP"),
    LEFT(-1f, 0f, "GRAVITY LEFT"),
    RIGHT(1f, 0f, "GRAVITY RIGHT");

    companion object {
        // Early levels are zero-G to ease players in; later levels cycle modes
        // with zero-G breaks between them.
        private val CYCLE = arrayOf(DOWN, LEFT, ZERO, RIGHT, UP, ZERO)

        fun forLevel(level: Int): Gravity =
            if (level < 3) ZERO else CYCLE[(level - 3) % CYCLE.size]
    }
}
