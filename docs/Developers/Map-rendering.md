# Map rendering

## Introduction - how does LibGDX render images?

Images in LibGDX are displayed on screen by a SpriteBatch, which uses GL to bind textures to load them in-memory, and can then very quickly display them on-screen.
The actual rendering is then very fast, but the binding process is slow.
Therefore, ideally we'd want as little bindings as possible, so the textures should contain as many images as possible.
This is why we compile images (ImagePacker.packImages()) into large PNGs.

However, due to limitations in different chipsets etc, these images are limited to a maximum size of 2048*2048 pixels, and the game contains more images than would fit into a single square of that size.
What we do, then, is separate them by category, and thus rendering proximity.
The 'android' folder contains Images, but also various sub-categories - Images.Flags, Images.Tech, etc.
Each of these sub-categories are compiled into a separate PNG file in the 'android/assets' folder.

When rendering, the major time-sink is in rebinding textures. We therefore need to be careful to minimize the number of -rebinds, or 'swapping between different categories'.

## Layering

Each map tile is comprised of several layers, and each layer needs to be rendered for all tiles before the next layer is.
For example, we don't want one tile's unit sprite to be overlayed by another's improvement.
This layering is done in TileGroupMap, where we take the individual parts for all tiles, separate them into the layers, and add them all to one big group.
This also has a performance advantage, since e.g. text and construction images in the various city buttons are not rendered until the very end, and therefore swap per the number of of cities and not for every single tile.
This also means that mods which add their own tilesets or unit sprites have better performance than 'render entire tile; would provide, since we first render all terrains, then all improvements, etc,
so if my tileset provides all terrains, it won't be swapped out until we're done.

## Debugging

Android Studio's built-in profiler has a CPU profiler which is perfect for this.
Boot up the game on your Android device, open a game, start recording CPU, move the screen around a bit, and stop recording.
Select the "GL Thread" from the list of threads, and change visualization to a flame graph. You'll then see what's actually taking rendering time.

You can find various games to test on [here](https://github.com/yairm210/Unciv/issues?q=label%3A%22Contains+Saved+Game%22) - [This](https://github.com/yairm210/Unciv/issues/4840) for example is a crowded one.

## Icosahedron 3D view (Map Editor and World Screen)

The icosahedron map editor and world screen share a dedicated 3D globe renderer path, separate from classic `TileGroup` 2D rendering.

Primary files:
- `core/src/com/unciv/ui/render/globe/IcosaGlobeActor.kt`
- `core/src/com/unciv/ui/render/globe/IcosaMeshRuntimeCache.kt`
- `core/src/com/unciv/ui/render/globe/GlobeTileOverlayResolver.kt`
- `core/src/com/unciv/ui/render/globe/GlobeOverlay*` policy/helper files
- `core/src/com/unciv/ui/screens/mapeditorscreen/MapEditorScreen.kt`
- `core/src/com/unciv/ui/screens/worldscreen/WorldScreen.kt`

Current mode behavior:
- Map Editor 3D mode is read-only by product decision.
- World Screen phase-3 3D mode is also view-only/read-only (navigation + inspection, no gameplay actions).

### Render pipeline

Each frame:
1. Project visible icosa tiles and their polygon corners to stage space.
2. Draw base tile fill and ownership/grid borders via `ShapeRenderer`.
3. Draw terrain/improvement/resource overlays as polygon-textured hexes via `PolygonSpriteBatch`.
4. Draw roads and markers.

Overlays are mapped per projected polygon vertex, not stamped as rectangles. This avoids most limb distortion artifacts and keeps texture clipping inside each hex.

### Orientation model

For icosa tiles, overlay orientation uses a hybrid strategy:
- Take a continuous local-north reference from sphere geometry.
- Snap to the closest polygon-aligned candidate (hex vertex directions).

This keeps edge alignment stable while avoiding abrupt flips.

### LOD near the limb

At grazing camera angles (the globe limb), tile textures are heavily minified and can alias.
The renderer applies screen-space LOD policies:
- Fade overlay detail by projected tile span and facing angle.
- Fade base terrain earlier than high-value overlays.
- Fade grid/border lines near the limb.

These policies are in `GlobeOverlayLodPolicy`.

### River/edge/border overlays

2D rendering composes terrain, edge transitions, and rivers in `TileLayerTerrain`.
The 3D path resolves equivalent texture layers through `GlobeTileOverlayResolver` and maps them onto projected tile polygons.

Important details for parity with 2D:
- River ownership is still tile-edge based (`hasBottomRightRiver` / `hasBottomRiver` / `hasBottomLeftRiver`).
- Directional overlays (river strips and edge-transition strips) must use directional orientation **and** directional frame basis.
- Directional overlays use `0` texel UV inset to avoid trimming border pixels needed for coast/river contact.
- UV V-axis mapping for unflipped atlas regions is inverted for Y-up polygon mapping, otherwise bottom river textures appear on opposite edges.

### Test coverage

Globe renderer tests live under:
- `tests/src/com/unciv/ui/render/globe/`

Important categories:
- Orientation and continuity policies.
- Polygon UV mapping and triangulation.
- Overlay LOD behavior.
- Terrain/edge/river overlay resolution helpers.
- Directional overlay frame-basis selection (`regular` vs `directional`).
- Directional overlay UV inset and V-axis window orientation.
- Saved-map regression coverage for river-edge selection (`android/assets/maps/Test`, tile `(105,46)`).
