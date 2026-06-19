package com.shapemerge

import com.badlogic.gdx.physics.box2d.Body

/** A polygon game object. [level] equals the number of polygon sides. */
class ShapeEntity(val level: Int, val body: Body, val radius: Float) {
    var removed = false

    // Shapes from the same multi-ball volley briefly share a group so they don't
    // merge with each other right at the launcher (0 = no group). The group is
    // cleared by [mergeGroupTimer] once they've spread out, after which they merge
    // like any other shape. They always merge with shapes outside their group.
    var mergeGroup = 0
    var mergeGroupTimer = 0f

    init {
        body.userData = this
    }
}
