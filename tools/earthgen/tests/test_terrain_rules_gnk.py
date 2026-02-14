from __future__ import annotations

import unittest
from pathlib import Path

from tools.earthgen.generate_unciv_earth_map import (
    EarthAlignment,
    TileClassification,
    build_map_payload,
    classify_tiles,
    validate_classification,
)
from tools.earthgen.terrain_rules_gnk import ClimateSample, classify_base_terrain, classify_features
from tools.earthgen.topology_io import RiverWriter, TopologyDump, TopologyEdge, TopologyTile
from tools.earthgen.unciv_map_io import decode_map_payload, encode_map_payload


class TerrainRuleTests(unittest.TestCase):
    def test_climate_snapshot_samples(self) -> None:
        cases = [
            (ClimateSample(True, False, 0.0, 27.0, 2300.0, 50.0), "Grassland", ["Jungle"]),
            (ClimateSample(True, False, 25.0, 24.0, 180.0, 80.0), "Desert", []),
            (ClimateSample(True, False, 52.0, 8.0, 600.0, 250.0), "Plains", []),
            (ClimateSample(True, False, 68.0, -4.0, 500.0, 100.0), "Tundra", []),
            (ClimateSample(True, False, 79.0, -12.0, 300.0, 100.0), "Snow", []),
            (ClimateSample(True, False, 34.0, 10.0, 1100.0, 3500.0), "Mountain", []),
            (ClimateSample(False, True, 10.0, 20.0, 1200.0, 0.0), "Lakes", []),
            (ClimateSample(False, False, 75.0, -8.0, 200.0, 0.0), "Ocean", ["Ice"]),
        ]
        for sample, expected_base, expected_features in cases:
            with self.subTest(sample=sample):
                base = classify_base_terrain(sample)
                features = classify_features(sample, base)
                self.assertEqual(expected_base, base)
                self.assertEqual(expected_features, features)

    def test_validate_classification_rejects_invalid_combinations(self) -> None:
        with self.assertRaisesRegex(ValueError, r"water\+hill"):
            validate_classification(
                [
                    TileClassification(
                        index=0,
                        x=0,
                        y=0,
                        latitude=0.0,
                        longitude=0.0,
                        neighbors=tuple(),
                        base_terrain="Ocean",
                        features=["Hill"],
                    )
                ]
            )

    def test_smoke_generation_payload_roundtrip(self) -> None:
        class FakeDatasets:
            def point_on_land(self, lon: float, lat: float) -> bool:
                return lon >= 0

            def point_in_lake(self, lon: float, lat: float) -> bool:
                return lon > 0.5 and lat > 0.5

            def sample_elevation(self, lon: float, lat: float) -> float:
                return 3500.0 if lon > 1.5 else 300.0

            def sample_temperature(self, lon: float, lat: float) -> float:
                return 26.0 - abs(lat) * 0.3

            def sample_precipitation(self, lon: float, lat: float) -> float:
                return 1200.0 if lon > 0 else 400.0

        topology = TopologyDump(
            frequency=1,
            layout_id="IcosaNetV2",
            tile_count=4,
            ruleset="Civ V - Gods & Kings",
            tiles=(
                TopologyTile(0, 0, 0, 0.0, -20.0, (1, 2)),
                TopologyTile(1, 1, 0, 0.0, 20.0, (0, 3)),
                TopologyTile(2, 0, 1, 20.0, 0.8, (0, 3)),
                TopologyTile(3, 1, 1, 35.0, 2.0, (1, 2)),
            ),
            edges=(
                TopologyEdge(0, 1, True, RiverWriter(0, "hasBottomRightRiver")),
                TopologyEdge(0, 2, True, RiverWriter(0, "hasBottomLeftRiver")),
                TopologyEdge(1, 3, True, RiverWriter(1, "hasBottomRightRiver")),
                TopologyEdge(2, 3, True, RiverWriter(2, "hasBottomRightRiver")),
            ),
            map_parameters_template={"shape": "Icosahedron", "goldbergFrequency": 1},
        )
        tiles = classify_tiles(
            topology,
            cache_dir=Path("."),
            alignment=EarthAlignment(),
            datasets=FakeDatasets(),
        )  # type: ignore[arg-type]

        validate_classification(tiles)
        payload = build_map_payload(topology, tiles, "Civ V - Gods & Kings", "SmokeTest")
        encoded = encode_map_payload(payload)
        decoded = decode_map_payload(encoded)

        self.assertIn("mapParameters", decoded)
        self.assertIn("tileList", decoded)
        self.assertEqual(4, len(decoded["tileList"]))

        for tile in decoded["tileList"]:
            self.assertIn(tile["baseTerrain"], {"Ocean", "Coast", "Lakes", "Grassland", "Plains", "Desert", "Tundra", "Snow", "Mountain"})
            for feature in tile.get("terrainFeatures", []):
                self.assertIn(feature, {"Hill", "Forest", "Jungle", "Marsh", "Ice"})


if __name__ == "__main__":
    unittest.main()
