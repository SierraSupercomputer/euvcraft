# EUVCRAFT M1 Starter Textures

Deterministic, editable 16×16 starter art for all M1 production items and blocks.
Generated from a single manifest using only Node built-ins — no external services,
no image libraries, no subprocesses.

Provenance: free Nemotron authored the initial manifest; free GLM replaced invalid
glyph patterns with integer draw recipes. The coordinator corrected validation,
generated the PNGs and inspected the atlas. These are original project starter
motifs (FOUP, boule, masks, resist, optics and machines), with no external sprites
or image-generation API. Resource tests validate the files; actual post-change
Minecraft rendering still needs the owner's client recheck.

---

## Files

| File | Purpose |
|------|---------|
| `tools/starter-textures.json` | **Primary editable source** — palettes, 16×16 draw recipes, output paths (schema v2) |
| `tools/generate-starter-textures.mjs` | Generator — validates manifest, rasterizes, emits PNGs + models + preview atlas |
| `build/starter-textures-preview.png` | 6× atlas of every generated asset on a neutral checkerboard |
| `docs/starter-textures.md` | This file |

## Commands

```bash
# Regenerate everything (textures, models, preview)
node tools/generate-starter-textures.mjs

# Verify only — prints MISSING/STALE per asset and exits 1 on any mismatch
node tools/generate-starter-textures.mjs --check
```

Any other argument (none besides `--check`) is refused with exit code 2.
`--check` never writes — including the preview — and reuses the exact buffers
that the write path would emit.

The generator writes **only**:

- the 23 item textures + 23 item models listed in `output.itemModelsToUpdate`,
- the block face textures for the 10 block models in `output.blockModelsToUpdate`
  (8 single `all` tiles + 2 controllers × side/top/front),
- `build/starter-textures-preview.png`.

Nothing is deleted; the `test_machine` fixture is not touched. Direct PNG edits are
**overwritten** by the next regeneration — edit the manifest instead.

---

## Manifest Schema (v2)

### Palettes

Each palette is an object of **named color slots** (`#RRGGBB` or `#RRGGBBAA`;
a missing `AA` means fully opaque — `#00000000` is only valid for palettes that
need real transparency, which is never used here):

```json
"CHIP_T1": {
  "outline": "#4D3A1A", "dark": "#7A5A2A", "mid": "#A87A3A",
  "light": "#D69A6A", "accent": "#E8B89A", "trim": "#FFD8C8"
}
```

