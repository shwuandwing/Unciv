from __future__ import annotations

import io
import json
import logging
import math
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, List, Sequence, Tuple

import numpy as np
import tifffile

logging.getLogger("tifffile").setLevel(logging.ERROR)


def wrap_longitude(lon: float) -> float:
    while lon < -180.0:
        lon += 360.0
    while lon >= 180.0:
        lon -= 360.0
    return lon


@dataclass(frozen=True)
class PolygonShape:
    rings: Tuple[Tuple[Tuple[float, float], ...], ...]
    min_lon: float
    max_lon: float
    min_lat: float
    max_lat: float
    center_lon: float

    def contains(self, lon: float, lat: float) -> bool:
        if lat < self.min_lat or lat > self.max_lat:
            return False
        # Rings are stored in unwrapped longitude space; shift point into same longitude band.
        lon = lon + 360.0 * round((self.center_lon - lon) / 360.0)
        if lon < self.min_lon or lon > self.max_lon:
            return False
        outer = self.rings[0]
        if not _point_in_ring(lon, lat, outer):
            return False
        for hole in self.rings[1:]:
            if _point_in_ring(lon, lat, hole):
                return False
        return True


def _point_in_ring(lon: float, lat: float, ring: Sequence[Tuple[float, float]]) -> bool:
    inside = False
    n = len(ring)
    if n < 3:
        return False
    j = n - 1
    for i in range(n):
        xi, yi = ring[i]
        xj, yj = ring[j]
        intersect = (yi > lat) != (yj > lat)
        if intersect:
            x_at_lat = (xj - xi) * (lat - yi) / (yj - yi + 1e-15) + xi
            if lon < x_at_lat:
                inside = not inside
        j = i
    return inside


@dataclass
class GeoRaster:
    data: np.ndarray
    nodata: float | None
    width: int
    height: int
    origin_lon: float
    origin_lat: float
    pixel_lon: float
    pixel_lat: float

    @classmethod
    def from_tiff_bytes(cls, content: bytes) -> "GeoRaster":
        with tifffile.TiffFile(io.BytesIO(content)) as tif:
            page = tif.pages[0]
            data = page.asarray()
            scale_tag = page.tags.get("ModelPixelScaleTag")
            tie_tag = page.tags.get("ModelTiepointTag")
            if scale_tag is None or tie_tag is None:
                raise ValueError("Missing GeoTIFF georeferencing tags")
            scale = scale_tag.value
            tie = tie_tag.value
            pixel_lon = float(scale[0])
            pixel_lat = float(scale[1])
            origin_lon = float(tie[3])
            origin_lat = float(tie[4])

            # Some WorldClim files expose GDAL_NODATA in a format tifffile cannot cast cleanly.
            # Keep nodata disabled here to avoid noisy parse warnings during bulk loads.
            nodata = None

            return cls(
                data=data,
                nodata=nodata,
                width=data.shape[1],
                height=data.shape[0],
                origin_lon=origin_lon,
                origin_lat=origin_lat,
                pixel_lon=pixel_lon,
                pixel_lat=pixel_lat,
            )

    def sample(self, lon: float, lat: float) -> float | None:
        lon = wrap_longitude(lon)
        col = int(math.floor((lon - self.origin_lon) / self.pixel_lon))
        row = int(math.floor((self.origin_lat - lat) / self.pixel_lat))

        if col < 0:
            col = 0
        elif col >= self.width:
            col = self.width - 1

        if row < 0:
            row = 0
        elif row >= self.height:
            row = self.height - 1

        value = float(self.data[row, col])
        if not math.isfinite(value):
            return None
        # WorldClim rasters often use extreme sentinels (~-3.4e38) for nodata.
        if abs(value) > 1e20:
            return None
        if self.nodata is not None and math.isclose(value, self.nodata, rel_tol=0.0, abs_tol=1e-6):
            return None
        return value


@dataclass
class EarthDatasets:
    land_polygons: List[PolygonShape]
    lake_polygons: List[PolygonShape]
    river_lines: List[List[Tuple[float, float]]]
    elevation: GeoRaster
    monthly_temperature: List[GeoRaster]
    monthly_precipitation: List[GeoRaster]

    def point_on_land(self, lon: float, lat: float) -> bool:
        lon = wrap_longitude(lon)
        return any(shape.contains(lon, lat) for shape in self.land_polygons)

    def point_in_lake(self, lon: float, lat: float) -> bool:
        lon = wrap_longitude(lon)
        return any(shape.contains(lon, lat) for shape in self.lake_polygons)

    def sample_elevation(self, lon: float, lat: float) -> float | None:
        return self.elevation.sample(lon, lat)

    def sample_temperature(self, lon: float, lat: float) -> float | None:
        values = [r.sample(lon, lat) for r in self.monthly_temperature]
        valid = [v for v in values if v is not None]
        if not valid:
            return None
        return float(sum(valid) / len(valid))

    def sample_precipitation(self, lon: float, lat: float) -> float | None:
        values = [r.sample(lon, lat) for r in self.monthly_precipitation]
        valid = [v for v in values if v is not None]
        if not valid:
            return None
        return float(sum(valid))


