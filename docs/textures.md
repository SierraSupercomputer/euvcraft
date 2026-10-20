# EUVCRAFT M1 Textures

Deterministic, editable 16×16 texture assets for all M1 production items and blocks.
Generated from a single manifest using only Node built-ins — no external services,
no image libraries, no subprocesses.

---

## File Overview

| File | Purpose |
|------|---------|
| `tools/starter-textures.json` | **Primary editable source** — palettes, 16×16 draw recipes, output paths (schema v2) |
| `tools/generate-starter-textures.mjs` | Starter generator — validates manifest, rasterizes, emits PNGs + models + preview atlas |
| `tools/generate-joined-panels.mjs` | Joined panel generator — validates manifest, rasterizes 48 joined tiles, emits 96 block models, 4 blockstates, item model, loot, recipe, preview |
| `build/starter-textures-preview.png` | 6× atlas of every starter asset on a neutral checkerboard |
| `build/joined-panels-preview.png` | 6× atlas of every joined panel tile + item on a neutral checkerboard |
| `docs/starter-textures.md` | Starter texture documentation |
| `docs/textures.md` | This file |

---

## Commands

```bash
# Regenerate starter textures (23 items + 10 blocks + preview)
node tools/generate-starter-textures.mjs

# Verify starter assets only — prints MISSING/STALE per asset, exits 1 on mismatch
node tools/generate-starter-textures.mjs --check

# Regenerate joined panels (96 block models + 4 blockstates + 1 item + loot + recipe + preview)
node tools/generate-joined-panels.mjs

# Verify joined panel assets only
node tools/generate-joined-panels.mjs --check
```

Any argument besides `--check` is refused with exit code 2.
`--check` never writes — including the preview — and reuses the exact buffers
that the write path would emit.

---

## Manifest Schema (v2)

### Palettes

Each palette is an object of **named color slots** (`#RRGGBB` or `#RRGGBBAA`;
a missing `AA` means fully opaque):

```json
"CHIP_T1": {
  "outline": "#4D3A1A", "dark": "#7A5A2A", "mid": "#A87A3A",
  "light": "#D69A6A", "accent": "#E8B89A", "trim": "#FFD8C8"
}
```

Palettes carry at least 4 slots. T1/T2 item pairs and formed/unformed block pairs
reuse the same `shape` (identical geometry) with a different palette.

### Shapes

A shape is an ordered array of integer draw ops applied to a 16×16 RGBA canvas:

| Op | Args | Meaning |
|----|------|---------|
| `rect` | `color, x, y, w, h` | filled rectangle |
| `frame` | `color, x, y, w, h` | 1-pixel rectangle outline |
| `line` | `color, x0, y0, x1, y1` | Bresenham line, endpoints included |
| `dot` | `color, x, y` | single pixel |

`color` must be a slot name in the sprite's palette. Every coordinate must be an
integer in the 0–15 canvas; empty or negative rects, unknown kinds/colors and
out-of-bounds ops are validation errors — nothing is silently clamped, cropped or padded.

### Items (`items.<id>`)

```json
"logic_chip_t1": { "palette": "CHIP_T1", "shape": "chip", "note": "…" }
```

The item canvas starts fully transparent and must keep at least one fully
transparent pixel (RGBA `0,0,0,0` background). Each entry generates
`textures/item/<id>.png` (16×16 RGBA8) and the model
`models/item/<id>.json` with parent `minecraft:item/generated` and
`layer0: "lithmod:item/<id>"`.

### Blocks (`blocks.<id>`) — Starter Textures

```json
"process_cell_controller_formed": {
  "faces": {
    "side":  { "palette": "CASING_FORMED",     "shape": "ctrl_side" },
    "top":   { "palette": "CASING_FORMED",     "shape": "ctrl_top" },
    "front": { "palette": "CTRL_FRONT_FORMED", "shape": "ctrl_front" }
  }
}
```

- Blocks with a single face use `"faces": { "all": … }` → `textures/block/<id>.png`.
- The two Process Cell controllers use exactly `side`, `top`, `front` →
  `<id>_side.png`, `<id>_top.png`, `<id>_front.png`.
- Every block pixel must end at alpha 255 — transparent block tiles are rejected.

### Joined Panels Extension (`joinedPanels`)

The joined panels section extends the manifest for the 2×2×3 multiblock:

