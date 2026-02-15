from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, Iterable, Mapping, Sequence

from tools.earthgen.jsonc import parse_jsonc_file


RULESET_TILE_RESOURCES_PATH = Path("android/assets/jsons/Civ V - Gods & Kings/TileResources.json")
DEFAULT_RESOURCE_PROFILE_PATH = Path("tools/earthgen/resource_profiles_gnk.yaml")

RESOURCE_TYPES = {"Bonus", "Luxury", "Strategic"}
GENERATED_BASE_TERRAINS = {"Ocean", "Coast", "Lakes", "Grassland", "Plains", "Desert", "Tundra", "Snow", "Mountain"}
GENERATED_FEATURES = {"Hill", "Forest", "Jungle", "Marsh", "Ice"}
RULE_TERRAIN_FEATURE_TOKENS = {"Hill", "Forest", "Jungle", "Marsh", "Flood plains"}
RULE_TERRAIN_BASE_TOKENS = {"Coast", "Desert", "Grassland", "Plains", "Snow", "Tundra"}


@dataclass(frozen=True)
class StrategicDepositAmount:
    sparse: int
    default: int
    abundant: int


@dataclass(frozen=True)
class RulesetResourceDefinition:
    name: str
    resource_type: str
    terrains_can_be_found_on: tuple[str, ...]
    major_deposit_amount: StrategicDepositAmount | None
    minor_deposit_amount: StrategicDepositAmount | None


@dataclass(frozen=True)
class RegionBoost:
    min_lon: float
    max_lon: float
    min_lat: float
    max_lat: float
    boost: float


@dataclass(frozen=True)
class ResourceProfile:
    name: str
    resource_type: str
    enabled: bool
    target_density_per_1000: float
    min_count: int
    min_distance: int
    major_ratio: float
    allowed_terrains: tuple[str, ...]
    required_features: tuple[str, ...]
    forbidden_features: tuple[str, ...]
    latitude_min: float | None
    latitude_max: float | None
    dataset_weights: Dict[str, float]
    region_boosts: tuple[RegionBoost, ...]
    notes: str


def _parse_deposit_amount(value: Any) -> StrategicDepositAmount | None:
    if value is None:
        return None
    return StrategicDepositAmount(
        sparse=int(value["sparse"]),
        default=int(value["default"]),
        abundant=int(value["abundant"]),
    )


def load_ruleset_resource_definitions(path: Path = RULESET_TILE_RESOURCES_PATH) -> Dict[str, RulesetResourceDefinition]:
    raw = parse_jsonc_file(path)
    if not isinstance(raw, list):
        raise ValueError(f"Expected list in ruleset tile resources file: {path}")

    definitions: Dict[str, RulesetResourceDefinition] = {}
    for entry in raw:
        name = str(entry["name"])
        resource_type = str(entry.get("resourceType", ""))
        if resource_type not in RESOURCE_TYPES:
            raise ValueError(f"Unsupported resourceType for {name}: {resource_type}")
        if name in definitions:
            raise ValueError(f"Duplicate ruleset resource entry: {name}")
        definitions[name] = RulesetResourceDefinition(
            name=name,
            resource_type=resource_type,
            terrains_can_be_found_on=tuple(str(v) for v in entry.get("terrainsCanBeFoundOn", []) or []),
            major_deposit_amount=_parse_deposit_amount(entry.get("majorDepositAmount")),
            minor_deposit_amount=_parse_deposit_amount(entry.get("minorDepositAmount")),
        )
    return definitions


def _parse_region_boosts(raw: Sequence[Mapping[str, Any]]) -> tuple[RegionBoost, ...]:
    boosts: list[RegionBoost] = []
    for boost in raw:
        boosts.append(
            RegionBoost(
                min_lon=float(boost["min_lon"]),
                max_lon=float(boost["max_lon"]),
                min_lat=float(boost["min_lat"]),
                max_lat=float(boost["max_lat"]),
                boost=float(boost["boost"]),
            )
        )
    return tuple(boosts)


def _profile_allowed_terrains(entry: Mapping[str, Any], ruleset_def: RulesetResourceDefinition) -> tuple[str, ...]:
    explicit = entry.get("allowed_terrains")
    if explicit is not None:
        return tuple(str(v) for v in explicit)
    return ruleset_def.terrains_can_be_found_on


def _parse_profile_entry(entry: Mapping[str, Any], ruleset_def: RulesetResourceDefinition) -> ResourceProfile:
    return ResourceProfile(
        name=ruleset_def.name,
        resource_type=str(entry["resource_type"]),
        enabled=bool(entry.get("enabled", True)),
        target_density_per_1000=float(entry.get("target_density_per_1000", 0.0)),
        min_count=int(entry.get("min_count", 0)),
        min_distance=int(entry.get("min_distance", 1)),
        major_ratio=float(entry.get("major_ratio", 0.5)),
        allowed_terrains=_profile_allowed_terrains(entry, ruleset_def),
        required_features=tuple(str(v) for v in entry.get("required_features", [])),
        forbidden_features=tuple(str(v) for v in entry.get("forbidden_features", [])),
        latitude_min=float(entry["latitude_min"]) if entry.get("latitude_min") is not None else None,
        latitude_max=float(entry["latitude_max"]) if entry.get("latitude_max") is not None else None,
        dataset_weights={str(k): float(v) for k, v in dict(entry.get("dataset_weights", {})).items()},
        region_boosts=_parse_region_boosts(entry.get("region_boosts", [])),
        notes=str(entry.get("notes", "")),
    )


