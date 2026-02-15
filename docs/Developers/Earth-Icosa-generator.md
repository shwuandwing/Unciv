# Earth Icosa Generator (v1)

This document describes the external Python pipeline that generates Earth-like Unciv maps for `Icosahedron` shape.

## Scope

v1 targets:

- Ruleset: `Civ V - Gods & Kings`
- Included: terrain + hydrology
  - Land/water/lakes/coast
  - Biomes (`Desert`, `Plains`, `Grassland`, `Tundra`, `Snow`)
  - Highlands (`Mountain`) and `Hill`
  - Vegetation (`Forest`, `Jungle`, `Marsh`)
  - Polar `Ice`
  - Rivers: top `N` longest river lines (default `20`)
- Included in v1.1:
  - Resource placement (Bonus/Luxury/Strategic) for Civ V - Gods & Kings
  - Deterministic resource generation by seed
  - Strategic deposit amounts (`resourceAmount`) persisted to map output
- Optional:
  - Fairness pass to reduce strategic starvation (`--resource-fairness`)

## Data Sources

Fetched by `tools/earthgen/fetch_datasets.py`:

- Natural Earth 110m land polygons
- Natural Earth 110m lakes polygons
- Natural Earth 110m river centerlines
- WorldClim 2.1 (10m) elevation
- WorldClim 2.1 (10m) monthly mean temperature
- WorldClim 2.1 (10m) monthly precipitation

Datasets are cached under `tools/earthgen/cache/` and tracked by checksum in `manifest.json`.

## Setup

From repo root:

```bash
python3 -m venv .venv-earthgen
. .venv-earthgen/bin/activate
pip install -r tools/earthgen/requirements.txt
```

## 1) Fetch datasets

```bash
python tools/earthgen/fetch_datasets.py --cache-dir tools/earthgen/cache
```

Expected output on first run includes `FETCH:` lines and `Wrote manifest:`.
Expected output on repeat run includes `CACHE HIT` and `Manifest unchanged.`

## 2) Dump Icosa topology from Unciv

```bash
JAVA_HOME=/home/linuxbrew/.linuxbrew/opt/openjdk@21 ./gradlew -q :desktop:run \
  --args="--dump-icosa-topology=/absolute/path/to/topology_f11.json --frequency=11"
```

Expected output:

- `Wrote Icosa topology dump to ... (tiles=..., edges=...)`

Notes:

- Use an absolute path for the dump output.
- Tile count formula is `10*f^2 + 2`.

## 3) Generate Earth-like map

Examples:

```bash
python tools/earthgen/generate_unciv_earth_map.py \
  --topology /absolute/path/to/topology_f11.json \
  --cache-dir tools/earthgen/cache \
  --ruleset "Civ V - Gods & Kings" \
  --size Medium \
  --river-count 20 \
  --name "Earth-Icosa-Medium" \
  --output android/assets/maps/Earth-Icosa-Medium
```

```bash
python tools/earthgen/generate_unciv_earth_map.py \
  --topology /absolute/path/to/topology_f14.json \
  --cache-dir tools/earthgen/cache \
  --frequency 14 \
  --river-count 20 \
  --output android/assets/maps/Earth-Icosa-f14
```

Expected output summary:

- `Wrote map to ... | tiles=... land=... water=... mountains=... frequency=... rivers=... selectedRivers=...`

Default orientation/alignment behavior:

- `--flip-latitude` default: enabled
- `--flip-longitude` default: disabled
- `--pole-alignment` default: `topology`

These defaults are chosen so Earth north/south and east/west orientation matches Unciv's current icosa net presentation without extra flags.
For current core builds, `topology` already uses the net-derived north axis (top-center to bottom-center in the unfolded net), so `map-centered` is mainly a debugging/comparison mode.

Resource behavior defaults:

- Resources are enabled by default
- Resource density defaults to `default`
- Resource seed defaults to `1337`
- Profile defaults to `tools/earthgen/resource_profiles_gnk.yaml`

## Size/Frequency behavior

Predefined mapping:

- `Tiny=5`
- `Small=8`
- `Medium=11`
- `Large=16`
- `Huge=22`

`--frequency` overrides `--size`.

## Orientation controls

You can override the defaults when debugging or comparing projections:

- `--no-flip-latitude`
- `--flip-longitude`
- `--longitude-offset <degrees>`
- `--pole-alignment topology|map-centered`
- `--disable-resources`
- `--resource-density sparse|default|abundant|<multiplier>`
- `--resource-seed <int>`
- `--disable-resource <name>` (repeatable)
- `--resource-fairness|--no-resource-fairness`

Example:

```bash
python tools/earthgen/generate_unciv_earth_map.py \
  --topology /absolute/path/to/topology_f22.json \
  --cache-dir tools/earthgen/cache \
  --size Huge \
  --river-count 20 \
  --no-flip-latitude \
  --longitude-offset 180 \
  --pole-alignment topology \
  --output android/assets/maps/Earth-Icosa-Huge-alt
```

## Validation / Regression Gate

Primary gate:

```bash
.venv-earthgen/bin/python -m pytest tools/earthgen/tests -q
```

Or:

```bash
tools/earthgen/run_validation.sh
```

Current suite validates:

- Dataset fetch/cache manifest behavior
- Polygon sampling behavior across antimeridian crossings
- Topology schema + canonical edge-writer mapping
- Terrain rule snapshots + invalid-combination guards
- River projection integrity (neighbor edges, continuity, serialization fields)
- Resource profile/ruleset contract integrity
- Resource suitability layer construction
- Resource scoring hard filters
- Resource placement overlap + strategic amount constraints
- Size/frequency mapping and generated tile-count checks

## Troubleshooting

- `Topology dump not found`:
  - Run desktop dump command with `--dump-icosa-topology` and matching frequency.
- `Topology frequency mismatch`:
  - Align `--size`/`--frequency` with the topology file frequency.
- Missing Python deps (`numpy`, `tifffile`, etc.):
  - Install from `tools/earthgen/requirements.txt` inside `.venv-earthgen`.
- Map not visible in game:
  - Confirm output path is under `android/assets/maps/` or imported into user map directory.
- Ocean tiles unexpectedly becoming land/desert near map seams:
  - Ensure you're on a revision including antimeridian polygon unwrapping in `dataset_sampling.py`.
- Resource profile validation errors:
  - Ensure profile contains one entry per G&K resource and no unknown resource names.
- Strategic resource count too low for a specific run:
  - Try `--resource-density abundant` and/or enable `--resource-fairness`.