```json
"joinedPanels": {
  "palette": { ... },      // 11 new palettes for steel/teal/amber roles
  "shapes": { ... },       // 8 new shapes: frames, stripes, vents, service, cabinet, panel, port, unformed
  "tiles": { ... },        // 11 tiles mapping palette+shape
  "faceMap": {             // formed state: role -> {face: tile}
    "controller": { "north": "controller_front", "south": "steel_vents", ... },
    "casing": { "north": "steel_stripes", "south": "steel_stripes", ... },
    "power_port": { "north": "steel_service", "south": "power_port_face", ... },
    "operating_panel": { "north": "panel_front", "south": "steel_vents", ... }
  },
  "roleAssignments": {     // part index (0..11) -> role
    "0": "controller", "1": "controller", "2": "operating_panel", ...
  },
  "unformedTiles": {       // unformed state: role -> tile
    "controller": "unformed_controller", ...
  },
  "output": { ... }        // validated output paths
}
```

**Geometry**: Fixed 2×2×3, `u=0..1, v=0..1, d=0..2`, `part = u*6 + v*3 + d` (0..11).
All members have `formed` (boolean) and `facing` (horizontal) and `part` (0..11).

**Face UV Mapping** (per FaceBakery canonical north-facing local coords):
- north: `x=1-u, y=1-v` → tileU=`u`, tileV=`v`
- south: `x=u, y=1-v` → tileU=`1-u`, tileV=`v`
- west: `x=d, y=1-v` → tileU=`d`, tileV=`v`
- east: `x=2-d, y=1-v` → tileU=`2-d`, tileV=`v`
- up: `x=u, y=d` → tileU=`u`, tileV=`d`
- down: `x=u, y=2-d` → tileU=`u`, tileV=`2-d`

**Roles** (4):
- `controller` — lower cabinet front door/handle (parts 0,1)
- `casing` — continuous steel/teal stripes (parts 4,5,6,7,10,11)
- `power_port` — recognizable on exposed surfaces (parts 8,9)
- `operating_panel` — upper panel screen/buttons (parts 2,3)

**Blockstates**: All 4 roles × 12 parts × 4 facings × 2 formed states = 384 variants.
- `formed=false` ignores `part` and `facing`, uses single unformed model.
- `formed=true` keyed by `part` + horizontal `facing` (rotation: north=0, east=90, south=180, west=270, no UV lock).

**Models**: 96 block models (`minecraft:block/cube` parent with 6 faces + particle),
1 item model (`minecraft:item/generated` for operating_panel).

**Textures**: 11 tile PNGs (16×16 RGBA8 fully opaque) + 1 item PNG (transparent bg).

**Loot**: operating_panel drops itself (`minecraft:survives_explosion` condition).

**Recipe**: operating_panel shaped crafting (3×3, vanilla ingredients: iron, redstone, copper, glass, quartz).

---

## Output Config

Both generators validate output paths against hardcoded expected values.
Extra, missing, or duplicated IDs in the model lists, or a changed path
or an absolute/`..`/backslash path, fails validation.

### Starter Output
```json
"output": {
  "itemsDir": "src/main/resources/assets/lithmod/models/item",
  "blocksDir": "src/main/resources/assets/lithmod/models/block",
  "texturesItemsDir": "src/main/resources/assets/lithmod/textures/item",
  "texturesBlocksDir": "src/main/resources/assets/lithmod/textures/block",
  "previewPath": "build/starter-textures-preview.png",
  "itemModelsToUpdate": [ 23 IDs ],
  "blockModelsToUpdate": [ 10 IDs ],
  "preserveUntouched": ["test_machine"]
}
```

### Joined Panels Output
```json
"output": {
  "blocksDir": "src/main/resources/assets/lithmod/models/block",
  "blockstatesDir": "src/main/resources/assets/lithmod/blockstates",
  "texturesBlocksDir": "src/main/resources/assets/lithmod/textures/block",
  "itemsDir": "src/main/resources/assets/lithmod/models/item",
  "texturesItemsDir": "src/main/resources/assets/lithmod/textures/item",
  "previewPath": "build/joined-panels-preview.png",
  "lootTablesDir": "src/main/resources/data/lithmod/loot_table/blocks",
  "recipesDir": "src/main/resources/data/lithmod/recipe/crafting",
  "blockModelsToUpdate": [ 96 IDs ]
}
```

---

## Preview Atlases

Both generators produce a 6× nearest-neighbor atlas over an 8-px neutral gray checkerboard.

### Starter Preview
- 37 sprites (23 items + 14 block faces) in 8-column grid
- Dimensions: 804×504
- Legend order: items 1–23, then block faces 24–37

### Joined Panels Preview
- 12 sprites (11 tiles + 1 item) in 8-column grid
- Shows each tile once (tiles are shared across parts/faces)
- Dimensions vary based on tile count

---

## Editing Workflow

### Starter Textures
1. Edit `tools/starter-textures.json` — palette colors, draw ops, notes.
2. Run `node tools/generate-starter-textures.mjs`.
3. Inspect `build/starter-textures-preview.png`.
4. Run `node tools/generate-starter-textures.mjs --check` and commit both the
   manifest and the regenerated assets.

