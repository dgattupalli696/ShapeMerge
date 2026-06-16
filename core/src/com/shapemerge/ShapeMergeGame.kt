package com.shapemerge

import com.badlogic.gdx.Game

class ShapeMergeGame : Game() {
    override fun create() {
        setScreen(GameScreen(this))
    }
}
