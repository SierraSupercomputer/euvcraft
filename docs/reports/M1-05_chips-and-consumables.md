# M1-05: Chips, scrap and consumable items

- Date: 2026-10-15
- Branch: m1-05-chips-and-consumables (worktree of master at abf867d, which has M1-00 and M1-01 merged)
- Commit hash: 71365e8 (code, assets and tests); the report is 37c8e0a plus one correction commit after it
- Java version used: 25.0.4.1 (Eclipse Temurin, Gradle toolchain from build.gradle; Gradle 9.2.1 runs on a 21 launcher JVM)
- Build/test commands: `.\gradlew.bat cleanTest test checkCoreImports --offline --no-build-cache`; `.\gradlew.bat runGameTestServer --offline --no-build-cache`

## 1. Summary: 3 to 6 sentences.
Seven plain items are registered: `logic_chip_t1`, `logic_chip_t2`, `memory_chip_t2`, `chip_scrap`, `resist_t1`, `resist_t2` and `chip_package`, each with a client item definition, an item model that parents a vanilla texture, a language entry and an entry in a new `lithmod:1_chips_and_consumables` creative tab. `ChipCatalog` answers a die type and node tier with the chip item and back again, and its entry list is the only place a later die type has to be added. `ResistCatalog` reads a new `lithmod:resist_tier` data component and answers 0 for any stack that is not a resist. The chip tooltip is one translatable line built from the catalog alone, so it names the die type and the node tier and carries no wafer count, roll or yield. Coverage is one JUnit resource scan (`ItemAssetResourceTest`, 3 tests) and three game tests in the `lithmod:chip_default` environment (`chip_catalog_lookups`, `chip_tooltip_lines`, `item_assets`), and each of the new tests was shown to fail when the code or a resource is broken on purpose.

## 2. Files changed: a table with columns path, new/modified/deleted, purpose.
| path | new/modified/deleted | purpose |
|------|---------------------|---------|
| src/main/java/com/euvcraft/lithmod/mc/ChipCatalog.java | new | die type and node tier to chip item, and back |
| src/main/java/com/euvcraft/lithmod/mc/ResistCatalog.java | new | resist tier of a stack, 0 when the stack is not a resist |
| src/main/java/com/euvcraft/lithmod/mc/ChipTooltipAppenders.java | new | tooltip line for every chip item, built from the catalog |
| src/main/java/com/euvcraft/lithmod/mc/gametest/ChipGameTests.java | new | game tests for the catalog, the resist tiers, the tooltip and the item assets |
| src/main/java/com/euvcraft/lithmod/mc/registry/ModItems.java | modified | registers the seven items |
| src/main/java/com/euvcraft/lithmod/mc/registry/ModDataComponents.java | modified | registers `lithmod:resist_tier` |
| src/main/java/com/euvcraft/lithmod/mc/registry/ModCreativeTabs.java | modified | registers the chips and consumables tab |
| src/main/java/com/euvcraft/lithmod/mc/EUVCRAFTMod.java | modified | registers the chip tooltip appender and the chip game tests |
| src/main/resources/assets/lithmod/lang/en_us.json | modified | item names, tab title, die type and tier names, chip tooltip line |
| src/main/resources/assets/lithmod/items/{logic_chip_t1,logic_chip_t2,memory_chip_t2,chip_scrap,resist_t1,resist_t2,chip_package}.json | new | client item definitions |
| src/main/resources/assets/lithmod/models/item/{logic_chip_t1,logic_chip_t2,memory_chip_t2,chip_scrap,resist_t1,resist_t2,chip_package}.json | new | placeholder item models on vanilla textures |
| src/test/java/com/euvcraft/lithmod/mc/ItemAssetResourceTest.java | new | scans the bundled client resources without the game |

## 3. Verification: the exact commands you ran with their real output trimmed to the relevant lines, plus a test table with columns test class, tests, failures, errors, skipped, and a total that equals the sum of the rows.
Commands:
```
.\gradlew.bat cleanTest test checkCoreImports --offline --no-build-cache
.\gradlew.bat runGameTestServer --offline --no-build-cache
```

Relevant output (unit tests and import check):
```
Core import check passed: no Minecraft/NeoForge imports found in core package.
ItemAssetResourceTest > every client item definition has a model and a language entry PASSED
ItemAssetResourceTest > the tab, tooltip, die type and tier language entries exist PASSED
ItemAssetResourceTest > no item model is left without a client item definition PASSED
BUILD SUCCESSFUL in 28s
```

