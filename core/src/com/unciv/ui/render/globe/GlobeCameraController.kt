package com.unciv.ui.render.globe

import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import kotlin.math.cos
import kotlin.math.sin

class GlobeCameraController(
    var yawDegrees: Float = -40f,
    var pitchDegrees: Float = 22f,
    var distance: Float = 4.2f,
    var minDistance: Float = 2.1f,
    var maxDistance: Float = 8.5f,
    var rotationSensitivity: Float = 0.33f,
    var zoomSensitivity: Float = 0.12f
) {
    fun rotateBy(deltaX: Float, deltaY: Float) {
        yawDegrees -= deltaX * rotationSensitivity
        pitchDegrees = (pitchDegrees + deltaY * rotationSensitivity).coerceIn(-85f, 85f)
    }

    fun zoomBy(scrollAmountY: Float) {
        val scale = 1f + scrollAmountY * zoomSensitivity
        distance = (distance * scale).coerceIn(minDistance, maxDistance)
    }

    fun applyTo(camera: PerspectiveCamera) {
        val yaw = MathUtils.degreesToRadians * yawDegrees
        val pitch = MathUtils.degreesToRadians * pitchDegrees
        val horizontal = distance * cos(pitch)

        val x = horizontal * cos(yaw)
        val y = distance * sin(pitch)
        val z = horizontal * sin(yaw)

        camera.position.set(x.toFloat(), y.toFloat(), z.toFloat())
        camera.up.set(Vector3.Y)
        camera.lookAt(0f, 0f, 0f)
        camera.near = 0.1f
        camera.far = 30f
        camera.update(true)
    }
}
