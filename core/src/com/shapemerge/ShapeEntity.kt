package com.shapemerge

import com.badlogic.gdx.physics.box2d.Body

/** A polygon game object. [level] equals the number of polygon sides. */
class ShapeEntity(val level: Int, val body: Body, val radius: Float) {
    var removed = false

    init {
        body.userData = this
    }
}
