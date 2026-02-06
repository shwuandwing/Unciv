package com.unciv.logic.map.topology

import com.badlogic.gdx.math.Vector3
import kotlin.math.sqrt

object GoldbergMeshBuilder {

    data class GoldbergMesh(
        val vertices: List<Vector3>,
        val neighbors: List<IntArray>,
        val vertexKeyToIndex: Map<String, Int>,
        val vertexFaces: List<IntArray>,
        val faces: List<IntArray>,
        val edgeMaxChord: Float
    )

    fun build(frequency: Int): GoldbergMesh {
        require(frequency >= 1) { "Goldberg frequency must be >= 1" }

        val baseVertices = buildBaseVertices()
        val faces = buildFaces()

        val vertexKeyToIndex = LinkedHashMap<String, Int>()
        val vertices = ArrayList<Vector3>()
        val neighbors = ArrayList<MutableSet<Int>>()

        fun addVertex(key: String, pos: Vector3): Int {
            val existing = vertexKeyToIndex[key]
            if (existing != null) return existing
            val index = vertices.size
            vertexKeyToIndex[key] = index
            vertices.add(pos)
            neighbors.add(LinkedHashSet())
            return index
        }

        fun keyForEdge(a: Int, b: Int, weightA: Int, weightB: Int, frequency: Int): String {
            val min = minOf(a, b)
            val max = maxOf(a, b)
            val t = if (a == min) weightB else weightA
            return "E${min}_${max}_$t"
        }

        fun keyForVertex(faceIndex: Int, i: Int, j: Int, k: Int, v0: Int, v1: Int, v2: Int, frequency: Int): String {
            if (i == frequency && j == 0 && k == 0) return "V$v0"
            if (j == frequency && i == 0 && k == 0) return "V$v1"
            if (k == frequency && i == 0 && j == 0) return "V$v2"
            if (k == 0) return keyForEdge(v0, v1, i, j, frequency)
            if (i == 0) return keyForEdge(v1, v2, j, k, frequency)
            if (j == 0) return keyForEdge(v2, v0, k, i, frequency)
            return "F${faceIndex}_${i}_${j}_${k}"
        }

        fun buildPoint(faceIndex: Int, i: Int, j: Int, k: Int, v0: Int, v1: Int, v2: Int): Int {
            val key = keyForVertex(faceIndex, i, j, k, v0, v1, v2, frequency)
            val existing = vertexKeyToIndex[key]
            if (existing != null) return existing
            val pos = Vector3(
                baseVertices[v0].x * i + baseVertices[v1].x * j + baseVertices[v2].x * k,
                baseVertices[v0].y * i + baseVertices[v1].y * j + baseVertices[v2].y * k,
                baseVertices[v0].z * i + baseVertices[v1].z * j + baseVertices[v2].z * k
            ).nor()
            return addVertex(key, pos)
        }

        fun connect(a: Int, b: Int) {
            if (a == b) return
            neighbors[a].add(b)
            neighbors[b].add(a)
        }

        for ((faceIndex, face) in faces.withIndex()) {
            val v0 = face[0]
            val v1 = face[1]
            val v2 = face[2]
            for (i in 0..frequency) {
                for (j in 0..(frequency - i)) {
                    val k = frequency - i - j
                    val idx = buildPoint(faceIndex, i, j, k, v0, v1, v2)
                    if (j > 0) {
                        val idx2 = buildPoint(faceIndex, i + 1, j - 1, k, v0, v1, v2)
                        connect(idx, idx2)
                    }
                    if (k > 0) {
                        val idx2 = buildPoint(faceIndex, i + 1, j, k - 1, v0, v1, v2)
                        connect(idx, idx2)
                    }
                    if (k > 0) {
                        val idx2 = buildPoint(faceIndex, i, j + 1, k - 1, v0, v1, v2)
                        connect(idx, idx2)
                    }
                }
            }
        }

        val neighborsFinal = neighbors.map { set -> set.toIntArray() }
        val vertexFaces = buildVertexFaces(vertexKeyToIndex, faces, vertices.size)
        val maxChord = computeMaxChord(vertices, neighborsFinal)

        return GoldbergMesh(vertices, neighborsFinal, vertexKeyToIndex, vertexFaces, faces, maxChord)
    }