def load_earth_datasets(cache_dir: Path) -> EarthDatasets:
    land = _load_polygons(cache_dir / "ne_110m_land.json")
    lakes = _load_polygons(cache_dir / "ne_110m_lakes.json")
    rivers = _load_river_lines(cache_dir / "ne_110m_rivers_lake_centerlines.json")

    elev = _load_single_raster_from_zip(cache_dir / "wc2.1_10m_elev.zip", suffix=".tif")
    tavg = _load_rasters_from_zip(cache_dir / "wc2.1_10m_tavg.zip", prefix="wc2.1_10m_tavg_", suffix=".tif")
    prec = _load_rasters_from_zip(cache_dir / "wc2.1_10m_prec.zip", prefix="wc2.1_10m_prec_", suffix=".tif")

    return EarthDatasets(
        land_polygons=land,
        lake_polygons=lakes,
        river_lines=rivers,
        elevation=elev,
        monthly_temperature=tavg,
        monthly_precipitation=prec,
    )


def geodesic_polyline_length_km(points: Sequence[Tuple[float, float]]) -> float:
    if len(points) < 2:
        return 0.0
    total = 0.0
    for a, b in zip(points, points[1:]):
        total += haversine_km(a[1], a[0], b[1], b[0])
    return total


def haversine_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    r = 6371.0088
    p1 = math.radians(lat1)
    p2 = math.radians(lat2)
    dl = math.radians(lon2 - lon1)
    dp = math.radians(lat2 - lat1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(min(1.0, math.sqrt(a)))


def _load_single_raster_from_zip(zip_path: Path, suffix: str) -> GeoRaster:
    rasters = _load_rasters_from_zip(zip_path, prefix="", suffix=suffix)
    if len(rasters) != 1:
        raise ValueError(f"Expected exactly one raster in {zip_path}, got {len(rasters)}")
    return rasters[0]


def _load_rasters_from_zip(zip_path: Path, prefix: str, suffix: str) -> List[GeoRaster]:
    if not zip_path.exists():
        raise FileNotFoundError(f"Missing raster archive: {zip_path}")
    rasters: List[GeoRaster] = []
    with zipfile.ZipFile(zip_path) as zf:
        names = sorted(
            n
            for n in zf.namelist()
            if n.lower().endswith(suffix.lower()) and (not prefix or Path(n).name.startswith(prefix))
        )
        if not names:
            raise ValueError(f"No raster files found in {zip_path} for prefix='{prefix}' suffix='{suffix}'")
        for name in names:
            rasters.append(GeoRaster.from_tiff_bytes(zf.read(name)))
    return rasters


def _load_polygons(path: Path) -> List[PolygonShape]:
    data = json.loads(path.read_text(encoding="utf-8"))
    shapes: List[PolygonShape] = []
    for feature in data.get("features", []):
        geometry = feature.get("geometry") or {}
        gtype = geometry.get("type")
        coords = geometry.get("coordinates")
        if not coords:
            continue
        if gtype == "Polygon":
            shape = _polygon_shape_from_coords(coords)
            if shape is not None:
                shapes.append(shape)
        elif gtype == "MultiPolygon":
            for polygon in coords:
                shape = _polygon_shape_from_coords(polygon)
                if shape is not None:
                    shapes.append(shape)
    return shapes


def _polygon_shape_from_coords(coords: Sequence[Sequence[Sequence[float]]]) -> PolygonShape | None:
    rings: List[Tuple[Tuple[float, float], ...]] = []
    lons: List[float] = []
    lats: List[float] = []

    for ring in coords:
        normalized: List[Tuple[float, float]] = []
        for pt in ring:
            lon = wrap_longitude(float(pt[0]))
            lat = float(pt[1])
            normalized.append((lon, lat))
            lats.append(lat)
        if len(normalized) >= 3:
            unwrapped = _unwrap_ring(normalized)
            rings.append(tuple(unwrapped))
            lons.extend([lon for lon, _ in unwrapped])

    if not rings:
        return None

    return PolygonShape(
        rings=tuple(rings),
        min_lon=min(lons),
        max_lon=max(lons),
        min_lat=min(lats),
        max_lat=max(lats),
        center_lon=(min(lons) + max(lons)) / 2.0,
    )


def _unwrap_ring(points: Sequence[Tuple[float, float]]) -> List[Tuple[float, float]]:
    if not points:
        return []
    unwrapped = [points[0]]
    for lon, lat in points[1:]:
        prev_lon = unwrapped[-1][0]
        lon_u = lon
        while lon_u - prev_lon > 180.0:
            lon_u -= 360.0
        while lon_u - prev_lon < -180.0:
            lon_u += 360.0
        unwrapped.append((lon_u, lat))
    return unwrapped


def _load_river_lines(path: Path) -> List[List[Tuple[float, float]]]:
    data = json.loads(path.read_text(encoding="utf-8"))
    lines: List[List[Tuple[float, float]]] = []
    for feature in data.get("features", []):
        geometry = feature.get("geometry") or {}
        gtype = geometry.get("type")
        coords = geometry.get("coordinates")
        if not coords:
            continue
        if gtype == "LineString":
            line = _normalize_line(coords)
            if len(line) > 1:
                lines.append(line)
        elif gtype == "MultiLineString":
            for segment in coords:
                line = _normalize_line(segment)
                if len(line) > 1:
                    lines.append(line)
    return lines


def _normalize_line(coords: Iterable[Sequence[float]]) -> List[Tuple[float, float]]:
    return [(wrap_longitude(float(pt[0])), float(pt[1])) for pt in coords]
