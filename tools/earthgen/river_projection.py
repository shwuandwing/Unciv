from __future__ import annotations

from collections import deque
from dataclasses import dataclass
from typing import Dict, Iterable, List, Sequence, Set, Tuple

import numpy as np

from tools.earthgen.dataset_sampling import geodesic_polyline_length_km, haversine_km, wrap_longitude
from tools.earthgen.topology_io import TopologyDump, build_edge_writer_index_from_dump


CanonicalEdge = Tuple[int, int]
LonLat = Tuple[float, float]


@dataclass(frozen=True)
class RiverProjectionResult:
    selected_lines: Tuple[Tuple[LonLat, ...], ...]
    chains: Tuple[Tuple[CanonicalEdge, ...], ...]
    edges: Tuple[CanonicalEdge, ...]
    skipped_segments: int


def canonical_edge(a: int, b: int) -> CanonicalEdge:
    return (a, b) if a < b else (b, a)


def select_longest_river_lines(river_lines: Sequence[Sequence[LonLat]], count: int) -> List[List[LonLat]]:
    if count <= 0:
        return []
    weighted = []
    for line in river_lines:
        if len(line) < 2:
            continue
        weighted.append((geodesic_polyline_length_km(line), list(line)))
    weighted.sort(key=lambda pair: pair[0], reverse=True)
    return [line for _, line in weighted[:count]]


def project_river_lines_to_edges(
    topology: TopologyDump,
    river_lines: Sequence[Sequence[LonLat]],
    max_rivers: int,
    tile_coordinates: Sequence[LonLat] | None = None,
    max_segment_km: float = 120.0,
) -> RiverProjectionResult:
    selected_raw = select_longest_river_lines(river_lines, max_rivers)
    selected = [densify_line(line, max_segment_km=max_segment_km) for line in selected_raw]
    if not selected:
        return RiverProjectionResult(selected_lines=tuple(), chains=tuple(), edges=tuple(), skipped_segments=0)

    writer_map = build_edge_writer_index_from_dump(topology)
    adjacency = _build_adjacency(topology.tile_count, writer_map.keys())
    locator = _TileLocator(topology, tile_coordinates=tile_coordinates)
    neighbor_cycle = _NeighborCycle(topology, tile_coordinates=tile_coordinates)
    path_cache: Dict[Tuple[int, int], Tuple[int, ...] | None] = {}

    unique_edges: Set[CanonicalEdge] = set()
    chains: List[Tuple[CanonicalEdge, ...]] = []
    skipped_segments = 0

    for line in selected:
        tile_seq = _dedupe_consecutive(locator.nearest_indices(line))
        if len(tile_seq) < 2:
            continue

        chain_raw: List[CanonicalEdge] = []
        current = tile_seq[0]
        for nxt in tile_seq[1:]:
            if nxt == current:
                continue
            path = _shortest_path(current, nxt, adjacency, path_cache)
            if path is None or len(path) < 2:
                skipped_segments += 1
                current = nxt
                continue

            for a, b in zip(path, path[1:]):
                edge = canonical_edge(a, b)
                if edge not in writer_map:
                    # This should be impossible if shortest_path uses representable adjacency,
                    # but keep the guard to avoid malformed writes.
                    skipped_segments += 1
                    continue
                chain_raw.append(edge)
            current = nxt

        if chain_raw:
            chain = _bridge_chain_for_corner_continuity(chain_raw, neighbor_cycle, writer_map)
            for edge in chain:
                unique_edges.add(edge)
            chains.append(tuple(chain))

    return RiverProjectionResult(
        selected_lines=tuple(tuple(line) for line in selected_raw),
        chains=tuple(chains),
        edges=tuple(sorted(unique_edges)),
        skipped_segments=skipped_segments,
    )


def _build_adjacency(tile_count: int, edges: Iterable[CanonicalEdge]) -> List[List[int]]:
    adjacency: List[Set[int]] = [set() for _ in range(tile_count)]
    for a, b in edges:
        adjacency[a].add(b)
        adjacency[b].add(a)
    return [sorted(neighbors) for neighbors in adjacency]


