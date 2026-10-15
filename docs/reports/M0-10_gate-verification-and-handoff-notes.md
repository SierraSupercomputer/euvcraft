# M0-10: M0 gate verification and handoff notes

- Date: 2026-10-14
- Branch: `master` (at `a14c1ce`)
- Commits: All M0 tasks including this report
- Java version used: Java 25 through the Gradle toolchain (`Eclipse Temurin JDK 25 (25.0.4.1+1-LTS)`)
- Build and test commands run:
  - `.\gradlew.bat test checkCoreImports --offline --console=plain`
  - `.\gradlew.bat runGameTestServer --offline --console=plain`

## 1. Summary

All M0 foundation work (M0-01 through M0-09b) has been verified. The full build passes with 250 unit tests and 688 game tests. The FOUP item survives save, load, inventory moves, chest, shulker box, ender chest, hopper transfer, and shift-click container moves with all hidden rolls preserved bit-for-bit. The network codec carries only the public view (capacity, count, layer) — no roll or flag values. End-to-end simulations for T3, T5, T7 and T7 compute with 8 and 24 cores confirm the core-count penalty matches the reference table. The ImportChecker now rejects `com.mojang` and `io.netty` imports in the core package; `RunSpec.CODEC` was moved to `RunSpecCodec` in the mc package. All data (tiers, generations, die rules) reload from data pack overrides. Lot run spec tests cover spec freezing at layer 1, tier/cores mismatch refusal at later layers and at evaluate, persistent codec round trip with and without spec, and client-view rejection. No absolute paths remain in tracked files.

## 2. Files changed

| path | new/modified/deleted | purpose |
|---|---|---|
| src/main/java/com/euvcraft/lithmod/core/ImportChecker.java | modified | Added patterns to reject `com.mojang.*` and `io.netty.*` imports in core package |
| src/main/java/com/euvcraft/lithmod/core/RunSpec.java | modified | Removed `CODEC` field (moved to mc package) |
| src/main/java/com/euvcraft/lithmod/mc/RunSpecCodec.java | new | Mojang codec for RunSpec persistence, keeping core package pure |
| src/main/java/com/euvcraft/lithmod/mc/FoupContentsCodec.java | modified | Uses `RunSpecCodec.CODEC` instead of `RunSpec.CODEC` |
| src/main/java/com/euvcraft/lithmod/mc/gametest/FoupGameTests.java | modified | Added 4 new game tests: shulker box, ender chest, hopper transfer, shift-click move |
| src/main/java/com/euvcraft/lithmod/mc/gametest/YieldGameTests.java | modified | Added 2 new game tests: end-to-end simulation and run spec verification |
| docs/m0-foundation.md | new | Complete foundation documentation for M1+ tasks |
| docs/manual-walkthrough.md | new | Manual client walkthrough checklist for project owner |
| docs/reports/M0-02_config-system.md | modified | Removed absolute path from report |
| docs/reports/M0-03b_stabilization-fix.md | modified | Removed absolute paths from report |

## 3. Verification

```
.\gradlew.bat test checkCoreImports --offline --console=plain
Core import check passed: no Minecraft/NeoForge imports found in core package.
BUILD SUCCESSFUL in 44s
```

```
.\gradlew.bat runGameTestServer --offline --console=plain
...
[20:33:48] [Server thread/INFO] [minecraft/GameTestServer]: ========= 688 GAME TESTS COMPLETE IN 1.685 min ======================
[20:33:48] [Server thread/INFO] [minecraft/GameTestServer]: All 688 required tests passed :)
BUILD SUCCESSFUL in 2m 58s
```

Unit test table (aggregated from `build/test-results/test/*.xml`):