def _validate_allowed_terrain_tokens(
    resource_name: str,
    allowed: Iterable[str],
    ruleset_tokens: Iterable[str],
) -> None:
    generated_tokens = GENERATED_BASE_TERRAINS | GENERATED_FEATURES | {"Flood plains"}
    ruleset_token_set = set(ruleset_tokens)
    for token in allowed:
        if token not in generated_tokens:
            raise ValueError(f"Resource profile {resource_name} references unsupported terrain token: {token}")
        if ruleset_token_set and token not in ruleset_token_set:
            raise ValueError(
                f"Resource profile {resource_name} uses terrain token not allowed in ruleset definition: {token}"
            )


def validate_resource_profiles(
    profiles: Mapping[str, ResourceProfile],
    ruleset_definitions: Mapping[str, RulesetResourceDefinition],
) -> None:
    profile_names = set(profiles.keys())
    ruleset_names = set(ruleset_definitions.keys())

    missing = sorted(ruleset_names - profile_names)
    if missing:
        raise ValueError(f"Missing profile entries for ruleset resources: {', '.join(missing)}")
    extra = sorted(profile_names - ruleset_names)
    if extra:
        raise ValueError(f"Profile includes unknown resources: {', '.join(extra)}")

    for name, profile in profiles.items():
        ruleset = ruleset_definitions[name]
        if profile.resource_type != ruleset.resource_type:
            raise ValueError(
                f"Resource profile {name} has type {profile.resource_type}, expected {ruleset.resource_type}"
            )
        if profile.resource_type not in RESOURCE_TYPES:
            raise ValueError(f"Resource profile {name} has unsupported type: {profile.resource_type}")
        if not (0.0 <= profile.major_ratio <= 1.0):
            raise ValueError(f"Resource profile {name} major_ratio must be in [0,1]")
        if profile.min_distance < 0:
            raise ValueError(f"Resource profile {name} min_distance must be >= 0")
        if profile.target_density_per_1000 < 0.0:
            raise ValueError(f"Resource profile {name} target_density_per_1000 must be >= 0")
        if profile.min_count < 0:
            raise ValueError(f"Resource profile {name} min_count must be >= 0")
        if profile.latitude_min is not None and profile.latitude_max is not None:
            if profile.latitude_min > profile.latitude_max:
                raise ValueError(f"Resource profile {name} has invalid latitude range")

        _validate_allowed_terrain_tokens(name, profile.allowed_terrains, ruleset.terrains_can_be_found_on)
        for feature in profile.required_features:
            if feature not in GENERATED_FEATURES:
                raise ValueError(f"Resource profile {name} requires unknown feature: {feature}")
        for feature in profile.forbidden_features:
            if feature not in GENERATED_FEATURES:
                raise ValueError(f"Resource profile {name} forbids unknown feature: {feature}")

        if profile.resource_type == "Strategic":
            if ruleset.major_deposit_amount is None or ruleset.minor_deposit_amount is None:
                raise ValueError(f"Strategic profile {name} requires major/minor deposit definitions in ruleset")


def load_resource_profiles(
    profile_path: Path = DEFAULT_RESOURCE_PROFILE_PATH,
    ruleset_path: Path = RULESET_TILE_RESOURCES_PATH,
) -> Dict[str, ResourceProfile]:
    ruleset = load_ruleset_resource_definitions(ruleset_path)
    raw_profile = parse_jsonc_file(profile_path)
    if not isinstance(raw_profile, dict):
        raise ValueError(f"Resource profile must be a mapping: {profile_path}")

    entries = raw_profile.get("resources")
    if not isinstance(entries, list):
        raise ValueError(f"Resource profile file missing list field 'resources': {profile_path}")

    parsed: Dict[str, ResourceProfile] = {}
    for item in entries:
        if not isinstance(item, dict):
            raise ValueError("Each resource profile entry must be an object")
        name = str(item["name"])
        ruleset_def = ruleset.get(name)
        if ruleset_def is None:
            raise ValueError(f"Unknown resource in profile: {name}")
        if name in parsed:
            raise ValueError(f"Duplicate resource profile entry: {name}")
        parsed[name] = _parse_profile_entry(item, ruleset_def)

    validate_resource_profiles(parsed, ruleset)
    return parsed


def resources_by_type(resources: Mapping[str, RulesetResourceDefinition]) -> Dict[str, list[str]]:
    grouped = {resource_type: [] for resource_type in RESOURCE_TYPES}
    for definition in resources.values():
        grouped[definition.resource_type].append(definition.name)
    for names in grouped.values():
        names.sort()
    return grouped
