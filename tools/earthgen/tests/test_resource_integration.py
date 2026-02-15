from __future__ import annotations

import unittest

from tools.earthgen.generate_unciv_earth_map import TileClassification, build_map_payload, parse_resource_density
from tools.earthgen.topology_io import TopologyDump, TopologyTile


class ResourceIntegrationTests(unittest.TestCase):
    def test_parse_resource_density_modes_and_multiplier(self) -> None:
        self.assertEqual(parse_resource_density("default"), ("default", 1.0))
        self.assertEqual(parse_resource_density("SPARSE"), ("sparse", 1.0))
        mode, multiplier = parse_resource_density("1.25")
        self.assertEqual(mode, "default")
        self.assertAlmostEqual(multiplier, 1.25)
        with self.assertRaises(ValueError):
            parse_resource_density("0")

    def test_build_payload_writes_resource_fields(self) -> None:
        topology = TopologyDump(
            frequency=1,
            layout_id="IcosaNetV2",
            tile_count=1,
            ruleset="Civ V - Gods & Kings",
            tiles=(TopologyTile(index=0, x=0, y=0, latitude=0.0, longitude=0.0, neighbors=()),),
            edges=tuple(),
            map_parameters_template={},
        )
        tiles = [
            TileClassification(
                index=0,
                x=0,
                y=0,
                latitude=0.0,
                longitude=0.0,
                neighbors=tuple(),
                base_terrain="Grassland",
                features=[],
                temperature_c=25.0,
                annual_precip_mm=1200.0,
                elevation_m=120.0,
            )
        ]
        payload = build_map_payload(
            topology=topology,
            tiles=tiles,
            ruleset_name="Civ V - Gods & Kings",
            map_name="ResourcePayloadTest",
            river_edges=tuple(),
            resources={0: ("Iron", 6)},
        )
        tile = payload["tileList"][0]
        self.assertEqual(tile["resource"], "Iron")
        self.assertEqual(tile["resourceAmount"], 6)


if __name__ == "__main__":
    unittest.main()
