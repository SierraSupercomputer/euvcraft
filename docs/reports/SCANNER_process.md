# SCANNER Process Integration — Frozen Calibration Yield Factor

**Date:** 2026-10-21  
**Status:** Implemented (M1 base-process refactor)

---

## Summary

This document describes the base-process refactor that enables the T4 Scanner to freeze a calibration quality multiplier at run admission. The multiplier is applied **once** to the first layer's effective yield, then preserved in the `RunSpec` for all subsequent layers and for packaging/highbin.

No new Scanner native block entity files were created (coordinator work). Only the shared `ProcessCellControllerBlockEntity` base class and core yield engine were extended with protected hooks.

---

## Changes

### 1. `ProcessCellControllerBlockEntity` (base class)

**New protected constructor**
```java
protected ProcessCellControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int inputSlots)
```
Existing public 8-slot constructor delegates to this.

**Equipment access hook (for Scanner override)**
```java
protected ItemStack equipment(int logicalSlot)
```
Base reads from real container slots 3..7. Scanner subclass overrides to provide physical module item views using only 3 actual inventory slots. **Scanner must never write phantom module stacks into the real container.**

**Admission hooks (for Scanner override)**
```java
protected boolean supportsTier(Tier tier)           // default: tier <= 3
protected boolean readyForAdmission()               // default: true
protected double admissionYieldMultiplier()         // default: 1.0
protected String admissionRefusal()                 // default: ""
```
- `supportsTier` enforced on **both** initial admission and pending contract restore.
- `readyForAdmission` + `admissionRefusal` gate admission after structural formation.
- `admissionYieldMultiplier` returns the frozen calibration factor (finite, 0 < factor ≤ 1).
- Base cell uses `structureFormed()` (virtual) instead of private `formed` field.

**Active run predicate**
```java
public boolean hasActiveRun()
```
Returns `true` when a run is active and not finished. Scanner uses this to lock calibration panel during a run.

**Updated `tryStartRun()`**
- Calls `structureFormed()`, `supportsTier()`, `readyForAdmission()`, `admissionRefusal()` before proceeding.
- Reads equipment via `equipment(slot)` instead of direct `slot()`.
- Passes `admissionYieldMultiplier()` to `Contract` constructor.

**Updated pending restore**
- Enforces `supportsTier(contract.tier())` on restore; invalidates run if unsupported.

---

### 2. `ProcessCellRunPersistence.Contract`

**New record component**
```java
double frozenYieldFactor
```

**Constructors**
- 16-arg canonical constructor (includes `frozenYieldFactor`).
- 15-arg legacy (defaults `frozenYieldFactor = 1.0`).
- 14-arg legacy (defaults grade = ORDINARY, `frozenYieldFactor = 1.0`).
- 13-arg legacy (defaults grade = ORDINARY, prep = 1, `frozenYieldFactor = 1.0`).

**Validation**
- `frozenYieldFactor` must be finite, strictly positive, ≤ 1.0.

**Codec migration**
- `frozenYieldFactor` is an optional codec field with default `1.0` for backward compatibility with existing saves.

**`request(FoupContents)`**
- Passes `frozenYieldFactor` into the reconstructed `ProcessRunRequest`.

---

### 3. `ProcessRunRequest`

**New record component**
```java
double frozenYieldFactor
```

**Constructors**
- 13-arg canonical constructor (includes `frozenYieldFactor`, validated finite 0 < factor ≤ 1).
- 12-arg legacy (defaults `frozenYieldFactor = 1.0`).

---

### 4. `YieldRollEngine.applyLayerStep` (core, no Minecraft deps)

**New overload**
```java
public static FoupContents applyLayerStep(
    FoupContents lot, Tier tier, String dieType, Integer cores,
    int layerNumber, double defectFactor, FabSettings settings,
    double frozenYieldFactor, RollSource rolls)
```

