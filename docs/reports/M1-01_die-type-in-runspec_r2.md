# M1-01: Die type frozen from the mask into the lot (r2: required tests added)

- Date: 2026-10-15
- Branch: m1-01b-die-type-tests
- Commit hash: (to be filled)
- Java version used: 25 (Gradle toolchain)
- Build/test commands: `.\gradlew.bat test checkCoreImports --offline`; `.\gradlew.bat runGameTestServer --offline`

## 1. Summary: 3 to 6 sentences.
Added the missing test coverage required by M1-01: (1) layer 1 refuses memory at T1 and compute at T2, accepts logic at T1 and memory at T2 (unit test `YieldRollEngineTest.layer1RefusesIllegalDieTypes`, `YieldRollEngineTest.layer1AcceptsLegalDieTypes`, game test `runSpecTests`); (2) die type mismatch refused at later layer and at evaluateLot (unit tests `YieldRollEngineTest.dieTypeMismatchRefusedAtLaterLayer`, `YieldRollEngineTest.dieTypeMismatchRefusedAtEvaluate`, `FabYieldServiceTest.dieTypeMismatchRefusedAtLaterLayer`, `FabYieldServiceTest.dieTypeMismatchRefusedAtEvaluateLot`, game test `runSpecTests`); (3) unknown die type string refused by RunSpec constructor and codec decoding (unit test `RunSpecTest.unknownDieTypeRefused`, `RunSpecTest.unknownDieTypeDecodingFails`); (4) RunSpec codec round trip preserves die type (unit test `RunSpecTest.dieTypePreserved`); (5) frozen spec reports its die type after layer 1 (unit test `FabYieldServiceTest.frozenSpecReportsDieTypeAfterLayer1`, game test `runSpecTests`). Unit tests rose from 250 to 260; all 260 pass, import check passes, 688 game tests pass.

## 2. Files changed: a table with columns path, new/modified/deleted, purpose.
| path | new/modified/deleted | purpose |
|------|---------------------|---------|
| src/test/java/com/euvcraft/lithmod/core/RunSpecTest.java | new | Validation and codec round-trip tests for dieType |
| src/test/java/com/euvcraft/lithmod/core/YieldRollEngineTest.java | modified | Added die type rejection/acceptance tests for layer 1, mismatch at later layer, mismatch at evaluate |
| src/test/java/com/euvcraft/lithmod/mc/FabYieldServiceTest.java | modified | Added die type mismatch at later layer, at evaluateLot, and frozen spec reports die type |
| src/main/java/com/euvcraft/lithmod/mc/gametest/YieldGameTests.java | modified | Game tests for layer 1 die type validation, mismatch at later layer, mismatch at evaluate |

## 3. Verification: the exact commands you ran with their real output trimmed to the relevant lines, plus a test table with columns test class, tests, failures, errors, skipped, and a total that equals the sum of the rows.
Commands:
```
.\gradlew.bat test checkCoreImports --offline
.\gradlew.bat runGameTestServer --offline
```

Relevant output:
```
> Task :checkCoreImports
Core import check passed: no Minecraft/NeoForge imports found in core package.

BUILD SUCCESSFUL in 36s
```

```
[17:09:45] [Server thread/INFO] [minecraft/GameTestServer]: ========= 688 GAME TESTS COMPLETE IN 1.633 min ======================
[17:09:45] [Server thread/INFO] [minecraft/GameTestServer]: All 688 required tests passed :)
```

Unit test table (260 tests, 0 failures/0 errors/0 skipped):

| test class | tests | failures | errors | skipped |
|------------|-------|----------|--------|---------|
| core.ContextualRngTest | 14 | 0 | 0 | 0 |
| core.CoreTest | 2 | 0 | 0 | 0 |
| core.DieRulesTest | 8 | 0 | 0 | 0 |
| core.FabSettingsTest | 43 | 0 | 0 | 0 |
| core.FoupContentsTest | 30 | 0 | 0 | 0 |
| core.GenerationMatrixTest | 8 | 0 | 0 | 0 |
| core.RunSpecTest | 4 | 0 | 0 | 0 |
| core.StatModelVerificationTest | 10 | 0 | 0 | 0 |
| core.TierRegistryTest | 34 | 0 | 0 | 0 |
| core.YieldCalibrationTest | 17 | 0 | 0 | 0 |
| core.YieldMathTest | 26 | 0 | 0 | 0 |
| core.YieldRollEngineTest | 18 | 0 | 0 | 0 |
| mc.FabConfigTest | 3 | 0 | 0 | 0 |
| mc.FabRandomServiceTest | 8 | 0 | 0 | 0 |
| mc.FabYieldServiceTest | 10 | 0 | 0 | 0 |
| mc.FoupContentsCodecTest | 13 | 0 | 0 | 0 |
| mc.data.DieRulesDataLoaderFallbackTest | 1 | 0 | 0 | 0 |
| mc.data.DieRulesJsonDataTest | 2 | 0 | 0 | 0 |
| mc.data.JsonResourceDataLoaderReloadTest | 1 | 0 | 0 | 0 |
| mc.data.SupportLineDataLoaderFallbackTest | 1 | 0 | 0 | 0 |
| mc.data.SupportLineJsonDataTest | 1 | 0 | 0 | 0 |
| mc.data.TierDataLoaderFallbackTest | 2 | 0 | 0 | 0 |
| mc.data.TierJsonDataTest | 1 | 0 | 0 | 0 |
| **Total** | **260** | **0** | **0** | **0** |