    private fun buildBaseVertices(): List<Vector3> {
        val t = (1.0 + sqrt(5.0)) / 2.0
        val verts = listOf(
            Vector3(-1f, t.toFloat(), 0f),
            Vector3(1f, t.toFloat(), 0f),
            Vector3(-1f, -t.toFloat(), 0f),
            Vector3(1f, -t.toFloat(), 0f),
            Vector3(0f, -1f, t.toFloat()),
            Vector3(0f, 1f, t.toFloat()),
            Vector3(0f, -1f, -t.toFloat()),
            Vector3(0f, 1f, -t.toFloat()),
            Vector3(t.toFloat(), 0f, -1f),
            Vector3(t.toFloat(), 0f, 1f),
            Vector3(-t.toFloat(), 0f, -1f),
            Vector3(-t.toFloat(), 0f, 1f)
        )
        return verts.map { it.nor() }
    }

    private fun buildFaces(): List<IntArray> = listOf(
        intArrayOf(0, 11, 5),
        intArrayOf(0, 5, 1),
        intArrayOf(0, 1, 7),
        intArrayOf(0, 7, 10),
        intArrayOf(0, 10, 11),
        intArrayOf(1, 5, 9),
        intArrayOf(5, 11, 4),
        intArrayOf(11, 10, 2),
        intArrayOf(10, 7, 6),
        intArrayOf(7, 1, 8),
        intArrayOf(3, 9, 4),
        intArrayOf(3, 4, 2),
        intArrayOf(3, 2, 6),
        intArrayOf(3, 6, 8),
        intArrayOf(3, 8, 9),
        intArrayOf(4, 9, 5),
        intArrayOf(2, 4, 11),
        intArrayOf(6, 2, 10),
        intArrayOf(8, 6, 7),
        intArrayOf(9, 8, 1)
    )

    private fun computeMaxChord(vertices: List<Vector3>, neighbors: List<IntArray>): Float {
        var maxChord = 0f
        for (i in vertices.indices) {
            val v = vertices[i]
            for (n in neighbors[i]) {
                val u = vertices[n]
                val dx = v.x - u.x
                val dy = v.y - u.y
                val dz = v.z - u.z
                val chord = sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
                if (chord > maxChord) maxChord = chord
            }
        }
        return maxChord
    }

    private fun buildVertexFaces(
        vertexKeyToIndex: Map<String, Int>,
        faces: List<IntArray>,
        vertexCount: Int
    ): List<IntArray> {
        val memberships = Array(vertexCount) { LinkedHashSet<Int>() }

        fun edgeKey(a: Int, b: Int): Pair<Int, Int> = if (a <= b) a to b else b to a

        val edgeToFaces = HashMap<Pair<Int, Int>, MutableList<Int>>()
        for ((faceIndex, face) in faces.withIndex()) {
            val a = face[0]
            val b = face[1]
            val c = face[2]
            edgeToFaces.getOrPut(edgeKey(a, b)) { ArrayList() }.add(faceIndex)
            edgeToFaces.getOrPut(edgeKey(b, c)) { ArrayList() }.add(faceIndex)
            edgeToFaces.getOrPut(edgeKey(c, a)) { ArrayList() }.add(faceIndex)
        }

        for ((key, index) in vertexKeyToIndex) {
            when {
                key.startsWith("F") -> {
                    val faceIndex = key.substring(1).substringBefore('_').toInt()
                    memberships[index].add(faceIndex)
                }
                key.startsWith("E") -> {
                    val parts = key.substring(1).split('_')
                    if (parts.size >= 2) {
                        val a = parts[0].toInt()
                        val b = parts[1].toInt()
                        val adjacentFaces = edgeToFaces[edgeKey(a, b)] ?: emptyList()
                        for (faceIndex in adjacentFaces) memberships[index].add(faceIndex)
                    }
                }
                key.startsWith("V") -> {
                    val vertex = key.substring(1).toInt()
                    for ((faceIndex, face) in faces.withIndex()) {
                        if (face.contains(vertex)) memberships[index].add(faceIndex)
                    }
                }
            }
        }

        return memberships.mapIndexed { index, set ->
            if (set.isEmpty()) throw IllegalStateException("Missing face membership for Goldberg vertex $index")
            set.toIntArray()
        }
    }
}
