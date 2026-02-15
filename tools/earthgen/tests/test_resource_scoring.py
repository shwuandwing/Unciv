from __future__ import annotations

import unittest
from types import SimpleNamespace

import numpy as np

from tools.earthgen.resource_dataset_sampling import ResourceDatasetLayers
from tools.earthgen.resource_rules_gnk import ResourceProfile, RulesetResourceDefinition
from tools.earthgen.resource_scoring import is_tile_eligible, rank_candidates_for_resource


def _layers() -> ResourceDatasetLayers:
    return ResourceDatasetLayers(
        tile_longitude=np.array([10.0, 20.0]),
        tile_latitude=np.array([40.0, 20.0]),
        temperature_c=np.array([11.0, 26.0]),
        precipitation_mm=np.array([400.0, 1900.0]),
        elevation_m=np.array([1200.0, 120.0]),
        slope=np.array([220.0, 30.0]),
        is_land=np.array([True, True]),
        is_water=np.array([False, False]),
        is_coast=np.array([False, False]),
        is_lake=np.array([False, False]),
        has_hill=np.array([True, False]),
        has_forest=np.array([False, True]),
        has_jungle=np.array([False, True]),
        has_marsh=np.array([False, False]),
        has_ice=np.array([False, False]),
        on_river=np.array([False, True]),
        fresh_water=np.array([False, True]),
        land_distance_to_coast=np.array([5.0, 2.0]),
        water_distance_to_land=np.array([999.0, 999.0]),
        river_distance=np.array([2.0, 0.0]),
    )


class ResourceScoringTests(unittest.TestCase):
    def test_hard_filter_only_keeps_eligible_tiles(self) -> None:
        profile = ResourceProfile(
            name="Iron",
            resource_type="Strategic",
            enabled=True,
            target_density_per_1000=1.0,
            min_count=0,
            min_distance=1,
            major_ratio=0.5,
            allowed_terrains=("Hill",),
            required_features=("Hill",),
            forbidden_features=("Jungle",),
            latitude_min=None,
            latitude_max=None,
            dataset_weights={"hill": 1.0},
            region_boosts=(),
            notes="",
        )
        ruleset = RulesetResourceDefinition(
            name="Iron",
            resource_type="Strategic",
            terrains_can_be_found_on=("Hill", "Grassland"),
            major_deposit_amount=None,
            minor_deposit_amount=None,
        )
        tiles = [
            SimpleNamespace(index=0, latitude=40.0, longitude=10.0, base_terrain="Plains", features=["Hill"]),
            SimpleNamespace(index=1, latitude=20.0, longitude=20.0, base_terrain="Grassland", features=["Jungle"]),
        ]
        layers = _layers()
        self.assertTrue(is_tile_eligible(profile, ruleset, tiles[0], layers, 0))
        self.assertFalse(is_tile_eligible(profile, ruleset, tiles[1], layers, 1))

    def test_score_prefers_higher_weighted_tile(self) -> None:
        profile = ResourceProfile(
            name="Bananas",
            resource_type="Bonus",
            enabled=True,
            target_density_per_1000=1.0,
            min_count=0,
            min_distance=1,
            major_ratio=0.0,
            allowed_terrains=("Jungle",),
            required_features=(),
            forbidden_features=(),
            latitude_min=None,
            latitude_max=None,
            dataset_weights={"jungle": 1.0, "wetness": 0.6, "warmth": 0.4},
            region_boosts=(),
            notes="",
        )
        ruleset = RulesetResourceDefinition(
            name="Bananas",
            resource_type="Bonus",
            terrains_can_be_found_on=("Jungle",),
            major_deposit_amount=None,
            minor_deposit_amount=None,
        )
        tiles = [
            SimpleNamespace(index=0, latitude=40.0, longitude=10.0, base_terrain="Plains", features=["Hill"]),
            SimpleNamespace(index=1, latitude=20.0, longitude=20.0, base_terrain="Grassland", features=["Jungle"]),
        ]
        ranked = rank_candidates_for_resource(profile, ruleset, tiles, _layers())
        self.assertEqual([entry.tile_index for entry in ranked], [1])
        self.assertGreater(ranked[0].score, 1.0)


if __name__ == "__main__":
    unittest.main()
