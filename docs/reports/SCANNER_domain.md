# Scanner Domain Implementation Report

## Overview

This document describes the pure domain implementation for the EUV Scanner multiblock structure and beam alignment system. All code is Minecraft-agnostic and uses only core Java types.

---

## Files Implemented

### 1. `BlockKind.java` (Extended)

**Location:** `src/main/java/com/euvcraft/lithmod/core/multiblock/BlockKind.java`

**Changes:** Added six new enum values for scanner-specific blocks:

| Enum Value | Purpose |
|------------|---------|
| `CALIBRATION_PANEL` | Exactly one required in scanner middle layer |
| `EXPOSURE_MODULE` | One of five modules, exactly one required in middle layer |
| `OPTICS_MODULE` | One of five modules, exactly one required in middle layer |
| `STAGE_MODULE` | One of five modules, exactly one required in middle layer |
| `FILTRATION_MODULE` | One of five modules, exactly one required in middle layer |
| `METROLOGY_MODULE` | One of five modules, exactly one required in middle layer |

These are appended to preserve existing ordinals for serialization stability.

---

### 2. `ScannerStructureMatcher.java`

**Location:** `src/main/java/com/euvcraft/lithmod/core/multiblock/ScannerStructureMatcher.java`

**Purpose:** Matches a 3×3×5 EUV scanner multiblock structure against a world lookup function.

#### Structure Definition

- **Shape:** Fixed `StructureShape(3, 3, 5)` = 45 blocks
- **Controller:** Bottom-front face (d=0, v=0), any column u ∈ {0,1,2}
- **Operating Panel:** Directly above controller at (u₀, 1, 0) — middle layer front face
- **Middle Layer (v=1):** Must contain exactly:
  - 1 × `CALIBRATION_PANEL`
  - 1 × `EXPOSURE_MODULE`
  - 1 × `OPTICS_MODULE`
  - 1 × `STAGE_MODULE`
  - 1 × `FILTRATION_MODULE`
  - 1 × `METROLOGY_MODULE`
  - (6 special blocks across the 15 middle-layer cells; remaining 9 are `CASING`)
- **Power Ports:** 1 or 2 × `POWER_PORT` anywhere in remaining 39 cells
- **All Other Cells:** `CASING`
- **Forbidden:** `WATER_INLET`, `OTHER`, extra controllers, extra operating panels, modules/calibration on top (v=2) or bottom (v=0) layers

#### Matching Algorithm

1. Tries three anchor columns deterministically: `u0 = 0, 1, 2` (with `v0 = 0` fixed)
2. First full match wins
3. Returns `MatchResult` with 45 member positions in canonical order: `u*15 + v*5 + d` (u outer, v middle, d inner)

#### Public API

```java
public static MatchResult match(
    MultiblockPos controllerPos,
    MultiblockDirection facing,
    Function<MultiblockPos, BlockKind> blockKindAt
)
```

- **Parameters:**
  - `controllerPos`: World position of the controller block (anchor)
  - `facing`: Horizontal facing (NORTH, SOUTH, EAST, WEST)
  - `blockKindAt`: Lookup function; non-structure blocks must return `BlockKind.OTHER`
- **Returns:** `MatchResult` (success with 45 positions, or failure with reason)

#### Coordinate System

For facing `f`:
- **back** = `-f` (structure extends away from controller face)
- **right** = `f` rotated 90° clockwise (seen from above)
- **up** = `+Y`

Box origin = `controller - u₀×right - v₀×up`
Cell `(u, v, d)` = `origin + u×right + v×up + d×back`

#### Error Messages

| Condition | Message |
|-----------|---------|
| Non-structure block | `"wrong block at [x,y,z]: expected structure block"` |
| Water inlet | `"water inlet not allowed in scanner structure at [x,y,z]"` |
| Extra controller | `"extra controller at [x,y,z]"` |
| Operating panel wrong pos | `"operating panel at wrong position [x,y,z]"` |
| Calibration panel wrong pos | `"calibration panel at wrong position [x,y,z] (must be in middle layer, not at panel position)"` |
| Module wrong layer | `"{MODULE_KIND} at wrong position [x,y,z] (modules must be in middle layer, not at panel position)"` |
| Missing calibration panel | `"expected exactly one calibration panel in middle layer, found N"` |
| Missing module | `"expected exactly one {MODULE_KIND} in middle layer, found N"` |
| Power port count | `"expected at least one power port, found N"` / `"expected at most two power ports, found N"` |
| Missing operating panel | `"expected exactly one operating panel above controller, found N"` |
| No valid anchor | `"no valid anchor column found"` |

