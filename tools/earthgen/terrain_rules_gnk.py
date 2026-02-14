from __future__ import annotations

from dataclasses import dataclass
from typing import List


LAND_BASE_TERRAINS = {"Desert", "Plains", "Grassland", "Tundra", "Snow", "Mountain"}
WATER_BASE_TERRAINS = {"Ocean", "Coast", "Lakes"}


@dataclass(frozen=True)
class ClimateSample:
    is_land: bool
    is_lake: bool
    latitude: float
    temperature_c: float | None
    annual_precip_mm: float | None
    elevation_m: float | None


def classify_base_terrain(sample: ClimateSample) -> str:
    if sample.is_lake:
        return "Lakes"
    if not sample.is_land:
        return "Ocean"

    temp = sample.temperature_c if sample.temperature_c is not None else _default_temperature(sample.latitude)
    precip = sample.annual_precip_mm if sample.annual_precip_mm is not None else _default_precip(sample.latitude)
    elev = sample.elevation_m if sample.elevation_m is not None else 0.0

    if elev >= 3200:
        return "Mountain"
    if abs(sample.latitude) >= 78 or temp <= -9:
        return "Snow"
    if abs(sample.latitude) >= 65 or temp <= -1:
        return "Tundra"
    if precip < 300 or (temp >= 20 and precip < 500):
        return "Desert"
    if precip < 900:
        return "Plains"
    return "Grassland"


def classify_features(sample: ClimateSample, base_terrain: str) -> List[str]:
    features: List[str] = []

    temp = sample.temperature_c if sample.temperature_c is not None else _default_temperature(sample.latitude)
    precip = sample.annual_precip_mm if sample.annual_precip_mm is not None else _default_precip(sample.latitude)
    elev = sample.elevation_m if sample.elevation_m is not None else 0.0

    if base_terrain in WATER_BASE_TERRAINS:
        if base_terrain != "Lakes" and (abs(sample.latitude) >= 72 or temp <= -6):
            features.append("Ice")
        return features

    if base_terrain == "Mountain":
        return features

    if elev >= 1400 and base_terrain not in {"Snow"}:
        features.append("Hill")

    if base_terrain in {"Snow", "Desert"}:
        return features

    if temp >= 24 and precip >= 1800:
        features.append("Jungle")
    elif precip >= 900 and -8 <= temp <= 26:
        features.append("Forest")

    if base_terrain in {"Grassland", "Plains"} and abs(sample.latitude) < 35 and precip >= 1400 and temp >= 18:
        # Marsh is intentionally sparse and mostly low-latitude / wet.
        if "Forest" not in features and "Jungle" not in features:
            features.append("Marsh")

    return features


def _default_temperature(latitude: float) -> float:
    # Smooth fallback when climate raster is unavailable.
    return 30.0 - abs(latitude) * 0.65


def _default_precip(latitude: float) -> float:
    # Rough global-scale pattern: wetter tropics/mid-latitudes, drier subtropics.
    abs_lat = abs(latitude)
    if abs_lat < 12:
        return 1800.0
    if abs_lat < 25:
        return 550.0
    if abs_lat < 45:
        return 900.0
    if abs_lat < 65:
        return 700.0
    return 300.0
