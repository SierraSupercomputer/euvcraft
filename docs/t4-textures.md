# T4 Panel Textures — Edit / Run / Check

## Files

- **Config:** `tools/t4-panels.json` — editable palette + drawing recipes (schema v1)
- **Generator:** `tools/generate-t4-panels.mjs` — standalone entry point
- **Library:** `tools/lib/t4-panels.mjs` — pure computation (no I/O)
- **Output root:** `src/main/resources/assets/lithmod/`

## Quick Start

```bash
# Regenerate all T4 panel assets (PNG + JSON)
node tools/generate-t4-panels.mjs

# Validate existing assets match current config (no writes)
node tools/generate-t4-panels.mjs --check
```

## Config Structure (`tools/t4-panels.json`)

```json
{
  "version": 1,
  "palette": { "steel": "#5A6E78", "teal": "#27A8B4", ... },
  "faceRecipes": {
    "large_north": [ ["rect", "steel", 0, 0, 48, 48], ... ],
    "large_south": [...],
    "large_west": [...],
    "large_east": [...],
    "large_up": [...],
    "large_down": [...],
    "small_north": [...],
    ...
  },
  "markings": {
    "controller_front": [...],
    "operating_panel_front": [...],
    "power_port": [...],
    "casing": [...]
  },
  "unformedRecipes": {
    "controller": [...],
    "casing": [...],
    "power_port": [...],
    "operating_panel": [...]
  }
}
```

### Palette

- Opaque `#RRGGBB` only (no alpha).
- All colors referenced by recipes must exist in `palette`.
- `steel` is mandatory (base fill for all canvases).

### Drawing Operations

Each recipe is an array of ops. Coordinates are **integer pixels** on the face canvas (before 16×16 tiling).

| Op | Args | Description |
|----|------|-------------|
| `rect` | `color, x, y, w, h` | Filled rectangle |
| `frame` | `color, x, y, w, h` | 1-px border rectangle |
| `dot` | `color, x, y` | Single pixel |
| `line` | `color, x0, y0, x1, y1` | Bresenham line |

### Face Canvas Sizes

| Size | north/south | west/east | up/down |
|------|-------------|-----------|---------|
| **large** (3×3×5 = 45 parts) | 48×48 | 80×48 | 48×80 |
| **small** (2×2×3 = 12 parts) | 32×32 | 48×32 | 32×48 |

Tiles are cropped at 16×16 boundaries from these canvases.

### Markings (applied on top of face tiles)

- `controller_front` → north face of controller parts
- `operating_panel_front` → north face of operating panel parts
- `power_port` → **all six faces** of power port parts
- `casing` → generic casing fallback (unused by current variant logic)

### Unformed Recipes (16×16 each)

One per role: `controller`, `casing`, `power_port`, `operating_panel`. Used for the `formed=false` variant and as fallback for `large=false, part≥12`.

## Output Assets

Generated under `src/main/resources/assets/lithmod/`:

```
textures/block/t4_<size>_<face>_tx<tx>_ty<ty>.png   # 16×16 tiles
textures/block/t4_marking_<name>.png                # marking overlays
textures/block/t4_unformed_<role>.png               # unformed bases
models/block/t4_<role>_<size>_part<P>_formed.json   # 45 (large) + 12 (small) per role
models/block/t4_<role>_unformed.json                # generic unformed cube_all
blockstates/t4_<blockId>.json                       # 5 blockstates, 4×2×45 variants each
models/item/t4_<blockId>.json                       # block item models
items/t4_<blockId>.json                             # client item refs
```

### Block IDs & Roles

| Block ID | Role |
|----------|------|
| `deposition_etcher` | controller |
| `advanced_test_package` | controller |
| `advanced_casing` | casing |
| `advanced_power_port` | power_port |
| `advanced_operating_panel` | operating_panel |

### Variant Selectors (per blockstate)

```
facing=north/east/south/west, formed=false                    → unformed model
facing=..., formed=true, large=true, part=0..44              → large formed part model
facing=..., formed=true, large=false, part=0..11             → small formed part model
facing=..., formed=true, large=false, part=12..44            → unformed (fallback)
```

Rotations: north=0°, east=90°, south=180°, west=270°.

## Validation Rules (enforced by generator)

- All palette colors are `#RRGGBB`.
- All recipe coordinates are integers within canvas bounds.
- Face canvases end fully opaque (alpha=255 everywhere).
- No duplicate output paths.
- Output paths stay under `src/main/resources/assets/lithmod/`.
- `--check` compares SHA-256 of every PNG/JSON; exits 1 on any mismatch.

## Preview

A preview atlas (`build/t4-panels-preview.png`) is written on normal runs (not on `--check`). It shows all 12 face canvases (large + small × 6 faces) with 16×16 tile grid overlays.

## Do Not

- Hand-edit generated PNG/JSON files — they are overwritten on regeneration.
- Delete files — the generator never deletes; coordinator owns cleanup.
- Add Java, crafting, loot, tags, lang — coordinator handles those separately.