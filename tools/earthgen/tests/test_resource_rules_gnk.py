from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from tools.earthgen.resource_rules_gnk import (
    DEFAULT_RESOURCE_PROFILE_PATH,
    RULESET_TILE_RESOURCES_PATH,
    load_resource_profiles,
    load_ruleset_resource_definitions,
)


class ResourceRulesTests(unittest.TestCase):
    def test_profile_covers_all_ruleset_resources_exactly_once(self) -> None:
        ruleset = load_ruleset_resource_definitions(RULESET_TILE_RESOURCES_PATH)
        profiles = load_resource_profiles(DEFAULT_RESOURCE_PROFILE_PATH, RULESET_TILE_RESOURCES_PATH)
        self.assertEqual(set(ruleset.keys()), set(profiles.keys()))

    def test_profile_terrain_tokens_are_ruleset_compatible(self) -> None:
        ruleset = load_ruleset_resource_definitions(RULESET_TILE_RESOURCES_PATH)
        profiles = load_resource_profiles(DEFAULT_RESOURCE_PROFILE_PATH, RULESET_TILE_RESOURCES_PATH)

        for name, profile in profiles.items():
            ruleset_tokens = set(ruleset[name].terrains_can_be_found_on)
            if not ruleset_tokens:
                # City-state-only resources do not have natural terrain constraints in ruleset.
                continue
            if not profile.allowed_terrains:
                continue
            for token in profile.allowed_terrains:
                # Flood plains is represented via desert + freshwater in Earth generator.
                if token == "Flood plains":
                    self.assertIn("Flood plains", ruleset_tokens)
                    continue
                self.assertIn(token, ruleset_tokens)

    def test_profile_parser_rejects_unknown_resource(self) -> None:
        with tempfile.TemporaryDirectory(prefix="earthgen_profile_test_") as temp_dir:
            profile_path = Path(temp_dir) / "profile.json"
            profile_path.write_text(
                json.dumps(
                    {
                        "version": 1,
                        "resources": [
                            {
                                "name": "NotAResource",
                                "resource_type": "Luxury",
                                "target_density_per_1000": 1.0,
                                "min_count": 0,
                                "min_distance": 2,
                                "major_ratio": 0.0,
                                "dataset_weights": {},
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )
            with self.assertRaisesRegex(ValueError, "Unknown resource"):
                load_resource_profiles(profile_path, RULESET_TILE_RESOURCES_PATH)


if __name__ == "__main__":
    unittest.main()
