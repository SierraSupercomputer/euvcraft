# M1-04: Silicon chain, Crystal Puller and Wafer Slicer

- Date: 2026-10-17 (final source-correction pass, same report file: the coordinator's
  allowed-file list names exactly this path)
- Branch: `m1-04-silicon-chain` (isolated worktree `.orchestrator/worktrees/euvcraft/m1-04-silicon-chain`).
  Baseline in `build/M1-04-start-evidence.json`: `4b5f4a74f116b1759898cd690ebfb3af837eb32f`.
  Frozen checkpoint this pass corrects: `8fd378507b543bf7acd19d9310cb19186d1a1834`.
- Java version used by this agent: none. No JVM was started by this agent.
- Build and test commands run by this agent: none. Section 3 records what the coordinator
  measured on earlier checkpoints and what still has to run on the current tree.
- No shell, no Git commands, no delegation, no configuration edits were used. Verification is
  owned by the coordinator.

## 1. Summary

M1-04 adds the first silicon chain: a Crystal Puller that turns four silicon into one boule
through a data-driven recipe runner, a Wafer Slicer that turns one boule plus one empty FOUP
into a sealed lot of unprocessed wafers, their menus/screens/blocks/items/assets/data, and 16
game tests plus unit tests for the slicing math. All numbers come from data or config.

The work went through one implementation pass and several repair/finalization passes; the
headings below describe the tree as it stands, not a pass-by-pass journal. The corrections
folded in on the way, at coordinator or Beam request:

1. **Defects fixed on request, in code.** Definition removal closes a machine completely
   (capacity, insert limit, synced capacity, per-tick cost and run all go, stored FE kept);
   a restored run adopts only its exact saved `MachineRecipeSpec`; the internal energy draw is
   full-or-zero so a partial draw spends nothing; `MachineRecipeRunner.loadState` drops a live
   run before restoring saved numbers; `WaferSlicerBlockEntity.loadAdditional` clears a stale
   `slicing`/`paused` state before applying saved data, restores only when a definition with a
   valid positive `slice_ticks` is loaded (otherwise `resetSlice()`), and publishes
   `DATA_PROGRESS`/`DATA_TICKS` to the menu at the end of load.
2. **Runner candidate selection fixed from two Beam reviews.** The restore tick used to match
   `firstMatch` and only then try `adoptRestored`, so a newly higher-priority overlapping
   recipe sitting earlier in the candidate list could reset a run even though the exact saved
   recipe still existed later. The tick now iterates all candidates and adopts the first that
   both matches the inputs and adopts the saved spec; it resets only when none does. Frozen
   per-run cost, exact-spec equality and reset-on-changed/removed recipes are unchanged.
3. **Test and coverage extensions.** `wafer_slicer_save_load_round_trip` asserts the synced
   `DATA_PROGRESS`/`DATA_TICKS` immediately after load, before any server tick;
   `machines_fail_closed_without_a_definition` also loads a saved half-way slicer run while its
   definition is missing and asserts it fails closed, with the registry restored in a
   `finally`; `crystal_puller_resumes_only_the_same_recipe` adds the overlapping case
   (decoy recipe first, original saved spec later) and asserts the actual adopted spec and
   product, not only tick numbers.
4. **Two genuine test defects fixed after the latest full runtime.** The shift-click menu test
   used one `emptyFoup` instance both as the shift-clicked input and the later simulated
   product, but quick-move mutates the stack to `EMPTY`, so the product probe was proving
   nothing; it now uses a fresh valid FOUP. The cooking-recipe test's boule predicate was
   backwards, its lookup did not prefer our exact keys, and it hardcoded 200/100 cooking times;
   it now uses the verified 4-argument `RecipeManager.getRecipeFor(type, input, level, key)`
   overload with our bundled keys, asserts a positive loaded cooking time (values stay in
   JSON), and checks both typed holders do not match a boule. It documents that this proves
   recipe loading, not default furnace selection.

