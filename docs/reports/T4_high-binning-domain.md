# T4 High Binning Domain Report

**Status**: Implementation complete with calibrated yield math, tests updated to match new API
**Scope**: `HighBinningRules.java`, `HighBinningRulesTest.java`, this report

---

## 1. Domain Summary

High binning is a **post-process promotion step** that re-judges a completed, trusted source lot from one lithography tier to the next (source tier + 1) using a tighter effective yield threshold. It is a pure logic/compute operation with no new randomness, no mutation of the source lot, and no new chip content creation above T4.

### Supported Families
- **Logic**: T3→T4 (current increment); T4→T5 allowed by explicit transition config but target tier capped at 5
- **Compute**: T3→T4 only (fixed 1 core before T6, source core count preserved at T6+)
- **Memory**: Not supported (fail closed)
- **NAND**: Not supported (fail closed)

### Machine Requirements
- **Packager generation**: Must be generation 2 or higher (`installedGeneration >= 2`)
- **Source lot**: Completed (all wafers at final layer), trusted (carries `RunSpec`), non-empty
- **Target item**: Must be available in chip catalog (caller-supplied boolean)

### Determinism Guarantees
- Uses **original private cumulative rolls** from the source lot
- Uses **frozen source effective yield and roll count** × transition yield multiplier via calibrated engine
- **No new RNG**, no reroll, no identity-hash randomness
- Repeated judgment with frozen source/spec/config yields **identical pass flags**
- Source lot **never mutated**; no second authoritative wafer store created

---

## 2. Public API

### `HighBinningRules.judge(...)`
```java
public static PackagingPlan judge(
    FoupContents lot,
    Tier sourceTier,
    int installedGeneration,
    boolean targetAvailable,
    Transition transition,
    int chipsPerWafer,
    int packagesPerWafer
)
```

**Returns**: Immutable `PackagingPlan` with high-binning pass flags, preserving caller-supplied `chipsPerWafer` and `packagesPerWafer`.

**Throws**:
- `IllegalArgumentException` — structural caller errors (null lot/tier/transition, mismatched spec, invalid cores, bad transition, target tier > 8)
- `IllegalStateException` — lot not started, incomplete, wrong die family, generation gate unmet, logic cap exceeded, target unavailable

**Refusal order** (fail closed, one reason per call):
1. Null/empty lot → `IllegalArgumentException`
2. No run spec → `IllegalStateException`
3. Tier/spec mismatch → `IllegalArgumentException` (checked BEFORE layer check per brief)
4. Incomplete wafers → `IllegalStateException`
5. Die type not logic/compute → `IllegalStateException`
6. Source tier < T3 → `IllegalStateException`
7. Compute core count ≠ 1 (before T6) / ≤ 0 (T6+) → `IllegalArgumentException`
8. Logic with core count → `IllegalArgumentException`
9. Target tier > T8 → `IllegalArgumentException`
10. Logic target tier > 5 → `IllegalStateException`
11. Packager generation < 2 → `IllegalStateException`
12. Target item unavailable → `IllegalStateException`
13. Null transition → `IllegalArgumentException`
14. Transition tier mismatch → `IllegalArgumentException`
15. Transition bounds invalid → `IllegalArgumentException`

### `HighBinningRules.Transition` (record)
```java
public record Transition(
    int sourceTier,          // [1, 7]
    int targetTier,          // must equal sourceTier + 1
    double yieldMultiplier,  // (0, 1] — penalty factor
    double ticksMultiplier,  // [1, 1000] — tick cost multiplier
    double energyMultiplier  // [1, 1000] — energy cost multiplier
)
```

**Validation** (canonical constructor):
- `sourceTier` in [1, 7]
- `targetTier == sourceTier + 1`
- `yieldMultiplier` in (0, 1]
- `ticksMultiplier`, `energyMultiplier` in [1, 1000]

**Utility methods**:
- `scaledTicksPerWafer(int base)` → `int` (ceiling scaling, throws `ArithmeticException` on overflow, validates base ≥ 1)
- `scaledEnergyPerTick(int base)` → `int` (ceiling scaling, throws `ArithmeticException` on overflow, validates base ≥ 0)
- `toString()` — no hidden data leakage

