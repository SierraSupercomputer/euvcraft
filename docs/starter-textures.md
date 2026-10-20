# EUVCRAFT M1 Starter Textures

Deterministic, editable 16×16 starter art for all M1 production items and blocks.
Generated from a single manifest using only Node built-ins — no external services, no image libraries.

---

## Files

| File | Purpose |
|------|---------|
| `tools/starter-textures.json` | **Primary editable source** — palette, per-sprite 16-char patterns, output paths |
| `tools/generate-starter-textures.mjs` | Generator — validates manifest, emits PNGs + models + preview atlas |
| `build/starter-textures-preview.png` | Compact nearest-neighbor grid of every generated asset on neutral checkerboard |
| `docs/starter-textures.md` | This file |

---

## Quick Start

```bash
# Regenerate everything (textures, models, preview)
node tools/generate-starter-textures.mjs

# Verify only — exits 1 if any asset is missing or stale
node tools/generate-starter-textures.mjs --check
```

The generator writes **only** the paths declared in `starter-textures.json` under `output.*`.
It never deletes or overwrites unrelated files.

---

## Manifest Structure (`starter-textures.json`)

### Palette
Each palette entry is an array of **exactly 6 hex colors** (`#RRGGBB` or `#RRGGBBAA`).
Shades 0–5 map to pattern chars `.` `#` `0`–`9` `A`–`F`:
- `.` → index 0 (transparent for items, darkest for blocks)
- `#` → index 1
- `0`–`9` → indices 2–11
- `A`–`F` → indices 12–17 (clamped to palette length)

> **Design rule:** 4–6 shades per sprite, dark outline / top-left highlight, crisp 1-pixel lines, no antialias.

### Items (`items.<id>`)
```json
"foup_mk1": {
  "palette": "ITEM_FOUP",
  "pattern": [
    "................",
    "..############..",
    ...
  ],
  "note": "FOUP carrier - rectangular box with vertical slots"
}
```
- `id` must match the registered item ID (e.g. `logic_chip_t1`)
- `pattern`: **exactly 16 strings of 16 chars** — each row becomes one scanline
- Generates `lithmod:item/<id>.png` and updates `models/item/<id>.json` to point at it

### Blocks (`blocks.<id>`)
```json
"process_cell_controller_formed": {
  "palette": ["GREEN_FORMED", "CONTROLLER_FRONT", "CONTROLLER_STRIPE"],
  "textures": {
    "side": { "palette": "GREEN_FORMED", "pattern": [...] },
    "top":  { "palette": "GREEN_FORMED", "pattern": [...] },
    "front": { "palette": "CONTROLLER_FRONT", "pattern": [...] },
    "stripe": { "palette": "CONTROLLER_STRIPE", "pattern": [...] }
  }
}
```
- Supports multi-face blocks (`orientable` parent) and single-face (`cube_all`)
- Each face gets its own PNG: `<id>_<face>.png` (or `<id>.png` for `all`)
- Block model JSON is regenerated to reference the new texture paths

### Output Config (`output`)
```json
"output": {
  "itemsDir": "src/main/resources/assets/lithmod/models/item",
  "blocksDir": "src/main/resources/assets/lithmod/models/block",
  "texturesItemsDir": "src/main/resources/assets/lithmod/textures/item",
  "texturesBlocksDir": "src/main/resources/assets/lithmod/textures/block",
  "previewPath": "build/starter-textures-preview.png",
  "itemModelsToUpdate": [ "foup_mk1", "logic_chip_t1", ... ],
  "blockModelsToUpdate": [ "crystal_puller", "process_cell_controller_formed", ... ],
  "preserveUntouched": [ "test_machine" ]
}
```
All paths are relative to the repo root (derived from `import.meta.url`).

---

## What Gets Generated

### Item Textures (23)
Transparent background, `minecraft:item/generated` parent.
| ID | Palette | Note |
|----|---------|------|
| `foup_mk1` | ITEM_FOUP | Grey box with vertical slots |
| `foup_mk2` | ITEM_FOUP | Same with internal dividers |
| `logic_chip_t1` | ITEM_CHIP_T1 | Copper/amber die with grid |
| `logic_chip_t2` | ITEM_CHIP_T2 | Blue/cyan die with grid |
| `memory_chip_t2` | ITEM_CHIP_MEMORY | Purple/violet dense cells |
| `chip_scrap` | ITEM_CHIP_SCRAP | Grey irregular shards |
| `silicon` | ITEM_SILICON | Grey polycrystalline grain |
| `silicon_boule` | ITEM_BOULE | Purple cylinder with seed |
| `resist_t1` | ITEM_RESIST_T1 | Amber liquid bottle |
| `resist_t2` | ITEM_RESIST_T2 | Teal/green liquid bottle |
| `chip_package` | ITEM_CHIP_PACKAGE | Beige box with pin grid |
| `blank_mask` | ITEM_BLANK_MASK | Plain grey plate |
| `mask` | ITEM_MASK | Cyan/teal circuit pattern |
| `mercury_lamp` | ITEM_LAMP_T1 | Amber glow tube |
| `i_line_lamp` | ITEM_LAMP_T2 | Blue/cyan glow tube |
| `contact_plate` | ITEM_OPTICS_T1 | Steel dot grid |
| `reduction_lens` | ITEM_OPTICS_T2 | Blue lens element |
| `manual_aligner` | ITEM_STAGE_T1 | Amber stage with knobs |
| `stepper_stage` | ITEM_STAGE_T2 | Blue/cyan precision stage |
| `basic_air_filter` | ITEM_CLEANROOM_T1 | Green pleated media |
| `fine_air_filter` | ITEM_CLEANROOM_T2 | Cyan dense pleated media |
| `optical_microscope` | ITEM_METROLOGY_T1 | Pink/magenta barrel |
| `overlay_scope` | ITEM_METROLOGY_T2 | Purple/violet barrel |