**Behavior**
- **First layer only:** computes `baseEffectiveYield = YieldMath.resolveEffectiveYield(...)`, then `finalEffectiveYield = clampYield(baseEffectiveYield * frozenYieldFactor)`. Creates `RunSpec` with `finalEffectiveYield`.
- **Later layers:** ignores `frozenYieldFactor`; uses `spec.effectiveYield()` (already factored) for step weight calibration.
- **Never** multiplies `defectFactor` by `frozenYieldFactor` (nonlinear, wrong).
- **Never** applies factor per layer.
- **Never** double-applies on resume (spec match uses stored effective yield).

**Backward-compatible overload** (original signature) delegates with `frozenYieldFactor = 1.0`.

---

### 5. `FabYieldService` (Minecraft-facing)

**New overloads**
```java
public static FoupContents applyLayerStep(
    ServerLevel level, FoupContents lot, Tier tier, String dieType,
    @Nullable Integer cores, int layerNumber, double defectFactor,
    double frozenYieldFactor)

public static FoupContents applyLayerStep(
    ServerLevel level, FoupContents lot, Tier tier, String dieType,
    @Nullable Integer cores, int layerNumber, double defectFactor,
    double frozenYieldFactor, FabSettings settings)
```
- Validate `frozenYieldFactor` (finite, 0 < factor ≤ 1).
- Delegate to `YieldRollEngine` overload.
- Preserve all existing overloads and callers.

---

### 6. `ProcessCellControllerBlockEntity.applyLayer`

Updated to read `contract.frozenYieldFactor()` and pass it to `FabYieldService.applyLayerStep(...)`.

---

## Data Flow

```
Scanner Calibration Panel
         │
         ▼ (player completes alignment)
    quality score 0..100
         │
         ▼ (interpolation: 50→0.75, 100→1.0)
    frozenYieldFactor ∈ (0, 1]
         │
         ▼ (at run admission)
ProcessCellControllerBlockEntity.tryStartRun()
         │
         ├── supportsTier(tier) ✓
         ├── readyForAdmission() ✓
         ├── admissionRefusal() == "" ✓
         │
         ▼
admissionYieldMultiplier() → frozenYieldFactor
         │
         ▼
ProcessCellRunPersistence.Contract(..., frozenYieldFactor)
         │
         ▼ (persisted to world save)
Codec: optional field, default 1.0
         │
         ▼ (on load / resume)
Contract.request(lot) → ProcessRunRequest(..., frozenYieldFactor)
         │
         ▼
YieldRollEngine.applyLayerStep(..., frozenYieldFactor)
         │
         ├── layer 1: effectiveYield = clamp(baseYield * frozenYieldFactor) → stored in RunSpec
         ├── layer 2..N: use RunSpec.effectiveYield() (already includes factor)
         │
         ▼
RunSpec.effectiveYield() used for:
    • all layer step weights
    • evaluateLot() pass/fail threshold
    • packaging / highbin yield calculations
```

---

## Invariants

| Invariant | Enforcement |
|-----------|-------------|
| Factor applied exactly once | Only at `layerNumber == 1 && spec == null` |
| Factor never multiplies `defectFactor` | Separate parameters; weight = `stepWeight(rollCount, effectiveYield, defectFactor)` |
| Factor never re-applied on resume | Later layers read `spec.effectiveYield()` |
| Factor preserved for packaging/highbin | `RunSpec.effectiveYield()` is final value |
| Backward compatibility | All legacy constructors default factor = 1.0; codec optional with default 1.0 |
| No phantom stacks in real container | `equipment(int)` returns copies; Scanner override uses physical module views |

---

## Testing

**`ScannerYieldFactorTest`** (`src/test/java/com/euvcraft/lithmod/mc/ScannerYieldFactorTest.java`)

| Test Group | Coverage |
|------------|----------|
| `FirstLayer` | factor 1.0 no-op; factor reduces effective yield; factor applies once not per layer; statistical pass rate at factor 0.5; validation rejects out-of-range |
| `Resume` | pause/resume does not re-apply factor; different factor at resume ignored |
| `DefectFactorSeparation` | defectFactor and frozen factor combine correctly (multiplicative on yield); they produce different outcomes |
| `FabYieldServiceOverloads` | overload existence and delegation |
| `ProcessRunRequestCarriesFactor` | validation; backward-compatible default |

