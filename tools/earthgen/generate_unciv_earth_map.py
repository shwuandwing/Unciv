#!/usr/bin/env python3
from __future__ import annotations

import argparse
import math
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Iterable, List, Sequence, Tuple

# Allow running as a script from repository root without `-m`.
if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from tools.earthgen.dataset_sampling import EarthDatasets, load_earth_datasets
from tools.earthgen.river_projection import CanonicalEdge, project_river_lines_to_edges
from tools.earthgen.terrain_rules_gnk import (
    ClimateSample,
    LAND_BASE_TERRAINS,
    WATER_BASE_TERRAINS,
    classify_base_terrain,
    classify_features,
)
from tools.earthgen.topology_io import (
    TopologyDump,
    build_edge_writer_index_from_dump,
    load_topology_dump,
)
from tools.earthgen.unciv_map_io import write_map_file
from tools.earthgen.dataset_sampling import wrap_longitude

import numpy as np


PREDEFINED_SIZE_TO_FREQUENCY = {
    "Tiny": 5,
    "Small": 8,
    "Medium": 11,
    "Large": 16,
    "Huge": 22,
}

VALID_BASE_TERRAINS = {
    "Ocean",
    "Coast",
    "Lakes",
    "Grassland",
    "Plains",
    "Desert",
    "Tundra",
    "Snow",
    "Mountain",
}

VALID_FEATURES = {
    "Hill",
    "Forest",
    "Jungle",
    "Marsh",
    "Ice",
}


@dataclass
class TileClassification:
    index: int
    x: int
    y: int
    latitude: float
    longitude: float
    neighbors: Tuple[int, ...]
    base_terrain: str
    features: List[str]


@dataclass(frozen=True)
class EarthAlignment:
    longitude_offset_deg: float = 0.0
    flip_latitude: bool = False
    flip_longitude: bool = False

    def transform(self, lon: float, lat: float) -> Tuple[float, float]:
        aligned_lon = -lon if self.flip_longitude else lon
        aligned_lon = wrap_longitude(aligned_lon + self.longitude_offset_deg)
        aligned_lat = -lat if self.flip_latitude else lat
        return aligned_lon, aligned_lat


def _topology_vectors(topology: TopologyDump) -> np.ndarray:
    lats = np.radians(np.array([tile.latitude for tile in topology.tiles], dtype=np.float64))
    lons = np.radians(np.array([tile.longitude for tile in topology.tiles], dtype=np.float64))
    return np.stack(
        (
            np.cos(lats) * np.cos(lons),
            np.cos(lats) * np.sin(lons),
            np.sin(lats),
        ),
        axis=1,
    )


def _build_sampling_coordinates(
    topology: TopologyDump,
    alignment: EarthAlignment,
    pole_alignment: str,
) -> List[Tuple[float, float]]:
    if pole_alignment not in {"topology", "map-centered"}:
        raise ValueError(f"Unsupported pole_alignment: {pole_alignment}")

    if pole_alignment == "topology":
        return [alignment.transform(tile.longitude, tile.latitude) for tile in topology.tiles]

    vectors = _topology_vectors(topology)
    xs = np.array([tile.x for tile in topology.tiles], dtype=np.float64)
    ys = np.array([tile.y for tile in topology.tiles], dtype=np.float64)
    center_x = float((xs.min() + xs.max()) / 2.0)

    top_y = float(ys.min())
    bottom_y = float(ys.max())

    top_candidates = np.where(ys == top_y)[0]
    bottom_candidates = np.where(ys == bottom_y)[0]
    top_index = int(top_candidates[np.argmin(np.abs(xs[top_candidates] - center_x))])
    bottom_index = int(bottom_candidates[np.argmin(np.abs(xs[bottom_candidates] - center_x))])

    north_axis = vectors[top_index] - vectors[bottom_index]
    axis_norm = float(np.linalg.norm(north_axis))
    if axis_norm < 1e-8:
        north_axis = vectors[top_index]
        axis_norm = float(np.linalg.norm(north_axis))
    north_axis /= axis_norm

    ref = np.array([1.0, 0.0, 0.0], dtype=np.float64)
    if abs(float(np.dot(ref, north_axis))) > 0.95:
        ref = np.array([0.0, 1.0, 0.0], dtype=np.float64)

    meridian = ref - north_axis * float(np.dot(ref, north_axis))
    meridian /= float(np.linalg.norm(meridian))
    east = np.cross(north_axis, meridian)
    east /= float(np.linalg.norm(east))

    sampling_coords: List[Tuple[float, float]] = []
    for vec in vectors:
        lat = math.degrees(math.asin(float(np.clip(np.dot(vec, north_axis), -1.0, 1.0))))
        lon = math.degrees(math.atan2(float(np.dot(vec, east)), float(np.dot(vec, meridian))))
        sampling_coords.append(alignment.transform(lon, lat))

    return sampling_coords


