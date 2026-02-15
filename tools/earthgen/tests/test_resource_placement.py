from __future__ import annotations

import unittest
from types import SimpleNamespace

from tools.earthgen.resource_placement import place_resources
from tools.earthgen.resource_rules_gnk import (
    ResourceProfile,
    RulesetResourceDefinition,
    StrategicDepositAmount,
)
from tools.earthgen.resource_scoring import RankedCandidate
from tools.earthgen.topology_io import TopologyDump, TopologyEdge, TopologyTile


def _topology() -> TopologyDump:
    tiles = (
        TopologyTile(index=0, x=0, y=0, latitude=10.0, longitude=10.0, neighbors=(1, 2, 3, 4, 5, 6)),
        TopologyTile(index=1, x=1, y=0, latitude=12.0, longitude=20.0, neighbors=(0, 2, 6)),
        TopologyTile(index=2, x=1, y=1, latitude=8.0, longitude=25.0, neighbors=(0, 1, 3)),
        TopologyTile(index=3, x=0, y=1, latitude=6.0, longitude=30.0, neighbors=(0, 2, 4)),
        TopologyTile(index=4, x=-1, y=1, latitude=4.0, longitude=35.0, neighbors=(0, 3, 5)),
        TopologyTile(index=5, x=-1, y=0, latitude=2.0, longitude=40.0, neighbors=(0, 4, 6)),
        TopologyTile(index=6, x=0, y=-1, latitude=14.0, longitude=15.0, neighbors=(0, 5, 1)),
    )
    return TopologyDump(
        frequency=1,
        layout_id="IcosaNetV2",
        tile_count=len(tiles),
        ruleset="Civ V - Gods & Kings",
        tiles=tiles,
        edges=tuple(TopologyEdge(a=0, b=i, representable=False, writer=None) for i in range(1, 7)),
        map_parameters_template={},
    )


class ResourcePlacementTests(unittest.TestCase):
    def test_no_overlap_and_strategic_amounts_positive(self) -> None:
        topology = _topology()
        tiles = [
            SimpleNamespace(index=i, base_terrain="Grassland", features=["Hill"] if i % 2 == 0 else [])
            for i in range(7)
        ]
        rules = {
            "Iron": RulesetResourceDefinition(
                name="Iron",
                resource_type="Strategic",
                terrains_can_be_found_on=("Grassland", "Hill"),
                major_deposit_amount=StrategicDepositAmount(sparse=4, default=6, abundant=9),
                minor_deposit_amount=StrategicDepositAmount(sparse=1, default=2, abundant=3),
            ),
            "Wheat": RulesetResourceDefinition(
                name="Wheat",
                resource_type="Bonus",
                terrains_can_be_found_on=("Grassland", "Plains"),
                major_deposit_amount=None,
                minor_deposit_amount=None,
            ),
        }
        profiles = {
            "Iron": ResourceProfile(
                name="Iron",
                resource_type="Strategic",
                enabled=True,
                target_density_per_1000=300.0,
                min_count=1,
                min_distance=2,
                major_ratio=0.5,
                allowed_terrains=("Grassland", "Hill"),
                required_features=(),
                forbidden_features=(),
                latitude_min=None,
                latitude_max=None,
                dataset_weights={"hill": 1.0},
                region_boosts=(),
                notes="",
            ),
            "Wheat": ResourceProfile(
                name="Wheat",
                resource_type="Bonus",
                enabled=True,
                target_density_per_1000=300.0,
                min_count=1,
                min_distance=1,
                major_ratio=0.0,
                allowed_terrains=("Grassland",),
                required_features=(),
                forbidden_features=(),
                latitude_min=None,
                latitude_max=None,
                dataset_weights={"grassland": 1.0},
                region_boosts=(),
                notes="",
            ),
        }
        ranked = {
            "Iron": [RankedCandidate(tile_index=i, score=10.0 - i) for i in range(7)],
            "Wheat": [RankedCandidate(tile_index=i, score=8.0 - i / 10.0) for i in range(7)],
        }

        result = place_resources(
            topology=topology,
            tiles=tiles,
            ruleset_definitions=rules,
            profiles=profiles,
            ranked_candidates=ranked,
            density_mode="default",
            density_multiplier=1.0,
            seed=42,
            fairness_mode=True,
        )

        self.assertEqual(len(result.placements_by_tile), len(set(result.placements_by_tile.keys())))
        for tile_index, placed in result.placements_by_tile.items():
            if placed.resource == "Iron":
                self.assertGreater(placed.amount, 0, msg=f"tile={tile_index}")

    def test_seed_changes_tie_breaking_tile_selection(self) -> None:
        topology = _topology()
        tiles = [SimpleNamespace(index=i, base_terrain="Grassland", features=[]) for i in range(7)]
        rules = {
            "Wheat": RulesetResourceDefinition(
                name="Wheat",
                resource_type="Bonus",
                terrains_can_be_found_on=("Grassland",),
                major_deposit_amount=None,
                minor_deposit_amount=None,
            )
        }
        profiles = {
            "Wheat": ResourceProfile(
                name="Wheat",
                resource_type="Bonus",
                enabled=True,
                target_density_per_1000=0.0,
                min_count=2,
                min_distance=1,
                major_ratio=0.0,
                allowed_terrains=("Grassland",),
                required_features=(),
                forbidden_features=(),
                latitude_min=None,
                latitude_max=None,
                dataset_weights={"grassland": 1.0},
                region_boosts=(),
                notes="",
            )
        }
        ranked = {"Wheat": [RankedCandidate(tile_index=i, score=10.0) for i in range(1, 7)]}

        placements_by_seed = set()
        for seed in range(10):
            result = place_resources(
                topology=topology,
                tiles=tiles,
                ruleset_definitions=rules,
                profiles=profiles,
                ranked_candidates=ranked,
                density_mode="default",
                density_multiplier=1.0,
                seed=seed,
                fairness_mode=False,
            )
            chosen_tiles = tuple(sorted(result.placements_by_tile.keys()))
            self.assertEqual(len(chosen_tiles), 2)
            placements_by_seed.add(chosen_tiles)

        self.assertGreater(len(placements_by_seed), 1)


if __name__ == "__main__":
    unittest.main()
