# T3 Substrate Foundation

## Overview

This milestone implements the substrate foundation for the EUVCRAFT mod's T3 silicon chain. It introduces a **wafer substrate grade** system that distinguishes between **ordinary silicon wafers** (existing) and **pure silicon wafers** (new, produced by a later machine from ordinary boules and water).

This is a **phase1sealed-lot** substrate foundation only — not the whole T3 chain. No T3 item registrations, machines, or admission logic are included here.

## Design Decisions

### 1. WaferSubstrate Enum

A new `WaferSubstrate` enum in `com.euvcraft.lithmod.core` with two values:

| Substrate | Stable ID | Generation |
|-----------|-----------|------------|
| `ORDINARY` | `"ordinary"` | 1 |
| `PURE` | `"pure"` | 2 |

- **Stable IDs** are permanent and used in saved data and network packets.
- **Generation numbers** increment when new substrates are introduced.
- Helper methods: `fromId(String)` (case-insensitive, returns null for unknown), `fromIdOrDefault(String)` (defaults to ORDINARY), `fromIdOrThrow(String)` (throws for invalid non-null ID).

### 2. FoupContents Grade Field

The `FoupContents` record now carries a `WaferSubstrate grade` field (5th component).

**Backward compatibility:**
- Legacy 3-arg constructor `FoupContents(UUID, int, List<Wafer>)` → defaults to ORDINARY
- Legacy 4-arg constructor `FoupContents(UUID, int, List<Wafer>, RunSpec)` → defaults to ORDINARY
- New 4-arg constructor `FoupContents(UUID, int, List<Wafer>, WaferSubstrate)` for explicit grade
- New 5-arg canonical constructor includes grade
- `empty()` overloads: default ORDINARY, or explicit grade

**All immutable mutations preserve grade:**
- `withWafer(Wafer)`
- `withWafers(List<Wafer>)`
- `withoutWaferAt(int)`
- `withRun(RunSpec)`

### 3. FoupPublicView Grade Field

The client-safe `FoupPublicView` record now includes a 4th component: `WaferSubstrate grade`.

**Backward compatibility:**
- Legacy 3-arg constructor `FoupPublicView(int, int, int)` → defaults to ORDINARY
- New 4-arg canonical constructor includes grade
- `empty()` overloads: default ORDINARY, or explicit grade

### 4. YieldRollEngine Preserves Grade

`YieldRollEngine.applyLayerStep()` now constructs the new lot with `lot.grade()`, preserving the substrate through every layer application.

### 5. WaferSlicing Explicit Grade Overload

- Legacy `wafersFor(FabSettings, int)` → defaults to ORDINARY
- New overload `wafersFor(FabSettings, int, WaferSubstrate)` for explicit grade (used by future pure wafer machine)

### 6. Persistent Codec (NBT)

**New field:** `grade` (String, optional, key `"grade"`)

- **Missing field** → defaults to ORDINARY (backward compatibility with existing saves)
- **Present & valid** (`"ordinary"` or `"pure"`) → uses that grade
- **Present & invalid** → **rejects** decode (does not silently upgrade)

The field is omitted when encoding an ORDINARY lot (to keep NBT smaller and compatible with old readers that ignore unknown fields).

### 7. Network Codec (StreamCodec)

**New wire format:** 4 VAR_INTs + 1 STRING_UTF8
- `capacity` (VAR_INT)
- `waferCount` (VAR_INT)
- `lotLayer` (VAR_INT)
- `gradeId` (STRING_UTF8) — **new field**

**Decoding:**
- Invalid/unknown `gradeId` → clamped to ORDINARY (display value, never drops connection)
- Nil UUID placeholder (`CLIENT_VIEW_LOT_ID`) remains untrusted
- `trustedContentsOf()` still rejects client views
- Legacy `fromPublicView(int, int, int)` preserved for all existing callers (defaults grade to ORDINARY)

### 8. Tooltip

`FoupTooltip` now shows:
- **Empty lot:** "Empty (not pure wafers)"
- **Non-empty ordinary:** "Ordinary silicon" + "Unprocessed" (layer 0) or "Processed: N layer(s)" (layer > 0)
- **Non-empty pure:** "Pure silicon" + progress wording

Existing wafer count/capacity/layer lines unchanged.

### 9. Translation Keys Added

