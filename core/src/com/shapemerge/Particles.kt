package com.shapemerge

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils

/**
 * Tiny CPU particle system rendered with ShapeRenderer. Particles are simple
 * colored squares that fly out, slow down and fade. Used for merge bursts,
 * circle-pop confetti and fast-shape trails.
 */
class Particles {

    private class P {
        var x = 0f; var y = 0f
        var vx = 0f; var vy = 0f
        var life = 0f; var maxLife = 0f
        var size = 0f
        val color = Color()
        var active = false
    }

    private val pool = Array(400) { P() }

    private fun spawn(): P? {
        for (p in pool) if (!p.active) return p
        return null
    }

    /** A radial burst of [count] particles from ([x],[y]) in the given color. */
    fun burst(x: Float, y: Float, count: Int, color: Color, speed: Float, size: Float) {
        repeat(count) {
            val p = spawn() ?: return
            val ang = MathUtils.random(0f, MathUtils.PI2)
            val sp = speed * MathUtils.random(0.3f, 1f)
            p.x = x; p.y = y
            p.vx = MathUtils.cos(ang) * sp
            p.vy = MathUtils.sin(ang) * sp
            p.maxLife = MathUtils.random(0.35f, 0.7f)
            p.life = p.maxLife
            p.size = size * MathUtils.random(0.6f, 1.2f)
            p.color.set(color)
            p.active = true
        }
    }

    fun update(delta: Float) {
        for (p in pool) {
            if (!p.active) continue
            p.life -= delta
            if (p.life <= 0f) { p.active = false; continue }
            p.x += p.vx * delta
            p.y += p.vy * delta
            p.vx *= 0.92f
            p.vy *= 0.92f
        }
    }

    /** Draws all particles. Must be called inside a Filled ShapeRenderer batch. */
    fun draw(sr: ShapeRenderer) {
        for (p in pool) {
            if (!p.active) continue
            val a = (p.life / p.maxLife).coerceIn(0f, 1f)
            sr.color = p.color.also { it.a = a }
            val h = p.size * 0.5f
            sr.rect(p.x - h, p.y - h, p.size, p.size)
        }
    }

    fun clear() {
        for (p in pool) p.active = false
    }
}
