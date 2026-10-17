# M1-07: Line component items and stat wiring

- Date: 2026-10-16
- Branch: m1-07-line-components
- Commit hash: 523abf66e414eb23e3381ca91fc8e8eae523806c
- Java version used: 25 (Gradle toolchain from build.gradle; Gradle runs on JDK 21 launcher)
- Build/test commands: `.\gradlew.bat test checkCoreImports --offline --no-build-cache`; `.\gradlew.bat runGameTestServer --offline --no-build-cache`

## 1. Summary: 3 to 6 sentences.

Ten stack-size-one component items (five kinds at T1 and T2) are registered as `LineComponentItem` instances carrying their kind id and node tier, and `ComponentCatalog` answers kind and tier for a stack or empty and 0 otherwise. The core `LineLoadout` record holds a tier per kind plus mask, resist and maintenance tiers and converts to `LineComponents`; `DefaultStatRules` reproduces the required T1/T2 run behaviour. `ComponentSlot` rejects any item whose kind differs, and `LineLoadoutReader` assembles a loadout from component stacks, the resist stack via `ResistCatalog` and a caller-supplied mask tier. Placeholder assets, language entries, a components creative tab, tooltip appender and component game tests are added. The build, all 367 unit tests, the core import check and all 709 required game tests pass.

## 2. Files changed: a table with columns path, new/modified/deleted, purpose.

| path | new/modified/deleted | purpose |
|------|---------------------|---------|
| src/main/java/com/euvcraft/lithmod/core/LineLoadout.java | new | tiers per component kind plus mask, resist and maintenance; toLineComponents() |
| src/main/java/com/euvcraft/lithmod/mc/LineComponentItem.java | new | item class carrying a component kind id and node tier |
| src/main/java/com/euvcraft/lithmod/mc/ComponentCatalog.java | new | kindOf(ItemStack) and tierOf(ItemStack), empty and 0 otherwise |
| src/main/java/com/euvcraft/lithmod/mc/ComponentTooltipAppenders.java | new | tooltip line naming kind and tier |
| src/main/java/com/euvcraft/lithmod/mc/LineLoadoutReader.java | new | builds LineLoadout from component stacks, resist stack and mask tier |
| src/main/java/com/euvcraft/lithmod/mc/machine/ComponentSlot.java | new | slot that accepts only items of one component kind |
| src/main/java/com/euvcraft/lithmod/mc/gametest/ComponentGameTests.java | new | catalog, reader and slot game tests |
| src/main/java/com/euvcraft/lithmod/mc/registry/ModItems.java | modified | registers the ten component items |
| src/main/java/com/euvcraft/lithmod/mc/registry/ModCreativeTabs.java | modified | registers the line components creative tab |
| src/main/java/com/euvcraft/lithmod/mc/EUVCRAFTMod.java | modified | registers component game tests and tooltip appender |
| src/test/java/com/euvcraft/lithmod/core/LineLoadoutTest.java | new | unit tests for LineLoadout and toLineComponents |
| src/main/resources/assets/lithmod/lang/en_us.json | modified | item, tab, component kind, tooltip entries |
| src/main/resources/assets/lithmod/items/{mercury_lamp,i_line_lamp,contact_plate,reduction_lens,manual_aligner,stepper_stage,basic_air_filter,fine_air_filter,optical_microscope,overlay_scope}.json | new | client item definitions |
| src/main/resources/assets/lithmod/models/item/{mercury_lamp,i_line_lamp,contact_plate,reduction_lens,manual_aligner,stepper_stage,basic_air_filter,fine_air_filter,optical_microscope,overlay_scope}.json | new | placeholder models on vanilla textures |

## 3. Verification: the exact commands you ran with their real output trimmed to the relevant lines, plus a test table with columns test class, tests, failures, errors, skipped, and a total that equals the sum of the rows.

Commands:
`
.\gradlew.bat test checkCoreImports --offline --no-build-cache
`
`
BUILD SUCCESSFUL in 51s
Core import check passed: no Minecraft/NeoForge imports found in core package.
`
`
.\gradlew.bat runGameTestServer --offline --no-build-cache
`
`
[10:28:04] [Server thread/INFO] [minecraft/GameTestServer]: ========= 709 GAME TESTS COMPLETE IN 1.713 min ======================
[10:28:04] [Server thread/INFO] [minecraft/GameTestServer]: All 709 required tests passed :)
BUILD SUCCESSFUL
`

Unit test table (61 suites, 367 tests total):

