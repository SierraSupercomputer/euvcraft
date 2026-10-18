# Testing EUVCRAFT

## Toolchain

NeoForge 26.3 targets the Java 25 runtime that Mojang ships with Minecraft 26.3.
`build.gradle` pins this with a Gradle Java toolchain:

```groovy
java.toolchain.languageVersion = JavaLanguageVersion.of(25)
```

Gradle's Foojay resolver (configured in `settings.gradle`) provisions that JDK, so
builds do not depend on `JAVA_HOME`. Documented commands below assume a normal
shell with no `JAVA_HOME` override.

## Unit tests (JUnit 5)

```powershell
.\gradlew.bat test --offline
.\gradlew.bat checkCoreImports --offline
```

ModDevGradle unit test support is enabled in `build.gradle`:

```groovy
neoForge {
    unitTest {
        enable()
    }
}
```

The `FabConfig` mc test verifies that the NeoForge config spec builds and its
defaults equal `FabSettings.defaults()`.

Per-class counts are in `build/test-results/test/*.xml`.

## Game tests

```powershell
.\gradlew.bat runGameTestServer --offline
```

A trivial game test is registered as `lithmod:trivial` and must report as passed.

The fab randomness game tests are `lithmod:fab_random_salt_round_trip` (the salt is
written to `<world>/dimensions/minecraft/overworld/data/lithmod/fab_random.dat` and
replaying the same context after reading that file back yields the same rolls) and
`lithmod:fab_random_client_side_rejected` (the server-authority guard rejects a
client-side call).

The FOUP game tests run in the `lithmod:foup_default` environment:

| test | what it proves |
|---|---|
| `lithmod:foup_stack_round_trip` | the hidden rolls survive `ItemStack.copy()` and a full `ItemStack.CODEC` round trip |
| `lithmod:foup_network_view_is_public_only` | the network codec carries capacity, wafer count and lot layer only; the decoded lot has placeholder rolls |
| `lithmod:foup_encoded_size` | logs the NBT and network size of a 25 and a 64 wafer lot |
| `lithmod:foup_creative_tab_entries` | the tab hands out one empty lot per variant, each with its own lot id and the configured capacity |
| `lithmod:foup_debug_commands` | the `/debug foup` branch exists exactly when `debugCommands` is true |
| `lithmod:foup_trusted_contents` | a stack with no lot, a client view holding wafers and a client view with a capacity of its own are all refused |
| `lithmod:foup_stored_capacity_wins` | an existing FOUP keeps its stored capacity when the config differs |

The yield engine game tests run in the `lithmod:yield_default` environment:

| test | what it proves |
|---|---|
| `lithmod:yield_layer_step` | a lot on a real `ServerLevel` moves one layer at a time up to the tier's last layer, every hidden roll moves strictly down from 1.0, repeating or skipping a layer is refused, and judging the finished lot does not change it |
| `lithmod:yield_pass_rate` | 1000 wafers are run through T1 and T3 with the world salt and the observed pass rates are logged next to the tier yields; the assertion is deliberately wide (10 points) because this is wiring evidence, and the accuracy claim lives in `YieldCalibrationTest` |

Each of `SiliconGameTests`, `MachineGameTests`, `MachineDropGameTests` and
`MultiblockGameTests` is registered with exactly **1 attempt / 1 required
success** (`new TestData<>(... 1, 1 ...)`; Multiblock used to be 5/5, the
others 3/3). This is deliberate and tied to how a positive server run reports:

A positive `runGameTestServer` run prints every test as passed and then shuts
the server down before a later attempt's retry results can be written into the
game-test XML. With attempts above 1 the XML report can therefore omit tests
entirely — with 3 attempts, all 16 `SiliconGameTests` cases print as passed in
the console yet never appear in the XML at all, because shutdown pre-empts the
retry bookkeeping.

Repetition is consequently NOT done with attempts inside one process and no
in-process repetition substitutes for it. At final M1-12 the suite must instead
be repeated with **THREE SEPARATE, complete `runGameTestServer` processes**, and
each run's game-test XML report checked so that **every registered `lithmod:*`
case ID appears in it, all passed, in every one of the three runs**. Any absent
case ID counts as a failure even if the console printed all passed. M1-04 has
not yet run those three process-level repeats; they are M1-12 acceptance work,
not an already-completed run of record.

