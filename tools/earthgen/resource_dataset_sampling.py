from __future__ import annotations

from collections import deque
from dataclasses import dataclass
from typing import Iterable, Mapping, Sequence

import numpy as np

from tools.earthgen.river_projection import CanonicalEdge
from tools.earthgen.terrain_rules_gnk import LAND_BASE_TERRAINS, WATER_BASE_TERRAINS
from tools.earthgen.topology_io import TopologyDump


@dataclass(frozen=True)
class ResourceDatasetLayers:
    tile_longitude: np.ndarray
    tile_latitude: np.ndarray
    temperature_c: np.ndarray
    precipitation_mm: np.ndarray
    elevation_m: np.ndarray
    slope: np.ndarray
    is_land: np.ndarray
    is_water: np.ndarray
    is_coast: np.ndarray
    is_lake: np.ndarray
    has_hill: np.ndarray
    has_forest: np.ndarray
    has_jungle: np.ndarray
    has_marsh: np.ndarray
    has_ice: np.ndarray
    on_river: np.ndarray
    fresh_water: np.ndarray
    land_distance_to_coast: np.ndarray
    water_distance_to_land: np.ndarray
    river_distance: np.ndarray


def _normalized(values: np.ndarray, low: float | None = None, high: float | None = None) -> np.ndarray:
    if values.size == 0:
        return values
    lo = float(np.nanmin(values) if low is None else low)
    hi = float(np.nanmax(values) if high is None else high)
    if hi - lo < 1e-9:
        return np.zeros_like(values, dtype=np.float64)
    out = (values.astype(np.float64) - lo) / (hi - lo)
    return np.clip(out, 0.0, 1.0)


def _bfs_distances(
    topology: TopologyDump,
    seeds: Sequence[int],
    passable_mask: np.ndarray | None = None,
    max_distance: int = 99_999,
) -> np.ndarray:
    count = len(topology.tiles)
    inf = 10**9
    distances = np.full(count, inf, dtype=np.int32)
    q: deque[int] = deque()
    for idx in seeds:
        distances[idx] = 0
        q.append(idx)

    while q:
        node = q.popleft()
        distance = int(distances[node])
        if distance >= max_distance:
            continue
        for neighbor in topology.tiles[node].neighbors:
            if passable_mask is not None and not bool(passable_mask[neighbor]):
                continue
            nd = distance + 1
            if nd < distances[neighbor]:
                distances[neighbor] = nd
                q.append(neighbor)
    return distances


def _edge_tiles(topology: TopologyDump, river_edges: Iterable[CanonicalEdge]) -> set[int]:
    edge_to_tiles: dict[CanonicalEdge, tuple[int, int]] = {}
    for edge in topology.edges:
        canonical = (edge.a, edge.b) if edge.a < edge.b else (edge.b, edge.a)
        edge_to_tiles[canonical] = (edge.a, edge.b)

    touched: set[int] = set()
    for edge in river_edges:
        tiles = edge_to_tiles.get(edge)
        if tiles is None:
            continue
        touched.add(tiles[0])
        touched.add(tiles[1])
    return touched


def build_resource_dataset_layers(
    topology: TopologyDump,
    classified_tiles: Sequence[object],
    river_edges: Iterable[CanonicalEdge],
) -> ResourceDatasetLayers:
    count = len(classified_tiles)

    lat = np.array([float(getattr(tile, "latitude")) for tile in classified_tiles], dtype=np.float64)
    lon = np.array([float(getattr(tile, "longitude")) for tile in classified_tiles], dtype=np.float64)
    temp = np.array([float(getattr(tile, "temperature_c")) for tile in classified_tiles], dtype=np.float64)
    precip = np.array([float(getattr(tile, "annual_precip_mm")) for tile in classified_tiles], dtype=np.float64)
    elev = np.array([float(getattr(tile, "elevation_m")) for tile in classified_tiles], dtype=np.float64)

    base = [str(getattr(tile, "base_terrain")) for tile in classified_tiles]
    feature_sets = [set(str(v) for v in getattr(tile, "features")) for tile in classified_tiles]

    is_land = np.array([b in LAND_BASE_TERRAINS for b in base], dtype=bool)
    is_water = np.array([b in WATER_BASE_TERRAINS for b in base], dtype=bool)
    is_coast = np.array([b == "Coast" for b in base], dtype=bool)
    is_lake = np.array([b == "Lakes" for b in base], dtype=bool)

    has_hill = np.array(["Hill" in features for features in feature_sets], dtype=bool)
    has_forest = np.array(["Forest" in features for features in feature_sets], dtype=bool)
    has_jungle = np.array(["Jungle" in features for features in feature_sets], dtype=bool)
    has_marsh = np.array(["Marsh" in features for features in feature_sets], dtype=bool)
    has_ice = np.array(["Ice" in features for features in feature_sets], dtype=bool)

    slope = np.zeros(count, dtype=np.float64)
    for i, tile in enumerate(topology.tiles):
        neighbors = tile.neighbors
        if not neighbors:
            continue
        neigh_elev = np.array([elev[n] for n in neighbors], dtype=np.float64)
        slope[i] = float(np.mean(np.abs(neigh_elev - elev[i])))

    river_tiles = _edge_tiles(topology, river_edges)
    on_river = np.array([i in river_tiles for i in range(count)], dtype=bool)

    freshwater = np.zeros(count, dtype=bool)
    for i, tile in enumerate(topology.tiles):
        if not is_land[i]:
            continue
        if on_river[i]:
            freshwater[i] = True
            continue
        if any(is_lake[n] for n in tile.neighbors):
            freshwater[i] = True

    coast_seed = [i for i, val in enumerate(is_coast) if bool(val)]
    coast_dist = _bfs_distances(topology, coast_seed, passable_mask=is_land) if coast_seed else np.full(count, 10**9)

    land_seed = [i for i, val in enumerate(is_land) if bool(val)]
    water_dist = _bfs_distances(topology, land_seed, passable_mask=is_water) if land_seed else np.full(count, 10**9)

    river_seed = [i for i, value in enumerate(on_river) if bool(value)]
    river_dist = _bfs_distances(topology, river_seed, passable_mask=None, max_distance=24) if river_seed else np.full(count, 10**9)

    # Clamp unreachable nodes to conservative finite values for downstream normalization.
    coast_dist = np.where(coast_dist >= 10**9, 999, coast_dist)
    water_dist = np.where(water_dist >= 10**9, 999, water_dist)
    river_dist = np.where(river_dist >= 10**9, 999, river_dist)

    return ResourceDatasetLayers(
        tile_longitude=lon,
        tile_latitude=lat,
        temperature_c=temp,
        precipitation_mm=precip,
        elevation_m=elev,
        slope=slope,
        is_land=is_land,
        is_water=is_water,
        is_coast=is_coast,
        is_lake=is_lake,
        has_hill=has_hill,
        has_forest=has_forest,
        has_jungle=has_jungle,
        has_marsh=has_marsh,
        has_ice=has_ice,
        on_river=on_river,
        fresh_water=freshwater,
        land_distance_to_coast=coast_dist.astype(np.float64),
        water_distance_to_land=water_dist.astype(np.float64),
        river_distance=river_dist.astype(np.float64),
    )