| test class | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| core.ContextualRngTest | 14 | 0 | 0 | 0 |
| core.CoreTest | 2 | 0 | 0 | 0 |
| core.DieRulesTest$Validation | 3 | 0 | 0 | 0 |
| core.DieRulesTest | 5 | 0 | 0 | 0 |
| core.FabSettingsTest$BuilderPattern | 3 | 0 | 0 | 0 |
| core.FabSettingsTest$Clamping | 12 | 0 | 0 | 0 |
| core.FabSettingsTest$ComfortableCoreOverrides | 2 | 0 | 0 | 0 |
| core.FabSettingsTest$CoreCountOptions | 3 | 0 | 0 | 0 |
| core.FabSettingsTest$Defaults | 2 | 0 | 0 | 0 |
| core.FabSettingsTest$Equality | 2 | 0 | 0 | 0 |
| core.FabSettingsTest$Immutability | 2 | 0 | 0 | 0 |
| core.FabSettingsTest$ListParsing | 8 | 0 | 0 | 0 |
| core.FabSettingsTest$RoundTrip | 3 | 0 | 0 | 0 |
| core.FabSettingsTest$TierYieldOverrides | 6 | 0 | 0 | 0 |
| core.FoupContentsTest$CapacityTest | 4 | 0 | 0 | 0 |
| core.FoupContentsTest$ImmutabilityTest | 6 | 0 | 0 | 0 |
| core.FoupContentsTest$InvalidDataTest | 4 | 0 | 0 | 0 |
| core.FoupContentsTest$PackingTest | 6 | 0 | 0 | 0 |
| core.FoupContentsTest$PublicViewTest | 4 | 0 | 0 | 0 |
| core.FoupContentsTest$WaferTest | 4 | 0 | 0 | 0 |
| core.FoupContentsTest | 2 | 0 | 0 | 0 |
| core.GenerationMatrixTest | 8 | 0 | 0 | 0 |
| core.StatModelVerificationTest | 10 | 0 | 0 | 0 |
| core.TierRegistryTest | 34 | 0 | 0 | 0 |
| core.YieldCalibrationTest$Accuracy | 6 | 0 | 0 | 0 |
| core.YieldCalibrationTest$CumulativeRoll | 3 | 0 | 0 | 0 |
| core.YieldCalibrationTest$DefectFactor | 2 | 0 | 0 | 0 |
| core.YieldCalibrationTest$GammaFunctions | 7 | 0 | 0 | 0 |
| core.YieldMathTest$Clamping | 2 | 0 | 0 | 0 |
| core.YieldMathTest$CoreCountPenalty | 9 | 0 | 0 | 0 |
| core.YieldMathTest$PassThreshold | 5 | 0 | 0 | 0 |
| core.YieldMathTest$ResolveEffectiveYield | 10 | 0 | 0 | 0 |
| core.YieldRollEngineTest$Judging | 3 | 0 | 0 | 0 |
| core.YieldRollEngineTest$LayerStep | 8 | 0 | 0 | 0 |
| core.YieldRollEngineTest$Rejections | 5 | 0 | 0 | 0 |
| mc.data.DieRulesDataLoaderFallbackTest | 1 | 0 | 0 | 0 |
| mc.data.DieRulesJsonDataTest | 2 | 0 | 0 | 0 |
| mc.data.JsonResourceDataLoaderReloadTest | 1 | 0 | 0 | 0 |
| mc.data.SupportLineDataLoaderFallbackTest | 1 | 0 | 0 | 0 |
| mc.data.SupportLineJsonDataTest | 1 | 0 | 0 | 0 |
| mc.data.TierDataLoaderFallbackTest | 2 | 0 | 0 | 0 |
| mc.data.TierJsonDataTest | 1 | 0 | 0 | 0 |
| mc.FabConfigTest | 3 | 0 | 0 | 0 |
| mc.FabRandomServiceTest | 8 | 0 | 0 | 0 |
| mc.FabYieldServiceTest | 8 | 0 | 0 | 0 |
| mc.FoupContentsCodecTest | 13 | 0 | 0 | 0 |
| **Total** | **250** | **0** | **0** | **0** |

Game tests (from `runGameTestServer` log):