## Silicon chain

`WaferSlicingTest` is the one unit test for the chain:

```powershell
.\gradlew.bat test --offline --tests "com.euvcraft.lithmod.core.WaferSlicingTest"
```

It covers `WaferSlicing.wafersFor(FabSettings, int)`: a boule exactly fills a FOUP of the
same size, a FOUP smaller than the yield comes back filled to its own capacity, a FOUP
larger than the yield comes back partially filled, the count follows `wafersPerBoule` and
stops caring about capacity above it, and a capacity below one or a null settings object is
refused. There is no case for a non-positive yield and none is needed: `FabSettings` clamps
`wafersPerBoule` into `[1, 64]` in its canonical constructor, so `min(wafersPerBoule,
capacity)` cannot come out below one once the capacity has been checked.

The silicon chain game tests run in the `lithmod:silicon_chain` environment:

| test | what it proves |
|---|---|
| `lithmod:wafer_slicer_produces_a_lot` | a boule plus an empty FOUP of a known capacity becomes a FOUP of the same item type holding `wafersFor` wafers, at layer 0, with a fresh lot id, no run spec and the input's capacity; both inputs are consumed only when the product appears |
| `lithmod:wafer_slicer_lot_takes_layer_one` | `FabYieldService.applyLayerStep` moves that lot to layer 1 at T1 for the logic die type and freezes a run spec, and refuses the same lot at a die type T1 cannot make |
| `lithmod:wafer_slicer_refuses_a_used_foup` | the server refuses a FOUP holding wafers, a FOUP carrying a run spec and a FOUP item with no lot in it, in the menu and in the tick, and never consumes an input for a lot it refused |
| `lithmod:wafer_slicer_pauses_without_energy` | three ticks of FE start a run and then pause it without consuming the boule or the FOUP, progress does not creep with an empty buffer, and recharging resumes the same run to a finished lot; on a second machine, exactly `energy_per_tick - 1` FE covers no tick at all and stays put while the run pauses, and one FE more is one progress tick and exactly `energy_per_tick` spent |
| `lithmod:wafer_slicer_save_load_round_trip` | progress, energy, the boule and the FOUP survive a `saveWithId`/`loadWithComponents` pair mid-slice and the restored run finishes without starting again, and the synced `DATA_PROGRESS`/`DATA_TICKS` already reflect the restored run immediately after load, before any server tick |
| `lithmod:wafer_slicer_rechecks_its_inputs` | taking the boule away mid-run drops the run and charges nothing for that tick, swapping the boule for raw silicon or the FOUP for one that already holds wafers is refused and leaves the replacement untouched, a full output slot pauses the run with its progress intact and no FE spent, and clearing the output resumes the same run |
| `lithmod:wafer_slicer_lot_size_follows_the_foup_capacity` | a capacity-6 FOUP comes back holding 6 wafers and full; a capacity-100 FOUP comes back holding 25 wafers and partially filled |
| `lithmod:wafer_slicer_menu_layout_and_shift_click` | both menus have 39 slots with the boule, FOUP and output first and in the same order, the three machine slots are bound to the slicer, the predicates agree on both sides for a boule, an ingot and an empty FOUP, shift-clicking routes a boule and a FOUP into their own slots, refuses an ingot and a used FOUP, and moves an input and the product back out to the player |
| `lithmod:wafer_slicer_foup_slot_client_boundary` | a client-view FOUP holding wafers is refused on both menus from its public wafer count, an empty client view is predicted acceptable because a hidden run spec never reaches a client, and the server refuses every client view outright so nothing runs on one |
| `lithmod:crystal_puller_runs_its_recipe` | the data recipe in `data/lithmod/machine_recipes/crystal_puller.json` runs end to end for exactly `energy_per_tick * ticks` FE, yields one boule and consumes all four silicon |
| `lithmod:crystal_puller_rechecks_its_inputs` | five silicon leave one behind next to the boule, three silicon (one short of the recipe) never start a run, removing or swapping the ingredient mid-run drops the run, writes nothing, consumes nothing and costs no FE, and exactly `energy_per_tick - 1` FE draws nothing and pauses while one FE more is one progress tick at the full cost |
| `lithmod:silicon_energy_follows_machine_definitions` | each machine's real buffer capacity, per-operation insert limit and per-tick cost equal its loaded definition, one insert takes at most `max_receive` and repeated inserts stop at `energy_capacity`, a hand-built smaller definition resizes the real buffer and the synced value, keeps the energy already stored, refuses insertion until the surplus is spent and moves the insert limit with it, and re-applying the loaded definition restores both numbers |
| `lithmod:silicon_machines_fail_closed_without_a_definition` | with the two definitions removed from the loaded registry for the length of the test, a part-way Crystal Puller and a charged Wafer Slicer both stop: no progress, no FE spent, no product, no input consumed, a capacity and a synced capacity of 0 and no FE accepted; loading a saved half-way slicer run while its definition is still missing drops that run instead of restoring it; restoring the definitions restores the capacity and the cost, the kept FE is still there, and the puller's first tick on it starts a run again and spends exactly one `energy_per_tick` while the slicer does not resume its dropped run |
| `lithmod:crystal_puller_resumes_only_the_same_recipe` | a run saved half-way and loaded back resumes at the same progress, the same recipe length and the same frozen per-tick cost and finishes as a boule; a run saved against a recipe and loaded against a different one that takes the same silicon, runs twice as long and gives twice the product starts from tick one instead of inheriting the paid-for progress; and with an overlapping candidate list (a decoy silicon recipe first, the original saved spec later) the runner adopts the original spec and pays its original product and cost |
| `lithmod:silicon_cooking_recipes_load_and_match` | the ordinary three-argument `RecipeManager.getRecipeFor(type, input, level)` selects our bundled `lithmod:silicon_from_smelting` and `lithmod:silicon_from_blasting` recipes for `minecraft:quartz` input, both match quartz and assemble `lithmod:silicon` with positive cooking times from their JSON; ordinary sand smelting produces glass (vanilla), our silicon recipes reject sand and boule, and blasting sand yields no silicon; this proves recipe loading and matching for the user's distinct QUARTZ ingredient, not default furnace selection |
| `lithmod:silicon_machines_expose_no_item_handler` | neither block exposes an item handler, a fluid handler or a hopper on any side, both expose energy that accepts FE and refuses to give it back, and a chest still does expose an item handler so the null assertions are not vacuous |

