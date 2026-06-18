package com.shapemerge

/** Platform vibration hook. The Android module provides a real implementation. */
interface Haptics {
    fun vibrate(ms: Int)

    companion object {
        val NONE = object : Haptics {
            override fun vibrate(ms: Int) {}
        }
    }
}
