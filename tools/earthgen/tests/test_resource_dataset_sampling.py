from __future__ import annotations

import unittest
from types import SimpleNamespace

from tools.earthgen.fetch_datasets import DEFAULT_DATASETS
from tools.earthgen.resource_dataset_sampling import build_resource_dataset_layers, metric_value
from tools.earthgen.topology_io import TopologyDump, TopologyEdge, TopologyTile


class ResourceDatasetSamplingTests(unittest.TestCase):
    def test_required_resource_dataset_ids_exist_in_catalog(self) -> None:
        required = {
            "geography_regions_polys",
            "geography_regions_points",
            "geography_regions_elevation_points",
        }
        self.assertTrue(required.issubset(DEFAULT_DATASETS.keys()))

    def test_layer_builder_marks_river_and_freshwater(self) -> None:
        tiles = (
            TopologyTile(index=0, x=0, y=0, latitude=10, longitude=10, neighbors=(1, 2)),
            TopologyTile(index=1, x=1, y=0, latitude=10, longitude=20, neighbors=(0, 2)),
            TopologyTile(index=2, x=0, y=1, latitude=5, longitude=15, neighbors=(0, 1)),
        )
        topology = TopologyDump(
            frequency=1,
            layout_id="IcosaNetV2",
            tile_count=3,
            ruleset="Civ V - Gods & Kings",
            tiles=tiles,
            edges=(TopologyEdge(a=0, b=1, representable=True, writer=None), TopologyEdge(a=1, b=2, representable=False, writer=None)),
            map_parameters_template={},
        )
        classified = [
            SimpleNamespace(index=0, latitude=10.0, longitude=10.0, base_terrain="Grassland", features=[], temperature_c=20.0, annual_precip_mm=700.0, elevation_m=200.0),
            SimpleNamespace(index=1, latitude=10.0, longitude=20.0, base_terrain="Grassland", features=[], temperature_c=22.0, annual_precip_mm=800.0, elevation_m=250.0),
            SimpleNamespace(index=2, latitude=5.0, longitude=15.0, base_terrain="Coast", features=[], temperature_c=24.0, annual_precip_mm=900.0, elevation_m=0.0),
        ]
        layers = build_resource_dataset_layers(topology, classified, river_edges=[(0, 1)])
        self.assertEqual(layers.on_river.tolist(), [True, True, False])
        self.assertEqual(layers.fresh_water.tolist(), [True, True, False])
        self.assertEqual(layers.is_land.tolist(), [True, True, False])
        self.assertGreater(metric_value("river_proximity", 0, layers), 0.8)
        self.assertGreater(metric_value("coast_proximity", 1, layers), 0.8)


if __name__ == "__main__":
    unittest.main()