| test | environment | result |
|---|---|---|
| lithmod:trivial | lithmod:default | passed |
| lithmod:foup_stack_round_trip | lithmod:foup_default | passed |
| lithmod:foup_network_view_is_public_only | lithmod:foup_default | passed |
| lithmod:foup_encoded_size | lithmod:foup_default | passed |
| lithmod:foup_creative_tab_entries | lithmod:foup_default | passed |
| lithmod:foup_debug_commands | lithmod:foup_default | passed |
| lithmod:foup_trusted_contents | lithmod:foup_default | passed |
| lithmod:foup_stored_capacity_wins | lithmod:foup_default | passed |
| lithmod:foup_shulker_box_round_trip | lithmod:foup_default | passed |
| lithmod:foup_ender_chest_round_trip | lithmod:foup_default | passed |
| lithmod:foup_hopper_transfer | lithmod:foup_default | passed |
| lithmod:foup_shift_click_move | lithmod:foup_default | passed |
| lithmod:fab_random_salt_round_trip | lithmod:fab_random_default | passed |
| lithmod:fab_random_client_side_rejected | lithmod:fab_random_default | passed |
| lithmod:yield_layer_step | lithmod:yield_default | passed |
| lithmod:yield_pass_rate | lithmod:yield_default | passed |
| lithmod:yield_end_to_end | lithmod:yield_default | passed |
| lithmod:yield_run_spec | lithmod:yield_default | passed |
| ... (arcforge/encodedlogistics/vanilla tests) | ... | passed |

All 688 required game tests passed.

Arcforge `creosote_moves` flake check: ran `runGameTestServer` 3 times on current HEAD — 0/3 failures. The flake reported in M0-07 (1/4 failures) is a pre-existing Arcforge issue unrelated to this mod.

## 4. Spec conformance

| item | met/partly/not met | evidence |
|---|---|---|
| 1. Gate: FOUP data survives save, load, inventory moves | met | `lithmod:foup_stack_round_trip` covers copy, NBT, chest, dropped entity, `Inventory.setItem`, `playerTouch`; new tests add shulker box, ender chest, hopper, shift-click; all compare every roll at each hop |
| 2. Gate: real stop/restart with debug inspect | manual | Documented in `docs/manual-walkthrough.md` steps 1-3; headless tests cannot simulate real relog |
| 3. Network check: stream codec contains no roll/flag values | met | `lithmod:foup_network_view_is_public_only` asserts `wafer.cumulativeRoll() == CLIENT_VIEW_ROLL` and `wafer.flags() == NO_FLAGS`; `FoupContentsCodecTest.the network codec carries no roll and no flag` |
| 4. End-to-end: T3, T5, T7 pass rates match tier yield | met | `lithmod:yield_end_to_end` runs 1000 wafers each; T3 90.00%, T5 92.50%, T7 85.20% vs targets 90/93/85% |
| 5. End-to-end: T7 compute 8 cores (85%), 24 cores (44%) | met | Same test: T7 8 cores 84.20% vs 85%, T7 24 cores 43.90% vs 44% |
| 6. ImportChecker rejects com.mojang and io.netty in core | met | `checkCoreImports` task passes; `RunSpec.CODEC` moved to `RunSpecCodec` in mc package |
| 7. Data reload from data pack override | met | `TierDataLoaderFallbackTest`, `SupportLineDataLoaderFallbackTest`, `DieRulesDataLoaderFallbackTest`, `JsonResourceDataLoaderReloadTest` all verify fallback and reload |
| 8. Lot run spec (a) yieldMultiplier change mid-run doesn't affect in-flight lot | met | `lithmod:yield_run_spec` test (a) verifies spec rollCount frozen at layer 1 |
| 9. Lot run spec (b) rollsPerLayer change derived from stored spec | met | `RunSpec.rollCount` used for calibration at later layers; test verifies spec rollCount unchanged |
| 10. Lot run spec (c) tier/cores mismatch refused at later layer and evaluate | met | `lithmod:yield_run_spec` tests (c) refuse T1->T3 at layer 2, T7 8->24 at layer 2, evaluate with wrong tier/cores |
| 11. Lot run spec (d) persistent codec round trip preserves RunSpec; empty lot decodes | met | `lithmod:yield_run_spec` tests (d) verify RunSpec survives NBT round trip; empty lot decodes without spec |
| 12. Lot run spec (e) client-view lot refused by applyLayerStep and evaluateLot | met | `lithmod:yield_run_spec` tests (e) expect `IllegalArgumentException` / `IllegalStateException` |
| 13. Item definitions exist at assets/lithmod/items/foup_mk1.json and foup_mk2.json | met | Both files exist, parent `minecraft:item/generated`, textures hopper/brewing_stand |
| 14. `FoupItems.contentsOf` used for diagnostics only; gameplay paths use `trustedContentsOf` | met | `FoupGameTests` and `YieldGameTests` use `trustedContentsOf`; `contentsOf` only in creative tab test |
| 15. Arcforge creosote_moves flake baseline established | met | 0/3 failures on HEAD; pre-existing flake documented |

