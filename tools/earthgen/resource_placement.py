from __future__ import annotations

from collections import deque
from dataclasses import dataclass
import random
from typing import Dict, Iterable, Mapping, Sequence

from tools.earthgen.resource_rules_gnk import ResourceProfile, RulesetResourceDefinition
from tools.earthgen.resource_scoring import RankedCandidate
from tools.earthgen.topology_io import TopologyDump


RESOURCE_PLACEMENT_ORDER = ("Strategic", "Luxury", "Bonus")
RESOURCE_DENSITY_MULTIPLIER = {
    "sparse": 0.72,
    "default": 1.0,
    "abundant": 1.35,
}


@dataclass(frozen=True)
class PlacedResource:
    resource: str
    amount: int


@dataclass(frozen=True)
class ResourcePlacementResult:
    placements_by_tile: Dict[int, PlacedResource]
    counts_by_resource: Dict[str, int]


def _is_within_distance(topology: TopologyDump, start: int, targets: set[int], max_distance: int) -> bool:
    if not targets or max_distance <= 0:
        return start in targets
    if start in targets:
        return True
    visited = {start}
    q: deque[tuple[int, int]] = deque([(start, 0)])
    while q:
        node, distance = q.popleft()
        if distance >= max_distance:
            continue
        for neighbor in topology.tiles[node].neighbors:
            if neighbor in visited:
                continue
            if neighbor in targets:
                return True
            visited.add(neighbor)
            q.append((neighbor, distance + 1))
    return False


def _target_count(
    profile: ResourceProfile,
    tile_count: int,
    density_mode: str,
    density_multiplier: float,
) -> int:
    mode_multiplier = RESOURCE_DENSITY_MULTIPLIER[density_mode]
    scaled = profile.target_density_per_1000 * (tile_count / 1000.0) * mode_multiplier * density_multiplier
    return max(profile.min_count, int(round(scaled)))


def _strategic_amount(
    resource_name: str,
    ruleset_def: RulesetResourceDefinition,
    profile: ResourceProfile,
    density_mode: str,
    tile_base_terrain: str,
    rng: random.Random,
) -> int:
    if resource_name == "Oil" and tile_base_terrain == "Coast":
        return 4
    if ruleset_def.major_deposit_amount is None or ruleset_def.minor_deposit_amount is None:
        return 1
    major = rng.random() < profile.major_ratio
    source = ruleset_def.major_deposit_amount if major else ruleset_def.minor_deposit_amount
    if density_mode == "sparse":
        return source.sparse
    if density_mode == "abundant":
        return source.abundant
    return source.default


def _resources_by_type(
    ruleset_definitions: Mapping[str, RulesetResourceDefinition],
    profiles: Mapping[str, ResourceProfile],
) -> Dict[str, list[str]]:
    grouped = {resource_type: [] for resource_type in RESOURCE_PLACEMENT_ORDER}
    for resource_name, profile in profiles.items():
        grouped[ruleset_definitions[resource_name].resource_type].append(resource_name)
    for names in grouped.values():
        names.sort()
    return grouped


def place_resources(
    topology: TopologyDump,
    tiles: Sequence[object],
    ruleset_definitions: Mapping[str, RulesetResourceDefinition],
    profiles: Mapping[str, ResourceProfile],
    ranked_candidates: Mapping[str, Sequence[RankedCandidate]],
    density_mode: str,
    density_multiplier: float,
    seed: int,
    fairness_mode: bool = False,
) -> ResourcePlacementResult:
    if density_mode not in RESOURCE_DENSITY_MULTIPLIER:
        raise ValueError(f"Unsupported resource density mode: {density_mode}")

    rng = random.Random(seed)
    tile_count = len(tiles)
    placement: Dict[int, PlacedResource] = {}
    counts: Dict[str, int] = {}
    per_resource_tiles: Dict[str, set[int]] = {name: set() for name in profiles.keys()}
    tile_by_index = {int(getattr(tile, "index")): tile for tile in tiles}
    grouped = _resources_by_type(ruleset_definitions, profiles)

    for resource_type in RESOURCE_PLACEMENT_ORDER:
        for resource_name in grouped[resource_type]:
            profile = profiles[resource_name]
            candidates = list(ranked_candidates.get(resource_name, []))
            rng.shuffle(candidates)
            # Preserve seeded shuffle order for equal-score candidates.
            candidates.sort(key=lambda item: -item.score)

            target_count = _target_count(profile, tile_count, density_mode=density_mode, density_multiplier=density_multiplier)
            placed = 0
            for candidate in candidates:
                if placed >= target_count:
                    break
                idx = candidate.tile_index
                if idx in placement:
                    continue
                if _is_within_distance(topology, idx, per_resource_tiles[resource_name], profile.min_distance):
                    continue
                tile = tile_by_index[idx]
                base_terrain = str(getattr(tile, "base_terrain"))
                amount = 0
                if resource_type == "Strategic":
                    amount = _strategic_amount(
                        resource_name,
                        ruleset_definitions[resource_name],
                        profile=profile,
                        density_mode=density_mode,
                        tile_base_terrain=base_terrain,
                        rng=rng,
                    )
                placement[idx] = PlacedResource(resource=resource_name, amount=amount)
                per_resource_tiles[resource_name].add(idx)
                placed += 1
            counts[resource_name] = placed

    if fairness_mode:
        for resource_name, profile in profiles.items():
            if ruleset_definitions[resource_name].resource_type != "Strategic":
                continue
            if counts.get(resource_name, 0) > 0:
                continue
            fairness_candidates = list(ranked_candidates.get(resource_name, []))
            rng.shuffle(fairness_candidates)
            fairness_candidates.sort(key=lambda item: -item.score)
            for candidate in fairness_candidates:
                idx = candidate.tile_index
                if idx in placement:
                    continue
                tile = tile_by_index[idx]
                amount = _strategic_amount(
                    resource_name,
                    ruleset_definitions[resource_name],
                    profile=profile,
                    density_mode=density_mode,
                    tile_base_terrain=str(getattr(tile, "base_terrain")),
                    rng=rng,
                )
                placement[idx] = PlacedResource(resource=resource_name, amount=amount)
                per_resource_tiles[resource_name].add(idx)
                counts[resource_name] = 1
                break

    # Ensure all strategic deposits have positive amount
    for idx, placed in placement.items():
        if ruleset_definitions[placed.resource].resource_type == "Strategic" and placed.amount <= 0:
            raise ValueError(f"Strategic resource {placed.resource} at tile {idx} has invalid amount={placed.amount}")

    return ResourcePlacementResult(placements_by_tile=placement, counts_by_resource=counts)
