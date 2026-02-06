package com.unciv.ui.render.globe

import com.badlogic.gdx.math.Vector3

object IcosaProjection {
    fun projectToSphere(unitDirection: Vector3, radius: Float): Vector3 =
        unitDirection.cpy().nor().scl(radius)

    fun normalizeToUnitSphere(vector: Vector3): Vector3 =
        vector.cpy().nor()
}
