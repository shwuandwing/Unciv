package com.unciv.ui.render.globe

import org.junit.Assert
import org.junit.Test

class GlobeCameraControllerTests {

    @Test
    fun resetToNorthRestoresDefaultOrientationAndDistance() {
        val controller = GlobeCameraController(
            yawDegrees = -35f,
            pitchDegrees = 18f,
            distance = 5.1f,
            minDistance = 2.0f,
            maxDistance = 9.0f
        )

        controller.rotateBy(deltaX = 180f, deltaY = -120f)
        controller.zoomBy(scrollAmountY = 2.5f)

        controller.resetToNorth()

        Assert.assertEquals(-35f, controller.yawDegrees, 0.0001f)
        Assert.assertEquals(18f, controller.pitchDegrees, 0.0001f)
        Assert.assertEquals(5.1f, controller.distance, 0.0001f)
    }
}