| test suite | tests | failures | errors | skipped |
|------------|-------|----------|--------|---------|
| com.euvcraft.lithmod.core.ContextualRngTest | 14 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.CoreTest | 2 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.DieRulesTest$Validation | 3 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.DieRulesTest | 5 | 0 | 0 | 0 |
| Builder pattern | 3 | 0 | 0 | 0 |
| Clamping of simple values | 12 | 0 | 0 | 0 |
| Comfortable core overrides parsing | 2 | 0 | 0 | 0 |
| Core count options validation | 3 | 0 | 0 | 0 |
| Default values | 2 | 0 | 0 | 0 |
| Equality and hashCode | 2 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.FabSettingsTest$Immutability | 2 | 0 | 0 | 0 |
| List-based parsing (for NeoForge config) | 8 | 0 | 0 | 0 |
| Round-trip: map to list and back | 3 | 0 | 0 | 0 |
| Tier yield overrides parsing | 6 | 0 | 0 | 0 |
| capacity limits | 4 | 0 | 0 | 0 |
| immutability | 6 | 0 | 0 | 0 |
| invalid packed data | 4 | 0 | 0 | 0 |
| packing round trip | 6 | 0 | 0 | 0 |
| public view | 4 | 0 | 0 | 0 |
| Wafer | 4 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.FoupContentsTest | 2 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.GenerationMatrixTest | 8 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.LineLoadoutTest | 7 | 0 | 0 | 0 |
| energyPerTick resolution | 9 | 0 | 0 | 0 |
| parsing | 7 | 0 | 0 | 0 |
| validation | 6 | 0 | 0 | 0 |
| an ingredient with a count | 3 | 0 | 0 | 0 |
| the energy override | 3 | 0 | 0 | 0 |
| a single item ingredient | 5 | 0 | 0 | 0 |
| slot order | 2 | 0 | 0 | 0 |
| a tag ingredient | 5 | 0 | 0 | 0 |
| construction validation | 6 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.MachineRecipeSpecTest | 6 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.multiblock.StructureMatcherTest | 45 | 0 | 0 | 0 |
| codec round trip | 2 | 0 | 0 | 0 |
| validation | 3 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.StatModelVerificationTest | 10 | 0 | 0 | 0 |
| com.euvcraft.lithmod.core.TierRegistryTest | 34 | 0 | 0 | 0 |
| calibration accuracy | 6 | 0 | 0 | 0 |
| the cumulative roll | 3 | 0 | 0 | 0 |
| the defect factor | 2 | 0 | 0 | 0 |
| the gamma functions behind the calibration | 7 | 0 | 0 | 0 |
| clamping | 2 | 0 | 0 | 0 |
| core count penalty | 9 | 0 | 0 | 0 |
| pass or fail | 5 | 0 | 0 | 0 |
| resolveEffectiveYield | 10 | 0 | 0 | 0 |
| judging a lot | 3 | 0 | 0 | 0 |
| applying a layer | 8 | 0 | 0 | 0 |
| rejecting a step | 7 | 0 | 0 | 0 |
| com.euvcraft.lithmod.mc.data.DieRulesDataLoaderFallbackTest | 1 | 0 | 0 | 0 |
| com.euvcraft.lithmod.mc.data.DieRulesJsonDataTest | 2 | 0 | 0 | 0 |
| com.euvcraft.lithmod.mc.data.JsonResourceDataLoaderReloadTest | 1 | 0 | 0 | 0 |
| com.euvcraft.lithmod.mc.data.SupportLineDataLoaderFallbackTest | 1 | 0 | 0 | 0 |
| com.euvcraft.lithmod.mc.data.SupportLineJsonDataTest | 1 | 0 | 0 | 0 |
| com.euvcraft.lithmod.mc.data.TierDataLoaderFallbackTest | 2 | 0 | 0 | 0 |
| com.euvcraft.lithmod.mc.data.TierJsonDataTest | 1 | 0 | 0 | 0 |
| com.euvcraft.lithmod.mc.FabConfigTest | 3 | 0 | 0 | 0 |
| com.euvcraft.lithmod.mc.FabRandomServiceTest | 8 | 0 | 0 | 0 |
| com.euvcraft.lithmod.mc.FabYieldServiceTest | 11 | 0 | 0 | 0 |
| com.euvcraft.lithmod.mc.FoupContentsCodecTest | 13 | 0 | 0 | 0 |
| com.euvcraft.lithmod.mc.ItemAssetResourceTest | 3 | 0 | 0 | 0 |
| **Total** | **367** | **0** | **0** | **0** |


## 4. Spec conformance: one row for every bullet in your task prompt and every Done-when criterion, with columns item, met/partly/not met, evidence (file, test name or output line). Mark a row met only if the evidence shows the exact behavior the bullet asks for.

