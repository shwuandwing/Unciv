from __future__ import annotations

import unittest

from tools.earthgen.generate_unciv_earth_map import (
    EarthAlignment,
    PREDEFINED_SIZE_TO_FREQUENCY,
    TileClassification,
    build_map_payload,
    classify_tiles,
    resolve_generation_frequency,
)
from tools.earthgen.topology_io import TopologyDump, TopologyTile
from tools.earthgen.unciv_map_io import decode_map_payload, encode_map_payload


class FakeDatasets:
    def point_on_land(self, lon: float, lat: float) -> bool:
        return True

    def point_in_lake(self, lon: float, lat: float) -> bool:
        return False

    def sample_elevation(self, lon: float, lat: float) -> float:
        return 120.0

    def sample_temperature(self, lon: float, lat: float) -> float:
        return 18.0

    def sample_precipitation(self, lon: float, lat: float) -> float:
        return 1100.0


def tile_count_for_frequency(freq: int) -> int:
    return 10 * freq * freq + 2


def synthetic_topology(freq: int) -> TopologyDump:
    count = tile_count_for_frequency(freq)
    tiles = []
    for idx in range(count):
        # Spread latitude for deterministic climate behavior.
        lat = -80.0 + (160.0 * idx / max(1, count - 1))
        lon = -180.0 + (360.0 * idx / max(1, count - 1))
        tiles.append(TopologyTile(idx, idx, 0, lat, lon, tuple()))

    return TopologyDump(
        frequency=freq,
        layout_id="IcosaNetV2",
        tile_count=count,
        ruleset="Civ V - Gods & Kings",
        tiles=tuple(tiles),
        edges=tuple(),
        map_parameters_template={"shape": "Icosahedron", "goldbergFrequency": freq},
    )


class SizeFrequencyMappingTests(unittest.TestCase):
    def test_predefined_mapping_matches_expected(self) -> None:
        expected = {
            "Tiny": 5,
            "Small": 8,
            "Medium": 11,
            "Large": 16,
            "Huge": 22,
        }
        self.assertEqual(expected, PREDEFINED_SIZE_TO_FREQUENCY)

    def test_frequency_override_wins_over_size(self) -> None:
        self.assertEqual(14, resolve_generation_frequency(size="Tiny", frequency=14, topology_frequency=None))
        self.assertEqual(11, resolve_generation_frequency(size="Medium", frequency=None, topology_frequency=None))
        self.assertEqual(9, resolve_generation_frequency(size=None, frequency=None, topology_frequency=9))

    def test_end_to_end_generation_for_predefined_sizes(self) -> None:
        datasets = FakeDatasets()
        for size, freq in PREDEFINED_SIZE_TO_FREQUENCY.items():
            with self.subTest(size=size):
                topology = synthetic_topology(freq)
                tiles = classify_tiles(
                    topology,
                    cache_dir=None,
                    alignment=EarthAlignment(),
                    datasets=datasets,
                )  # type: ignore[arg-type]
                payload = build_map_payload(
                    topology=topology,
                    tiles=tiles,
                    ruleset_name="Civ V - Gods & Kings",
                    map_name=f"Earth-{size}",
                    size_name=size,
                )
                decoded = decode_map_payload(encode_map_payload(payload))
                self.assertEqual(tile_count_for_frequency(freq), len(decoded["tileList"]))
                self.assertEqual(freq, decoded["mapParameters"]["goldbergFrequency"])
                self.assertEqual(size, decoded["mapParameters"]["mapSize"]["name"])

    def test_custom_frequency_generation(self) -> None:
        freq = 14
        topology = synthetic_topology(freq)
        tiles = classify_tiles(
            topology,
            cache_dir=None,
            alignment=EarthAlignment(),
            datasets=FakeDatasets(),
        )  # type: ignore[arg-type]
        payload = build_map_payload(
            topology=topology,
            tiles=tiles,
            ruleset_name="Civ V - Gods & Kings",
            map_name="Earth-Custom",
            size_name=None,
        )
        decoded = decode_map_payload(encode_map_payload(payload))
        self.assertEqual(tile_count_for_frequency(freq), len(decoded["tileList"]))
        self.assertEqual(freq, decoded["mapParameters"]["goldbergFrequency"])


if __name__ == "__main__":
    unittest.main()
