from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from tools.earthgen.topology_io import build_edge_writer_index, load_topology_dump, validate_topology_dump


class TopologySchemaTests(unittest.TestCase):
    def make_sample(self) -> dict:
        return {
            "frequency": 1,
            "layoutId": "IcosaNetV2",
            "tileCount": 3,
            "ruleset": "Civ V - Gods & Kings",
            "tiles": [
                {"index": 0, "x": 0, "y": 0, "latitude": 0.0, "longitude": 0.0, "neighbors": [1, 2]},
                {"index": 1, "x": 1, "y": 0, "latitude": 0.0, "longitude": 1.0, "neighbors": [0, 2]},
                {"index": 2, "x": 0, "y": 1, "latitude": 1.0, "longitude": 0.0, "neighbors": [0, 1]},
            ],
            "edges": [
                {
                    "a": 0,
                    "b": 1,
                    "representable": True,
                    "clockFromA": 4,
                    "writer": {"tileIndex": 0, "field": "hasBottomRightRiver"},
                },
                {
                    "a": 0,
                    "b": 2,
                    "representable": True,
                    "clockFromA": 8,
                    "writer": {"tileIndex": 0, "field": "hasBottomLeftRiver"},
                },
                {
                    "a": 1,
                    "b": 2,
                    "representable": False,
                    "clockFromA": -1,
                    "writer": None,
                },
            ],
            "mapParametersTemplate": {"shape": "Icosahedron", "goldbergFrequency": 1},
        }

    def test_schema_validator_accepts_valid_payload(self) -> None:
        payload = self.make_sample()
        validate_topology_dump(payload)

    def test_schema_validator_rejects_duplicate_edges(self) -> None:
        payload = self.make_sample()
        payload["edges"].append(
            {
                "a": 1,
                "b": 0,
                "representable": True,
                "clockFromA": 10,
                "writer": {"tileIndex": 1, "field": "hasBottomRightRiver"},
            }
        )
        with self.assertRaisesRegex(ValueError, "Duplicate undirected edge"):
            validate_topology_dump(payload)

    def test_can_build_writer_index_for_representable_edges(self) -> None:
        payload = self.make_sample()
        mapping = build_edge_writer_index(payload)
        self.assertEqual(mapping[(0, 1)], (0, "hasBottomRightRiver"))
        self.assertEqual(mapping[(0, 2)], (0, "hasBottomLeftRiver"))
        self.assertNotIn((1, 2), mapping)

    def test_load_from_file_roundtrip(self) -> None:
        payload = self.make_sample()
        with tempfile.TemporaryDirectory(prefix="topology_schema_test_") as td:
            path = Path(td) / "topology.json"
            path.write_text(json.dumps(payload), encoding="utf-8")
            dump = load_topology_dump(path)
            self.assertEqual(dump.frequency, 1)
            self.assertEqual(len(dump.tiles), 3)
            self.assertEqual(len(dump.edges), 3)

    def test_accepts_optional_default_omitted_fields(self) -> None:
        payload = self.make_sample()
        payload.pop("layoutId")
        payload.pop("ruleset")
        payload["edges"][2] = {"a": 1, "b": 2}

        validate_topology_dump(payload)
        mapping = build_edge_writer_index(payload)
        self.assertNotIn((1, 2), mapping)

        with tempfile.TemporaryDirectory(prefix="topology_schema_optional_") as td:
            path = Path(td) / "topology.json"
            path.write_text(json.dumps(payload), encoding="utf-8")
            dump = load_topology_dump(path)
            self.assertEqual(dump.layout_id, "IcosaNetV2")
            self.assertEqual(dump.ruleset, "Civ V - Gods & Kings")


if __name__ == "__main__":
    unittest.main()
