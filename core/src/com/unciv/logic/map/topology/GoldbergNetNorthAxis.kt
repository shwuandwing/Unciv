package com.unciv.logic.map.topology

import com.badlogic.gdx.math.Vector3
import com.unciv.logic.map.HexCoord
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2

/**
 * Computes a deterministic orientation basis from the unfolded icosa net.
 *
 * "North" is defined by the vector from the bottom-center tile to the top-center tile,
 * where top/bottom rows follow rendered net vertical ordering (equivalent to `HexCoord.y`),
 * and centers are selected within each row's horizontal span.
 */
object GoldbergNetNorthAxis {

    data class PoleTileIndices(
        val topCenterIndex: Int,
        val bottomCenterIndex: Int
    )

    data class Basis(
        val northAxis: Vector3,
        val southAxis: Vector3,
        val meridianAxis: Vector3,
        val eastAxis: Vector3,
        val topCenterIndex: Int,
        val bottomCenterIndex: Int
    )

    fun selectPoleTileIndices(indexToCoord: List<HexCoord>): PoleTileIndices {
        require(indexToCoord.isNotEmpty()) { "Cannot select pole tiles from empty coordinate set" }

        // For icosa net rendering, vertical row ordering is defined by HexCoord.y.
        val minY = indexToCoord.minOf { it.y }
        val maxY = indexToCoord.maxOf { it.y }

        val topCandidates = indexToCoord.indices
            .asSequence()
            .filter { indexToCoord[it].y == minY }
            .toList()
        val bottomCandidates = indexToCoord.indices
            .asSequence()
            .filter { indexToCoord[it].y == maxY }
            .toList()

        val topCenterX = topCandidates.let { candidates ->
            val minX = candidates.minOf { indexToCoord[it].x }
            val maxX = candidates.maxOf { indexToCoord[it].x }
            (minX + maxX) / 2.0
        }
        val bottomCenterX = bottomCandidates.let { candidates ->
            val minX = candidates.minOf { indexToCoord[it].x }
            val maxX = candidates.maxOf { indexToCoord[it].x }
            (minX + maxX) / 2.0
        }

        val topCenter = topCandidates
            .asSequence()
            .minWithOrNull(
                compareBy<Int> { abs(indexToCoord[it].x - topCenterX) }
                    .thenBy { indexToCoord[it].x }
                    .thenBy { it }
            )
            ?: error("No top-row candidates found for net-derived north selection")

        val bottomCenter = bottomCandidates
            .asSequence()
            .minWithOrNull(
                compareBy<Int> { abs(indexToCoord[it].x - bottomCenterX) }
                    .thenBy { indexToCoord[it].x }
                    .thenBy { it }
            )
            ?: error("No bottom-row candidates found for net-derived north selection")

        return PoleTileIndices(topCenter, bottomCenter)
    }

    fun buildBasis(
        mesh: GoldbergMeshBuilder.GoldbergMesh,
        indexToCoord: List<HexCoord>
    ): Basis {
        require(mesh.vertices.size == indexToCoord.size) {
            "Mesh/layout size mismatch: mesh=${mesh.vertices.size} coords=${indexToCoord.size}"
        }

        val poles = selectPoleTileIndices(indexToCoord)
        val top = mesh.vertices[poles.topCenterIndex].cpy().nor()
        val bottom = mesh.vertices[poles.bottomCenterIndex].cpy().nor()

        val north = top.cpy().sub(bottom)
        if (north.len2() <= 1e-8f) north.set(top)
        if (north.len2() <= 1e-8f) north.set(Vector3.Y)
        north.nor()

        val meridian = projectToTangent(Vector3.X, north)
        if (meridian.len2() <= 1e-8f) meridian.set(projectToTangent(Vector3.Z, north))
        if (meridian.len2() <= 1e-8f) meridian.set(projectToTangent(Vector3.Y, north))
        if (meridian.len2() <= 1e-8f) meridian.set(Vector3.X)
        meridian.nor()

        val east = north.cpy().crs(meridian)
        if (east.len2() <= 1e-8f) east.set(Vector3.Z)
        east.nor()

        return Basis(
            northAxis = north,
            southAxis = north.cpy().scl(-1f),
            meridianAxis = meridian,
            eastAxis = east,
            topCenterIndex = poles.topCenterIndex,
            bottomCenterIndex = poles.bottomCenterIndex
        )
    }

    fun latitudeDegrees(direction: Vector3, basis: Basis): Double {
        val unit = direction.cpy().nor()
        return Math.toDegrees(asin(unit.dot(basis.northAxis).toDouble().coerceIn(-1.0, 1.0)))
    }

    fun longitudeDegrees(direction: Vector3, basis: Basis): Double {
        val unit = direction.cpy().nor()
        return Math.toDegrees(
            atan2(
                unit.dot(basis.eastAxis).toDouble(),
                unit.dot(basis.meridianAxis).toDouble()
            )
        )
    }

    private fun projectToTangent(seed: Vector3, normal: Vector3): Vector3 {
        return seed.cpy().sub(normal.cpy().scl(seed.dot(normal)))
    }
}