---

### 3. `BeamAlignment.java`

**Location:** `src/main/java/com/euvcraft/lithmod/core/BeamAlignment.java`

**Purpose:** Immutable pure record representing beam alignment state with deterministic adjustment and quality metrics.

#### State

| Field | Type | Range | Description |
|-------|------|-------|-------------|
| `x` | `int` | [-10, 10] | X knob position |
| `y` | `int` | [-10, 10] | Y knob position |
| `focus` | `int` | [-10, 10] | Focus knob position |
| `targetX` | `int` | [-5, 5] | Target X position |
| `targetY` | `int` | [-5, 5] | Target Y position |
| `targetFocus` | `int` | [-5, 5] | Target focus position |

#### Constants

```java
public static final int KNOB_MIN = -10;
public static final int KNOB_MAX = 10;
public static final int TARGET_MIN = -5;
public static final int TARGET_MAX = 5;
```

#### Public API

##### `static BeamAlignment create(long seed)`

Deterministically generates a `BeamAlignment` from a seed using `java.util.Random`. All six values are generated within their respective bounds.

##### `BeamAlignment adjust(int control)`

Returns a new `BeamAlignment` with one knob adjusted. Controls:

| Control | Action |
|---------|--------|
| 0 | `x--` (clamped to -10) |
| 1 | `x++` (clamped to 10) |
| 2 | `y--` (clamped to -10) |
| 3 | `y++` (clamped to 10) |
| 4 | `focus--` (clamped to -10) |
| 5 | `focus++` (clamped to 10) |

Invalid controls (outside 0..5) return `this` unchanged. Targets never change.

##### `int spotX()`

Computes X spot offset: `2*(x - targetX) + (y - targetY)`

##### `int spotY()`

Computes Y spot offset: `(x - targetX) - 2*(y - targetY)`

##### `int focusError()`

Computes focus error: `abs(focus - targetFocus)`

##### `int quality()`

Computes alignment quality in [0, 100]:

```
raw = 100 - 4*(|spotX| + |spotY|) - 10*focusError
quality = clamp(raw, 0, 100)
```

##### `double multiplier(double min, double max)`

Interpolates multiplier based on quality:

```
multiplier = min + (max - min) * quality / 100.0
```

**Validation:** Throws `IllegalArgumentException` if:
- `min` or `max` is not finite (NaN or ±Infinity)
- `max < min`

---

### 4. `BeamAlignmentTest.java`

**Location:** `src/test/java/com/euvcraft/lithmod/core/BeamAlignmentTest.java`

**Coverage:**

| Test Category | Tests |
|---------------|-------|
| Perfect alignment | `quality == 100`, `spotX == 0`, `spotY == 0`, `focusError == 0` |
| Bad alignment | Quality clamps to 0, spot/focus errors non-zero |
| Coupled controls | `spotX` and `spotY` depend on both `x` and `y`; `focus` independent |
| Bounds enforcement | Knobs clamp at -10/10; invalid controls no-op; targets immutable |
| Determinism | `create(seed)` identical for same seed; `adjust` chains deterministic |
| Finite parameters | `multiplier` rejects NaN, ±Infinity, `max < min`; accepts valid ranges |

---

### 5. `ScannerStructureMatcherTest.java`

**Location:** `src/test/java/com/euvcraft/lithmod/core/multiblock/ScannerStructureMatcherTest.java`

**Coverage:**

| Test Category | Tests |
|---------------|-------|
| Full rotations | All 4 facings × 3 columns = 12 successful matches |
| Canonical order | 45 members in `u*15 + v*5 + d` order |
| Power ports | Accepts 1 port; accepts 2 ports; rejects 0; rejects 3 |
| Middle layer permutations | 5 permutations × 3 columns × 4 facings of module/calibration placement |
| Invalid counts | Missing/extra calibration panel; missing/extra each module; extra controller; extra operating panel |
| Layer violations | Modules/calibration on bottom (v=0) or top (v=2) layer rejected |
| Water/foreign | `WATER_INLET` rejected; `OTHER` rejected |
| Operating panel position | Missing panel rejected; panel at back face rejected |
| Determinism | Repeated matches identical |
| Geometry invariants | Controller in box; no duplicate cells |

