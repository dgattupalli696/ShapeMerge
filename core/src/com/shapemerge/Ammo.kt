package com.shapemerge

/** The kind of projectile loaded in the launcher. */
enum class AmmoKind { SHAPE, BOMB, CANNONBALL, MULTIBALL }

/**
 * A loaded projectile. Normal ammo is a polygon [level]; power-up kinds ignore
 * level (except where noted) and have special launch behavior.
 */
class Ammo(val kind: AmmoKind, val level: Int = Constants.MIN_LEVEL) {
    val isPowerUp: Boolean get() = kind != AmmoKind.SHAPE
}