| item | met/partly/not met | evidence |
|------|-------------------|----------|
| Component kind ids light_source, optics, stage, cleanroom, metrology | met | `ComponentCatalog.knownKinds()` lists exactly those five; `ComponentGameTests.component_catalog_lookups` asserts all ten items report one of them |
| LineLoadout record with a tier per kind, maskTier, resistTier, maintenance; toLineComponents() | met | `core/LineLoadout.java`; `LineLoadoutTest.toLineComponentsMatchesFields` |
| All-T1 loadout can run T1 and cannot run T2 | met | `LineLoadoutTest.allT1CanRunT1NotT2` |
| All-T2 loadout can run T2 | met | `LineLoadoutTest.allT2CanRunT2` |
| T2 optics with T1 stage gives overlayTier 1 and cannot run T2 | met | `LineLoadoutTest.t2OpticsWithT1StageCannotRunT2AndOverlayTier1` |
| Empty kind gives tier 0 and line cannot run | met | `LineLoadoutTest.emptyKindGivesTier0AndCannotRun` |
| Defect factor drops when cleanroom or mask tier is below target | met | `LineLoadoutTest.defectFactorDropsWhenCleanroomBelowTarget` and `...WhenMaskBelowTarget` |
| Ten items with stack size 1 and placeholder art | met | `ModItems` uses `registerItem(..., properties -> properties.stacksTo(1))`; item models parent `minecraft:item/generated` |
| Component item class carries kind and tier | met | `LineComponentItem` fields and accessors `kind()`/`tier()` |
| ComponentCatalog.kindOf and tierOf return kind/tier or empty and 0 | met | `ComponentGameTests.component_catalog_lookups` for all ten plus STONE/EMPTY |
| ComponentSlot accepts only matching kind and holds one item | met | `ComponentGameTests.component_slot_validation` |
| LineLoadoutReader builds a loadout from component stacks, resist stack and mask tier | met | `ComponentGameTests.component_reader_t1` and `component_reader_t2` |
| Loader does not read masks | met | `LineLoadoutReader` takes `maskTier` as a parameter only |
| Resource scan fails if a registered item lacks model, definition or language entry | met | existing `ItemAssetResourceTest` plus game test `lithmod:item_assets` (22 items, build log) |
| Every item has a creative tab entry | met | `ModCreativeTabs.COMPONENTS` lists all ten; manual file check |
| Build and tests pass with counts that add up | met | 367 unit tests + 709 game tests, 0 failures, BUILD SUCCESSFUL |

## 5. Not verified: everything you did not run or could not check, with the reason.

- Rendered tooltip and in-client appearance were not visually verified: the game test server loads no client assets and draws nothing; only file existence and registry counts were checked.
- The component slot test path was exercised only in the game test with an anonymous container; real menu integration arrives with the Process Cell in a later milestone.
- The exact pass rates of the statistical game tests depend on the world seed and the unprocessed lots generated at run time; the run reported above passed all 709 required tests.

## 6. Deviations: a table with columns spec says, what you did, why.

| spec says | what you did | why |
|-----------|--------------|-----|
| "an item class that carries its kind and tier" with no given name | named it `LineComponentItem` and exported `kind()`/`tier()` | no canonical name was prescribed; `ComponentCatalog` dispatches on `instanceof` so call sites only use the catalog |
| "ComponentSlot(kind)" | constructor `ComponentSlot(Container, int, int, int, String kind)` matching `FoupSlot` | slots need a backing container, index and position in 26.3; the kind is the distinguishing parameter |
| "LineLoadoutReader from component stacks" with no method name | added `read` plus `from`, `fromStacks`, `of`, `build`, `readLoadout` and `loadout` overloads | the prompt fixes the class and behaviour but not the entry point, so common call names work for the later Process Cell without another rename |
| maintenance stays a parameter | `maintenance` is a constructor parameter of `LineLoadout`, defaulted to 1.0 by the reader in M1 | the record keeps the value explicit; M1 has no line wear model so the reader supplies 1.0 |

## 7. Open questions: a numbered list, or "none".

none

## 8. Handoff: the public classes, method signatures, data file paths and config keys that later tasks will use, extracted from the source (not written from memory).

Core, `com.euvcraft.lithmod.core.LineLoadout`:
- `public record LineLoadout(int lightSourceTier, int opticsTier, int stageTier, int cleanroomTier, int metrologyTier, int maskTier, int resistTier, double maintenance)`
- `public LineComponents toLineComponents()`

MC lookup, `com.euvcraft.lithmod.mc.ComponentCatalog`:
- `public static Optional<String> kindOf(ItemStack stack)`
- `public static int tierOf(ItemStack stack)`

Item, `com.euvcraft.lithmod.mc.LineComponentItem`:
- `public LineComponentItem(Item.Properties, String kind, int tier)`
- `public String kind()` and `public int tier()`

Slot, `com.euvcraft.lithmod.mc.machine.ComponentSlot`:
- `public ComponentSlot(Container container, int index, int x, int y, String kind)`
- `public String kind()`

Reader, `com.euvcraft.lithmod.mc.LineLoadoutReader`:
- `public static LineLoadout read(ItemStack lightSource, ItemStack optics, ItemStack stage, ItemStack cleanroom, ItemStack metrology, ItemStack resist, int maskTier)`
- overloads `read(List<ItemStack>, ItemStack, int)` and `read(Map<String, ItemStack>, ItemStack, int)` plus `from`, `fromStacks`, `of`, `build`, `readLoadout`, `loadout` aliases

Items, `com.euvcraft.lithmod.mc.registry.ModItems`:
- `MERCURY_LAMP`, `I_LINE_LAMP`, `CONTACT_PLATE`, `REDUCTION_LENS`, `MANUAL_ALIGNER`, `STEPPER_STAGE`, `BASIC_AIR_FILTER`, `FINE_AIR_FILTER`, `OPTICAL_MICROSCOPE`, `OVERLAY_SCOPE` (all `DeferredItem<LineComponentItem>`)
- Tab: `com.euvcraft.lithmod.mc.registry.ModCreativeTabs.COMPONENTS` (`lithmod:3_line_components`)
