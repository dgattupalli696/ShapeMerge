package com.shapemerge

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Sound

/** Loads and plays the game's sound effects. Merge supports pitch for combos. */
class Sounds {
    private var throwS: Sound? = null
    private var mergeS: Sound? = null
    private var popS: Sound? = null

    fun load() {
        runCatching {
            throwS = Gdx.audio.newSound(Gdx.files.internal("sfx/throw.wav"))
            mergeS = Gdx.audio.newSound(Gdx.files.internal("sfx/merge.wav"))
            popS = Gdx.audio.newSound(Gdx.files.internal("sfx/pop.wav"))
        }
    }

    fun playThrow() {
        throwS?.play(0.4f)
    }

    /** [pitch] 1.0 = normal; higher = brighter (used to rise with combo/level). */
    fun playMerge(pitch: Float) {
        mergeS?.play(0.55f, pitch.coerceIn(0.5f, 3f), 0f)
    }

    fun playPop() {
        popS?.play(0.8f)
    }

    fun dispose() {
        throwS?.dispose()
        mergeS?.dispose()
        popS?.dispose()
    }
}
