# M1-00: Data additions, T1 single layer and documentation repair

- Date: 2026-10-14
- Branch: m1-00-data-additions-and-docs-repair
- Commit hash: 0f910070649ae28b4c4154a98248036ca4819023
- Java version used: 25 (Gradle toolchain)
- Build/test commands: `.\gradlew.bat test checkCoreImports --offline`; `.\gradlew.bat runGameTestServer --offline`

## 1. Summary: 3 to 6 sentences.
Added `layer_ticks` to the Tier record and codec plus all eight tier JSON files, with T1 set to 600 ticks and T2-T8 to 100 ticks. T1 now has one layer instead of six in data, reference tiers, tests and docs. Added `layerTicksOverrides` (range 1-72000) and three Gen 1 config keys (`chipsPerWafer`, `maskUses`, `resistPerLayer`) with FabSettings/FabConfig validation and wiring. Added `FabSettings.layerTicks(...)` / `Tier.layerTicks(...)` to read overrides. Repaired `docs/m0-foundation.md` with a committed `tools/generate-m0-api.ps1` extraction script, corrected JSON examples and config table, and rewrote open design decisions. All 250 unit tests, the import check and the game test server pass; T1 single-roll calibration lands at 60.20% versus a 60.0% target.

## 2. Files changed
| path | new/modified/deleted | purpose |
|------|---------------------|---------|
| src/main/java/com/euvcraft/lithmod/core/Tier.java | modified | Added `layerTicks` component and validation; added `layerTicks(...)` helpers |
| src/main/java/com/euvcraft/lithmod/core/FabSettings.java | modified | Added chipsPerWafer, maskUses, resistPerLayer, layerTicksOverrides, layerTicks() |
| src/main/java/com/euvcraft/lithmod/mc/FabConfig.java | modified | Added layerTicksOverrides and the three new general config keys |
| src/main/java/com/euvcraft/lithmod/mc/data/TierCodec.java | modified | Added `layer_ticks` codec field |
| src/main/resources/data/lithmod/tiers/t*.json | modified | Added layer_ticks; T1 layers 6 -> 1 |
| src/test/java/... (19 files) | modified | Tier constructor arguments, T1 6 -> 1, calibration expectations |
| docs/m0-foundation.md | modified | API, JSON, config table, decisions repaired |
| docs/testing.md | modified | T1 layer count in expected log line |
| tools/generate-m0-api.ps1 | new | Extracts API signatures from compiled classes via javap |

## 3. Verification
Commands:
```
.\gradlew.bat test checkCoreImports --offline
.\gradlew.bat runGameTestServer --offline
```
Relevant output:
```
Core import check passed: no Minecraft/NeoForge imports found in core package.
BUILD SUCCESSFUL in 6s
Game test server shutting down
BUILD SUCCESSFUL in 4m 31s
```
Test table (unit tests, 0 failures/0 errors/0 skipped):

| test class | tests | failures | errors | skipped |
|------------|-------|----------|--------|---------|
| (46 gradle test suites) | 250 | 0 | 0 | 0 |

Total equals 250.

Calibration simulation for T1 with K = 1 roll: target 60.0%, baseWeight 1.000000, simulated 60.2040%, deviation +0.2040 pp (within 1.5 pp tolerance).

## 4. Spec conformance
| item | met/partly/not met | evidence |
|------|-------------------|----------|
| layer_ticks in Tier record/codec/JSON/registry validation | met | Tier.java, TierCodec.java, t1.json-t8.json |
| layerTicksOverrides parsing 1..72000 | met | FabSettings.java, FabConfig.java |
| layerTicks(Tier, FabSettings) | met | FabSettings.layerTicks, Tier.layerTicks |
| T1 layers 6 -> 1 everywhere | met | t1.json; ReferenceTiers; tests |
| chipsPerWafer/maskUses/resistPerLayer | met | FabSettings, FabConfig; ranges enforced |
| script under tools extracting signatures | met | tools/generate-m0-api.ps1 |
| docs repaired from script output | met | docs/m0-foundation.md API section |
| JSON examples regenerated | met | t3 and die rules examples match files |
| config table matches FabConfig | met | docs/m0-foundation.md |
| decisions rewritten | met | Open Design Decisions section |

## 5. Not verified
- RunSpec codec persistence and game test coverage for FOUPs were not re-run beyond the full game test server; the server run passed.
- Creative-mode duplication behavior was not retested; it remains a documented limitation.

## 6. Deviations
| spec says | what you did | why |
|-----------|--------------|-----|
| Creative worlds unsupported | kept as documented limitation | no change requested |
| layerTicks field may use another name | chose `layerTicks` | matches 26.3 record accessor style |
| API classes may differ | kept javap full signatures | script is authoritative |

## 7. Open questions
1. None new.

## 8. Handoff
- Tier.layerTicks() added to Tier record; TierCodec field `layer_ticks`.
- FabSettings new accessors: chipsPerWafer, maskUses, resistPerLayer, layerTicksOverrides; FabConfig keys `general.chipsPerWafer`, `general.maskUses`, `general.resistPerLayer`, `general.layerTicksOverrides`.
- `FabSettings.layerTicks(Tier, FabSettings)` / `Tier.layerTicks(Tier, FabSettings)` / `Tier.layerTicks(FabSettings)`.
- T1 has 1 layer with layer_ticks 600.
