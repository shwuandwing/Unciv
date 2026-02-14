#!/usr/bin/env python3
"""Fetch and cache Earth-generation datasets with checksum manifests.

This script is intentionally stdlib-only for portability.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import tempfile
import time
from pathlib import Path
from typing import Dict, Any
from urllib.request import urlopen, Request

MANIFEST_VERSION = 1
CHUNK_SIZE = 1024 * 1024

DEFAULT_DATASETS: Dict[str, Dict[str, str]] = {
    "land_polygons": {
        "url": "https://raw.githubusercontent.com/martynafford/natural-earth-geojson/master/110m/physical/ne_110m_land.json",
        "filename": "ne_110m_land.json",
        "description": "Natural Earth 110m land polygons",
    },
    "lake_polygons": {
        "url": "https://raw.githubusercontent.com/martynafford/natural-earth-geojson/master/110m/physical/ne_110m_lakes.json",
        "filename": "ne_110m_lakes.json",
        "description": "Natural Earth 110m lakes polygons",
    },
    "river_lines": {
        "url": "https://raw.githubusercontent.com/martynafford/natural-earth-geojson/master/110m/physical/ne_110m_rivers_lake_centerlines.json",
        "filename": "ne_110m_rivers_lake_centerlines.json",
        "description": "Natural Earth 110m river centerlines",
    },
    "elevation_worldclim_10m": {
        "url": "https://geodata.ucdavis.edu/climate/worldclim/2_1/base/wc2.1_10m_elev.zip",
        "filename": "wc2.1_10m_elev.zip",
        "description": "WorldClim 2.1 elevation raster (10m)",
    },
    "temperature_worldclim_10m": {
        "url": "https://geodata.ucdavis.edu/climate/worldclim/2_1/base/wc2.1_10m_tavg.zip",
        "filename": "wc2.1_10m_tavg.zip",
        "description": "WorldClim 2.1 monthly mean temperature raster (10m)",
    },
    "precip_worldclim_10m": {
        "url": "https://geodata.ucdavis.edu/climate/worldclim/2_1/base/wc2.1_10m_prec.zip",
        "filename": "wc2.1_10m_prec.zip",
        "description": "WorldClim 2.1 monthly precipitation raster (10m)",
    },
}


def sha256sum(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as fh:
        while True:
            chunk = fh.read(CHUNK_SIZE)
            if not chunk:
                break
            digest.update(chunk)
    return digest.hexdigest()


def load_manifest(path: Path) -> Dict[str, Any]:
    if not path.exists():
        return {"version": MANIFEST_VERSION, "datasets": {}}
    data = json.loads(path.read_text(encoding="utf-8"))
    if "datasets" not in data:
        data["datasets"] = {}
    if "version" not in data:
        data["version"] = MANIFEST_VERSION
    return data


def save_manifest(path: Path, data: Dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp_fd, tmp_name = tempfile.mkstemp(prefix="manifest_", suffix=".json", dir=str(path.parent))
    os.close(tmp_fd)
    tmp_path = Path(tmp_name)
    try:
        tmp_path.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        tmp_path.replace(path)
    finally:
        if tmp_path.exists():
            tmp_path.unlink(missing_ok=True)


def download_file(url: str, out_path: Path) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    req = Request(url, headers={"User-Agent": "Unciv-EarthGen/1.0"})
    with urlopen(req) as resp:
        tmp_fd, tmp_name = tempfile.mkstemp(prefix="download_", dir=str(out_path.parent))
        os.close(tmp_fd)
        tmp_path = Path(tmp_name)
        try:
            with tmp_path.open("wb") as fh:
                shutil.copyfileobj(resp, fh)
            tmp_path.replace(out_path)
        finally:
            if tmp_path.exists():
                tmp_path.unlink(missing_ok=True)


def load_catalog(path: Path | None) -> Dict[str, Dict[str, str]]:
    if path is None:
        return DEFAULT_DATASETS
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise ValueError("Dataset catalog must be a JSON object keyed by dataset id")
    for dataset_id, entry in data.items():
        if not isinstance(entry, dict):
            raise ValueError(f"Dataset entry for {dataset_id} must be a JSON object")
        if "url" not in entry:
            raise ValueError(f"Dataset entry for {dataset_id} missing required key: url")
        entry.setdefault("filename", Path(entry["url"]).name)
        entry.setdefault("description", "")
    return data


def fetch_datasets(
    cache_dir: Path,
    manifest_path: Path,
    catalog: Dict[str, Dict[str, str]],
    dataset_ids: list[str] | None = None,
    force: bool = False,
) -> Dict[str, Any]:
    selected = dataset_ids or sorted(catalog.keys())
    unknown = [item for item in selected if item not in catalog]
    if unknown:
        raise ValueError(f"Unknown dataset id(s): {', '.join(unknown)}")

    manifest = load_manifest(manifest_path)
    datasets_manifest: Dict[str, Any] = dict(manifest.get("datasets", {}))

    changed = False

    for dataset_id in selected:
        spec = catalog[dataset_id]
        rel_name = spec.get("filename", Path(spec["url"]).name)
        dest_path = cache_dir / rel_name
        existing = datasets_manifest.get(dataset_id)

        should_download = force or not dest_path.exists()
        reason = "missing file" if not dest_path.exists() else "forced"

        if not should_download and existing:
            current_sha = sha256sum(dest_path)
            expected_sha = existing.get("sha256")
            if current_sha == expected_sha:
                print(f"CACHE HIT: {dataset_id} ({dest_path})")
                continue
            should_download = True
            reason = "checksum mismatch"

        if should_download:
            print(f"FETCH: {dataset_id} ({reason})")
            download_file(spec["url"], dest_path)
            changed = True

        checksum = sha256sum(dest_path)
        stat = dest_path.stat()
        downloaded_at = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())

        new_entry = {
            "id": dataset_id,
            "url": spec["url"],
            "filename": str(Path(rel_name)),
            "description": spec.get("description", ""),
            "sha256": checksum,
            "size_bytes": stat.st_size,
            "downloaded_at": downloaded_at,
        }

        if existing:
            if existing.get("sha256") == checksum and existing.get("url") == spec["url"] and existing.get("filename") == rel_name and not should_download:
                # Keep old timestamp to preserve idempotent manifest writes.
                new_entry["downloaded_at"] = existing.get("downloaded_at", downloaded_at)
            elif existing != new_entry:
                changed = True
        else:
            changed = True

        datasets_manifest[dataset_id] = new_entry

    # Remove stale entries if a dataset is no longer in catalog.
    stale_keys = [k for k in datasets_manifest.keys() if k not in catalog]
    if stale_keys:
        changed = True
        for key in stale_keys:
            datasets_manifest.pop(key, None)

    new_manifest = {
        "version": MANIFEST_VERSION,
        "generated_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "datasets": {k: datasets_manifest[k] for k in sorted(datasets_manifest.keys())},
    }

    if not changed and manifest.get("datasets") == new_manifest["datasets"]:
        # Preserve exact manifest if only generated_at would differ.
        print("Manifest unchanged.")
        return manifest

    save_manifest(manifest_path, new_manifest)
    print(f"Wrote manifest: {manifest_path}")
    return new_manifest


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Fetch datasets used by the Earth Icosahedron map generator")
    parser.add_argument("--cache-dir", default="tools/earthgen/cache", help="Directory used for downloaded dataset files")
    parser.add_argument("--manifest", default="manifest.json", help="Manifest filename (stored inside cache directory by default)")
    parser.add_argument("--dataset-catalog", default=None, help="Optional JSON file with dataset definitions")
    parser.add_argument("--dataset", action="append", dest="datasets", help="Specific dataset id to fetch; can be repeated")
    parser.add_argument("--force", action="store_true", help="Force re-download even when checksums match")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    cache_dir = Path(args.cache_dir)
    manifest_path = Path(args.manifest)
    if not manifest_path.is_absolute():
        manifest_path = cache_dir / manifest_path

    catalog_path = Path(args.dataset_catalog) if args.dataset_catalog else None
    catalog = load_catalog(catalog_path)

    fetch_datasets(
        cache_dir=cache_dir,
        manifest_path=manifest_path,
        catalog=catalog,
        dataset_ids=args.datasets,
        force=args.force,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