| Key | English |
|-----|---------|
| `tooltip.lithmod.foup.empty` | Empty (not pure wafers) |
| `tooltip.lithmod.foup.substrate.ordinary` | Ordinary silicon |
| `tooltip.lithmod.foup.substrate.pure` | Pure silicon |
| `tooltip.lithmod.foup.unprocessed` | Unprocessed |
| `tooltip.lithmod.foup.processed` | Processed: %s layer(s) |

## Testing

### Core Tests (`FoupSubstrateTest.java`)
- Enum stable IDs and generations
- `fromId` case-insensitive lookup
- `fromIdOrDefault` / `fromIdOrThrow` behavior
- Legacy constructors default to ORDINARY
- Explicit grade constructors
- All mutation methods preserve grade
- `publicView()` includes grade
- `FoupPublicView` legacy constructor defaults
- `WaferSlicing` legacy API and explicit overload

### Codec Tests (`FoupSubstrateCodecTest.java`)
- Persistent codec preserves ORDINARY and PURE grades
- Missing grade field defaults to ORDINARY
- Invalid explicit grade rejected (not silently upgraded)
- Network codec includes grade in wire format
- Invalid network grade clamped to ORDINARY
- Legacy `fromPublicView(int,int,int)` works and defaults grade
- Network round-trip stability with PURE grade
- Privacy invariance: different hidden rolls/calibration → identical public fields including grade
- Client view placeholder still rejected by `trustedContentsOf()`
- Malformed grade strings clamped to ORDINARY
- Packet size increase for grade field is minimal

### Existing Codec Tests Updated (`FoupContentsCodecTest.java`)
- All existing tests retained, no deleted/loosened assertions
- Updated to use `FoupPublicView` 4-arg constructor where needed
- Added tests for PURE grade persistence and NBT shape
- Packet size expectations updated for new field

### Game Tests (`FoupGameTests.java`)
- Existing game tests continue to pass (backward compatibility)
- No new game tests required for this foundation layer

## Migration Notes

### Existing Saves
- Lots saved before this change **lack the grade field** → decoded as ORDINARY ✓
- No data loss, no silent upgrades

### Existing Code Calling Legacy Constructors
- `new FoupContents(UUID, int, List<Wafer>)` → works, grade = ORDINARY
- `new FoupContents(UUID, int, List<Wafer>, RunSpec)` → works, grade = ORDINARY
- `new FoupPublicView(int, int, int)` → works, grade = ORDINARY
- `FoupContentsCodec.fromPublicView(int, int, int)` → works, grade = ORDINARY

### Network Compatibility
- **Server → Client:** Old clients (without grade field in STREAM_CODEC) would fail to decode. This is a **breaking network protocol change** requiring coordinated client/server update.
- **Client → Server:** Not applicable (client never sends FOUP contents)

## Scope Boundary

**Included:**
- `WaferSubstrate` enum
- Grade field in `FoupContents`, `FoupPublicView`, `YieldRollEngine`, `WaferSlicing`
- Persistent and network codec support
- Tooltip updates
- Core and codec unit tests
- Documentation

**Explicitly Excluded (future work):**
- Pure boule/water → pure boule machine
- Pure wafer slicing machine integration
- T3 item registrations
- T3 machine admission logic
- Mask design public projection
- RunSpec/calibration/UUID/flags/rolls public exposure

## Files Changed

### New Files
- `src/main/java/com/euvcraft/lithmod/core/WaferSubstrate.java`
- `src/test/java/com/euvcraft/lithmod/core/FoupSubstrateTest.java`
- `src/test/java/com/euvcraft/lithmod/mc/FoupSubstrateCodecTest.java`
- `docs/reports/T3_substrate-foundation.md`

### Modified Files
- `src/main/java/com/euvcraft/lithmod/core/FoupContents.java`
- `src/main/java/com/euvcraft/lithmod/core/FoupPublicView.java`
- `src/main/java/com/euvcraft/lithmod/core/YieldRollEngine.java`
- `src/main/java/com/euvcraft/lithmod/core/WaferSlicing.java`
- `src/main/java/com/euvcraft/lithmod/mc/FoupContentsCodec.java`
- `src/main/java/com/euvcraft/lithmod/mc/FoupTooltip.java`
- `src/main/resources/assets/lithmod/lang/en_us.json`
- `src/test/java/com/euvcraft/lithmod/mc/FoupContentsCodecTest.java`

## Verification

All tests pass:
- Core substrate tests
- Codec substrate tests
- Updated existing codec tests
- No regressions in existing functionality

The implementation follows the principle: **default to ORDINARY for backward compatibility, reject invalid explicit grades, clamp invalid network grades.**