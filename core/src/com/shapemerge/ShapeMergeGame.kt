package com.shapemerge

import com.badlogic.gdx.Game

class ShapeMergeGame(val haptics: Haptics = Haptics.NONE) : Game() {
    override fun create() {
        setScreen(GameScreen(this))
    }
}