class _TileLocator:
    def __init__(self, topology: TopologyDump, tile_coordinates: Sequence[LonLat] | None = None):
        if tile_coordinates is None:
            coord_lons = [tile.longitude for tile in topology.tiles]
            coord_lats = [tile.latitude for tile in topology.tiles]
        else:
            if len(tile_coordinates) != topology.tile_count:
                raise ValueError(
                    f"tile_coordinates length mismatch: expected {topology.tile_count}, got {len(tile_coordinates)}"
                )
            coord_lons = [coord[0] for coord in tile_coordinates]
            coord_lats = [coord[1] for coord in tile_coordinates]

        lats = np.radians(np.array(coord_lats, dtype=np.float64))
        lons = np.radians(np.array(coord_lons, dtype=np.float64))
        x = np.cos(lats) * np.cos(lons)
        y = np.cos(lats) * np.sin(lons)
        z = np.sin(lats)
        self._tile_vectors = np.stack((x, y, z), axis=1)

    def nearest_indices(self, points: Sequence[LonLat]) -> List[int]:
        if not points:
            return []
        lons = np.radians(np.array([p[0] for p in points], dtype=np.float64))
        lats = np.radians(np.array([p[1] for p in points], dtype=np.float64))
        px = np.cos(lats) * np.cos(lons)
        py = np.cos(lats) * np.sin(lons)
        pz = np.sin(lats)
        vectors = np.stack((px, py, pz), axis=1)

        # Max dot product = minimum angular distance on unit sphere.
        dots = vectors @ self._tile_vectors.T
        return [int(i) for i in np.argmax(dots, axis=1)]


class _NeighborCycle:
    def __init__(self, topology: TopologyDump, tile_coordinates: Sequence[LonLat] | None = None):
        if tile_coordinates is None:
            coord_lons = [tile.longitude for tile in topology.tiles]
            coord_lats = [tile.latitude for tile in topology.tiles]
        else:
            if len(tile_coordinates) != topology.tile_count:
                raise ValueError(
                    f"tile_coordinates length mismatch: expected {topology.tile_count}, got {len(tile_coordinates)}"
                )
            coord_lons = [coord[0] for coord in tile_coordinates]
            coord_lats = [coord[1] for coord in tile_coordinates]

        lats = np.radians(np.array(coord_lats, dtype=np.float64))
        lons = np.radians(np.array(coord_lons, dtype=np.float64))
        vectors = np.stack(
            (np.cos(lats) * np.cos(lons), np.cos(lats) * np.sin(lons), np.sin(lats)),
            axis=1,
        )

        self._order: List[List[int]] = []
        self._index: List[Dict[int, int]] = []
        global_z = np.array([0.0, 0.0, 1.0], dtype=np.float64)
        global_y = np.array([0.0, 1.0, 0.0], dtype=np.float64)

        for tile in topology.tiles:
            idx = tile.index
            up = vectors[idx]
            east = np.cross(global_z, up)
            east_norm = np.linalg.norm(east)
            if east_norm < 1e-8:
                east = np.cross(global_y, up)
                east_norm = np.linalg.norm(east)
            east /= east_norm
            north = np.cross(up, east)

            angle_neighbors: List[Tuple[float, int]] = []
            for neighbor in tile.neighbors:
                delta = vectors[neighbor] - up * np.dot(vectors[neighbor], up)
                angle = float(np.arctan2(np.dot(delta, east), np.dot(delta, north)))
                angle_neighbors.append((angle, neighbor))
            angle_neighbors.sort(key=lambda pair: pair[0])
            ordered_neighbors = [neighbor for _, neighbor in angle_neighbors]
            self._order.append(ordered_neighbors)
            self._index.append({neighbor: local for local, neighbor in enumerate(ordered_neighbors)})

    def intermediate_neighbors(self, tile: int, neighbor_from: int, neighbor_to: int) -> List[int]:
        ordered = self._order[tile]
        if len(ordered) < 3:
            return []
        index_map = self._index[tile]
        if neighbor_from not in index_map or neighbor_to not in index_map:
            return []

        from_i = index_map[neighbor_from]
        to_i = index_map[neighbor_to]
        if from_i == to_i:
            return []

        cw: List[int] = []
        i = (from_i + 1) % len(ordered)
        while i != to_i:
            cw.append(ordered[i])
            i = (i + 1) % len(ordered)

        ccw: List[int] = []
        i = (from_i - 1) % len(ordered)
        while i != to_i:
            ccw.append(ordered[i])
            i = (i - 1) % len(ordered)

        return cw if len(cw) <= len(ccw) else ccw


