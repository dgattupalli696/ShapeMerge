package com.shapemerge

import com.badlogic.gdx.physics.box2d.Contact
import com.badlogic.gdx.physics.box2d.ContactImpulse
import com.badlogic.gdx.physics.box2d.ContactListener
import com.badlogic.gdx.physics.box2d.Manifold

/**
 * Detects collisions between two shapes of the same level and reports them so the
 * game can merge them. Merging must happen outside world.step(), so this only
 * queues the pair via [onSameLevelContact].
 */
class MergeContactListener(
    private val onSameLevelContact: (ShapeEntity, ShapeEntity) -> Unit
) : ContactListener {

    override fun beginContact(contact: Contact) {
        val a = contact.fixtureA.body.userData
        val b = contact.fixtureB.body.userData
        if (a is ShapeEntity && b is ShapeEntity &&
            a !== b && !a.removed && !b.removed &&
            a.level == b.level &&
            !(a.mergeGroup != 0 && a.mergeGroup == b.mergeGroup)
        ) {
            onSameLevelContact(a, b)
        }
    }

    override fun endContact(contact: Contact) {}

    override fun preSolve(contact: Contact, oldManifold: Manifold?) {
        val fa = contact.fixtureA
        val fb = contact.fixtureB
        val dividerIsA = fa.userData == Constants.DIVIDER
        val dividerIsB = fb.userData == Constants.DIVIDER
        if (!dividerIsA && !dividerIsB) return
        val other = (if (dividerIsA) fb else fa).body
        // One-way: any body still below the divider and moving up passes through;
        // this lets launched shapes and power-up projectiles enter the playground.
        if (other.position.y < Constants.LAUNCH_ZONE_TOP && other.linearVelocity.y > 0f) {
            contact.isEnabled = false
        }
    }

    override fun postSolve(contact: Contact, impulse: ContactImpulse?) {}
}
