from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Any, Tuple


ALLOWED_RIVER_FIELDS = {"hasBottomRiver", "hasBottomLeftRiver", "hasBottomRightRiver"}
DEFAULT_LAYOUT_ID = "IcosaNetV2"
DEFAULT_RULESET = "Civ V - Gods & Kings"


@dataclass(frozen=True)
class RiverWriter:
    tile_index: int
    field: str


@dataclass(frozen=True)
class TopologyEdge:
    a: int
    b: int
    representable: bool
    writer: RiverWriter | None


@dataclass(frozen=True)
class TopologyTile:
    index: int
    x: int
    y: int
    latitude: float
    longitude: float
    neighbors: Tuple[int, ...]


@dataclass(frozen=True)
class TopologyDump:
    frequency: int
    layout_id: str
    tile_count: int
    ruleset: str
    tiles: Tuple[TopologyTile, ...]
    edges: Tuple[TopologyEdge, ...]
    map_parameters_template: Dict[str, Any]


def load_topology_dump(path: Path) -> TopologyDump:
    data = json.loads(path.read_text(encoding="utf-8"))
    validate_topology_dump(data)

    tiles = tuple(
        TopologyTile(
            index=int(tile["index"]),
            x=int(tile.get("x", 0)),
            y=int(tile.get("y", 0)),
            latitude=float(tile.get("latitude", 0.0)),
            longitude=float(tile.get("longitude", 0.0)),
            neighbors=tuple(int(n) for n in tile["neighbors"]),
        )
        for tile in data["tiles"]
    )

    edges = []
    for edge in data["edges"]:
        representable = bool(edge.get("representable", False))
        writer = edge.get("writer")
        if writer is not None:
            writer_obj = RiverWriter(tile_index=int(writer["tileIndex"]), field=str(writer["field"]))
        else:
            writer_obj = None
        edges.append(
            TopologyEdge(
                a=int(edge["a"]),
                b=int(edge["b"]),
                representable=representable,
                writer=writer_obj,
            )
        )

    return TopologyDump(
        frequency=int(data["frequency"]),
        layout_id=str(data.get("layoutId", DEFAULT_LAYOUT_ID)),
        tile_count=int(data["tileCount"]),
        ruleset=str(data.get("ruleset", DEFAULT_RULESET)),
        tiles=tiles,
        edges=tuple(edges),
        map_parameters_template=dict(data.get("mapParametersTemplate", {})),
    )


def validate_topology_dump(data: Dict[str, Any]) -> None:
    required_top_keys = {
        "frequency",
        "tileCount",
        "tiles",
        "edges",
    }
    missing = sorted(required_top_keys - set(data.keys()))
    if missing:
        raise ValueError(f"Topology dump missing key(s): {', '.join(missing)}")

    tiles = data["tiles"]
    edges = data["edges"]
    if not isinstance(tiles, list) or not isinstance(edges, list):
        raise ValueError("Topology dump must contain list fields 'tiles' and 'edges'")

    tile_count = int(data["tileCount"])
    if tile_count != len(tiles):
        raise ValueError(f"tileCount mismatch: declared={tile_count}, actual={len(tiles)}")

    tile_indices = set()
    for tile in tiles:
        for key in ["index", "neighbors"]:
            if key not in tile:
                raise ValueError(f"Tile entry missing key: {key}")
        idx = int(tile["index"])
        if idx in tile_indices:
            raise ValueError(f"Duplicate tile index: {idx}")
        tile_indices.add(idx)

    if tile_indices != set(range(tile_count)):
        raise ValueError("Tile indices must be contiguous from 0 to tileCount-1")

    edge_pairs = set()
    for edge in edges:
        for key in ["a", "b"]:
            if key not in edge:
                raise ValueError(f"Edge entry missing key: {key}")

        a = int(edge["a"])
        b = int(edge["b"])
        if a == b:
            raise ValueError(f"Invalid self-edge: {a}")
        if a not in tile_indices or b not in tile_indices:
            raise ValueError(f"Edge references unknown tile(s): ({a}, {b})")

        pair = (a, b) if a < b else (b, a)
        if pair in edge_pairs:
            raise ValueError(f"Duplicate undirected edge entry: {pair}")
        edge_pairs.add(pair)

        representable = bool(edge.get("representable", False))
        writer = edge.get("writer")
        if representable:
            if writer is None:
                raise ValueError(f"Representable edge missing writer: {pair}")
            if int(writer.get("tileIndex", -1)) not in pair:
                raise ValueError(f"Writer tileIndex must match edge endpoint: {pair}")
            field = writer.get("field")
            if field not in ALLOWED_RIVER_FIELDS:
                raise ValueError(f"Invalid river field '{field}' for edge {pair}")


def build_edge_writer_index(data: Dict[str, Any]) -> Dict[Tuple[int, int], Tuple[int, str]]:
    """Return undirected edge -> (tileIndex, field) mapping for representable edges."""
    validate_topology_dump(data)
    mapping: Dict[Tuple[int, int], Tuple[int, str]] = {}
    for edge in data["edges"]:
        if not bool(edge.get("representable", False)):
            continue
        a = int(edge["a"])
        b = int(edge["b"])
        pair = (a, b) if a < b else (b, a)
        writer = edge["writer"]
        mapping[pair] = (int(writer["tileIndex"]), str(writer["field"]))
    return mapping


def build_edge_writer_index_from_dump(dump: TopologyDump) -> Dict[Tuple[int, int], Tuple[int, str]]:
    """Return undirected edge -> (tileIndex, field) mapping for representable edges."""
    mapping: Dict[Tuple[int, int], Tuple[int, str]] = {}
    for edge in dump.edges:
        if not edge.representable or edge.writer is None:
            continue
        pair = (edge.a, edge.b) if edge.a < edge.b else (edge.b, edge.a)
        mapping[pair] = (edge.writer.tile_index, edge.writer.field)
    return mapping
