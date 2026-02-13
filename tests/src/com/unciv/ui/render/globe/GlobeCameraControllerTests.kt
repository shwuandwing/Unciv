package com.unciv.ui.render.globe

import com.badlogic.gdx.math.Vector3
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

    @Test
    fun snapshotAndRestoreRoundTripsCameraViewState() {
        val controller = GlobeCameraController(
            yawDegrees = 10f,
            pitchDegrees = -14f,
            distance = 3.2f,
            minDistance = 2f,
            maxDistance = 6f
        )
        controller.rotateBy(deltaX = 25f, deltaY = 30f)
        controller.zoomBy(scrollAmountY = -0.8f)
        val saved = controller.snapshot()

        controller.rotateBy(deltaX = -200f, deltaY = 150f)
        controller.zoomBy(scrollAmountY = 3f)
        controller.restore(saved)

        Assert.assertEquals(saved.yawDegrees, controller.yawDegrees, 0.0001f)
        Assert.assertEquals(saved.pitchDegrees, controller.pitchDegrees, 0.0001f)
        Assert.assertEquals(saved.distance, controller.distance, 0.0001f)
    }

    @Test
    fun restoreClampsPitchAndDistanceToConfiguredBounds() {
        val controller = GlobeCameraController(
            yawDegrees = 0f,
            pitchDegrees = 0f,
            distance = 4f,
            minDistance = 2f,
            maxDistance = 5f
        )

        controller.restore(
            GlobeCameraController.ViewState(
                yawDegrees = 123f,
                pitchDegrees = 100f,
                distance = 10f
            )
        )

        Assert.assertEquals(123f, controller.yawDegrees, 0.0001f)
        Assert.assertEquals(85f, controller.pitchDegrees, 0.0001f)
        Assert.assertEquals(5f, controller.distance, 0.0001f)
    }

    @Test
    fun centerOnDirectionAlignsYawAndPitchToTargetVector() {
        val controller = GlobeCameraController(
            yawDegrees = 0f,
            pitchDegrees = 0f,
            distance = 4f,
            minDistance = 2f,
            maxDistance = 5f
        )

        controller.centerOnDirection(Vector3(0f, 0f, 1f))
        Assert.assertEquals(90f, controller.yawDegrees, 0.0001f)
        Assert.assertEquals(0f, controller.pitchDegrees, 0.0001f)

        controller.centerOnDirection(Vector3(0f, 1f, 0f))
        Assert.assertEquals(85f, controller.pitchDegrees, 0.0001f)
    }
}