def _dedupe_consecutive(indices: Sequence[int]) -> List[int]:
    out: List[int] = []
    for idx in indices:
        if not out or out[-1] != idx:
            out.append(idx)
    return out


def _bridge_chain_for_corner_continuity(
    chain: Sequence[CanonicalEdge],
    neighbor_cycle: _NeighborCycle,
    writer_map: Dict[CanonicalEdge, Tuple[int, str]],
) -> List[CanonicalEdge]:
    if len(chain) < 2:
        return list(chain)

    bridged: List[CanonicalEdge] = [chain[0]]
    for edge in chain[1:]:
        previous = bridged[-1]
        shared_tiles = set(previous) & set(edge)
        if len(shared_tiles) == 1:
            shared = next(iter(shared_tiles))
            prev_other = previous[0] if previous[1] == shared else previous[1]
            next_other = edge[0] if edge[1] == shared else edge[1]
            for intermediate_neighbor in neighbor_cycle.intermediate_neighbors(shared, prev_other, next_other):
                bridge_edge = canonical_edge(shared, intermediate_neighbor)
                if bridge_edge in writer_map and bridged[-1] != bridge_edge:
                    bridged.append(bridge_edge)
        if bridged[-1] != edge:
            bridged.append(edge)
    return bridged


def _shortest_path(
    start: int,
    end: int,
    adjacency: Sequence[Sequence[int]],
    path_cache: Dict[Tuple[int, int], Tuple[int, ...] | None],
) -> Tuple[int, ...] | None:
    if start == end:
        return (start,)
    cached = path_cache.get((start, end))
    if cached is not None:
        return cached

    parents = {start: -1}
    queue = deque([start])
    found = False
    while queue:
        current = queue.popleft()
        if current == end:
            found = True
            break
        for neighbor in adjacency[current]:
            if neighbor in parents:
                continue
            parents[neighbor] = current
            queue.append(neighbor)

    if not found:
        path_cache[(start, end)] = None
        return None

    path_nodes = [end]
    while path_nodes[-1] != start:
        path_nodes.append(parents[path_nodes[-1]])
    path_nodes.reverse()

    path = tuple(path_nodes)
    reverse_path = tuple(reversed(path))
    path_cache[(start, end)] = path
    path_cache[(end, start)] = reverse_path
    return path


def densify_line(line: Sequence[LonLat], max_segment_km: float) -> List[LonLat]:
    if len(line) < 2 or max_segment_km <= 0:
        return list(line)

    densified: List[LonLat] = [line[0]]
    for (lon1, lat1), (lon2, lat2) in zip(line, line[1:]):
        segment_km = haversine_km(lat1, lon1, lat2, lon2)
        steps = max(1, int(np.ceil(segment_km / max_segment_km)))
        lon2_unwrapped = _unwrap_lon_delta(lon1, lon2)
        for step in range(1, steps + 1):
            t = step / steps
            lon = wrap_longitude(lon1 + (lon2_unwrapped - lon1) * t)
            lat = lat1 + (lat2 - lat1) * t
            densified.append((lon, lat))
    return densified


def _unwrap_lon_delta(lon_from: float, lon_to: float) -> float:
    delta = lon_to - lon_from
    if delta > 180.0:
        return lon_to - 360.0
    if delta < -180.0:
        return lon_to + 360.0
    return lon_to