Pending the coordinator's next run: this repair fixes the overlapping decoy-spec compile
error, moves restored-progress adoption into `MachineRecipeRunner.tick` alone, makes a second
save before any adoption tick keep the pending restored run, and extends
`crystal_puller_resumes_only_the_same_recipe` (decoy-first `firstMatch` proof, pending-snapshot
reload) plus `wafer_slicer_save_load_round_trip` (zero-FE paused slice restored and resumed);
all of it is unread-by-runtime evidence until then.

Not applied, deliberately: Beam's second wafer-count clamp (redundant with `FabSettings`'s
`[1, 64]` clamp; documented in `WaferSlicing` and `docs/testing.md`), and no recipe change for
the sand/vanilla-glass overlap, which is a pending product decision (section 7).

## 2. Files changed

All M1-04 changes since baseline `4b5f4a7`, by area:

| area | files | change |
|---|---|---|
| core | `src/main/java/com/euvcraft/lithmod/core/WaferSlicing.java` | `wafersFor(FabSettings, int)` slicing math; javadoc explains why there is no clamp of its own |
| framework | `src/main/java/com/euvcraft/lithmod/mc/machine/FabMachineBlockEntity.java` | definition apply/close-down (`refreshDefinition`, private `withoutDefinition`), base tick returns before recipe work with no definition, real-buffer-sized energy buffer and `DATA_CAPACITY`, full-or-zero internal `energySource()` draw |
| framework | `src/main/java/com/euvcraft/lithmod/mc/recipe/MachineRecipeRunner.java` | candidate matching, exact-spec persistence (`saveState`/`loadState` with `MachineRecipeSpecCodec.CODEC` under `spec`), `adoptRestored` replacing `adopt`, frozen per-run cost, restore-tick candidate iteration over `inputsMatch` AND `adoptRestored` |
| silicon machines | `src/main/java/com/euvcraft/lithmod/mc/machine/silicon/{SiliconMachines,BouleSlot,EmptyFoupSlot,CrystalPullerBlock,CrystalPullerBlockEntity,CrystalPullerMenu,WaferSlicerBlock,WaferSlicerBlockEntity,WaferSlicerMenu,SiliconMachineBlock,SiliconMachineScreen}.java` | the two machines: registration, slot rules, menus, slicer's own runner-free slicing with persistence and fail-closed load, blocks and screen |
| gametest | `src/main/java/com/euvcraft/lithmod/mc/gametest/SiliconGameTests.java` | 16 registered tests in `lithmod:silicon_chain`; helpers `saveTag`/`loadTag`, `withoutDefinitions`/`withRecipes` synchronous registry swaps restored in `finally`; energy probes; slicer round-trip sync assertions; missing-definition slicer restore coverage; overlapping-recipe resume coverage; shift-click fresh-FOUP fix; cooking test on the 4-arg key-preferred lookup |
| registry | `src/main/java/com/euvcraft/lithmod/mc/registry/{ModItems,ModCreativeTabs}.java`, `src/main/java/com/euvcraft/lithmod/mc/EUVCRAFTMod.java` | silicon item chain registration, creative tab entries, mod wiring of the machine and test types |
| data | `data/lithmod/machines/{crystal_puller,wafer_slicer}.json`, `data/lithmod/machine_recipes/crystal_puller.json`, `data/lithmod/recipe/silicon_from_{smelting,blasting}.json`, `data/lithmod/loot_table/blocks/{crystal_puller,wafer_slicer}.json`, `data/lithmod/tags/block/machine.json`, `data/minecraft/tags/block/{mineable/pickaxe,needs_stone_tool}.json` | machine definitions (20000 FE, 2000 max receive, 20/30 FE per tick, `slice_ticks: 200`), the puller recipe (4 silicon → 1 boule, 400 ticks), the sand cooking recipes (200/100 ticks — values pending a possible M1-11 change), loot, tags |
| assets | `src/main/resources/assets/lithmod/**` for the two machines, silicon, boules and FOUPs, plus `lang/en_us.json` | blockstates, item models, language entries; no new binary assets or dependencies |
| docs | `docs/testing.md`, `docs/reports/M1-04_silicon-chain.md` | test table for the 16 game tests (this pass: round-trip sync, missing-def slicer restore, overlap-resume and cooking rows updated) and this report |

