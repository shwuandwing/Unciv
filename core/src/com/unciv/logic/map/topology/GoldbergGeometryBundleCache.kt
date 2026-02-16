package com.unciv.logic.map.topology

import com.badlogic.gdx.math.Vector3
import com.unciv.logic.map.HexMath
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * Shared immutable geometry bundle for Goldberg/icosa maps keyed by frequency+layout.
 *
 * This centralizes expensive mesh/layout/orientation and render-geometry derivations so
 * map generation, topology creation, topology export, and globe rendering all reuse them.
 */
object GoldbergGeometryBundleCache {

    data class Key(
        val frequency: Int,
        val layoutId: String
    )

    data class Bundle(
        val frequency: Int,
        val layoutId: String,
        val mesh: GoldbergMeshBuilder.GoldbergMesh,
        val layout: GoldbergNetLayoutBuilder.LayoutResult,
        val orientationBasis: GoldbergNetNorthAxis.Basis,
        val latitudes: IntArray,
        val longitudes: IntArray,
        val seamEdges: Array<IntArray>,
        val primaryFaceByIndex: IntArray,
        val faceLabelTileByFace: IntArray,
        val centers: Array<Vector3>,
        val normals: Array<Vector3>,
        val neighborRings: Array<IntArray>,
        val cornerRings: Array<Array<Vector3>>
    )

    private const val maxEntries = 8
    private val cache = object : LinkedHashMap<Key, Bundle>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Bundle>?): Boolean {
            return size > maxEntries
        }
    }

    @Synchronized
    fun get(frequency: Int, layoutId: String): Bundle {
        require(frequency >= 1) { "Goldberg frequency must be >= 1" }
        val normalizedLayoutId = layoutId.ifBlank { GoldbergNetLayoutBuilder.DEFAULT_LAYOUT }
        val key = Key(frequency, normalizedLayoutId)
        return cache[key] ?: buildBundle(frequency, normalizedLayoutId).also { cache[key] = it }
    }

    private fun buildBundle(frequency: Int, layoutId: String): Bundle {
        val mesh = GoldbergMeshBuilder.build(frequency)
        val layout = GoldbergNetLayoutBuilder.buildIndexToCoord(frequency, mesh, layoutId)
        val orientationBasis = GoldbergNetNorthAxis.buildBasis(mesh, layout)

        val latitudes = IntArray(mesh.vertices.size)
        val longitudes = IntArray(mesh.vertices.size)
        for (index in mesh.vertices.indices) {
            val direction = mesh.vertices[index]
            val lat = GoldbergNetNorthAxis.latitudeDegrees(direction, orientationBasis)
            val lon = GoldbergNetNorthAxis.longitudeDegrees(direction, orientationBasis)
            latitudes[index] = (lat * 1000.0).roundToInt()
            longitudes[index] = (lon * 1000.0).roundToInt()
        }

        val seamEdges = Array(mesh.neighbors.size) { index ->
            val seams = ArrayList<Int>(2)
            val coord = layout.indexToCoord[index]
            for (neighbor in mesh.neighbors[index]) {
                if (HexMath.getDistance(coord, layout.indexToCoord[neighbor]) > 1) {
                    seams += neighbor
                }
            }
            seams.sort()
            seams.toIntArray()
        }

        val primaryFaceByIndex = IntArray(mesh.vertexFaces.size) { index ->
            mesh.vertexFaces[index].minOrNull() ?: -1
        }
        val faceLabelTileByFace = computeFaceLabelTiles(mesh, layout, primaryFaceByIndex)

        val centers = Array(mesh.vertices.size) { index ->
            mesh.vertices[index].cpy().nor()
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
                centers[index].cpy().add(left).add(right).nor()
            }
        }

        return Bundle(
            frequency = frequency,
            layoutId = layoutId,
            mesh = mesh,
            layout = layout,
            orientationBasis = orientationBasis,
            latitudes = latitudes,
            longitudes = longitudes,
            seamEdges = seamEdges,
            primaryFaceByIndex = primaryFaceByIndex,
            faceLabelTileByFace = faceLabelTileByFace,
            centers = centers,
            normals = normals,
            neighborRings = neighborRings,
            cornerRings = cornerRings
        )
    }

    private fun computeFaceLabelTiles(
        mesh: GoldbergMeshBuilder.GoldbergMesh,
        layout: GoldbergNetLayoutBuilder.LayoutResult,
        primaryFaceByIndex: IntArray
    ): IntArray {
        val worldPositions = Array(layout.indexToCoord.size) { index ->
            HexMath.hex2WorldCoords(layout.indexToCoord[index])
        }
        val faceLabelTileByFace = IntArray(mesh.faces.size) { -1 }

        for (face in mesh.faces.indices) {
            val candidates = ArrayList<Int>()
            for (index in primaryFaceByIndex.indices) {
                if (primaryFaceByIndex[index] == face) candidates += index
            }
            if (candidates.isEmpty()) continue

            var centerX = 0f
            var centerY = 0f
            for (index in candidates) {
                val pos = worldPositions[index]
                centerX += pos.x
                centerY += pos.y
            }
            centerX /= candidates.size
            centerY /= candidates.size

            var bestIndex = candidates.first()
            var bestSameFaceNeighbors = -1
            var bestCenterDist = Float.MAX_VALUE
            for (index in candidates) {
                var sameFaceNeighbors = 0
                for (neighbor in mesh.neighbors[index]) {
                    if (primaryFaceByIndex[neighbor] == face) sameFaceNeighbors++
                }
                val pos = worldPositions[index]
                val dx = pos.x - centerX
                val dy = pos.y - centerY
                val centerDist = dx * dx + dy * dy
                if (sameFaceNeighbors > bestSameFaceNeighbors ||
                    (sameFaceNeighbors == bestSameFaceNeighbors && centerDist < bestCenterDist) ||
                    (sameFaceNeighbors == bestSameFaceNeighbors && centerDist == bestCenterDist && index < bestIndex)
                ) {
                    bestIndex = index
                    bestSameFaceNeighbors = sameFaceNeighbors
                    bestCenterDist = centerDist
                }
            }
            faceLabelTileByFace[face] = bestIndex
        }

        return faceLabelTileByFace
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
