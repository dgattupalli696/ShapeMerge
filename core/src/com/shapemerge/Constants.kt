package com.shapemerge

import com.badlogic.gdx.graphics.Color

object Constants {
    // World is measured in meters (top-down board, portrait).
    const val WORLD_WIDTH = 9f
    const val WORLD_HEIGHT = 16f

    // HUD virtual resolution (pixels).
    const val HUD_WIDTH = 720f
    const val HUD_HEIGHT = 1280f

    // Launcher anchor (sits well above the bottom edge for comfortable dragging).
    const val LAUNCH_X = WORLD_WIDTH / 2f
    const val LAUNCH_Y = 2.4f

    // Horizontal divider separating the launch zone (below) from the playground (above).
    const val LAUNCH_ZONE_TOP = 4.6f

    // Position of the "next shape" preview, in the launch zone left of the launcher.
    const val NEXT_X = 2.6f
    const val NEXT_Y = 2.3f

    // Polygon ladder: triangle (3 sides) .. decagon (10 sides).
    // Merging two decagons (or a multi-merge that reaches CIRCLE_LEVEL) forms a
    // circle that pops for bonus points and disappears.
    const val MIN_LEVEL = 3
    const val MAX_LEVEL = 10
    const val CIRCLE_LEVEL = 11

    // Shapes grow with each polygon (triangle small, square bigger, ...), but the
    // growth tapers and is capped: radius grows linearly while AREA grows with the
    // square, so without a cap the top shapes explode in size and fill the board.
    const val BASE_RADIUS = 0.55f
    const val RADIUS_STEP = 0.18f
    const val MAX_RADIUS = 1.05f

    fun radiusForLevel(level: Int): Float {
        val lvl = level.coerceIn(MIN_LEVEL, MAX_LEVEL)
        return minOf(BASE_RADIUS + (lvl - MIN_LEVEL) * RADIUS_STEP, MAX_RADIUS)
    }
    const val SHAPE_DENSITY = 1f
    const val SHAPE_FRICTION = 0.25f
    const val SHAPE_RESTITUTION = 0.45f
    const val LINEAR_DAMPING = 0.6f
    const val ANGULAR_DAMPING = 1.0f

    const val WALL_RESTITUTION = 0.5f

    // Aiming / throwing.
    const val MAX_PULL = 5.0f
    const val MIN_PULL = 0.4f
    const val IMPULSE_SCALE = 5.5f

    // Max tilt of the aim away from straight up (radians). ~62 degrees keeps the
    // arrow inside a clear upward cone instead of swinging sideways/flat.
    const val MAX_AIM_TILT_RAD = 1.082f

    // Progression.
    const val BASE_TARGET = 600

    // Lose when shapes occupy this fraction of the playground area.
    const val FILL_LIMIT = 0.62f

    // Distinct color per level, generated across the hue wheel.
    private val levelColors: Array<Color> = Array(CIRCLE_LEVEL + 1) { lvl ->
        if (lvl < MIN_LEVEL) {
            Color.WHITE
        } else {
            val t = (lvl - MIN_LEVEL).toFloat() / (MAX_LEVEL - MIN_LEVEL).toFloat()
            Color().fromHsv(t.coerceIn(0f, 1f) * 300f, 0.62f, 0.96f).apply { a = 1f }
        }
    }

    fun colorForLevel(level: Int): Color =
        levelColors[level.coerceIn(MIN_LEVEL, CIRCLE_LEVEL)]
}
