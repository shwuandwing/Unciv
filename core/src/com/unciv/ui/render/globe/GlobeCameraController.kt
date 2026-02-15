package com.unciv.ui.render.globe

import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import yairm210.purity.annotations.Readonly
import kotlin.math.asin
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
    private val defaultYawDegrees = yawDegrees
    private val defaultPitchDegrees = pitchDegrees
    private val defaultDistance = distance
    private val orientationNorth = Vector3.Y.cpy()
    private val orientationMeridian = Vector3.X.cpy()
    private val orientationEast = Vector3.Z.cpy()
    private val tempForward = Vector3()
    private val tempUp = Vector3()
    private val tempProjection = Vector3()
    private val tempPosition = Vector3()

    data class ViewState(
        val yawDegrees: Float,
        val pitchDegrees: Float,
        val distance: Float
    )

    fun rotateBy(deltaX: Float, deltaY: Float) {
        yawDegrees -= deltaX * rotationSensitivity
        pitchDegrees = (pitchDegrees + deltaY * rotationSensitivity).coerceIn(-85f, 85f)
    }

    fun zoomBy(scrollAmountY: Float) {
        val scale = 1f + scrollAmountY * zoomSensitivity
        distance = (distance * scale).coerceIn(minDistance, maxDistance)
    }

    fun centerOnDirection(direction: Vector3) {
        if (direction.isZero(0.0001f)) return
        val normalized = direction.cpy().nor()
        val meridianComponent = normalized.dot(orientationMeridian)
        val eastComponent = normalized.dot(orientationEast)
        val northComponent = normalized.dot(orientationNorth)
        yawDegrees = (MathUtils.radiansToDegrees * kotlin.math.atan2(eastComponent, meridianComponent))
        pitchDegrees = (MathUtils.radiansToDegrees * asin(northComponent)).coerceIn(-85f, 85f)
    }

    fun setOrientationAxes(
        northAxis: Vector3,
        meridianAxis: Vector3,
        eastAxis: Vector3
    ) {
        orientationNorth.set(northAxis)
        if (orientationNorth.len2() <= 1e-8f) orientationNorth.set(Vector3.Y)
        orientationNorth.nor()

        orientationMeridian.set(meridianAxis)
        projectToTangent(orientationMeridian, orientationNorth)
        if (orientationMeridian.len2() <= 1e-8f) {
            orientationMeridian.set(eastAxis)
            projectToTangent(orientationMeridian, orientationNorth)
        }
        if (orientationMeridian.len2() <= 1e-8f) {
            orientationMeridian.set(Vector3.X)
            projectToTangent(orientationMeridian, orientationNorth)
        }
        if (orientationMeridian.len2() <= 1e-8f) {
            orientationMeridian.set(Vector3.Z)
            projectToTangent(orientationMeridian, orientationNorth)
        }
        orientationMeridian.nor()

        orientationEast.set(orientationNorth).crs(orientationMeridian)
        if (orientationEast.len2() <= 1e-8f) orientationEast.set(Vector3.Z)
        orientationEast.nor()
        orientationMeridian.set(orientationEast).crs(orientationNorth).nor()
    }

    fun applyTo(camera: PerspectiveCamera) {
        val yaw = MathUtils.degreesToRadians * yawDegrees
        val pitch = MathUtils.degreesToRadians * pitchDegrees
        val horizontal = cos(pitch)
        val meridianComponent = (horizontal * cos(yaw)).toFloat()
        val northComponent = sin(pitch).toFloat()
        val eastComponent = (horizontal * sin(yaw)).toFloat()

        tempPosition.set(orientationMeridian).scl(meridianComponent)
            .add(tempProjection.set(orientationNorth).scl(northComponent))
            .add(tempProjection.set(orientationEast).scl(eastComponent))
            .scl(distance)

        camera.position.set(tempPosition)

        val forward = tempForward.set(camera.position).scl(-1f).nor()
        tempUp.set(orientationNorth).sub(tempProjection.set(forward).scl(orientationNorth.dot(forward)))
        if (tempUp.len2() <= 1e-8f) {
            tempUp.set(orientationMeridian).sub(tempProjection.set(forward).scl(orientationMeridian.dot(forward)))
        }
        if (tempUp.len2() <= 1e-8f) tempUp.set(Vector3.Y)
        camera.up.set(tempUp.nor())
        camera.lookAt(0f, 0f, 0f)
        camera.near = 0.1f
        camera.far = 30f
        camera.update(true)
    }

    fun resetToNorth() {
        yawDegrees = defaultYawDegrees
        pitchDegrees = defaultPitchDegrees
        distance = defaultDistance
    }

    fun resetToNorth(
        northAxis: Vector3,
        meridianAxis: Vector3,
        eastAxis: Vector3
    ) {
        setOrientationAxes(northAxis, meridianAxis, eastAxis)
        yawDegrees = defaultYawDegrees
        pitchDegrees = defaultPitchDegrees
        distance = defaultDistance
    }

    @Readonly
    fun snapshot(): ViewState = ViewState(
        yawDegrees = yawDegrees,
        pitchDegrees = pitchDegrees,
        distance = distance
    )

    fun restore(state: ViewState) {
        yawDegrees = state.yawDegrees
        pitchDegrees = state.pitchDegrees.coerceIn(-85f, 85f)
        distance = state.distance.coerceIn(minDistance, maxDistance)
    }

    private fun projectToTangent(vector: Vector3, normal: Vector3) {
        vector.sub(tempProjection.set(normal).scl(vector.dot(normal)))
    }
}