Every one of those tests logs a line beginning `[M1-04]` with the numbers it asserted on.

Two tests reach past what a game test can type at a machine and change the loaded data itself,
synchronously and only for the length of the test, putting the live registry back in a
`finally`: `silicon_machines_fail_closed_without_a_definition` builds a definition registry
without the two machine ids and `crystal_puller_resumes_only_the_same_recipe` installs a
recipe that takes the same silicon, runs twice as long and gives twice the product. No
production seam was added for either. Everything else in the table is driven through the
public tick with the real data pack.

A removed machine definition closes a machine rather than only refusing to size it: the
buffer's capacity and insert limit go to 0, the synced capacity follows, the per-tick cost
goes to 0 and the run in progress is dropped, while the FE already stored is left alone and
is there again when the definition returns. This is the same code path a real datapack edit
takes, because `refreshDefinition` runs on every tick.

The machines' internal draw is full-or-zero: `FabMachineBlockEntity.energySource` commits an
extraction only when the buffer can cover the whole requested amount, and otherwise lets the
transaction abort on close, so a partial tick can never drain FE it cannot use. External
extraction stays forbidden through `WithdrawNothing` on every side and stored energy
persistence is untouched.

## Debug commands

`/debug foup fill <count> <layer>` and `/debug foup inspect` are only registered when
`features.debugCommands` is `true` in `run/config/lithmod-local.toml`, which is off by
default. The mod asks for permission level 2 on its own nodes, but vanilla already owns
`/debug` at level 3 and brigadier keeps the existing node's requirement when two literals
with the same name merge, so the effective gate for `/debug foup ...` is level 3 in
practice. To exercise the commands in the game test run, set the key to `true` before
starting `runGameTestServer`; with the key off the same test only asserts that the branch
is absent.

## Tier data reload (manual)

Expected startup log line (one line, id, yield percent and layers):

```text
Loaded 8 tiers: T1(60%, 1 layers), T2(80%, 12 layers), ...
```

Valid override:

