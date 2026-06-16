package com.shapemerge

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.physics.box2d.Body
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.CircleShape
import com.badlogic.gdx.physics.box2d.FixtureDef
import com.badlogic.gdx.physics.box2d.World

object ShapeFactory {

    /** Local-space vertices for a regular [sides]-gon of the given [radius]. */
    fun polygonVertices(sides: Int, radius: Float): FloatArray {
        val verts = FloatArray(sides * 2)
        // Start at the top so polygons look upright.
        val offset = MathUtils.PI / 2f
        for (i in 0 until sides) {
            val angle = MathUtils.PI2 * i / sides + offset
            verts[i * 2] = MathUtils.cos(angle) * radius
            verts[i * 2 + 1] = MathUtils.sin(angle) * radius
        }
        return verts
    }

    fun create(
        world: World,
        level: Int,
        x: Float,
        y: Float,
        radius: Float = Constants.SHAPE_RADIUS
    ): ShapeEntity {
        val bodyDef = BodyDef().apply {
            type = BodyDef.BodyType.DynamicBody
            position.set(x, y)
            linearDamping = Constants.LINEAR_DAMPING
            angularDamping = Constants.ANGULAR_DAMPING
            bullet = true
        }
        val body: Body = world.createBody(bodyDef)

        // Physics uses a circle (smooth collisions and supports any side count;
        // Box2D polygons are limited to 8 vertices). The polygon is only visual.
        val shape = CircleShape()
        shape.radius = radius

        val fixtureDef = FixtureDef().apply {
            this.shape = shape
            density = Constants.SHAPE_DENSITY
            friction = Constants.SHAPE_FRICTION
            restitution = Constants.SHAPE_RESTITUTION
        }
        body.createFixture(fixtureDef)
        shape.dispose()

        return ShapeEntity(level, body, radius)
    }
}