**Geometry Oracle:** Test expectations use hand-written basis vectors (not production code) to avoid mirroring bugs:
- `facingVector`: NORTH=(0,0,-1), SOUTH=(0,0,1), EAST=(1,0,0), WEST=(-1,0,0)
- `rightVector`: NORTH=(1,0,0), SOUTH=(-1,0,0), EAST=(0,0,1), WEST=(0,0,-1)
- `backVector = -facingVector`
- `up = (0,1,0)`

---

## Design Decisions

### 1. Position Type: `MultiblockPos`

Uses the existing `MultiblockPos` record (x, y, z ints) rather than Minecraft's `BlockPos`. This keeps the domain pure and testable without Minecraft dependencies.

### 2. Fixed Shape Constant

`ScannerStructureMatcher.SCANNER_SHAPE = new StructureShape(3, 3, 5)` exposes the shape for tests and potential future configurability.

### 3. Module Array

The five module kinds are defined in a fixed array `MODULE_KINDS` for iteration. Order is: EXPOSURE, OPTICS, STAGE, FILTRATION, METROLOGY. This order is arbitrary but stable.

### 4. Middle Layer Index

Hardcoded as `v = 1` (0-indexed: bottom=0, middle=1, top=2). This matches the 3-height shape where the operating panel also sits at v=1 (directly above controller at v=0).

### 5. Canonical Member Order

Matches `StructureMatcher` and `PanelStructureMatcher`: u outer, v middle, d inner. Index formula: `u * (height * length) + v * length + d` = `u*15 + v*5 + d`.

### 6. Deterministic `create(seed)`

Uses `java.util.Random(seed)` which is specified to produce identical sequences across Java implementations. Six `nextInt` calls generate the state.

### 7. `adjust` Returns New Instance

`BeamAlignment` is a record (immutable). `adjust` returns a new record with clamped values. This is pure functional style.

### 8. `multiplier` Validation

Explicitly rejects non-finite `min`/`max` and `max < min` per requirements. Does not validate that the result is finite (it always is if inputs are valid).

### 9. No Validation in Record Constructor

Per requirements: "No validation claims; coordinator runs tests." The record constructor does not validate ranges. `create()` and `adjust()` guarantee valid state. Callers constructing records directly are responsible for validity.

---

## Limitations & Assumptions

1. **No Minecraft Integration:** This is pure domain logic. Block-to-`BlockKind` mapping and world access are external concerns.

2. **Fixed Shape:** The 3×3×5 shape is hardcoded. Extending to other sizes would require a new matcher or parameterization.

3. **Module Set Fixed:** The five module kinds are fixed. Adding/removing modules requires code changes.

4. **Single Calibration Panel:** Exactly one required. No support for multiple or zero.

5. **Power Port Placement:** Anywhere in the 45 cells except the 7 reserved (controller, panel, calibration, 5 modules). No restrictions on layer or face.

6. **Operating Panel Position:** Fixed at `(u₀, 1, 0)` — directly above controller on middle layer front face. This overlaps with the middle layer cell budget (15 cells - 1 panel - 6 special = 8 casing in middle layer).

7. **No Rotation of Modules:** Modules are not oriented; they are just block kinds at positions.

8. **Seed Determinism:** `create(seed)` uses `Random` which is implementation-specified but stable within a JVM version. Cross-JVM reproducibility is not guaranteed by spec but works in practice.

8. **Quality Formula:** The coefficients (4, 10, 100) are hardcoded. Changing them requires code modification.

9. **Multiplier Range:** No clamping of output beyond the `[min, max]` interpolation. Quality is already clamped to [0, 100].

---

## Testing Notes

- All tests use the geometry oracle (hand-written basis vectors) to avoid mirroring production bugs.
- Tests do not call the matcher to build expected positions.
- Deterministic shuffling in test fixtures uses fixed seeds for reproducibility.
- The test for "controller on top row" is documented but not executable since the matcher only tries `v0=0`.

---

## Future Considerations

- If additional scanner variants are needed (different sizes, module sets), consider a parameterized `ScannerStructureMatcher` or a builder pattern.
- The `BlockKind` enum grows with each machine type. Consider a registry-based approach if the set becomes large.
- `BeamAlignment` could be extended with a `withTargets(...)` method for external target updates (currently targets are immutable after creation).