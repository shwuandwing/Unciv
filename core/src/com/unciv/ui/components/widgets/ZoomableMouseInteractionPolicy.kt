package com.unciv.ui.components.widgets

import com.badlogic.gdx.Input

object ZoomableMouseInteractionPolicy {
    fun shouldStartPan(button: Int): Boolean = button != Input.Buttons.RIGHT

    fun shouldContinuePan(
        leftDown: Boolean,
        rightDown: Boolean,
        middleDown: Boolean,
        backDown: Boolean,
        forwardDown: Boolean
    ): Boolean {
        // Desktop: if only a non-primary mouse button is held, don't pan.
        if (!leftDown && (rightDown || middleDown || backDown || forwardDown)) return false
        // Touch devices report no mouse buttons pressed; keep panning enabled there.
        return true
    }

    fun shouldAutoScroll(isTouched: Boolean, anyMouseButtonDown: Boolean): Boolean =
        !isTouched && !anyMouseButtonDown
}