Configs (`FabSettings`) were not changed by M1-04 except reading existing values.

## 3. Verification

Executed runtime evidence, all measured by the coordinator, kept as historical — none of it
was produced by this agent, and none of it covers the current tree:

- Checkpoint `1dd1889c`: `checkCoreImports --offline` passed with the jar; unit tests
  `409 tests in 68 suites, 0 failures`; full game-test server 730 tests, one required
  (`wafer_slicer_menu_layout_and_shift_click`) and one optional
  (`arcforge:creosote_moves`, another mod) failure — the run did not pass, and the report
  never claimed otherwise.
- Checkpoint `8fd` (latest full runtime, before source froze for writing again): 732
  attempts, **2 required failures, no optional failure in this run**. Both required failures
  are TEST defects, not source defects, and both are fixed above: the shift-click test's
  mutated `emptyFoup`, and the cooking test's ambiguous first-match lookup plus backwards
  boule predicate and hardcoded 200/100 timings.

Pending on the current tree (coordinator owns them; should be run before any sign-off):

```powershell
.\gradlew.bat checkCoreImports --offline
.\gradlew.bat test --offline            # then read build/test-results/test/*.xml
.\gradlew.bat runGameTestServer --offline   # 16 lithmod:silicon_chain tests included
```

| suite | tests | failures | status |
|---|---|---|---|
| `WaferSlicingTest` | 7 written | 0 on earlier checkpoints | pending on current tree |
| all other unit classes | 409 total / 68 suites on `1dd1889c` | 0 | pending on current tree |
| `lithmod:silicon_chain` game tests | 16 registered | 2 required test-defect failures on `8fd` | pending on current tree |
| executed mutation (negative-control) runs | — | — | none by this agent; pending |

Deliberately not fabricated: this pass's source changes are verified only by reading. Until
the coordinator's fresh run, the current tree has **no passing runtime evidence**, and this
text claims none.

## 4. Spec conformance

| item | status | evidence |
|---|---|---|
| Slicer produces a fresh unprocessed lot of the right size/capacity | met | `wafer_slicer_produces_a_lot`, `wafer_slicer_lot_size_follows_the_foup_capacity` |
| Sliced lot takes layer 1 through `FabYieldService` | met | `wafer_slicer_lot_takes_layer_one`, refused die type asserted |
| Used/filled/started/component-less FOUPs refused in menu and tick | met | `wafer_slicer_refuses_a_used_foup`, `wafer_slicer_foup_slot_client_boundary` |
| Pause without FE costs nothing; partial draw is full-or-zero | met | `wafer_slicer_pauses_without_energy`, `crystal_puller_rechecks_its_inputs`, full-or-zero `energySource()` |
| Slicer progress survives save/load and syncs to the menu at load | met | `wafer_slicer_save_load_round_trip` incl. immediate `DATA_PROGRESS`/`DATA_TICKS` assertions |
| Puller recipe runs for exactly `energy_per_tick * ticks` | met | `crystal_puller_runs_its_recipe` |
| Inputs re-checked every tick; mid-run removal/swap drops the run | met | `crystal_puller_rechecks_its_inputs`, `wafer_slicer_rechecks_its_inputs` |
| Missing/removed definition fails closed, FE kept, no resumed slicer run | met | `machines_fail_closed_without_a_definition` including the saved-slicer restore under a missing definition |
| Restored run resumes only its exact saved spec | met | `crystal_puller_resumes_only_the_same_recipe`; `Runner.tick` iterates and adopts `inputsMatch && adoptRestored` |
| Overlapping candidates: higher-priority decoy does not steal a saved run | met | same test, decoy-free iteration; adopted spec equals the original |
| Bundled cooking recipes load, match sand, assemble silicon | met | `silicon_cooking_recipes_load_and_match` via the 4-arg key-preferred lookup |
| Prove default furnace selection for sand | not asserted, deliberately | sand also matches vanilla glass; pending M1-11 product decision (section 7) |
| No hardcoded numbers in Java | met | tick/energy/counts read from data; cooking times asserted as positive only, values stay JSON |
| Neither machine hopper- or item-handler-reachable; energy exposed correctly | met | `silicon_machines_expose_no_item_handler` |
| Menu layouts and shift-click routing both sides | met | round-trip layout assertions and the corrected fresh-FOUP product probe |