### Joined Panels
1. Edit `tools/starter-textures.json` `joinedPanels` section — palettes, shapes, tiles, faceMap, roleAssignments.
2. Run `node tools/generate-joined-panels.mjs`.
3. Inspect `build/joined-panels-preview.png`.
4. Run `node tools/generate-joined-panels.mjs --check` and commit both the
   manifest and the regenerated assets.

> Direct PNG edits are overwritten on the next regeneration. The manifest is the
> single source of truth; if you hand-edit a PNG, that edit is lost (the `--check`
> gate will flag drift as STALE before the owner's build sees it).

---

## Determinism Guarantees

- Byte-identical PNGs per run: fixed filter 0, level-9 deflate, no timestamps/metadata.
- Per-chunk CRC32 (unsigned) from `zlib.crc32`; full-shape PNGs carry their own IHDR.
- `--check` compares recomputed PNGs by SHA-256 and models as parsed JSON text with
  only line endings normalized (LF/CRLF) so Git `core.autocrlf` does not cause
  false drift.
- `--check` heals nothing and writes nothing; exit 1 lists each missing/stale asset.

---

## JUnit Tests

### StarterTextureResourceTest
Validates real packaged starter resources (ImageIO + Gson):
- All 23 item models → `lithmod:item/<id>` layer0; PNG 16×16, alpha 0/255, at least one 0.
- All 10 block models → expected parents (`cube_all` / `orientable`) and face texture paths; each texture 16×16 alpha 255.
- `test_machine` model/texture fixtures preserved untouched.
- Formed/unformed sibling models reference disjoint texture sets; pixel data differs per state.
- Seven block-item models reference existing local block models.

### JoinedPanelResourceTest
Validates joined panel resources:
- All 11 tile textures exist as 16×16 opaque PNGs.
- All 96 block models reference `minecraft:block/cube` with 6 faces + particle; texture refs follow `joined_<tile>_<face>_u<U>v<V>` format.
- All 4 blockstates cover 12 parts × 4 facings × 2 states = 96 variants each, no overlap; formed variants have correct y-rotation.
- Operating panel item model exists with transparent 16×16 PNG.
- Operating panel loot table drops itself with survives_explosion condition.
- Operating panel crafting recipe valid shaped 3×3 with vanilla ingredients.
- No extra joined panel block models/blockstates beyond expected.

### PresentationResourceTest
Validates recipe presentation:
- All 23 crafting JSONs valid (was 22).
- All 3 pattern generator JSONs decode via codecs.
- Generated `docs/recipes.md` contains 31 resource IDs (was 30), source paths, no placeholders.

### PresentationGameTests
Data-derived checks against live registries:
- Creative tab order: 33 entries (was 32), operating_panel after power_port.
- Recipe registry, crafting lookup, pattern production, obtainability, FOUP crafting/init, datapack override, assets/harvest, survival drops.

Run with: `./gradlew test --tests "StarterTextureResourceTest,JoinedPanelResourceTest,PresentationResourceTest,PresentationGameTests"`

---

## Limitations & Known Gaps

| Area | Status |
|------|--------|
| Animated textures | Not supported — static 16×16 only |
| Mipmaps | Not generated — relies on Minecraft's downsampling |
| Blockstate variants | Starter: only `facing`+`formed`; Joined: `facing`+`formed`+`part` |
| Atlas labels | Numbers documented here, not burned into PNG (no font dependency) |
| Duplicate manifest keys | Collapsed silently by `JSON.parse`; lists are the dedupe point |
| Client rendering | Not claimed — visual confirmation is the owner's walkthrough |
| JSON line endings | Generators emit LF; `--check` normalizes CRLF↔LF before comparing |
| Joined panel UV seams | Continuous across block boundaries by design; outer border only on machine edges |

---

## Adding New Items/Blocks

### Starter Textures
1. Register the item/block in Java and add its ID to the generator's
   `EXPECTED_LIST`s plus this manifest (palette + shape + output list).
2. Describe it with the four drawing ops or reuse an existing shape with a new palette.
3. Regenerate, run `--check` and the resource test, preview, then commit.

### Joined Panels
1. Add new tile to `joinedPanels.tiles` with palette+shape.
2. Add tile to `faceMap` for relevant roles/faces.
3. Assign role to parts in `roleAssignments`.
4. Add unformed tile to `unformedTiles` if new role.
5. Regenerate, run `--check` and JoinedPanelResourceTest, preview, then commit.

---

## License

Generated assets are part of EUVCRAFT M1. Manifest and generators are project-internal tooling.