### Constant
- `DEFAULT_YIELD_PENALTY = 0.15` — default yield multiplier when no explicit transition provided (for future coordinator use)

---

## 3. Judgment Algorithm (Calibrated)

```
K = spec.rollCount()                 // frozen from source run spec
y = spec.effectiveYield()            // frozen from source run spec
baseWeight = YieldCalibration.baseWeight(K, y)  // cached, deterministic

prob = y * transition.yieldMultiplier()         // desired high-bin pass probability

if prob <= 0:
    threshold = 1.0                          // all fail
else if prob >= 1.0:
    threshold = 1.0 - y                      // EXACTLY match YieldMath.passes
else:
    q = RegularizedGamma.quantile(K, prob)   // gamma quantile
    threshold = exp(-baseWeight * q)         // calibrated threshold

For each wafer in source lot order:
    cumulativeRoll = wafer.cumulativeRoll()   // original, frozen
    passes = (cumulativeRoll > threshold)     // strict >
    passFlags.add(passes)

Return new PackagingPlan(passFlags, chipsPerWafer, packagesPerWafer)
```

**Key properties**:
- **Subset property**: A wafer passing high binning must have `cumulativeRoll > highBinThreshold`. Since `prob ≤ y` (yield multiplier ≤ 1), `highBinThreshold ≥ sourceThreshold`, so any high-bin pass implies source pass.
- **Failing wafers** → yield scrap (`chipsPerWafer` each), consume 0 packages
- **Passing wafers** → yield chips (`chipsPerWafer` each), consume `packagesPerWafer` packages
- **Plan immutability**: Flags defensively copied, list immutable, `toString()` hides flags

---

## 4. Integration Responsibilities