## 5. Not verified

| item | reason |
|---|---|
| Real server stop/start with relog and debug inspect | Headless game test server cannot simulate real client relog; documented as manual step in `docs/manual-walkthrough.md` |
| Tooltip rendering in real client | Requires display; headless tests only verify tooltip text content |
| Item model rendering in real client | Requires display; headless tests cannot verify model loads without placeholder |
| Creative middle-click duplication exploit | Documented as accepted limitation; not testable without real creative client |
| Cross-session roll reproducibility across JVM updates | Depends on `StrictMath.pow` bit-identity; boundary wafer could flip on JVM update |

## 6. Deviations

| spec says | what you did | why |
|---|---|---|
| `applyLayerStep(serverLevel, lot, tier, layerNumber, defectFactor)` signature | 5-arg overload delegates to 6-arg with `cores=null` | 5-arg call means "no core count"; rolls must calibrate against effective yield including penalty |
| Cache calibration per `(tier, rollCount, yield)` | Key is `(rollCount, yield)` | Tier only contributes through layer count (fixes rollCount) and resolved yield; keying on pair covers settings changes between layers |
| `evaluateLot(lot, tier, cores)` doesn't change lot | Also refuses lot with no run spec or mismatched tier/cores | Lot without spec has no rolls multiplied in; mismatch means calibration was wrong |
| Empty lot refused at layer step | Refused with `IllegalArgumentException` | No wafers to roll; accepting would consume cycle without advancing layer |
| Network rolls never sent to client | Kept; `applyLayerStep` takes `ServerLevel`, `evaluateLot` rejects client-view lots | Only server has real rolls; `evaluateLot` cannot check level but rejects client-view lots |
| Gamma quantile by bisection on regularized lower incomplete gamma | 15-term Lanczos `logGamma` | Pinned by `P(1,x)=1-e^-x`, `P(2,x)=1-e^-x(1+x)`, quantile inversion at every tier's roll count |
| Nothing numeric hard-coded | Core uses tier data and settings; `RegularizedGamma` has algorithm constants | Bisection tolerance and iteration caps are numerical hygiene, not gameplay tuning |
| No new dependencies | None added | ~200 lines of gamma code vs. library dependency |
| RunSpec CODEC in core | Moved to `RunSpecCodec` in mc package | Keeps core free of Mojang imports; `ImportChecker` now rejects `com.mojang` |

## 7. Open questions

1. Cross-session roll reproducibility depends on `StrictMath.pow` / `StrictMath.log` bit-identity across JVM builds; a boundary-case wafer could flip on a JVM update. **Status: Open**

2. Defect factor is per-layer; if a line degrades midway, rolls calibrated at two different weights coexist in one lot. Current behaviour allows it; should be confirmed before machines exist. **Status: Open**

3. `rollsPerLayer` changes the roll count and calibration; raising it makes existing lots more expensive to finish without touching them. Whether a config change should be refused while lots are in flight is undecided. **Status: Open**

4. The lower clamp at 1% is unreachable from shipped config (smallest multiplier 0.1 × smallest tier 60% = 6%). Only matters for a data pack with very low `default_yield_percent`. **Status: Accepted limitation**

5. A lot processed at one tier and judged at another is now refused (was permitted). The test station should decide if this is the final rule. **Status: Open**

6. Creative mode protection: `trustedContentsOf` blocks client-view injection but not server-side middle-click duplication. Saved-data keyed by lotId with duplicate rule proposed but not implemented. **Status: Documented limitation**

7. Arcforge `creosote_moves` is an optional test that fails intermittently; pre-existing, not caused by this mod. **Status: Documented**

## 8. Handoff

