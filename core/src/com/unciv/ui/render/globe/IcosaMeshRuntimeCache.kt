package com.unciv.ui.render.globe

import com.badlogic.gdx.math.Vector3
import com.unciv.logic.map.GoldbergFrequency
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.topology.GoldbergGeometryBundleCache
import com.unciv.logic.map.topology.GoldbergNetLayoutBuilder
import com.unciv.logic.map.topology.GoldbergNetNorthAxis

class IcosaMeshRuntimeCache private constructor(
    val centers: Array<Vector3>,
    val normals: Array<Vector3>,
    val neighborRings: Array<IntArray>,
    val cornerRings: Array<Array<Vector3>>,
    val localNorthOffsets: Array<Vector3>,
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
            val layoutId = tileMap.mapParameters.goldbergLayout
                .takeIf { it.isNotBlank() }
                ?: GoldbergNetLayoutBuilder.DEFAULT_LAYOUT
            val geometry = GoldbergGeometryBundleCache.get(frequency, layoutId)
            require(geometry.mesh.vertices.size == tileMap.tileList.size) {
                "Goldberg mesh size mismatch: mesh=${geometry.mesh.vertices.size} tiles=${tileMap.tileList.size}"
            }

            val localNorthOffsets = Array(geometry.centers.size) { index ->
                GlobeOverlayOrientation.localNorthOffsetDirection(geometry.centers[index])
            }

            return IcosaMeshRuntimeCache(
                centers = geometry.centers,
                normals = geometry.normals,
                neighborRings = geometry.neighborRings,
                cornerRings = geometry.cornerRings,
                localNorthOffsets = localNorthOffsets,
                orientationBasis = geometry.orientationBasis
            )
        }
    }
}
