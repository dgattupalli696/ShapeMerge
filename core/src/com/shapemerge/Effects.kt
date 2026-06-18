package com.shapemerge

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2

/**
 * Lightweight "juice" manager: camera screen shake, slow-motion, and a zoom punch.
 * [update] is driven by the REAL frame delta; [timeScale] is what the game should
 * multiply its own delta by so slow-mo affects the simulation but not the effects.
 */
class Effects {

    // Screen shake.
    private var shakeTime = 0f
    private var shakeDuration = 0f
    private var shakeMagnitude = 0f
    val shakeOffset = Vector2()

    // Slow motion (eases back to normal speed).
    private var slowTime = 0f
    private var slowDuration = 0f
    private var slowScale = 1f
    var timeScale = 1f
        private set

    // Zoom punch (snaps in, eases back out). Smaller zoom = closer.
    private var zoomTime = 0f
    private var zoomDuration = 0f
    private var zoomAmount = 0f
    var cameraZoom = 1f
        private set

    /** Shake the camera. Magnitude is in world units; keeps the strongest active shake. */
    fun shake(magnitude: Float, duration: Float) {
        if (magnitude >= shakeMagnitude || shakeTime >= shakeDuration) {
            shakeMagnitude = magnitude
            shakeDuration = duration
            shakeTime = 0f
        }
    }

    /** Drop to [scale] of normal speed, easing back to 1 over [duration]. */
    fun slowMo(scale: Float, duration: Float) {
        slowScale = scale
        slowDuration = duration
        slowTime = 0f
    }

    /** Zoom in by [amount] (e.g. 0.12) then ease back out over [duration]. */
    fun zoomPunch(amount: Float, duration: Float) {
        zoomAmount = amount
        zoomDuration = duration
        zoomTime = 0f
    }

    fun update(realDelta: Float) {
        if (shakeTime < shakeDuration) {
            shakeTime += realDelta
            val k = (1f - shakeTime / shakeDuration).coerceIn(0f, 1f)
            val m = shakeMagnitude * k
            shakeOffset.set(MathUtils.random(-m, m), MathUtils.random(-m, m))
        } else {
            shakeOffset.set(0f, 0f)
            shakeMagnitude = 0f
        }

        if (slowTime < slowDuration) {
            slowTime += realDelta
            val k = (slowTime / slowDuration).coerceIn(0f, 1f)
            timeScale = MathUtils.lerp(slowScale, 1f, k)
        } else {
            timeScale = 1f
        }

        if (zoomTime < zoomDuration) {
            zoomTime += realDelta
            val k = (zoomTime / zoomDuration).coerceIn(0f, 1f)
            cameraZoom = MathUtils.lerp(1f - zoomAmount, 1f, k)
        } else {
            cameraZoom = 1f
        }
    }
}
