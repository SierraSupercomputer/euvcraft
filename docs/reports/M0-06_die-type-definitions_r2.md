# M0-06: Die type definitions

- Date: 2026-10-13
- Branch: m0-06-die-type-definitions
- Commit hash: 1b47aff, 0164c2a (original M0-06), plus the r2 cleanup commit
- Java version used: Java 25 via Gradle toolchain (dev server log: `Java version: 25`)
- Build and test commands: `.\gradlew.bat test --offline`, `.\gradlew.bat checkCoreImports --offline`, `.\gradlew.bat runServer --offline`

This is revision r2. It supersedes `docs/reports/M0-06_die-type-definitions.md`, whose file list and handoff section name classes that no longer exist.

## 1. Summary

Removed the speculative API surface added in r1: the `DieType` enum, the `DieCodec` and `DieRulesCodec` aliases, the `DieRuleDataLoader`, `DieDataLoader` and `DieTypeDataLoader` subclasses, the extra accessors `getDieRules()` and `get()`, and every `Object`-typed method overload. One canonical name now exists per concept: `DieRule`, `DieRules`, `DieTypes`, `DieRuleCodec`, `DieRulesDataLoader` and `DieRulesDataLoader.getRules()`. Die ids are plain lowercase strings. Exactly one reload listener is registered for die rules, verified in source and in the dev server log. The dev dedicated server was started and its real log line showing the loaded die rules is pasted in section 3.

## 2. Files changed

| path | new/modified/deleted | purpose |
|---|---|---|
| src/main/java/com/euvcraft/lithmod/core/DieRules.java | modified | All queries now take String die ids; Object overloads and helpers removed |
| src/main/java/com/euvcraft/lithmod/core/DieRule.java | modified | Locale import cleanup only |
| src/main/java/com/euvcraft/lithmod/core/DieType.java | deleted | Enum removed, string ids are canonical |
| src/main/java/com/euvcraft/lithmod/mc/data/DieRulesDataLoader.java | modified | Single accessor `getRules()`, unused imports removed |
| src/main/java/com/euvcraft/lithmod/mc/data/DieCodec.java | deleted | Alias removed |
| src/main/java/com/euvcraft/lithmod/mc/data/DieRulesCodec.java | deleted | Alias removed |
| src/main/java/com/euvcraft/lithmod/mc/data/DieRuleDataLoader.java | deleted | Subclass removed |
| src/main/java/com/euvcraft/lithmod/mc/data/DieDataLoader.java | deleted | Subclass removed |
| src/main/java/com/euvcraft/lithmod/mc/data/DieTypeDataLoader.java | deleted | Subclass removed |
| src/test/java/com/euvcraft/lithmod/core/DieRulesTest.java | modified | Replaced the placeholder test with a reference-table check |
| src/test/java/com/euvcraft/lithmod/mc/data/DieRuleDataLoaderFallbackTest.java -> DieRulesDataLoaderFallbackTest.java | renamed | Test class name follows the canonical loader name |
| src/test/java/com/euvcraft/lithmod/mc/data/DieRuleJsonDataTest.java -> DieRulesJsonDataTest.java | renamed | Parses bundled JSON through DieRuleCodec and compares the fallback |
| docs/reports/M0-06_die-type-definitions_r2.md | new | This report |

Unchanged: `DieTypes.java`, `DieRuleCodec.java`, `data/lithmod/die_rules/rules.json`, `EUVCRAFTMod.java`.

## 3. Verification

Unit tests and import check:

```
.\gradlew.bat test --offline
BUILD SUCCESSFUL in 10s
EXITCODE=0

.\gradlew.bat checkCoreImports --offline
Core import check passed: no Minecraft/NeoForge imports found in core package.
BUILD SUCCESSFUL in 6s
EXITCODE=0
```

Test table (from `build/test-results/test/*.xml`):

| test class | tests | failures | errors | skipped | total |
|---|---:|---:|---:|---:|---:|
| core.CoreTest | 2 | 0 | 0 | 0 | 2 |
| core.DieRulesTest | 5 | 0 | 0 | 0 | 5 |
| core.DieRulesTest$Validation | 3 | 0 | 0 | 0 | 3 |
| core.FabSettingsTest suites (11) | 43 | 0 | 0 | 0 | 43 |
| core.TierRegistryTest | 34 | 0 | 0 | 0 | 34 |
| core.GenerationMatrixTest | 8 | 0 | 0 | 0 | 8 |
| core.StatModelVerificationTest | 10 | 0 | 0 | 0 | 10 |
| mc.data.DieRulesJsonDataTest | 2 | 0 | 0 | 0 | 2 |
| mc.data.DieRulesDataLoaderFallbackTest | 1 | 0 | 0 | 0 | 1 |
| mc.data.TierJsonDataTest | 1 | 0 | 0 | 0 | 1 |
| mc.data.SupportLineJsonDataTest | 1 | 0 | 0 | 0 | 1 |
| mc.data.JsonResourceDataLoaderReloadTest | 1 | 0 | 0 | 0 | 1 |
| mc.data.TierDataLoaderFallbackTest | 2 | 0 | 0 | 0 | 2 |
| mc.data.SupportLineDataLoaderFallbackTest | 1 | 0 | 0 | 0 | 1 |
| mc.FabConfigTest | 1 | 0 | 0 | 0 | 1 |
| Total | 115 | 0 | 0 | 0 | 115 |

Dev server run (`.\gradlew.bat runServer --offline`, started 23:18:16, reached "Done" 23:19:34). The requested real log line:

