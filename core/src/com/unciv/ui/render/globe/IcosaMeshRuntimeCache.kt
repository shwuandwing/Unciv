package com.unciv.ui.render.globe

import com.badlogic.gdx.math.Vector3
import com.unciv.logic.map.GoldbergFrequency
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.topology.GoldbergNetLayoutBuilder
import com.unciv.logic.map.topology.GoldbergNetNorthAxis
import com.unciv.logic.map.topology.GoldbergMeshBuilder
import kotlin.math.abs
import kotlin.math.atan2

class IcosaMeshRuntimeCache private constructor(
    val centers: Array<Vector3>,
    val normals: Array<Vector3>,
    val neighborRings: Array<IntArray>,
    val cornerRings: Array<Array<Vector3>>,
    val orientationBasis: GoldbergNetNorthAxis.Basis
) {
    companion object {
        fun from(tileMap: TileMap): IcosaMeshRuntimeCache {
            require(tileMap.mapParameters.shape == MapShape.icosahedron) {
                "IcosaMeshRuntimeCache only supports icosahedron maps"
            }

            val frequency = tileMap.mapParameters.goldbergFrequency
                .takeIf { it > 0 }
                ?: GoldbergFrequency.selectForMapSize(tileMap.mapParameters.mapSize)
            val mesh = GoldbergMeshBuilder.build(frequency)
            val layoutId = tileMap.mapParameters.goldbergLayout
                .takeIf { it.isNotBlank() }
                ?: GoldbergNetLayoutBuilder.DEFAULT_LAYOUT
            val layout = GoldbergNetLayoutBuilder.buildIndexToCoord(frequency, mesh, layoutId)
            val orientationBasis = GoldbergNetNorthAxis.buildBasis(mesh, layout.indexToCoord)
            require(mesh.vertices.size == tileMap.tileList.size) {
                "Goldberg mesh size mismatch: mesh=${mesh.vertices.size} tiles=${tileMap.tileList.size}"
            }

            val centers = Array(mesh.vertices.size) { index ->
                IcosaProjection.normalizeToUnitSphere(mesh.vertices[index])
            }
            val normals = Array(mesh.vertices.size) { index ->
                centers[index].cpy()
            }

            val neighborRings = Array(mesh.neighbors.size) { index ->
                sortNeighborRing(index, centers, mesh.neighbors[index])
            }

            val cornerRings = Array(mesh.neighbors.size) { index ->
                val ring = neighborRings[index]
                Array(ring.size) { cornerIndex ->
                    val left = centers[ring[cornerIndex]]
                    val right = centers[ring[(cornerIndex + 1) % ring.size]]
                    IcosaProjection.normalizeToUnitSphere(
                        centers[index].cpy().add(left).add(right)
                    )
                }
            }

            return IcosaMeshRuntimeCache(centers, normals, neighborRings, cornerRings, orientationBasis)
        }

        private fun sortNeighborRing(index: Int, centers: Array<Vector3>, neighbors: IntArray): IntArray {
            if (neighbors.size <= 1) return neighbors.copyOf()

            val center = centers[index]
            val tangentSeed = if (abs(center.y) < 0.9f) Vector3.Y else Vector3.X
            val tangent = tangentSeed.cpy().crs(center).nor()
            val bitangent = center.cpy().crs(tangent).nor()

            return neighbors
                .map { neighbor ->
                    val vec = centers[neighbor]
                    val x = vec.dot(tangent)
                    val y = vec.dot(bitangent)
                    val angle = atan2(y.toDouble(), x.toDouble()).toFloat()
                    neighbor to angle
                }
                .sortedBy { it.second }
                .map { it.first }
                .toIntArray()
        }
    }
}