def resolve_generation_frequency(size: str | None, frequency: int | None, topology_frequency: int | None = None) -> int:
    if frequency is not None:
        if frequency <= 0:
            raise ValueError("--frequency must be a positive integer")
        return frequency
    if size is not None:
        return PREDEFINED_SIZE_TO_FREQUENCY[size]
    if topology_frequency is not None:
        return topology_frequency
    raise ValueError("Unable to resolve target frequency. Provide --frequency, --size, or a topology file.")


def resolve_topology_path(topology_arg: str | None, cache_dir: Path, frequency: int) -> Path:
    if topology_arg:
        return Path(topology_arg)
    return cache_dir / f"topology_f{frequency}.json"


def load_generation_topology(topology_path: Path, expected_frequency: int | None) -> TopologyDump:
    if not topology_path.exists():
        raise FileNotFoundError(
            f"Topology dump not found: {topology_path}. "
            "Generate one with: ./gradlew -q :desktop:run "
            "--args=\"--dump-icosa-topology=<path> --frequency=<f>\""
        )
    topology = load_topology_dump(topology_path)
    if expected_frequency is not None and topology.frequency != expected_frequency:
        raise ValueError(
            f"Topology frequency mismatch: expected={expected_frequency}, file={topology.frequency} ({topology_path})"
        )
    return topology


def classify_tiles(
    topology: TopologyDump,
    cache_dir: Path,
    alignment: EarthAlignment,
    datasets: EarthDatasets | None = None,
    sampling_coordinates: Sequence[Tuple[float, float]] | None = None,
) -> List[TileClassification]:
    datasets = datasets or load_earth_datasets(cache_dir)
    classified: List[TileClassification] = []
    coordinates = (
        list(sampling_coordinates)
        if sampling_coordinates is not None
        else [alignment.transform(tile.longitude, tile.latitude) for tile in topology.tiles]
    )

    for tile in topology.tiles:
        sample_lon, sample_lat = coordinates[tile.index]
        on_land = datasets.point_on_land(sample_lon, sample_lat)
        in_lake = datasets.point_in_lake(sample_lon, sample_lat)

        climate = ClimateSample(
            is_land=on_land,
            is_lake=in_lake,
            latitude=sample_lat,
            temperature_c=datasets.sample_temperature(sample_lon, sample_lat),
            annual_precip_mm=datasets.sample_precipitation(sample_lon, sample_lat),
            elevation_m=datasets.sample_elevation(sample_lon, sample_lat),
        )

        base = classify_base_terrain(climate)
        features = classify_features(climate, base)

        classified.append(
            TileClassification(
                index=tile.index,
                x=tile.x,
                y=tile.y,
                latitude=sample_lat,
                longitude=sample_lon,
                neighbors=tile.neighbors,
                base_terrain=base,
                features=features,
            )
        )

    # Post pass: convert ocean tiles adjacent to land into coast.
    land_like = {tile.index for tile in classified if tile.base_terrain in LAND_BASE_TERRAINS}
    for tile in classified:
        if tile.base_terrain != "Ocean":
            continue
        if any(neighbor in land_like for neighbor in tile.neighbors):
            tile.base_terrain = "Coast"

    return classified


def validate_classification(tiles: Sequence[TileClassification]) -> None:
    for tile in tiles:
        if tile.base_terrain not in VALID_BASE_TERRAINS:
            raise ValueError(f"Unknown base terrain on tile {tile.index}: {tile.base_terrain}")
        for feature in tile.features:
            if feature not in VALID_FEATURES:
                raise ValueError(f"Unknown terrain feature on tile {tile.index}: {feature}")
        if tile.base_terrain in WATER_BASE_TERRAINS and "Hill" in tile.features:
            raise ValueError(f"Invalid water+hill combination on tile {tile.index}")
        if tile.base_terrain == "Mountain" and tile.features:
            raise ValueError(f"Mountain tile should not have extra features in v1: tile={tile.index}")


