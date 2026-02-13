package com.unciv.ui.components.widgets

import com.badlogic.gdx.Input
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomableMouseInteractionPolicyTests {

    @Test
    fun rightClickDoesNotStartPan() {
        assertFalse(ZoomableMouseInteractionPolicy.shouldStartPan(Input.Buttons.RIGHT))
        assertTrue(ZoomableMouseInteractionPolicy.shouldStartPan(Input.Buttons.LEFT))
    }

    @Test
    fun panContinuesForLeftOrTouchButNotRightOnly() {
        assertFalse(
            ZoomableMouseInteractionPolicy.shouldContinuePan(
                leftDown = false,
                rightDown = true,
                middleDown = false,
                backDown = false,
                forwardDown = false
            )
        )
        assertTrue(
            ZoomableMouseInteractionPolicy.shouldContinuePan(
                leftDown = true,
                rightDown = false,
                middleDown = false,
                backDown = false,
                forwardDown = false
            )
        )
        assertTrue(
            ZoomableMouseInteractionPolicy.shouldContinuePan(
                leftDown = false,
                rightDown = false,
                middleDown = false,
                backDown = false,
                forwardDown = false
            )
        )
    }

    @Test
    fun autoScrollDisabledWhenMouseButtonIsHeld() {
        assertFalse(ZoomableMouseInteractionPolicy.shouldAutoScroll(isTouched = false, anyMouseButtonDown = true))
        assertFalse(ZoomableMouseInteractionPolicy.shouldAutoScroll(isTouched = true, anyMouseButtonDown = false))
        assertTrue(ZoomableMouseInteractionPolicy.shouldAutoScroll(isTouched = false, anyMouseButtonDown = false))
    }
}