### Core public API (package `com.euvcraft.lithmod.core`)

- `TierRegistry`
  - `getRegistry()`, `byIndex(int)`, `byId(String)`, `required()`, `all()`, `next(Tier)`, `previous(Tier)`, `compareByIndex(Tier, Tier)`
- `GenerationMatrix`
  - `get(String line, int tierIndex)`, `isSatisfied(String line, int tierIndex, int availableGen)`, `unmet(Tier, Map<String,Integer>)`
- `DieRules`
  - `canMake(Tier, DieType, int coreCount)`, `effectiveCoreCap(Tier, DieType, int requestedCores)`
- `StatRules`
  - `statsFor(Tier, GenerationMatrix, Map<String,Integer>)`, `wafersPerHour(Tier, GenerationMatrix, Map<String,Integer>)`
- `FabSettings`
  - `foupCapacity()`, `wafersPerBoule()`, `rollsPerLayer()`, `yieldMultiplier()`, `tierYieldOverrides()`, `coreCountOptions()`, `comfortableCoreOverrides()`, `coreCountPenaltySteepness()`, `debugCommands()`, `t6Enabled()`
- `FabRandomService`
  - `storage(Level)`, `data(Level)`, `rngFor(Level, UUID, int, int)`, `requireServerSide(Level)`, `requireServerSide(boolean, String)`
- `FoupContents`
  - `empty(UUID, int)`, `withWafer(Wafer)`, `withoutWaferAt(int)`, `withRun(RunSpec)`, `run()`, `lotId()`, `capacity()`, `size()`, `lotLayer()`, `wafers()`, `publicView()`, `isEmpty()`, `isClientView()`
- `RunSpec`
  - `RunSpec(int tierIndex, int cores, double effectiveYield, int rollCount)`, `of(Tier, Integer, FabSettings)`, `matches(Tier, Integer)`, `coresOrNull()`, `NO_CORES = 0`, `coresOrZero(Integer)`
- `FoupItems.trustedContentsOf(ItemStack)` — only sanctioned way to read a lot from a stack
- `FabYieldService`
  - `applyLayerStep(ServerLevel, FoupContents, Tier, Integer, int, double)` — with core count
  - `applyLayerStep(ServerLevel, FoupContents, Tier, int, double)` — without core count
  - `evaluateLot(FoupContents, Tier, Integer)` — no level check
  - `evaluateLot(ServerLevel, FoupContents, Tier, Integer)` — explicit server check
  - `countPassing(FoupContents, Tier, Integer)` and `countPassing(ServerLevel, ...)`
- `YieldMath`
  - `passThreshold(double)`, `passes(double, double)`, `effectiveYield(double, Integer, Integer, double)`
  - `clampYield(double)`, `resolveEffectiveYield(Tier, FabSettings, Integer)`
  - `MIN_EFFECTIVE_YIELD = 0.01`, `MAX_EFFECTIVE_YIELD = 0.99`
- `YieldCalibration`
  - `calibrateBaseWeight(int, double)`, `baseWeight(int, double)`, `stepWeight(int, double, double)`, `stepRoll(Rng, double)`, `clearCache()`
  - `MIN_DEFECT_FACTOR = 0.05`
- `YieldRollEngine`
  - `applyLayerStep(FoupContents, Tier, Integer, int, double, FabSettings, RollSource)`
  - `evaluate(FoupContents, Tier, Integer)` — judges against stored spec
  - `countPassing(boolean[])`, `requireCompleteLot(FoupContents, Tier)`
  - `rollCount(Tier, FabSettings)`, `stepIndex(int, int, int)`
- `RollSource` — `Rng rngFor(int waferIndex, int stepIndex)`
- `JsonResourceDataLoader` — generic reload listener base

### Minecraft-facing API (package `com.euvcraft.lithmod.mc`)