1. Create `run/<world>/datapacks/tier-override/data/lithmod/tiers/t3.json`.
2. Put an override such as a different `default_yield_percent`.
3. Run the dev server and execute `/reload`.
4. The server log shows the changed value in the `Loaded 8 tiers:` line.

Invalid override:

1. Add a second data pack with invalid data, for example a negative `layers` value.
2. Execute `/reload`.
3. The error names the file and field; the previous registry stays in place and
   the next `Loaded 8 tiers:` line keeps the previous values.

## OneDrive note

This repository lives inside a OneDrive folder. Exclude `.git`, `build`, `.gradle`,
and `run` from sync; see the task report for the steps. Do not move the repository.

## Mask and pattern generator (M1-06)

The mask item and the Pattern Generator run under the same commands as the rest of
the mod (`.\gradlew.bat test --offline`, `.\gradlew.bat checkCoreImports --offline`,
`.\gradlew.bat runGameTestServer --offline`). The counts below are the M1-06 coordinator
run at head `433a8f5`.

Unit tests: 71 suites / 71 classes, 450 tests, 0 failures, 0 errors, 0 skipped. Three
classes are new and hold 41 of the new methods: `MaskDataTest` (12, mask record
validation and use consumption), `MaskRulesTest` (19, the Gen 1 mask-shop gate) and
`MaskDataCodecTest` (10, JSON/NBT/stream round trips and rejection of bad data). The
per-class table is in `docs/reports/M1-06_mask-and-pattern-generator.md`.

Every new unit method has an executed negative control, not a suggested one: 16
deliberate mutants against the 31 core methods at `66a17d9` and 4 against the 10 codec
methods at `d90cee1`. The mutated files were restored byte-for-byte and the positive
suite re-run afterwards, so the passing table above is a post-restore run. The failures
quoted in those logs are named JUnit assertions, not XML-regex matches.

Game tests: the XML report holds 751 case records, all 751 passed, 0 required failures
and 0 optional cases skipped, and all 81 registered `lithmod:*` IDs are present and
passing. Most of the 751 come from the toolchain, so 751 is not the number of project
tests, and the 81 IDs are cases, not assertions. The 19 new server cases are
`lithmod:mask_catalog_and_creative_tab`, `lithmod:mask_data_component_round_trip`,
`lithmod:mask_tooltip_lines`, and these 16: `pattern_generator_block_use_opens_the_menu`,
`pattern_generator_cancels_when_an_input_is_withdrawn`, `pattern_generator_cuts_a_logic_t1_mask`,
`pattern_generator_cuts_a_logic_t2_mask`, `pattern_generator_cuts_a_memory_t2_mask`,
`pattern_generator_exposes_energy_only`, `pattern_generator_menu_layout_and_shift_click`,
`pattern_generator_pauses_on_a_full_output_slot`, `pattern_generator_pauses_without_energy`,
`pattern_generator_refuses_a_compute_t3_design`, `pattern_generator_refuses_a_memory_t1_design`,
`pattern_generator_refuses_a_product_that_is_not_a_mask`,
`pattern_generator_refuses_a_product_without_a_design`,
`pattern_generator_save_load_round_trip`, `pattern_generator_ticker_is_server_side` and
`pattern_generator_uses_the_config_at_completion` (all under the `lithmod:` namespace).

Each of those 19 cases was also observed failing under a deliberate production fault
before being restored: the `behavior` fault drives the completion stamp, Gen 1 gating,
runner guards, menu, ticker, interaction and stack/tooltip/status-wire paths; the
`energy` fault drives the FE cap and the pending cost; the `persistence` fault changes
only the snapshot writer back to the old NBT form and catches the exact recipe-identity
loss. Five cases outside the target set also fail under the behavior fault because they
share the same base hooks. The case-by-case control table is in the M1-06 report.

Client limit: no live client rendering or screen check was done, and the client-side
ticker branch is not covered — the server tests cover the server ticker and the
detached-level guard only, not `ClientLevel`. The new models reuse vanilla textures as
placeholders, and the pre-existing missing GUI bar assets are a disclosed presentation
follow-up. No visual pass is claimed. The mask data is server persistence, not a network
packet.

No three-separate-process repeat of the suite was run for M1-06; that remains M1-12
acceptance work.