### Block Textures (10 models → 16 texture files)
Opaque, seamless tiles where applicable.
| Model | Faces | Palette(s) |
|-------|-------|------------|
| `crystal_puller` | all | PULLER_CRYSTAL |
| `wafer_slicer` | all | SLICER_BLADE |
| `test_package` | all | STEEL_CASING |
| `pattern_generator` | all | PATTERN_GRID |
| `process_cell_controller_formed` | side, top, front, stripe | GREEN_FORMED, CONTROLLER_FRONT, CONTROLLER_STRIPE |
| `process_cell_controller_unformed` | side, top, front, stripe | AMBER_UNFORMED, CONTROLLER_FRONT, CONTROLLER_STRIPE |
| `process_cell_casing_formed` | all | GREEN_FORMED |
| `process_cell_casing_unformed` | all | AMBER_UNFORMED |
| `process_cell_power_port_formed` | all | GREEN_FORMED |
| `process_cell_power_port_unformed` | all | AMBER_UNFORMED |

> `test_machine` is **preserved untouched** — not in manifest, not regenerated.

### Block Models
Regenerated to reference `lithmod:block/<texture>` paths.
- Simple blocks (`crystal_puller`, `wafer_slicer`, `test_package`, `pattern_generator`, casings, ports) → `minecraft:block/cube_all`
- Directional controller → `minecraft:block/orientable` with `top`, `bottom`, `side`, `front`

Existing blockstates (rotations, `formed` property) are **not modified**.

---

## Preview Atlas

`build/starter-textures-preview.png` — a single image containing every generated texture in a compact grid:
- Nearest-neighbor scaling (1×)
- Neutral checkerboard background (no transparency confusion)
- Cells ordered: all items first, then blocks
- Useful for quick visual regression checks

---

## Determinism Guarantees

- **Byte-identical PNGs** on every run: fixed filter (0), fixed zlib level, no timestamps, no metadata
- **CRC32** validated per chunk
- **SHA-256** of each output used by `--check` for staleness detection
- Manifest order (key iteration) is the enumeration order — stable in Node.js

---

## Editing Workflow

1. Edit `tools/starter-textures.json`:
   - Adjust palette colors
   - Modify pattern rows (keep 16×16!)
   - Add/remove entries from `itemModelsToUpdate` / `blockModelsToUpdate` if registry changes
2. Run `node tools/generate-starter-textures.mjs`
3. Inspect `build/starter-textures-preview.png`
4. Commit **both** the manifest and the generated assets

> **Direct PNG edits are overwritten** on next regeneration. The manifest is the source of truth.

---

## Validation (`--check`)

```bash
node tools/generate-starter-textures.mjs --check
```
- Recomputes every PNG and model from manifest
- Compares SHA-256 against files on disk
- **Never writes anything** (including preview)
- Exits 0 if all match, 1 if any missing/stale
- Use in CI to catch drift

---

## JUnit Test (`StarterTextureResourceTest`)

Validates at build time:
- All 23 item models reference `lithmod:item/<id>` and the PNG exists + decodes 16×16 RGBA
- All 10 block models reference correct `lithmod:block/…` textures, each PNG exists + decodes 16×16 RGBA (opaque)
- `test_machine` resources untouched
- Missing resources **fail** the test (no silent skip)
- Uses `ImageIO` + Gson — same stack as other resource tests

Run with: `./gradlew test --tests StarterTextureResourceTest`

---

## Limitations & Known Gaps

| Area | Status |
|------|--------|
| Animated textures | Not supported — static 16×16 only |
| Higher-resolution mipmaps | Not generated — relies on Minecraft's downsampling |
| Blockstate variants beyond `facing`+`formed` | Not handled — only the two process cell types use variants |
| Custom model parents (e.g. `orientable` with `particle`) | Hardcoded per block type in generator |
| Palette sharing across sprites | Manual — edit manifest to share a palette name |
| Numbered atlas legend | Not implemented — preview grid is unlabelled |
| Windows CRLF in generated JSON | Forced LF via `writeFileSync(..., {encoding:'utf8'})` — Git `core.autocrlf` may normalize on checkout; `--check` hashes **file bytes**, so regenerate on the same platform or configure `.gitattributes` for LF |

---

## Adding New Items/Blocks

1. Register the item/block in Java (ModItems, ModMultiblocks, etc.)
2. Add palette entry if needed (6 shades)
3. Add entry to `items` or `blocks` in manifest with 16×16 pattern
4. Add ID to `output.itemModelsToUpdate` or `output.blockModelsToUpdate`
5. Run generator
6. Verify preview
7. Commit

---

## Palette Design Notes

- **T1 (copper/amber)** → warm oranges/browns → early-tier silicon chain
- **T2 (blue/cyan)** → cool blues → advanced lithography
- **Formed (green)** → active/energized multiblock state
- **Unformed (amber)** → inactive/structural multiblock state
- **Steel casing** → neutral greys for industrial blocks
- **Distinct silhouettes** — not just colored squares; each sprite has unique shape language

---

## License

Generated assets are part of EUVCRAFT M1. Manifest and generator are project-internal tooling.