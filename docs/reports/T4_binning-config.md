# T4 High Binning Configuration

**Date:** October 21, 2026
**Scope:** M1 pack — T4 binning config implementation
**Author:** Nemotron 3 Ultra (code), Beam (review pending)

## Summary

This document describes the high-binning configuration system for the Test & Package Gen 2 multiblock.
High binning promotes a completed, trusted source lot from one tier to the next by re-judging its wafers
against a tighter effective yield. The logic and compute families are supported; memory and NAND are refused.

## Architecture

### Core Rules (no Minecraft dependencies)
- `com.euvcraft.lithmod.core.HighBinningRules` — Pure logic for judging lots.
  - `Transition` record: `sourceTier`, `targetTier`, `yieldMultiplier`, `ticksMultiplier`, `energyMultiplier`
  - `judge(...)` validates structure, tier, cores, generation, target availability, and transition match.
  - Deterministic: uses source lot's original cumulative rolls and frozen effective yield × yield multiplier.
  - No new randomness; source lot never mutated.

### Data Layer (Minecraft/NeoForge)
- `HighBinningRecipe` — Record pairing `dieType` ("logic" | "compute") with a validated `Transition`.
  - Constructor enforces: family ∈ {logic, compute}, source ≥ 3, target = source + 1, logic target ≤ 5.
- `HighBinningDataLoader` — Extends `JsonResourceDataLoader<HighBinningRecipe, Registry>`.
  - Directory: `data/lithmod/high_binning/`
  - Codec fields: `die_type`, `source_tier`, `target_tier`, `yield_multiplier`, `ticks_multiplier`, `energy_multiplier`.
  - Reload semantics:
    - Missing config before first reload → no binning (empty `Optional`).
    - Valid empty reload → clears registry.
    - Failed reload → preserves previous valid registry.
    - Duplicate family/source in same reload → rejects whole prepared map.
  - Registry: immutable, keyed by `"family:sourceTier"`, exposes `forSource(String dieType, int sourceTier)`.
  - Registered via `@SubscribeEvent onAddServerReloadListeners(AddServerReloadListenersEvent)`.
  - No Java fallback recipes or default numbers.

## JSON Resources

### `data/lithmod/high_binning/logic_t3_t4.json`
```json
{
  "die_type": "logic",
  "source_tier": 3,
  "target_tier": 4,
  "yield_multiplier": 0.15,
  "ticks_multiplier": 2.0,
  "energy_multiplier": 2.0
}
```

### `data/lithmod/high_binning/compute_t3_t4.json`
```json
{
  "die_type": "compute",
  "source_tier": 3,
  "target_tier": 4,
  "yield_multiplier": 0.15,
  "ticks_multiplier": 2.0,
  "energy_multiplier": 2.0
}
```

**Field semantics:**
- `die_type`: `"logic"` or `"compute"` (memory/nand rejected).
- `source_tier`: ≥ 3 (T3 or higher).
- `target_tier`: must equal `source_tier + 1`.
- `yield_multiplier`: ∈ (0, 1] — penalty applied to source effective yield.
- `ticks_multiplier`: ≥ 1, ≤ 1000 — ceiling-scaled per-wafer tick cost.
- `energy_multiplier`: ≥ 1, ≤ 1000 — ceiling-scaled per-tick energy cost.

**Not included in this increment:**
- Memory/NAND families.
- T4→T5 transitions (logic T4→T5 is architecturally allowed but not configured yet; compute T4→T5 refused by core rules).
- Native caller checks target ChipCatalog existence, source PackagingRules, gen 2, and freezes plan/target/mode/costs at admission. Loader does not inspect nearby fab/masks/substrate.

## Validation Summary

| Check | Layer | Error Type |
|-------|-------|------------|
| die_type ∈ {logic, compute} | Recipe constructor | `IllegalArgumentException` |
| source_tier ≥ 3 | Recipe constructor | `IllegalArgumentException` |
| target_tier = source_tier + 1 | Transition constructor + Recipe | `IllegalArgumentException` |
| logic target ≤ 5 | Recipe constructor | `IllegalArgumentException` |
| yield_multiplier ∈ (0, 1] | Transition constructor | `IllegalArgumentException` |
| ticks_multiplier ∈ [1, 1000] | Transition constructor | `IllegalArgumentException` |
| energy_multiplier ∈ [1, 1000] | Transition constructor | `IllegalArgumentException` |
| Duplicate family/source in reload | `buildRegistry` | `IllegalArgumentException` |
| Missing config before first reload | Loader | returns empty `Optional` |
| Valid empty reload | Loader | clears registry |
| Failed reload | Loader | preserves previous registry |

## Unit Tests

**File:** `src/test/java/com/euvcraft/lithmod/mc/HighBinningRecipeCodecTest.java`

Coverage:
- Codec round-trip (encode/decode) for logic and compute families.
- Parse valid resource JSON for both families.
- Bad family: memory, nand, unknown, empty, null → rejected.
- Bad tiers: source < 3, target ≠ source+1, target < source → rejected.
- Logic target cap: T5 allowed, T6+ rejected; compute no cap.
- Yield multiplier bounds: 0, negative, >1, NaN, ±Infinity rejected; 1.0 and ε allowed.
- Cost multiplier bounds: <1, >1000 rejected; 1.0 and 1000.0 allowed.
- Duplicate family/source detection in `buildRegistry` → throws.
- Different families same source allowed; different sources same family allowed.
- Immutable publication: returned collections unmodifiable.
- Empty registry: size 0, `forSource` returns empty.
- Missing required JSON fields → decode fails.
- Unknown JSON field → ignored (lenient codec).
- Empty map to `buildRegistry` → empty registry (tests empty-reload behavior without bootstrap).

**No Minecraft Item registry access in any test.**

## Integration Points (Coordinator Wires Later)

- `EUVCRAFTMod` registers `HighBinningDataLoader` listener in `onAddServerReloadListeners`.
- Native caller (Test & Package Gen 2 block entity) will:
  1. Obtain `Optional<Registry>` from `HighBinningDataLoader.getRegistry()`.
  2. Call `registry.forSource(dieType, sourceTier)` to get `Optional<HighBinningRecipe>`.
  3. Validate target chip exists in ChipCatalog, source PackagingRules, gen ≥ 2.
  4. Freeze plan, target, mode, costs at admission via `PackagingRun.Snapshot`.
  5. Call `HighBinningRules.judge(...)` with the transition from the recipe.

## Limitations & Open Items

1. **No GameTests** — Unit tests only; GameTest coverage deferred to coordinator.
2. **No T4→T5 resource files** — Logic T4→T5 architecturally allowed but not configured; compute T4→T5 refused by core rules.
3. **No fallback defaults** — Missing config = no binning (fail closed).
4. **Identifier API** — Uses `net.minecraft.resources.Identifier` (26.3), not `ResourceLocation`.
5. **Core-count validation** — Enforced by `HighBinningRules.Transition` constructor and `HighBinningRules.judge` at admission; `HighBinningRecipe` does not re-validate cores.
6. **Test execution pending coordinator** — No invented passing claims.

## Files in Scope

```
src/main/java/com/euvcraft/lithmod/mc/recipe/HighBinningRecipe.java
src/main/java/com/euvcraft/lithmod/mc/data/HighBinningDataLoader.java
src/main/resources/data/lithmod/high_binning/logic_t3_t4.json
src/main/resources/data/lithmod/high_binning/compute_t3_t4.json
src/test/java/com/euvcraft/lithmod/mc/HighBinningRecipeCodecTest.java
docs/reports/T4_binning-config.md
```