def metric_value(metric: str, tile_index: int, layers: ResourceDatasetLayers) -> float:
    abs_lat = abs(float(layers.tile_latitude[tile_index]))
    warmth = _normalized(np.array([layers.temperature_c[tile_index]]), low=-20.0, high=35.0)[0]
    wetness = _normalized(np.array([layers.precipitation_mm[tile_index]]), low=0.0, high=3500.0)[0]
    aridity = float(np.clip(1.0 - wetness, 0.0, 1.0))
    elevation = _normalized(np.array([layers.elevation_m[tile_index]]), low=0.0, high=4500.0)[0]
    slope = _normalized(np.array([layers.slope[tile_index]]), low=0.0, high=800.0)[0]
    coast_proximity = float(np.clip(1.0 - layers.land_distance_to_coast[tile_index] / 12.0, 0.0, 1.0))
    interiorness = float(np.clip(layers.land_distance_to_coast[tile_index] / 12.0, 0.0, 1.0))
    river_proximity = float(np.clip(1.0 - layers.river_distance[tile_index] / 7.0, 0.0, 1.0))
    water_shallow = float(np.clip(1.0 - layers.water_distance_to_land[tile_index] / 4.0, 0.0, 1.0))
    water_deep = float(np.clip(layers.water_distance_to_land[tile_index] / 6.0, 0.0, 1.0))

    lookup: Mapping[str, float] = {
        "land": 1.0 if bool(layers.is_land[tile_index]) else 0.0,
        "water": 1.0 if bool(layers.is_water[tile_index]) else 0.0,
        "warmth": float(warmth),
        "wetness": float(wetness),
        "aridity": aridity,
        "elevation": float(elevation),
        "slope": float(slope),
        "coast_proximity": coast_proximity,
        "interiorness": interiorness,
        "river_proximity": river_proximity,
        "hill": 1.0 if bool(layers.has_hill[tile_index]) else 0.0,
        "forest": 1.0 if bool(layers.has_forest[tile_index]) else 0.0,
        "jungle": 1.0 if bool(layers.has_jungle[tile_index]) else 0.0,
        "marsh": 1.0 if bool(layers.has_marsh[tile_index]) else 0.0,
        "polar": float(np.clip((abs_lat - 55.0) / 35.0, 0.0, 1.0)),
        "temperate": float(np.clip(1.0 - abs(abs_lat - 38.0) / 32.0, 0.0, 1.0)),
        "tropical": float(np.clip(1.0 - abs_lat / 28.0, 0.0, 1.0)),
        "water_shallow": water_shallow,
        "water_deep": water_deep,
        "desert": 1.0 if not bool(layers.is_water[tile_index]) and aridity > 0.72 else 0.0,
        "plains": 1.0 if not bool(layers.is_water[tile_index]) and 0.55 <= aridity <= 0.78 else 0.0,
        "grassland": 1.0 if bool(layers.is_land[tile_index]) and aridity < 0.55 else 0.0,
        "tundra": 1.0 if bool(layers.is_land[tile_index]) and abs_lat >= 58.0 and abs_lat < 74.0 else 0.0,
        "snow": 1.0 if bool(layers.is_land[tile_index]) and abs_lat >= 74.0 else 0.0,
        "fresh_water": 1.0 if bool(layers.fresh_water[tile_index]) else 0.0,
    }
    return float(lookup.get(metric, 0.0))
