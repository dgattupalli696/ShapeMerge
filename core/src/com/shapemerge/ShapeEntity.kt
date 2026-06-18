package com.shapemerge

import com.badlogic.gdx.physics.box2d.Body

/** A polygon game object. [level] equals the number of polygon sides. */
class ShapeEntity(val level: Int, val body: Body, val radius: Float) {
    var removed = false

    // Shapes from the same multi-ball volley share a group and never merge with
    // each other (0 = no group). They still merge with everything else.
    var mergeGroup = 0

    init {
        body.userData = this
    }
}