Relevant output (game tests):
```
[19:07:38] [Worker-Main-3/INFO] [com.euvcraft.lithmod.mc.EUVCRAFTMod/]: Registered EUVCRAFT chip game tests
[19:08:16] [Server thread/INFO] [minecraft/GameTestRunner]: Running test environment 'lithmod:chip_default' batch 0 (3 tests)...
[19:08:16] [Server thread/INFO] [com.euvcraft.lithmod.mc.EUVCRAFTMod/]: Checked assets for 9 EUVCRAFT items
[19:08:58] [Server thread/INFO] [minecraft/GameTestServer]: ========= 691 GAME TESTS COMPLETE IN 1.268 min ======================
[19:08:58] [Server thread/INFO] [minecraft/GameTestServer]: All 691 required tests passed :)
BUILD SUCCESSFUL in 2m 47s
```
691 = the 688 tests that passed on abf867d plus the three tests of this task.

File listing for the creative tab check (one client item definition and one item model per registered item):
```
=== item definitions ===
chip_package.json  chip_scrap.json  foup_mk1.json  foup_mk2.json
logic_chip_t1.json  logic_chip_t2.json  memory_chip_t2.json  resist_t1.json  resist_t2.json
=== item models ===
chip_package.json  chip_scrap.json  foup_mk1.json  foup_mk2.json
logic_chip_t1.json  logic_chip_t2.json  memory_chip_t2.json  resist_t1.json  resist_t2.json
=== lang keys ===
itemGroup.lithmod.chips, item.lithmod.logic_chip_t1, item.lithmod.logic_chip_t2, item.lithmod.memory_chip_t2,
item.lithmod.chip_scrap, item.lithmod.resist_t1, item.lithmod.resist_t2, item.lithmod.chip_package,
dieType.lithmod.logic, dieType.lithmod.memory, tier.lithmod.t1, tier.lithmod.t2, tooltip.lithmod.chip
```

Vanilla textures the seven models point at, checked against `assets/minecraft/textures/item/` of the 26.3 client jar:
```
total item textures: 831
redstone            EXISTS        (logic_chip_t1)
lapis_lazuli        EXISTS        (logic_chip_t2)
emerald             EXISTS        (memory_chip_t2)
iron_nugget         EXISTS        (chip_scrap)
honeycomb           EXISTS        (resist_t1)
slime_ball          EXISTS        (resist_t2)
paper               EXISTS        (chip_package)
```

