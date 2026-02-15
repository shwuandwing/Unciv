# Earth Map Generator (Icosahedron)

Generate Earth-like Unciv maps for `Icosahedron` shape using real-world datasets.

## Quick Start

Run from repo root:

```bash
# 1) Create venv and install deps (first time)
python3 -m venv tools/earthgen/.venv
tools/earthgen/.venv/bin/pip install -r tools/earthgen/requirements.txt

# 2) Download/refresh datasets (first time or when needed)
tools/earthgen/.venv/bin/python tools/earthgen/fetch_datasets.py

# 3) Generate map (topology is auto-generated on first run if missing)
tools/earthgen/.venv/bin/python tools/earthgen/generate_unciv_earth_map.py \
  --size Huge \
  --name "Earth-Icosa-Huge" \
  --output android/assets/maps/Earth-Icosa-Huge

#    (Requires a working Java runtime because auto-generation uses ./gradlew :desktop:run)

# 4) Generate map with realistic resources (default behavior)
tools/earthgen/.venv/bin/python tools/earthgen/generate_unciv_earth_map.py \
  --size Huge \
  --name "Earth-Icosa-Huge-resources" \
  --resource-density default \
  --resource-seed 1337 \
  --output android/assets/maps/Earth-Icosa-Huge-resources
```

## Common Options

- `--size Tiny|Small|Medium|Large|Huge` or `--frequency <n>`
- `--river-count <n>` (default `20`)
- `--output <path>` (required)
- `--name <map-name>`
- `--auto-generate-topology` / `--no-auto-generate-topology` (default: enabled)
- `--enable-resources` / `--disable-resources` (default: enabled)
- `--resource-density sparse|default|abundant|<multiplier>`
- `--resource-seed <int>`
- `--resource-profile <path>`
- `--disable-resource <name>` (repeatable)
- `--resource-fairness` / `--no-resource-fairness` (default: disabled)

Orientation defaults (current):

- `--pole-alignment topology`
- `--flip-latitude` enabled
- `--flip-longitude` disabled

You can override with:

- `--no-flip-latitude`
- `--flip-longitude`
- `--longitude-offset <degrees>`
- `--pole-alignment map-centered`

Resource examples:

```bash
# Sparse resources, deterministic layout
tools/earthgen/.venv/bin/python tools/earthgen/generate_unciv_earth_map.py \
  --size Large \
  --resource-density sparse \
  --resource-seed 99 \
  --output android/assets/maps/Earth-Icosa-Large-res-sparse

# Keep resources enabled but disable one type
tools/earthgen/.venv/bin/python tools/earthgen/generate_unciv_earth_map.py \
  --size Huge \
  --disable-resource Uranium \
  --output android/assets/maps/Earth-Icosa-Huge-no-uranium

# Disable all resource placement (terrain/hydrology only)
tools/earthgen/.venv/bin/python tools/earthgen/generate_unciv_earth_map.py \
  --size Huge \
  --disable-resources \
  --output android/assets/maps/Earth-Icosa-Huge-terrain-only
```

## Validate

```bash
tools/earthgen/.venv/bin/python -m pytest tools/earthgen/tests -q
```

## Output

The generated map file can be loaded in Unciv Map Editor from:

- `android/assets/maps/<your-map-name>`