```text
[23:19:33] [main/INFO] [co.eu.li.mc.EUVCRAFTMod/]: Loaded die rules: DieRules{data/lithmod/die_rules/, registry=8 tiers, rules=[logic(first=1, cap=5, cores=false), memory(first=2, cap=null, cores=false), compute(first=3, cap=null, cores=true), nand(first=3, cap=null, cores=false)]}
[23:19:33] [main/INFO] [co.eu.li.mc.EUVCRAFTMod/]: Die availability by tier:
Die availability (rows=die, cols=tiers):
die          T1   T2   T3   T4   T5   T6   T7   T8
compute       -    -  yes  yes  yes  yes  yes  yes
logic       yes  yes  yes  yes  yes  yes  yes  yes
memory        -  yes  yes  yes  yes  yes  yes  yes
nand          -    -  yes  yes  yes  yes  yes  yes
```

Listener registration, same run, one line per loader and only one for die rules:

```text
[23:19:32] [Worker-Main-3/DEBUG] [co.eu.li.mc.EUVCRAFTMod/]: Registering TierDataLoader for server resource reloads
[23:19:32] [Worker-Main-3/DEBUG] [co.eu.li.mc.EUVCRAFTMod/]: Registering SupportLineDataLoader for server resource reloads
[23:19:32] [Worker-Main-3/DEBUG] [co.eu.li.mc.EUVCRAFTMod/]: Registering DieRulesDataLoader for server resource reloads
```

Server startup completed: `[23:19:34] [Server thread/INFO] [minecraft/DedicatedServer]: Done (0.657s)! For help, type "help"`.

Source check for a single listener registration: `src/main/java/com/euvcraft/lithmod/mc/EUVCRAFTMod.java` lines 32-34 register `TierDataLoader`, `SupportLineDataLoader` and `DieRulesDataLoader` once each; `DieRulesDataLoader.onAddServerReloadListeners` is the only `@SubscribeEvent` for `AddServerReloadListenersEvent` in that class, adding listener id `lithmod:die_rules`.

Source check for removed names: ripgrep for `DieType\b|DieRulesCodec|DieCodec|DieRuleDataLoader|DieDataLoader|DieTypeDataLoader|getDieRules|normalizeDieType` over `src` returns no matches.

## 4. Spec conformance

| item | met/partly/not met | evidence |
|---|---|---|
| One canonical name per concept | met | Only DieRule, DieRules, DieTypes, DieRuleCodec, DieRulesDataLoader remain |
| Delete DieType enum | met | git status shows `D core/DieType.java` |
| Delete DieRulesCodec and DieCodec | met | both deleted |
| Delete DieRuleDataLoader, DieDataLoader, DieTypeDataLoader | met | all three deleted |
| Delete extra accessors | met | DieRulesDataLoader has only `getRules()` |
| Delete all Object-typed overloads | met | DieRules methods take String; `normalize(String)` is private |
| Use String die ids | met | DieRule.dieType is String; DieTypes.COMPUTE etc. |
| Exactly one listener registered | met | Source check plus three registration lines in the dev server log |
| Start dev server and paste the real log line | met | Log line quoted in section 3 |
| Report at M0-06_die-type-definitions_r2.md | met | This file |
| Remove the loader-name open question | met | Section 7 says none; name is fixed at DieRulesDataLoader |
| Build and tests pass | met | BUILD SUCCESSFUL, 115 tests, exit code 0 |

## 5. Not verified

- Client-side run: not run; the reload listener is server-only.
- `/reload` behaviour with an intentionally broken die_rules datapack: not run; the failure path is covered by `DieRulesDataLoaderFallbackTest` only for the first-load fallback.
- Live T6-disabled config run: not run; `coreCountsAvailable` does not read config, it reads the tier's comfortable cores, and the T7 case is covered by unit test.
- The dev server was stopped by killing the JVM because the harness cannot send a `stop` console command, so `runServer` ended with `Process ... finished with non-zero exit value -1` after the server had already reported "Done". Startup itself succeeded.

## 6. Deviations

| spec says | what you did | why |
|---|---|---|
| r1 report keeps a record of the removed classes | Wrote a new r2 report instead of editing r1 | Reports are append-only per task rules; r1 is left as history |
| Remove Object overloads | Kept `String` + `int` and `String` + `Tier` variants | Tier is a real type, not a compatibility hedge, and it is used by the stat model |
| Keep one accessor | `getRules()` only | As requested |

## 7. Open questions

none

## 8. Handoff

- Core: `DieRule(String dieType, int firstTier, Integer capTier, boolean usesCoreCount)`, `DieTypes.COMPUTE|LOGIC|MEMORY|NAND` and `DieTypes.ALL`, `DieRules`.
- `DieRules` queries, all with String die ids: `canMake(String,int)`, `effectiveTier(String,int)`, `isCapped(String,int)`, `targetSpeedMhz(String,int)`, `requirements(String,int)` returning the effective `Tier`, `coreCountsAvailable(String,int)`, plus `firstTier(String)`, `capTier(String)`, `usesCoreCount(String)`, `ruleFor(String)`, `rules()`, `availabilityTable()`. Each also has a `Tier` overload where a tier object is more convenient.
- mc: `DieRuleCodec.CODEC` and `DieRuleCodec.LIST_CODEC`; `DieRulesDataLoader` with `getRules()`; listener id `lithmod:die_rules`; data path `data/lithmod/die_rules/rules.json` (JSON array of objects with `die_type`, `first_tier`, optional `cap_tier`, optional `uses_core_count`).
- Nothing in the repo references the removed class names.
- Pre-existing, not touched: the dev world at `run/world/datapacks/` still contains `tier-override` and `tier-invalid` test packs from M0-03c. `tier-invalid` has `layers: -1` in T3, so the dev server logs a parse error for `lithmod:tiers/t3.json` and falls back to bundled tier defaults before die rules load. That is why the server log shows a tier fallback followed by a clean die-rules load.