Unit test table (counts from build/test-results/test/*.xml, 263 tests):

| test class | tests | failures | errors | skipped |
|------------|-------|----------|--------|---------|
| core.ContextualRngTest | 14 | 0 | 0 | 0 |
| core.CoreTest | 2 | 0 | 0 | 0 |
| core.DieRulesTest | 8 | 0 | 0 | 0 |
| core.FabSettingsTest | 43 | 0 | 0 | 0 |
| core.FoupContentsTest | 30 | 0 | 0 | 0 |
| core.GenerationMatrixTest | 8 | 0 | 0 | 0 |
| core.RunSpecTest | 5 | 0 | 0 | 0 |
| core.StatModelVerificationTest | 10 | 0 | 0 | 0 |
| core.TierRegistryTest | 34 | 0 | 0 | 0 |
| core.YieldCalibrationTest | 18 | 0 | 0 | 0 |
| core.YieldMathTest | 26 | 0 | 0 | 0 |
| core.YieldRollEngineTest | 18 | 0 | 0 | 0 |
| mc.data.DieRulesDataLoaderFallbackTest | 1 | 0 | 0 | 0 |
| mc.data.DieRulesJsonDataTest | 2 | 0 | 0 | 0 |
| mc.data.JsonResourceDataLoaderReloadTest | 1 | 0 | 0 | 0 |
| mc.data.SupportLineDataLoaderFallbackTest | 1 | 0 | 0 | 0 |
| mc.data.SupportLineJsonDataTest | 1 | 0 | 0 | 0 |
| mc.data.TierDataLoaderFallbackTest | 2 | 0 | 0 | 0 |
| mc.data.TierJsonDataTest | 1 | 0 | 0 | 0 |
| mc.FabConfigTest | 3 | 0 | 0 | 0 |
| mc.FabRandomServiceTest | 8 | 0 | 0 | 0 |
| mc.FabYieldServiceTest | 11 | 0 | 0 | 0 |
| mc.FoupContentsCodecTest | 13 | 0 | 0 | 0 |
| mc.ItemAssetResourceTest | 3 | 0 | 0 | 0 |
| **Total** | **263** | **0** | **0** | **0** |

Negative controls (each break was reverted before the runs above):
```
.\gradlew.bat test --offline --tests "com.euvcraft.lithmod.mc.ItemAssetResourceTest"   # memory_chip_t2 model file renamed away
ItemAssetResourceTest > every client item definition has a model and a language entry FAILED
    AssertionFailedError: no item model for memory_chip_t2, the item renders as the missing-model cube ==> expected: not <null>
ItemAssetResourceTest > no item model is left without a client item definition FAILED
3 tests completed, 2 failed

.\gradlew.bat test --offline --tests "com.euvcraft.lithmod.mc.ItemAssetResourceTest"   # item.lithmod.chip_scrap entry removed
ItemAssetResourceTest > every client item definition has a model and a language entry FAILED
    AssertionFailedError: no language entry for chip_scrap, the creative tab shows its registry path ==> expected: <true> but was: <false>

.\gradlew.bat runGameTestServer --offline   # chip_scrap definition renamed away and the logic T1 catalog entry deleted
[19:05:02] [Server thread/ERROR] [minecraft/LogTestReporter]: lithmod:chip_catalog_lookups failed at 9745545, 4, -12429975! no chip for logic at tier 1 on tick 0
[19:05:02] [Server thread/ERROR] [minecraft/LogTestReporter]: lithmod:chip_tooltip_lines failed at 9745551, 4, -12429975! lithmod:logic_chip_t1 does not have exactly a name line and a chip line: 2 -> 1 on tick 0
[19:05:02] [Server thread/ERROR] [minecraft/LogTestReporter]: lithmod:item_assets failed at 9745477, 4, -12429951! lithmod:chip_scrap has no client item definition on tick 0
[19:05:03] [Server thread/ERROR] [minecraft/GameTestServer]: 3 required tests failed :(
```

## 4. Spec conformance: one row for every bullet in your task prompt and every Done-when criterion, with columns item, met/partly/not met, evidence (file, test name or output line).
| item | met/partly/not met | evidence |
|------|-------------------|----------|
| Chips `logic_chip_t1`, `logic_chip_t2`, `memory_chip_t2` registered, one item per die type and tier | met | `ModItems.java` lines registering the three items; `ChipGameTests.chip_catalog_lookups` asserts each catalog answer equals the matching `ModItems` item |
| Chips are ordinary stackable items with stack size 64 | met | no `stacksTo` call on the seven items, so the vanilla maximum applies; `chip_catalog_lookups` asserts `stack.getMaxStackSize() == 64` and `item.getMaxStackSize(stack) == 64` for all seven |
| Chip tooltip is a translatable line naming the die type and the tier | met | `chip_tooltip_lines`: line 1 is `tooltip.lithmod.chip` with arguments `dieType.lithmod.<die>` and `tier.lithmod.t<n>` for each of the three chips |
| Chip tooltip does not read tier data | met | `ChipTooltipAppenders` builds the line from `ChipCatalog.designOf` only, which holds a die type string and a tier index; no tier data is read anywhere in the tooltip path |
| `chip_scrap`, one generic scrap item, stackable | met | `ModItems.CHIP_SCRAP`; stack size asserted in `chip_catalog_lookups` |
| `resist_t1` and `resist_t2` implemented so a resist item reports its tier 1 or 2 | met | `ModItems.RESIST_T1`/`RESIST_T2` carry `ModDataComponents.RESIST_TIER`; `chip_catalog_lookups` asserts `ResistCatalog.tierOf` returns 1 and 2 |
| `chip_package` registered | met | `ModItems.CHIP_PACKAGE`; present in the chips tab, asserted in `chip_catalog_lookups` |
| Every item has a model, a client item definition, a language entry and a creative tab entry | met | `item_assets` walks the item registry and logs "Checked assets for 9 EUVCRAFT items"; `ItemAssetResourceTest` does the same scan from the classpath; `chip_catalog_lookups` asserts all seven appear in `ModCreativeTabs.CHIPS` |
| `ChipCatalog.chipFor(String, int)` returns the chip item or empty | met | returns `Optional<Item>`; `chip_catalog_lookups` asserts present for the three chips and empty for memory at T1 |
| Memory at T1 and every compute chip return empty today | met | `chip_catalog_lookups` asserts empty for memory at tier 1 and, over every tier of `TierDataLoader.getRegistry().all()`, for compute and NAND |
| The catalog extends by registering more entries, with no change to callers | met | `ChipCatalog.ENTRIES` is the only list; `chipFor` and `designOf` read it, and the negative control deleted one entry without touching either method |
| `ResistCatalog.tierOf(ItemStack)` returns 1, 2 or 0 | met | `chip_catalog_lookups` asserts 1, 2, 0 for stone, 0 for a chip, 0 for an empty stack and 0 for a resist whose component was removed |
| Placeholder art only, vanilla textures through parent models, no new binary art | met | the seven models use `"parent": "minecraft:item/generated"` with `minecraft:item/<texture>`; the textures were checked to exist in the 26.3 client jar; `git status` shows only .json files added |
| Pure logic in core, Minecraft-facing code in mc | met | `ChipCatalog` and `ResistCatalog` return `Item`/`ItemStack` and live in `mc`; `checkCoreImports` reports "no Minecraft/NeoForge imports found in core package" |
| Nothing numeric hard-coded beyond item identity | met | no config, tier data or JSON number is read by the new code; the only literals are the resist tier indexes 1 and 2 in the item definitions and the die type indexes in `ChipCatalog.ENTRIES`, which are what the items are |
| Server authoritative, no hidden values in tooltips | met | the tooltip line is built from two translatable keys and the game test rejects any numeric argument; no chip carries a data component at all |
| No machines, no recipes, no capabilities, no changes to Encoded Logistics or Arcforge | met | the diff touches no block, menu, recipe or capability file and neither mod jar; `runGameTestServer` still runs the Arcforge game tests (they passed inside the 691) |
| Done when: build and all tests pass with counts that add up | met | `BUILD SUCCESSFUL in 28s` and `All 691 required tests passed :)`; 24 unit test rows sum to 263 |
| Done when: every item shows a model in the creative tab, files verified to exist | met | the two file listings in section 3 show 9 item definitions and 9 item models side by side, and `chip_catalog_lookups` asserts all seven new items are in the tab; rendering itself is listed under not verified |
| Done when: report at docs/reports/M1-05_chips-and-consumables.md | met | this file |

## 5. Not verified: everything you did not run or could not check, with the reason.
- Rendering in a client was not run: `runGameTestServer` is a dedicated server, which loads no client assets and draws nothing. The model files, item definitions and language entries were verified to exist and to name real vanilla textures, not to appear on screen.
- The rendered tooltip text ("Die type: Logic, node tier: T1") was not checked, because a server has no language file for `lithmod` and `Component.getString()` would return the raw key there. The game test checks the key and the two arguments instead, and `ItemAssetResourceTest` checks that the language entries exist.
- Dropping, crafting, burning or otherwise moving these items in a world was not tried; the items have no behaviour and no recipe is in scope for this task.
- `ResistCatalog.tierOf` was not tested against a stack that arrives from the network with a hostile tier value; the value comes from a registered `DataComponentType<Integer>` and no code path in this milestone writes anything but 1 or 2.
- The `lithmod:1_chips_and_consumables` tab was not opened in a client; `chip_catalog_lookups` builds its contents on the server and checks that all seven items are in it.
- The M1-02 and M1-03 work that sits uncommitted in the main working tree was not built or tested against this branch; this branch is master at abf867d plus the work below.

## 6. Deviations: a table with columns spec says, what you did, why.
| spec says | what you did | why |
|-----------|--------------|-----|
| "a creative tab entry" for each item | added a second tab `lithmod:1_chips_and_consumables` instead of adding to `lithmod:0_main` | the M0-08 game test `foup_creative_tab_entries` asserts that the main tab holds exactly 2 entries; adding to it would have meant rewriting a test this task does not own |
| `chipFor` "returns the chip item or empty" | returns `Optional<Item>` | an `Optional` cannot be confused with `ItemStack.EMPTY` or `Items.AIR` at a call site, and it makes the empty case explicit in the tests |
| "Implement them so that a resist item reports its tier" | added a `lithmod:resist_tier` data component and made the resists plain items | a data component is the 26.3 way to carry a value on an item, needs no `Item` subclass, and keeps `ResistCatalog` free of `instanceof` checks |
| stack size 64 stated in the prompt | no `stacksTo` call, the vanilla default of 64 is used | rule 3: no numeric gameplay value is hard-coded when the vanilla default already is the value |
| die type and tier names in the tooltip | new language keys `dieType.lithmod.<die>` and `tier.lithmod.t<n>` | the tooltip must not read tier data, so the names come from the language file; only the keys that today's three chips can reach are present |
| the prompt does not say what a chip is called | each chip also has the ordinary `item.lithmod.<id>` name ("Logic Chip T1") that the game shows above the tooltip line | an item without a name entry shows its registry path in the creative tab, and the tooltip line then carries the die type and tier |

## 7. Open questions: a numbered list, or "none".
1. Should `ChipCatalog.chipFor` stay with an `Optional<Item>` return, or would the project rather have `Optional<ItemStack>` at the call sites in the Test and Package machine?
2. Should the two creative tabs be merged once M1-12 decides how many tabs the finished mod wants, and who owns the `foup_creative_tab_entries` assertion then?
3. Should the chip tooltip also name where the die came from (mask, lot) later, or stay a pure die-type and tier line?

## 8. Handoff: the public classes, method signatures, data file paths and config keys that later tasks will use, extracted from the source (not written from memory).
Items, `com.euvcraft.lithmod.mc.registry.ModItems`:
- `public static final DeferredRegister.Items ITEMS`
- `public static final DeferredItem<Item> LOGIC_CHIP_T1` (`lithmod:logic_chip_t1`)
- `public static final DeferredItem<Item> LOGIC_CHIP_T2` (`lithmod:logic_chip_t2`)
- `public static final DeferredItem<Item> MEMORY_CHIP_T2` (`lithmod:memory_chip_t2`)
- `public static final DeferredItem<Item> CHIP_SCRAP` (`lithmod:chip_scrap`)
- `public static final DeferredItem<Item> RESIST_T1` (`lithmod:resist_t1`, `resist_tier` = 1)
- `public static final DeferredItem<Item> RESIST_T2` (`lithmod:resist_t2`, `resist_tier` = 2)
- `public static final DeferredItem<Item> CHIP_PACKAGE` (`lithmod:chip_package`)

Chip lookup, `com.euvcraft.lithmod.mc.ChipCatalog`:
- `public static Optional<Item> chipFor(String dieType, int tierIndex)`
- `public static Optional<Design> designOf(Item item)`
- `public record ChipCatalog.Design(String dieType, int tierIndex)`
- extending it means adding one `new Entry(new Design(<dieType>, <tierIndex>), ModItems.<CHIP>)` line to the private `ENTRIES` list; no call site changes

Resist lookup, `com.euvcraft.lithmod.mc.ResistCatalog`:
- `public static int tierOf(ItemStack stack)` (0 when the stack is not a resist)

Tooltip, `com.euvcraft.lithmod.mc.ChipTooltipAppenders`:
- `public static void register(RegisterTooltipAppendersEvent event)`; already wired in `EUVCRAFTMod`

Data component, `com.euvcraft.lithmod.mc.registry.ModDataComponents`:
- `public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> RESIST_TIER` (`lithmod:resist_tier`, `Codec.INT` plus `ByteBufCodecs.VAR_INT`)

Creative tab, `com.euvcraft.lithmod.mc.registry.ModCreativeTabs`:
- `public static final Supplier<CreativeModeTab> CHIPS` (`lithmod:1_chips_and_consumables`, title `itemGroup.lithmod.chips`)

Resource files:
- `src/main/resources/assets/lithmod/items/<id>.json` for logic_chip_t1, logic_chip_t2, memory_chip_t2, chip_scrap, resist_t1, resist_t2, chip_package
- `src/main/resources/assets/lithmod/models/item/<id>.json` for the same seven ids
- `src/main/resources/assets/lithmod/lang/en_us.json` with `item.lithmod.<id>`, `itemGroup.lithmod.chips`, `tooltip.lithmod.chip` ("Die type: %s, node tier: %s"), `dieType.lithmod.logic`, `dieType.lithmod.memory`, `tier.lithmod.t1`, `tier.lithmod.t2`

Game tests, `com.euvcraft.lithmod.mc.gametest.ChipGameTests`, environment `lithmod:chip_default`:
- `lithmod:chip_catalog_lookups`, `lithmod:chip_tooltip_lines`, `lithmod:item_assets`

Config keys: none added.