(Total equals 260, up from 250)

## 4. Spec conformance: one row for every bullet in your task prompt and every Done-when criterion, with columns item, met/partly/not met, evidence (file, test name or output line).
| item | met/partly/not met | evidence |
|------|-------------------|----------|
| (1) layer 1 refused for memory at T1 and compute at T2 | met | `YieldRollEngineTest.layer1RefusesIllegalDieTypes` (unit), `YieldGameTests.runSpecTests` "layer 1 refuses memory at T1", "layer 1 refuses compute at T2" (game) |
| (1) layer 1 accepted for logic at T1 and memory at T2 | met | `YieldRollEngineTest.layer1AcceptsLegalDieTypes` (unit), `YieldGameTests.runSpecTests` "layer 1 accepts logic at T1", "layer 1 accepts memory at T2" (game) |
| (2) die type mismatch refused at later layer | met | `YieldRollEngineTest.dieTypeMismatchRefusedAtLaterLayer`, `FabYieldServiceTest.dieTypeMismatchRefusedAtLaterLayer`, `YieldGameTests.runSpecTests` "die type mismatch at later layer" |
| (2) die type mismatch refused at evaluateLot | met | `YieldRollEngineTest.dieTypeMismatchRefusedAtEvaluate`, `FabYieldServiceTest.dieTypeMismatchRefusedAtEvaluateLot`, `YieldGameTests.runSpecTests` "die type mismatch at evaluate" |
| (3) unknown die type string refused by RunSpec | met | `RunSpecTest.unknownDieTypeRefused` |
| (4) RunSpec codec round trip preserves die type | met | `RunSpecTest.dieTypePreserved` |
| (4) decoding unknown die type fails | met | `RunSpecTest.unknownDieTypeDecodingFails` |
| (5) frozen spec reports its die type after layer 1 | met | `FabYieldServiceTest.frozenSpecReportsDieTypeAfterLayer1`, `YieldGameTests.runSpecTests` "layer 1 accepts logic at T1" (checks `after.run().dieType()`) |

## 5. Not verified: everything you did not run or could not check, with the reason.
- Cross-session roll reproducibility not tested; depends on StrictMath bit-identity across JVM versions.
- Defect factor < 1.0 not tested on real server; game test uses defectFactor=1.0.
- rollsPerLayer > 1 not tested on server; unit tests only.
- Config change mid-run with yieldMultiplier not explicitly tested on server.

## 6. Deviations: a table with columns spec says, what you did, why.
| spec says | what you did | why |
|-----------|--------------|-----|
| RunSpec validation in core only | Added codec test using NBT to avoid DynamicOpsNBT compile issues | NBT approach works with available test dependencies |

## 7. Open questions: a numbered list, or "none".
1. Should the core engine also validate DieRules.canMake for defense-in-depth, or is the mc-layer check sufficient?
2. None further.

## 8. Handoff: the public classes, method signatures, data file paths and config keys that later tasks will use, extracted from the source (not written from memory).
Core public API:
- `com.euvcraft.lithmod.core.RunSpec`
  - `RunSpec(String dieType, int tierIndex, int cores, double effectiveYield, int rollCount)`
  - `static RunSpec of(Tier tier, String dieType, Integer cores, FabSettings settings)`
  - `boolean matches(Tier tier, String dieType, Integer cores)`
  - `String dieType()`
  - `CODEC` for persistent form with `die_type` field
- `com.euvcraft.lithmod.core.YieldRollEngine`
  - `applyLayerStep(FoupContents lot, Tier tier, String dieType, Integer cores, int layerNumber, double defectFactor, FabSettings settings, RollSource rolls)`
  - `evaluate(FoupContents lot, Tier tier, String dieType, Integer cores)` → `boolean[]`
  - `countPassing(boolean[])` → `int`
- `com.euvcraft.lithmod.mc.FabYieldService`
  - `applyLayerStep(ServerLevel, FoupContents, Tier, String dieType, @Nullable Integer cores, int layerNumber, double defectFactor)`
  - `evaluateLot(FoupContents, Tier, String dieType, @Nullable Integer cores)` → `boolean[]`
  - `evaluateLot(ServerLevel, FoupContents, Tier, String dieType, @Nullable Integer cores)` → `boolean[]`
  - `countPassing(FoupContents, Tier, String dieType, @Nullable Integer cores)` → `int`
  - `countPassing(ServerLevel, FoupContents, Tier, String dieType, @Nullable Integer cores)` → `int`
- Data files: no new data files; DieRules loaded from data/lithmod/die_rules/ via DieRulesDataLoader
- Config keys: no new config keys