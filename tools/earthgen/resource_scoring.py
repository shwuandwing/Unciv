from __future__ import annotations

from dataclasses import dataclass
from typing import Dict, Iterable, Mapping, Sequence

from tools.earthgen.resource_dataset_sampling import ResourceDatasetLayers, metric_value
from tools.earthgen.resource_rules_gnk import ResourceProfile, RulesetResourceDefinition


@dataclass(frozen=True)
class RankedCandidate:
    tile_index: int
    score: float


def _token_matches_tile(
    terrain_token: str,
    tile: object,
    layers: ResourceDatasetLayers,
    tile_index: int,
) -> bool:
    base_terrain = str(getattr(tile, "base_terrain"))
    features = set(str(v) for v in getattr(tile, "features"))

    if terrain_token in {"Grassland", "Plains", "Desert", "Tundra", "Snow", "Coast"}:
        return base_terrain == terrain_token
    if terrain_token in {"Forest", "Jungle", "Marsh", "Hill"}:
        return terrain_token in features
    if terrain_token == "Flood plains":
        return base_terrain == "Desert" and bool(layers.fresh_water[tile_index])
    return False


def _within_latitude_range(profile: ResourceProfile, latitude: float) -> bool:
    if profile.latitude_min is not None and latitude < profile.latitude_min:
        return False
    if profile.latitude_max is not None and latitude > profile.latitude_max:
        return False
    return True


def is_tile_eligible(
    profile: ResourceProfile,
    ruleset_def: RulesetResourceDefinition,
    tile: object,
    layers: ResourceDatasetLayers,
    tile_index: int,
) -> bool:
    if not profile.enabled:
        return False

    latitude = float(getattr(tile, "latitude"))
    if not _within_latitude_range(profile, latitude):
        return False

    features = set(str(v) for v in getattr(tile, "features"))
    if any(feature not in features for feature in profile.required_features):
        return False
    if any(feature in features for feature in profile.forbidden_features):
        return False

    allowed_tokens = profile.allowed_terrains or ruleset_def.terrains_can_be_found_on
    if allowed_tokens:
        if not any(_token_matches_tile(token, tile, layers, tile_index) for token in allowed_tokens):
            return False

    return True


def _region_boost(profile: ResourceProfile, lon: float, lat: float) -> float:
    boost = 0.0
    for region in profile.region_boosts:
        if region.min_lon <= lon <= region.max_lon and region.min_lat <= lat <= region.max_lat:
            boost += region.boost
    return boost


def score_tile_for_resource(profile: ResourceProfile, tile: object, layers: ResourceDatasetLayers, tile_index: int) -> float:
    score = 0.2
    for metric_name, weight in profile.dataset_weights.items():
        score += weight * metric_value(metric_name, tile_index, layers)

    lon = float(getattr(tile, "longitude"))
    lat = float(getattr(tile, "latitude"))
    score += _region_boost(profile, lon, lat)
    return float(score)


def rank_candidates_for_resource(
    profile: ResourceProfile,
    ruleset_def: RulesetResourceDefinition,
    tiles: Sequence[object],
    layers: ResourceDatasetLayers,
) -> list[RankedCandidate]:
    ranked: list[RankedCandidate] = []
    for tile in tiles:
        index = int(getattr(tile, "index"))
        if not is_tile_eligible(profile, ruleset_def, tile, layers, index):
            continue
        score = score_tile_for_resource(profile, tile, layers, index)
        ranked.append(RankedCandidate(tile_index=index, score=score))
    ranked.sort(key=lambda value: (-value.score, value.tile_index))
    return ranked


def rank_candidates_by_resource(
    profiles: Mapping[str, ResourceProfile],
    ruleset_definitions: Mapping[str, RulesetResourceDefinition],
    tiles: Sequence[object],
    layers: ResourceDatasetLayers,
    disabled_resources: Iterable[str] = (),
) -> Dict[str, list[RankedCandidate]]:
    disabled = set(disabled_resources)
    ranked: Dict[str, list[RankedCandidate]] = {}
    for resource_name, profile in profiles.items():
        if resource_name in disabled:
            ranked[resource_name] = []
            continue
        ruleset_def = ruleset_definitions[resource_name]
        ranked[resource_name] = rank_candidates_for_resource(profile, ruleset_def, tiles, layers)
    return ranked