Palettes carry at least 4 slots. T1/T2 item pairs and formed/unformed block pairs
reuse the same `shape` (identical geometry) with a different palette, e.g.
`chip` on `CHIP_T1`/`CHIP_T2`, `ctrl_front` on `CTRL_FRONT_FORMED`/`_UNFORMED`.

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
`layer0: "lithmod:item/<id>", replacing the old nonexistent `minecraft:item/…` refs.

### Blocks (`blocks.<id>`)

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

### Output Config (`output`)

The five path keys are fixed and validated against exact expected values
(extra, missing or duplicated IDs in the two model lists, or a changed path
or an absolute/`..`/backslash path, fails validation):

```json
"itemsDir": "src/main/resources/assets/lithmod/models/item",
"blocksDir": "src/main/resources/assets/lithmod/models/block",
"texturesItemsDir": "src/main/resources/assets/lithmod/textures/item",
"texturesBlocksDir": "src/main/resources/assets/lithmod/textures/block",
"previewPath": "build/starter-textures-preview.png",
"itemModelsToUpdate": [ "foup_mk1", … 23 IDs … ],
"blockModelsToUpdate": [ "crystal_puller", … 10 IDs … ],
"preserveUntouched": [ "test_machine" ]
```

Enumeration and output bytes are deterministic on Node 24: manifests are read in
listed order, files are hashed with SHA-256, PNGs use fixed level-9 `zlib.deflateSync`
and unsigned `zlib.crc32` chunk CRCs. Note: duplicate JSON keys in the manifest would
be silently collapsed by `JSON.parse` — the two ID *lists* are what get duplicate-checked.

---

## Generated Art

**Items (23)** — restrained steel/copper/blue palettes with 3+ shades and distinct
silhouettes: FOUP canisters with handle + slot window (Mk2 adds wafer dots),
word-pin chips (T1 amber / T2 blue), dense-cell memory die, scrap shards, silicon nugget,
seed-capped boule, resist bottles (T1/T2), chip package pin tray, blank plate + circuit
mask, glow tubes (mercury/i-line), contact plate dot grid, reduction lens barrel,
stage with head/knobs, pleated filters, microscope/barrel scopes — paired T1/T2 reuse
geometry with different palettes.

**Blocks (10 models → 14 face textures)** — recognizable face motifs: Crystal Puller
crystal chamber with glowing seed, Slicer blade slot, Test Package chip banks, purple
Pattern Generator grid, plain-metal casing tiles, controller side vents/top inset plus a
front face with wafer motif and a segmented status bar, and gold lightning-bolt ports.
Formed variants render green, unformed render amber, on the same correct faces —
the unformed controller model only points at `…_unformed` textures (matched by
exact `_formed`/`_unformed` suffix, never a loose `endsWith('formed')`).

Block models: four machines + casings + ports use `minecraft:block/cube_all` with
`lithmod:block/<id>`; controllers use `minecraft:block/orientable` with
`top`/`bottom` (=`top`)/`side`/`front` (+`particle` = side) referencing only that model's
own textures. Blockstates (facing rotations, `formed` property) are not modified.
The seven regenerable block-item models (`crystal_puller`, `wafer_slicer`,
`test_package`, `pattern_generator`, `process_cell_controller`, `process_cell_casing`,
`process_cell_power_port`) keep their `lithmod:block/<id>` parents — the generator does
not write them, the resource test asserts they stay intact.

---

## Preview Atlas

`build/starter-textures-preview.png` after a regular (non-`--check`) run:

- Every sampled sprite is scaled 6× (nearest neighbor), so the PNG header dimensions
  are always the true pixel size (currently 804×504).
- Sprites are alpha-composited over a neutral gray 8-px checkerboard, so
  transparency reads as checkers, never as white or black.
- Order is row-major inside an 8-column grid. The legend below is the numeric order
  (cell 1 = top-left, next cell right, wrapping to the next row):

| Cells | Contents |
|-------|----------|
| 1–23 | item textures in `itemModelsToUpdate` order |
| 24–27 | `crystal_puller`, `wafer_slicer`, `test_package`, `pattern_generator` |
| 28–33 | controller formed side/top/front, controller unformed side/top/front |
| 34–37 | casing formed, casing unformed, port formed, port unformed |

---

## Editing Workflow

1. Edit `tools/starter-textures.json` — palette colors, draw ops, notes.
2. Run `node tools/generate-starter-textures.mjs`.
3. Inspect `build/starter-textures-preview.png`.
4. Run `node tools/generate-starter-textures.mjs --check` and commit both the
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

## JUnit Test (`StarterTextureResourceTest`)

Validates real packaged resources (ImageIO + Gson, same stack as the other
resource tests). Missing sources fail, never skip:

- All 23 item models → `lithmod:item/<id>` layer0; PNG decodes 16×16 with an alpha
  raster, and every pixel's alpha is exactly 0 or 255 with at least one 0 (true
  transparent background — a has-alpha-channel check alone would not prove it).
- All 10 block models → expected parents (`cube_all` / `orientable`) and face
  texture paths; each texture decodes 16×16 and is alpha 255 at every pixel.
- `test_machine` model/texture fixtures preserved untouched.
- Formed/unformed sibling models reference disjoint texture sets, and
  controller fronts / casing / port pixel data actually differ per state
  (amber-unformed vs green-formed cue is verified, not assumed).
- Seven block-item models reference existing local block models. Process Cell inventory items
  use their `_unformed` variants; the four standalone machines use their base models.

Run with: `./gradlew test --tests StarterTextureResourceTest`

---

## Limitations & Known Gaps

| Area | Status |
|------|--------|
| Animated textures | Not supported — static 16×16 only |
| Mipmaps | Not generated — relies on Minecraft's downsampling |
| Blockstate variants | Only `facing`+`formed`; the generator never edits blockstates |
| Atlas labels | Numbers are documented here, not burned into the PNG (no font dependency) |
| Duplicate manifest keys | Collapsed silently by `JSON.parse`; lists are the dedupe point |
| Client rendering | Not claimed — visual confirmation is the owner's M1-12 walkthrough |
| JSON line endings | Generator emits LF; `--check` normalizes CRLF↔LF before comparing |

---

## Adding New Items/Blocks

1. Register the item/block in Java and add its ID to the generator's
   `EXPECTED_LIST`s plus this manifest (palette + shape + output list).
2. Describe it with the four drawing ops or reuse an existing shape with a new palette.
3. Regenerate, run `--check` and the resource test, preview, then commit.

---

## License

Generated assets are part of EUVCRAFT M1. Manifest and generator are project-internal tooling.
