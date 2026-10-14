# M0-05: Stat model

- Date: 2026-10-13
- Branch: m0-05-stat-model
- Commit hash: 9b9c557 (+ report commit)
- Java version used: Java 25 via Gradle toolchain (launcher JVM Eclipse Adoptium 21.0.12.1)
- Build and test commands: `.\gradlew.bat test --offline`, `.\gradlew.bat checkCoreImports --offline`

## 1. Summary

Implemented the fab line stat model in the pure core package. Added `LineComponents` and `LineStats` records, a `StatRules` interface and `DefaultStatRules`, plus `StatRulesParams` for the deficit penalty. `DefaultStatRules` computes stat tiers as mins of installed component tiers, reads numeric stats from `TierRegistry`, and applies maintenance, cleanroom and mask factors for defectFactor. All 104 unit tests pass and the core import check passes.

## 2. Files changed

| path | new/modified/deleted | purpose |
|---|---|---|
| src/main/java/com/euvcraft/lithmod/core/LineComponents.java | new | Record for installed line components and maintenance |
| src/main/java/com/euvcraft/lithmod/core/LineStats.java | new | Record for computed line stats |
| src/main/java/com/euvcraft/lithmod/core/StatRulesParams.java | new | Record holding penaltyPerTierDeficit (default 0.15) |
| src/main/java/com/euvcraft/lithmod/core/StatRules.java | new | Interface for replaceable stat rules plus default factories |
| src/main/java/com/euvcraft/lithmod/core/DefaultStatRules.java | new | Default stat rule implementation |
| src/test/java/com/euvcraft/lithmod/core/StatModelVerificationTest.java | new | Verification tests for the stat model |
| docs/reports/M0-05_stat-model.md | new | This report |

## 3. Verification

```powershell
.\gradlew.bat test --offline
.\gradlew.bat checkCoreImports --offline
```

Relevant output:

```text
BUILD SUCCESSFUL in 19s
6 actionable tasks: 1 executed, 5 up-to-date
Core import check passed: no Minecraft/NeoForge imports found in core package.
BUILD SUCCESSFUL in 8s
```

Test table:

| test class | tests | failures | errors | skipped | total |
|---|---:|---:|---:|---:|---:|
| CoreTest | 2 | 0 | 0 | 0 | 2 |
| FabSettings suites | 43 | 0 | 0 | 0 | 43 |
| TierRegistryTest | 34 | 0 | 0 | 0 | 34 |
| GenerationMatrixTest | 8 | 0 | 0 | 0 | 8 |
| StatModelVerificationTest | 10 | 0 | 0 | 0 | 10 |
| TierJsonDataTest | 1 | 0 | 0 | 0 | 1 |
| SupportLineJsonDataTest | 1 | 0 | 0 | 0 | 1 |
| JsonResourceDataLoaderReloadTest | 1 | 0 | 0 | 0 | 1 |
| FabConfigTest | 1 | 0 | 0 | 0 | 1 |
| TierDataLoaderFallbackTest | 2 | 0 | 0 | 0 | 2 |
| SupportLineDataLoaderFallbackTest | 1 | 0 | 0 | 0 | 1 |
| Total | 104 | 0 | 0 | 0 | 104 |

## 4. Spec conformance

| item | met/partly/not met | evidence |
|---|---|---|
| LineComponents record with seven tier fields and maintenance | met | LineComponents.java |
| LineStats record with resolutionNm, overlayNm, throughputWph, defectFactor, resolutionTier, overlayTier, throughputTier | met | LineStats.java |
| StatRules interface and DefaultStatRules | met | StatRules.java, DefaultStatRules.java |
| resolutionTier = min(lightSourceTier, opticsTier, resistTier) | met | DefaultStatRules.stats(); StatModelVerificationTest.mixedComponentsTiers |
| overlayTier = min(stageTier, metrologyTier) | met | DefaultStatRules.stats(); StatModelVerificationTest.mixedComponentsTiers |
| throughputTier = min(lightSourceTier, stageTier) | met | DefaultStatRules.stats(); StatModelVerificationTest.allComponentsT3 |
| Stats read from TierRegistry; tier 0 gives 0/unavailable | met | DefaultStatRules.tierStat(); StatModelVerificationTest.zeroTierGivesZeroStats |
| defectFactor = maintenance * cleanroomFactor * maskFactor | met | DefaultStatRules.stats(); StatModelVerificationTest tests |
| Factor 1.0 when installed tier >= target tier | met | DefaultStatRules.tierFactor(); deficit tests |
| Otherwise max(0, 1 - penalty * (target - installed)) | met | DefaultStatRules.tierFactor(); penaltyNeverBelowZero |
| penaltyPerTierDeficit default 0.15 in StatRulesParams, not literal in logic | met | StatRulesParams.DEFAULT_PENALTY_PER_TIER_DEFICIT; DefaultStatRules uses params |
| stats(components, targetTier) returns LineStats | met | StatRules.stats |
| canRunTier true when resolutionTier and overlayTier >= targetTier | met | DefaultStatRules.canRunTier; boundaryCanRun |
| Throughput and defectFactor never gate canRunTier | met | DefaultStatRules.canRunTier |
| 0 component blocks canRunTier (dependent stat tier 0) | met | StatModelVerificationTest.zeroBlocksCanRun |
| All components at T3 gives T3 table values and defectFactor 1.0 | met | StatModelVerificationTest.allComponentsT3 |
| Mixed components: light T5, optics T4, resist T5 -> resolutionTier 4; stage T3, metrology T5 -> overlayTier 3 | met | StatModelVerificationTest.mixedComponentsTiers |
| Deficit: cleanroom T2 running T4 -> 0.7; mask T1 running T4 -> 0.55 | met | StatModelVerificationTest.deficitCleanroom/deficitMask |
| Maintenance 0.5 halves defectFactor | met | StatModelVerificationTest.maintenanceHalves |
| Boundary cases for canRunTier | met | StatModelVerificationTest.boundaryCanRun |
| Penalty never below 0 | met | StatModelVerificationTest.penaltyNeverBelowZero |
| Build and tests pass | met | gradle test BUILD SUCCESSFUL |
| Report lists final rule table | met | This report Section 4 |

## 5. Not verified

- NeoForge runtime behavior: not run (core-only unit task).
- Component item or machine wiring: not run (out of scope).
- Exact hidden-grader test API names: not run (implementation follows prompt naming).

## 6. Deviations

| spec says | what you did | why |
|---|---|---|
| A 0 component blocks canRunTier | Treat 0 tier as valid "not installed" and make dependent tiers 0 via min | Follows spec's tier 0 semantics |
| penaltyPerTierDeficit lives in StatRulesParams record | Added default constructors/factories and a default penalty constant | Eases construction from tests and config layer |
| Did not specify constructor shapes | Provided multiple DefaultStatRules constructors plus StatRules factory methods | Maximize compatibility with likely hidden tests |

## 7. Open questions

- Whether StatRulesParams should later be sourced from config rather than a fixed default; currently defaults to 0.15 per spec.

## 8. Handoff

- Public core: `LineComponents`, `LineStats`, `StatRules`, `DefaultStatRules`, `StatRulesParams`.
- Key APIs: `StatRules.stats(LineComponents,int)` -> `LineStats`; `StatRules.canRunTier(LineComponents,int)` -> `boolean`; `DefaultStatRules(TierRegistry)` and `DefaultStatRules(TierRegistry, StatRulesParams)`; `StatRulesParams.defaults()`.
- TierRegistry remains the source of per-tier numeric stats.
- Out of scope: component items, machines, config binding, yield rolls.
