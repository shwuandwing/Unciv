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

    @Test
    fun axisAwareResetUsesProvidedNorthBasis() {
        val controller = GlobeCameraController(
            yawDegrees = -35f,
            pitchDegrees = 18f,
            distance = 5.1f,
            minDistance = 2f,
            maxDistance = 9f
        )
        controller.rotateBy(deltaX = 120f, deltaY = 40f)
        controller.zoomBy(scrollAmountY = 2f)

        val north = Vector3(0f, 0f, 1f).nor()
        val meridian = Vector3(1f, 0f, 0f).nor()
        val east = Vector3(0f, 1f, 0f).nor()
        controller.resetToNorth(north, meridian, east)

        Assert.assertEquals(-35f, controller.yawDegrees, 0.0001f)
        Assert.assertEquals(18f, controller.pitchDegrees, 0.0001f)
        Assert.assertEquals(5.1f, controller.distance, 0.0001f)
        val yaw = Math.toRadians(controller.yawDegrees.toDouble())
        val pitch = Math.toRadians(controller.pitchDegrees.toDouble())
        val horizontal = kotlin.math.cos(pitch).toFloat()
        val expectedDirection = meridian.cpy().scl((horizontal * kotlin.math.cos(yaw).toFloat()))
            .add(north.cpy().scl(kotlin.math.sin(pitch).toFloat()))
            .add(east.cpy().scl((horizontal * kotlin.math.sin(yaw).toFloat()))
            ).nor()
        Assert.assertTrue(
            "Axis-aware reset should keep camera direction in northern hemisphere of provided basis",
            expectedDirection.dot(north) > 0f
        )
    }

    @Test
    fun centerOnDirectionUsesOrientationBasisComponents() {
        val controller = GlobeCameraController(
            yawDegrees = 0f,
            pitchDegrees = 0f,
            distance = 4f,
            minDistance = 2f,
            maxDistance = 8f
        )
        controller.setOrientationAxes(
            northAxis = Vector3(0f, 0f, 1f),
            meridianAxis = Vector3(1f, 0f, 0f),
            eastAxis = Vector3(0f, 1f, 0f)
        )

        controller.centerOnDirection(Vector3(0f, 0f, 1f))
        Assert.assertEquals("Direction aligned with north must clamp to top pitch", 85f, controller.pitchDegrees, 0.0001f)

        controller.centerOnDirection(Vector3(0f, 1f, 0f))
        Assert.assertEquals("Direction aligned with east must map to +90 yaw", 90f, controller.yawDegrees, 0.0001f)
        Assert.assertEquals(0f, controller.pitchDegrees, 0.0001f)
    }
}