Run with:
```bash
./gradlew test --tests ScannerYieldFactorTest
```

---

## Limitations & Out-of-Scope

| Item | Reason |
|------|--------|
| Scanner native block entity (`ScannerControllerBlockEntity`) | Coordinator work; this refactor only provides base-class hooks |
| Calibration panel UI / alignment puzzle | Separate M1 increment |
| Calibration persistence (hardware identity, alignment state) | Scanner native work |
| Calibration gating (lock during active run) | Scanner uses `hasActiveRun()` hook |
| T4 machine JSON / recipes / modules | Separate M1 increment |
| Diagnostic tuning, mask-triggered calibration, cleanroom quality | Future progression (OWNER_SPEC.md) |

---

## Migration Notes

- **Existing worlds:** No migration needed. Codec reads `frozenYieldFactor` as optional with default `1.0`. All existing saves deserialize with factor = 1.0 (no calibration effect).
- **Existing callers:** All legacy constructors and overloads preserved. `ProcessRun.start(request)` works unchanged because `ProcessRunRequest` 12-arg constructor defaults factor = 1.0.
- **T1–T3 cells:** Unaffected. `supportsTier` defaults to `tier <= 3`, `admissionYieldMultiplier` defaults to `1.0`, `readyForAdmission` defaults to `true`, `admissionRefusal` defaults to `""`.
- **T4 Scanner:** Subclass overrides the four admission hooks and `equipment(int)` to implement calibration-gated admission and physical module views.

---

## Files Modified

| File | Role |
|------|------|
| `src/main/java/com/euvcraft/lithmod/mc/multiblock/ProcessCellControllerBlockEntity.java` | Base controller: constructor, equipment hook, admission hooks, active run predicate, updated `tryStartRun`/`applyLayer` |
| `src/main/java/com/euvcraft/lithmod/mc/machine/process/ProcessCellRunPersistence.java` | Contract record + codec: `frozenYieldFactor` field, validation, migration default |
| `src/main/java/com/euvcraft/lithmod/core/ProcessRunRequest.java` | Request record: `frozenYieldFactor` field, validation, backward-compat constructor |
| `src/main/java/com/euvcraft/lithmod/core/YieldRollEngine.java` | Core engine: new `applyLayerStep` overload with frozen factor logic |
| `src/main/java/com/euvcraft/lithmod/mc/FabYieldService.java` | Minecraft-facing: new overloads with frozen factor, validation, delegation |
| `src/test/java/com/euvcraft/lithmod/mc/ScannerYieldFactorTest.java` | Regression tests for frozen factor math and resume semantics |
| `docs/reports/SCANNER_process.md` | This document |

---

## Acceptance Checklist

- [x] `ProcessCellControllerBlockEntity` protected constructor with 4 args
- [x] `equipment(int logicalSlot)` protected hook
- [x] `supportsTier(Tier)` default `tier <= 3`, enforced on admission + restore
- [x] `readyForAdmission()` default `true`
- [x] `admissionYieldMultiplier()` default `1.0`, finite (0, 1]
- [x] `admissionRefusal()` default `""`
- [x] Admission uses `structureFormed()` (virtual) not private `formed`
- [x] `hasActiveRun()` public predicate
- [x] `Contract.frozenYieldFactor` with validation, default 1.0, codec optional default 1.0
- [x] `ProcessRunRequest.frozenYieldFactor` with validation, default 1.0
- [x] `YieldRollEngine.applyLayerStep` overload applies factor **once** at first layer
- [x] Factor stored in `RunSpec.effectiveYield()` for all layers + packaging
- [x] No defectFactor multiplication, no per-layer application, no double-apply on resume
- [x] `FabYieldService` overloads with validation, preserve existing callers
- [x] `ScannerYieldFactorTest` with real core/service math
- [x] No new Scanner native files (coordinator responsibility)
- [x] No test deletions
- [x] Backward compatibility for all existing constructors and saves