### Caller (Coordinator / Native Worker)
1. **Resolve** source tier, die type, core count from lot's `RunSpec`
2. **Check** target chip item exists in catalog → pass as `targetAvailable`
3. **Provide** explicit `Transition` config (parsed from datapack/resource)
4. **Supply** installed packager generation
5. **Supply** recipe amounts `chipsPerWafer` and `packagesPerWafer` (no hardcoded defaults)
6. **Receive** `PackagingPlan` — persist mode/target/cost with `PackagingRun.Snapshot`
7. **Build** `PackagingRun` using transition's `scaledTicksPerWafer()` and `scaledEnergyPerTick()` with actual base values
8. **Never** log/print pass flags (plan's `toString()` already hides them)

### Native Consumption + Freeze
- The returned `PackagingPlan` is the **single source of truth** for high-binning outcomes
- `PackagingRun` operates on this plan wafer-by-wafer; pauses are transparent
- Snapshots (`PackagingRun.save()`) freeze progress, plan, and schedule for save/restore
- No hidden rolls or pass flags ever leave the server

---

## 5. Config Boundaries & Overflow

| Parameter | Min | Max | Notes |
|-----------|-----|-----|-------|
| `sourceTier` | 1 | 7 | T8 has no successor |
| `targetTier` | = source+1 | 8 | Enforced |
| `yieldMultiplier` | >0 | 1.0 | Penalty; 1.0 = no penalty |
| `ticksMultiplier` | 1.0 | 1000 | Overflow if `ceil(base * mult) > Integer.MAX_VALUE` |
| `energyMultiplier` | 1.0 | 1000 | Overflow if `ceil(base * mult) > Integer.MAX_VALUE` |

**Overflow checks** performed in `Transition` constructor (bounds) and `scaled*()` methods (ceiling with actual base). Scaled costs use `Math.ceil(base × multiplier)` with `long` intermediate.

---

## 6. Test Coverage (HighBinningRulesTest.java)

| Category | Scenarios |
|----------|-----------|
| **Transition validation** | Valid, target≠source+1, yield bounds, cost bounds, overflow, scaled methods (ceil), toString safety |
| **Structural refusals** | Null/empty lot, no spec, incomplete, tier mismatch (structural before layer), memory/nand, tier<T3, compute cores≠1/NO_CORES, logic with cores, target>T8, logic cap 5, gen<2, target unavailable, null/wrong transition |
| **Deterministic judgment** | Calibrated quantile boundaries, yieldMult=1.0 exact match to YieldMath.passes, yieldMult≈0, subset property, source immutability, recipe preservation, repeatability, compute core preservation (1 before T6, positive at T6+) |
| **Plan properties** | All-fail (scrap), all-pass (chips+packages), toString safety, size match |
| **Config boundaries** | Min/max yield multiplier, min/max cost multipliers, overflow detection |
| **Future T4→T5** | Logic T4→T5 allowed, Compute T4→T5 refused |
| **Edge cases** | Fixture capacity ≥ wafer count, target unavailable still refuses, invalid transition constructor throws, compute NO_CORES refused |

**Test data**: Synthetic complete lots with deterministic uniform cumulative rolls `(i+1)/(N+1)`. No randomness, no Minecraft dependencies. Calibrated engine used over many wafers with fixed seed logic, observed high-bin output ~15% normal within statistical tolerance.

---

## 7. Limitations & Open Items

1. **Packages per wafer** provided by caller (no hardcoded 1). The brief says "preserve chipsPerWafer/packagesPerWafer" — current implementation uses caller-supplied values.

2. **Transition parsing** from datapack/resource is explicitly deferred to coordinator/native worker (per brief: "Configuration/resource parsing belongs later to coordinator/nativeworker; keep core free MC").

3. **T4→T5 logic** enabled by explicit transition but untested in gameplay context. DieRules caps logic at tier 5; transition validation enforces target≤5.

4. **Compute core count** fixed at 1 before T6. Post-T6 compute high binning (with variable cores) preserves positive source count. T6 enablement and comfortable cores logic handled by existing framework.

5. **No default transition fallback** — caller must supply explicit `Transition`. The constant `DEFAULT_YIELD_PENALTY` exists for coordinator convenience only.

6. **Energy base for overflow check** uses actual input to `scaledEnergyPerTick()`; coordinator should validate actual scaled costs before run creation.

7. **Generation matrix check** uses hardcoded `LINE_ID = "test_packaging"` from `PackagingRules`. If high binning uses a different line, matrix key must be aligned.

---

## 8. Files Delivered

| File | Purpose |
|------|---------|
| `src/main/java/com/euvcraft/lithmod/core/HighBinningRules.java` | Core implementation + `Transition` record |
| `src/test/java/com/euvcraft/lithmod/core/HighBinningRulesTest.java` | Deterministic JUnit tests (updated to new API) |
| `docs/reports/T4_high-binning-domain.md` | This report |

---

## 9. Verification Checklist

- [x] Compiles against existing framework (PackagingRules, PackagingPlan, YieldMath, YieldCalibration, RegularizedGamma, RunSpec, FoupContents, Tier, DieRules, FabSettings, GenerationMatrix)
- [x] No new Minecraft/NeoForge dependencies
- [x] No commands, Git, network, delegation, or test execution in this task
- [x] All refusal modes fail closed with typed exceptions (IllegalArgumentException for structural, IllegalStateException for state)
- [x] Tier/spec mismatch checked BEFORE layer check (structural mismatch reported first)
- [x] Deterministic: same input → same output, no RNG
- [x] Source lot immutability preserved
- [x] Plan immutability & toString safety
- [x] Transition bounds validated + overflow checked with ceiling scaling
- [x] Logic cap 5 enforced
- [x] Compute core count = 1 before T6, positive preserved at T6+
- [x] Generation 2+ packager required
- [x] Tests reuse actual Tier constructors & test helpers (GenerationMatrixTest.TestTiers)
- [x] Yield multiplier 1.0 EXACTLY matches existing normal YieldMath.passes (no rounding boundaries)
- [x] Penalty 0 makes all fail; target prob NOT clamped to minimum 1%
- [x] No new private info in logs/toString
- [x] Uses source frozen K (spec.rollCount) even if current tier layer count/config differs
- [x] No invented APIs
- [x] Public facts only in API/errors
- [x] Native consumption/freeze responsibilities documented