def build_map_payload(
    topology: TopologyDump,
    tiles: Sequence[TileClassification],
    ruleset_name: str,
    map_name: str,
    river_edges: Iterable[CanonicalEdge] = (),
    size_name: str | None = None,
) -> Dict:
    map_parameters = dict(topology.map_parameters_template)
    map_parameters["name"] = map_name
    map_parameters["shape"] = "Icosahedron"
    map_parameters["type"] = "Custom"
    map_parameters["baseRuleset"] = ruleset_name
    map_parameters["goldbergFrequency"] = topology.frequency
    map_parameters["goldbergLayout"] = topology.layout_id
    map_parameters["worldWrap"] = False

    if size_name is not None:
        map_size = map_parameters.get("mapSize")
        if isinstance(map_size, dict):
            map_size["name"] = size_name
            map_parameters["mapSize"] = map_size
        else:
            map_parameters["mapSize"] = {"name": size_name}

    tile_list: List[Dict] = []
    for tile in sorted(tiles, key=lambda t: t.index):
        tile_json = {
            "position": {"x": tile.x, "y": tile.y},
            "baseTerrain": tile.base_terrain,
        }
        if tile.features:
            tile_json["terrainFeatures"] = list(tile.features)
        tile_list.append(tile_json)

    river_writer_map = build_edge_writer_index_from_dump(topology)
    for edge in river_edges:
        writer = river_writer_map.get(edge)
        if writer is None:
            raise ValueError(f"River edge has no writer mapping in topology: {edge}")
        tile_index, field = writer
        tile_list[tile_index][field] = True

    return {
        "mapParameters": map_parameters,
        "tileList": tile_list,
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate an Earth-like Unciv Icosahedron map")
    parser.add_argument("--topology", default=None, help="Path to topology JSON from --dump-icosa-topology")
    parser.add_argument("--cache-dir", default="tools/earthgen/cache", help="Dataset cache directory")
    parser.add_argument("--ruleset", default="Civ V - Gods & Kings", help="Ruleset name written into mapParameters")
    parser.add_argument("--size", choices=tuple(PREDEFINED_SIZE_TO_FREQUENCY.keys()), default=None)
    parser.add_argument("--frequency", type=int, default=None, help="Custom frequency; overrides --size")
    parser.add_argument("--river-count", type=int, default=20, help="Number of longest rivers to project")
    parser.add_argument(
        "--longitude-offset",
        type=float,
        default=0.0,
        help="Degrees to rotate Earth data eastward relative to topology coordinates",
    )
    parser.add_argument(
        "--flip-latitude",
        action=argparse.BooleanOptionalAction,
        default=True,
        help="Flip latitude sign before sampling Earth datasets (default: enabled)",
    )
    parser.add_argument(
        "--flip-longitude",
        action=argparse.BooleanOptionalAction,
        default=False,
        help="Mirror longitude sign before sampling Earth datasets (default: disabled)",
    )
    parser.add_argument(
        "--pole-alignment",
        choices=("topology", "map-centered"),
        default="topology",
        help="How to align Earth north/south poles on the unfolded icosa net",
    )
    parser.add_argument("--name", default="Earth-Icosahedron", help="Map name")
    parser.add_argument("--output", required=True, help="Output map file path")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    cache_dir = Path(args.cache_dir)

    requested_frequency = resolve_generation_frequency(args.size, args.frequency, None)
    topology_path = resolve_topology_path(args.topology, cache_dir, requested_frequency)
    topology = load_generation_topology(topology_path, requested_frequency)

    alignment = EarthAlignment(
        longitude_offset_deg=float(args.longitude_offset),
        flip_latitude=bool(args.flip_latitude),
        flip_longitude=bool(args.flip_longitude),
    )
    sampling_coordinates = _build_sampling_coordinates(
        topology=topology,
        alignment=alignment,
        pole_alignment=str(args.pole_alignment),
    )

    datasets = load_earth_datasets(cache_dir)
    tiles = classify_tiles(
        topology,
        cache_dir=cache_dir,
        alignment=alignment,
        datasets=datasets,
        sampling_coordinates=sampling_coordinates,
    )
    validate_classification(tiles)

    river_count = max(0, int(args.river_count))
    river_projection = project_river_lines_to_edges(
        topology=topology,
        river_lines=datasets.river_lines,
        max_rivers=river_count,
        tile_coordinates=sampling_coordinates,
    )

    payload = build_map_payload(
        topology=topology,
        tiles=tiles,
        ruleset_name=args.ruleset,
        map_name=args.name,
        river_edges=river_projection.edges,
        size_name=args.size,
    )

    output_path = Path(args.output)
    write_map_file(output_path, payload)

    water = sum(1 for tile in tiles if tile.base_terrain in WATER_BASE_TERRAINS)
    land = len(tiles) - water
    mountains = sum(1 for tile in tiles if tile.base_terrain == "Mountain")
    print(
        f"Wrote map to {output_path} | tiles={len(tiles)} land={land} water={water} mountains={mountains} "
        f"frequency={topology.frequency} rivers={len(river_projection.edges)} selectedRivers={len(river_projection.selected_lines)} "
        f"lonOffset={alignment.longitude_offset_deg} flipLat={alignment.flip_latitude} flipLon={alignment.flip_longitude} "
        f"poleAlign={args.pole_alignment}"
    )
    if river_projection.skipped_segments:
        print(f"Warning: skipped {river_projection.skipped_segments} river segments that could not be projected")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