## 5. Not verified

- **Nothing in the correction passes has been compiled or run by this agent.** All "met"
  above is source inspection plus historical coordinator runs on earlier checkpoints.
- The registry swaps in tests reach loader fields reflectively; they are synchronous and
  restored in `finally`, but a real `/reload` mid-run remains untested.
- `RecipeHolder#id()` returning `ResourceKey<Recipe<?>>` rests on the 26.3 signature extracts
  plus the code compiling in sources; a mismatch would be a compile-time assertion error.
- The framework edits were not run against the M1-02/M1-03 game tests on the current tree.
- The two screen classes' client visuals were never loaded.
- Fresh final counts, test-class table and executed mutation evidence for the current tree
  are pending and will be supplied by the coordinator; earlier numbers above are historical.

## 6. Deviations

| spec says | what was done | why |
|---|---|---|
| Test a changed loaded registry | reflective synchronous swap, restored in `finally` | loaders expose no setter; the loader file is outside the allowed paths |
| Persist the running recipe spec | done, under the existing `spec` key, no new field | a save with no spec resumes nothing |
| Beam: clamp the wafer count | not added | redundant with the `FabSettings` clamp |
| Restore against the current definition's cost | not adopted | a resumed run must cost what it would have cost uninterrupted |
| Assert 200/100 cooking times | relaxed to positive-value assertions | the runtime verdict called the hardcoded timings a defect; values remain in JSON |
| Sandbox tests reusing one FOUP stack | corrected | reusing a stack mutated by quick-move broke the product probe without property a real defect |

## 7. Open questions

1. **Pending user decision (M1-11), blocking further recipe work**: sand also matches vanilla
   glass, so a plain furnace's default selection may return `minecraft:glass` instead of the
   bundled silicon recipe. The user was asked to choose between preserving glass and blasting
   to silicon, replacing the sand smelting, or a distinct silicon ingredient. No answer yet,
   so no recipe was changed and nothing in the tests assumes one.
2. Both machine definitions still declare placeholder `energy_capacity 20000` /
   `max_receive 2000`; a datapack override would prove they are per machine.
3. Would a real mid-run `/reload` be worth a dedicated test later? The current swaps are
   synchronous and a real reload is untested.

## 8. Handoff

For whoever continues on this area:

- `MachineRecipeRunner` is the recipe engine for data-recipe machines; `WaferSlicerBlockEntity`
  deliberately does not use it and owns its own slice loop with the same pause rules,
  persistence and fail-closed behavior. Their public APIs are stable: `runner()`,
  `progress()`, `ticks()`, `isRunning()`, `isPaused()`, `hasRestoredProgress()`,
  `adoptRestored(MachineRecipe)`, `saveState`/`loadState`; slicer `BOULE_SLOT/FOUP_SLOT/
  OUTPUT_SLOT`, `SLICE_TICKS_PARAM`, `resetSlice()`, `acceptedFoup`, `acceptsFoupStack`.
- `withoutDefinitions`/`withRecipes` in `SiliconGameTests` are the documented way tests get a
  different loaded registry; always restore in `finally`.
- The game test set is 16 tests under `lithmod:silicon_chain`, listed in `docs/testing.md`.
- Recipes are simple; FOUP products cannot be expressed as recipes because their contents are
  generated (fresh lot id, capacity, wafers), which is why the slicer has no runner.
- Nothing in this report was compiled, run, committed or verified; the coordinator owns all
  verification, Git and backups.