- `FabConfig` — config binding (type `LOCAL`)
- `FabRandomData` — `SavedData` with salt
- `FoupContentsCodec` — `CODEC` (persistent, includes optional runSpec), `STREAM_CODEC` (network, public view only), `isClientView(FoupContents)`, `CLIENT_VIEW_LOT_ID`, `CLIENT_VIEW_ROLL`
- `FoupItems` — `configuredCapacity()`, `emptyFoup(Item)`, `with(Item, FoupContents)`, `contentsOf(ItemStack)`, `trustedContentsOf(ItemStack)`, `isFoup(ItemStack)`
- `RunSpecCodec.CODEC` — persistent codec for RunSpec
- `FoupDebugCommand` — `/debug foup fill|inspect` (requires `features.debugCommands=true`)
- `ModItems.FOUP_MK1`, `ModItems.FOUP_MK2`
- `ModDataComponents.FOUP_CONTENTS`
- `ModCreativeTabs.MAIN`

### Config keys (all under `lithmod` namespace, type `LOCAL`)

| key | type | default | range |
|---|---|---|---|
| `general.foupCapacity` | int | 25 | 1..64 |
| `general.wafersPerBoule` | int | 25 | 1..64 |
| `general.rollsPerLayer` | int | 1 | 1..8 |
| `general.yieldMultiplier` | double | 1.0 | 0.1..2.0 |
| `general.tierYieldOverrides` | list<string> | [] | T1..T8, 1..99 |
| `compute.coreCountOptions` | list<int> | [1,2,4,8,12,24] | 1..256 |
| `compute.comfortableCoreOverrides` | list<string> | [] | T6..T8, 1..256 |
| `compute.coreCountPenaltySteepness` | double | 2.0 | 0..10 |
| `features.debugCommands` | boolean | false | — |

### Data file locations

- `data/lithmod/tiers/t1.json` ... `t8.json` — tier definitions
- `data/lithmod/support_lines/matrix.json` — generation matrix
- `data/lithmod/die_rules/rules.json` — die type rules

### Creative-mode findings (accepted limitation)

- Creative inventory replacement creates client-view lot with `Wafer.NO_ROLLS` (1.0f) → passes every yield check. `trustedContentsOf` detects and rejects, returns fresh empty lot, logs warning.
- Creative middle-click duplicates genuine server lot (same hidden rolls, same lotId) → `trustedContentsOf` cannot detect.
- Mod is not intended for creative worlds; these are documented, not fixed.

### 26.3 API notes (consolidated from all M0 reports)

| Feature | Discovery | Source Report |
|---|---|---|
| Reload listener signature | `SimpleJsonResourceReloadListener.apply(Map<Identifier,T>, ResourceManager, ProfilerFiller)` | M0-03b |
| Config type constants | `ModConfig.Type.LOCAL` (no `SERVER` type in 26.3) | M0-03c |
| Saved data API | `SavedData` attached via `overworld().getDataStorage()`; type id determines file name | M0-07 |
| Game test registration | `RegisterGameTestsEvent`, `TestEnvironmentDefinition`, `FunctionGameTestInstance`, `DeferredRegister<TEST_FUNCTION>` | M0-03c |
| Codec forms | `RecordCodecBuilder.create(instance -> instance.group(...).apply(instance, ...))` works on classpath | M0-07b |
| Item models | `minecraft:item/generated` with `layer0` texture; explicit model file needed in 1.21.4+ | M0-08 |
| Data component | `ItemStack.CODEC` for NBT; `StreamCodec<ByteBuf, T>` for network | M0-08 |
| Container NBT | `ContainerHelper.saveAllItems/loadAllItems` with `TagValueOutput/Input` | M0-08 |
| Creative packet | `ServerboundSetCreativeModeSlotPacket` uses `ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC` | M0-08 |
| Slot cloning | `Slot.safeClone()` → `item.copyWithCount(item.getMaxStackSize())` | M0-08 |
| StrictMath | Used for `pow`, `log`, `abs`, `exp` in calibration for cross-JVM reproducibility | M0-09b |

Report path: `docs/reports/M0-10_gate-verification-and-handoff-notes.md`

---

**Summary:** All M0 foundation work verified. 250 unit tests + 688 game tests pass. FOUP data survives all container moves and save/load with rolls preserved. Network codec leaks no hidden data. End-to-end simulation confirms yield model and core-count penalty. Run spec freezes parameters at layer 1. ImportChecker enforces core purity. Data reloads from overrides. Documentation and manual walkthrough written. Ready for M1.