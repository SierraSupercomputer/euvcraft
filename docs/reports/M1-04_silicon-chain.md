# M1-04: Silicon chain, Crystal Puller and Wafer Slicer

- Date: 2026-10-17 (second repair pass, same report file: the coordinator's allowed-file list
  names exactly this path)
- Branch: `m1-04-silicon-chain` (isolated worktree `.orchestrator/worktrees/euvcraft/m1-04-silicon-chain`).
  Frozen checkpoint this repairs: `1dd1889cf996b30e314aac591b4e3ae6dbd3c359`. Baseline recorded
  in `build/M1-04-start-evidence.json`: `4b5f4a74f116b1759898cd690ebfb3af837eb32f`.
- Java version used by me: none. No JVM was started by this agent.
- Build and test commands run by me: none. Section 3 records what the coordinator measured on
  this checkpoint and what it still has to run on the new tree.
- `git worktree list`, `git branch --show-current`, `git status --short`: not run (no shell).
  The coordinator must take them before and after committing.

## 0. Third repair pass (this pass): full-or-zero internal energy draw

One small correctness repair fed back from coordinator source inspection after checkpoint
`152ddf4` (which compiled clean in 11s, main + test). Nothing else was touched.

1. **Full-or-zero internal draw** (`FabMachineBlockEntity.energySource`): the internal
   adapter used to extract and commit unconditionally, so a buffer holding
   `energy_per_tick - 1` FE drained those FE with no work done — the runner and the slicer
   paused correctly, but the partial spend was already gone. The draw now commits only when
   the extraction equals the full requested positive amount; otherwise it returns 0 and the
   transaction aborts on close, rolling the partial extraction back. Zero-cost config stays
   coherent (`amount <= 0` returns 0 as before); external extraction stays forbidden through
   `WithdrawNothing`; stored-energy persistence and definition-resize behavior are unchanged.
2. **`MachineRecipeRunner.loadState` clears a previous run first**: `loadAdditional` can run
   on a block entity that is already mid-run, not only a fresh constructor, and the valid
   restore used to overwrite `progress`/`ticks` while a stale `current` survived. The valid
   branch now nulls `current` and `paused` before restoring the saved numbers; the frozen
   per-tick cost and the exact-spec adoption are unchanged.
3. **Test-extension and expectation fixes in `SiliconGameTests`** (no new test functions):
   `wafer_slicer_pauses_without_energy` and `crystal_puller_rechecks_its_inputs` now put in
   exactly `perTick - 1` FE and tick once (unchanged FE, zero progress, no output, paused),
   then add exactly 1 FE and tick once (one progress tick, exactly `perTick` spent);
   `machinesFailClosedWithoutDefinition` no longer claims the restored-registry tick does
   nothing — with valid inputs and stored FE it legitimately restarts the run, so the
   expectation is now `energyBefore - perTick` with progress 1 (the missing-definition
   no-progress/no-spend assertions are untouched); and the drain in
   `silicon_energy_follows_machine_definitions` now asserts an over-large draw aborts
   (FE kept) and drains with the exact stored amount, because `draw(MAX_VALUE)` can no
   longer partially drain. `docs/testing.md` rows and the pause-semantics note follow.

| path | change |
|---|---|
| `src/main/java/com/euvcraft/lithmod/mc/machine/FabMachineBlockEntity.java` | `energySource()` commits only a full-amount extraction; partial draw is rolled back and returns 0 |
| `src/main/java/com/euvcraft/lithmod/mc/recipe/MachineRecipeRunner.java` | `loadState` valid branch clears `current` and `paused` before restoring the saved numbers |
| `src/main/java/com/euvcraft/lithmod/mc/gametest/SiliconGameTests.java` | perTick-1/+1 FE probes in the slicer pause and puller recheck cases; fail-closed restored-tick expectation (`energyBefore - perTick`, progress 1); over-large draw refuses and exact stored amount drains in the definitions test |
| `docs/testing.md` | three silicon table rows and the pause-semantics note updated |
| `docs/reports/M1-04_silicon-chain.md` | this section |

Verification pending until the coordinator supplies real results: `checkCoreImports`,
`test`, `runGameTestServer` and any negative-control runs on this tree are **pending**, as
marked in section 3. Nothing in this pass was compiled or run by the reporting agent.


## 1. Summary

Second repair pass, scoped to four things the coordinator fed back.

1. **Fail closed, including on a removed definition.** `refreshDefinition()` used to do
   nothing when the definition was absent, so a machine that had been charged kept its
   capacity, its per-tick cost and its run, and a runner could complete with `cost = 0`.
   `withoutDefinition()` now closes the machine in place: capacity and insert limit 0,
   synced capacity 0, per-tick cost 0, run dropped, stored FE untouched. The base tick returns
   before it matches, re-adopts or runs anything. New game test
   `silicon_machines_fail_closed_without_a_definition`.
2. **The real cooking-recipe lookup, not a JSON schema mirror.** `getRecipeFor(SMELTING`/`BLASTING,
   SingleRecipeInput(sand), level)` against the live manager, asserting the bundled holder keys,
   the assembled `lithmod:silicon` and the `cookingtime` from the JSON. New game test
   `silicon_cooking_recipes_load_and_match`; `silicon_cooking_recipe_files_match_the_vanilla_schema`
   is gone.
3. **A restored run belongs to one recipe.** The save now carries the running
   `MachineRecipeSpec` (`MachineRecipeSpecCodec.CODEC` through `ValueOutput.store` /
   `ValueInput.read`) and `adoptRestored` hands progress back only to that exact spec; a
   missing or changed spec resets the run. The running per-tick cost stays frozen, so a
   resumed run costs exactly what an uninterrupted one costs. `MachineRecipeRunner.adopt` became
   `adoptRestored`. New game test `crystal_puller_resumes_only_the_same_recipe`.
4. **Claims corrected.** The report no longer claims work I did not run, the literal
   "(negative control)" labels on ordinary bad-input cases are gone from
   `SiliconGameTests`, and the one required runtime failure the coordinator found
   (`wafer_slicer_menu_layout_and_shift_click`) is fixed: the assertion read menu slot index 32
   instead of player inventory slot 2.

Beam's wafer-clamp suggestion was **not** applied: `FabSettings`'s canonical constructor
already clamps `wafersPerBoule` into `[1, 64]`, so `WaferSlicing`'s `min` cannot yield a
non-positive count and a second bound would be a second place to change the same number. This
is now stated in `WaferSlicing`'s javadoc and in `docs/testing.md`. The integration review
returned no final text, so it is recorded as **incomplete**, not as an approval.

## 2. Files changed in this pass

| path | change |
|---|---|
| `src/main/java/com/euvcraft/lithmod/mc/machine/FabMachineBlockEntity.java` | new private `withoutDefinition()`; `refreshDefinition` uses `ifPresentOrElse`; `serverTick` returns before matching/re-adopting/running with no definition; `loadAdditional` drops restored progress when no definition is loaded |
| `src/main/java/com/euvcraft/lithmod/mc/recipe/MachineRecipeRunner.java` | `saveState` writes the running `MachineRecipeSpec`; `loadState` reads it; `adopt(MachineRecipe)` replaced by `adoptRestored(MachineRecipe)` returning whether the spec matched; `reset` clears the pending spec |
| `src/main/java/com/euvcraft/lithmod/mc/machine/silicon/CrystalPullerBlockEntity.java` | javadoc: a removed definition closes the machine and keeps the FE |
| `src/main/java/com/euvcraft/lithmod/mc/machine/silicon/WaferSlicerBlockEntity.java` | javadoc: a removed definition stops slicing **and** refuses FE, keeping stored FE |
| `src/main/java/com/euvcraft/lithmod/core/WaferSlicing.java` | javadoc: why there is no clamp of its own |
| `src/main/java/com/euvcraft/lithmod/mc/gametest/SiliconGameTests.java` | 14 → 16 tests; menu assertion `getItem(hotbar + 2)` → `getItem(2)`; cooking schema mirror → real recipe-manager lookup; new fail-closed and resume-only-the-same-recipe tests; `saveTag`/`loadTag` helpers and the temporary `withoutDefinitions`/`withRecipes` registry swaps; "(negative control)" wording removed from input cases |
| `docs/testing.md` | silicon tables updated to 16 tests, the two registry swaps described, wafer clamp note |
| `docs/reports/M1-04_silicon-chain.md` | this report |

Everything else the first pass touched (`SiliconMachines`, the blocks, menus, screens, items,
creative tab, loot tables, tags, assets, `data/lithmod/**`) is unchanged by this pass.

## 3. Verification

**Measured by the coordinator on checkpoint `1dd1889c`, before this pass** (not by me; I ran no
command and started no JVM):

- `.\gradlew.bat checkCoreImports --offline` against the committed sources, with the jar
  produced: **passed**. This replaces the earlier temporary compilation-probe limitation.
- `build/test-results/test/*.xml` totals: **409 tests in 68 suites, 0 failures, 0 errors,
  0 skipped**.
- Full game test server on the same checkpoint: **730 tests total**, one required failure
  (`lithmod:wafer_slicer_menu_layout_and_shift_click`) and one optional failure
  (`arcforge:creosote_moves`, another mod's suite). The earlier
  `wafer_slicer_refuses_a_used_foup` failure was already fixed and did not recur.
  **The full runtime run therefore did not pass**, and nothing here claims it did.

Still pending, all of it the coordinator's (and it applies to the current tree — checkpoint
`152ddf4` plus the section-0 repair — where nothing has been compiled or run yet, and to any
negative-control runs on the new full-or-zero and probe assertions):

```powershell
.\gradlew.bat checkCoreImports --offline   # on the new tree
.\gradlew.bat test --offline               # then read build/test-results/test/*.xml
.\gradlew.bat runGameTestServer --offline  # 16 lithmod:silicon_chain tests included
```

| suite | tests | failures | errors | skipped |
|---|---|---|---|---|
| `WaferSlicingTest` | 7 written, unchanged this pass | pending | pending | pending |
| every other unit test class | pending on the new tree | pending | pending | pending |
| `lithmod:silicon_chain` game tests | 16 registered | pending | pending | pending |

Deliberate mutation (negative-control) runs: **none executed by this agent**. No mutant was
introduced, broken and reverted, so nothing in this repository is evidence that any assertion
can fail. The coordinator owns that evidence; the labels `(negative control)` were removed from
the ordinary bad-input cases precisely because they claimed something that was never run. The
one failure the coordinator's runtime run did produce
(`wafer_slicer_menu_layout_and_shift_click`) is the only executed failure evidence in this
report, and it is a genuine test defect rather than a mutant.

## 4. Spec conformance

| item | met/partly/not met | evidence |
|---|---|---|
| Missing definition closes the machine | met | `FabMachineBlockEntity.withoutDefinition()`: `reconfigure(0, 0)`, `setMachineEnergyPerTick(0)`, `reset()`, `DATA_CAPACITY` from the buffer |
| Removed definition does the same | met | `refreshDefinition()` runs `withoutDefinition` on every tick, so a deletion closes it the same tick |
| Stored FE survives both | met | `reconfigure` never touches `energy`; the new test asserts the amount is unchanged under the swap |
| Menu capacity follows | met | `withoutDefinition` and `applyDefinition` both write `DATA_CAPACITY` from the real buffer |
| Base tick returns before work | met | `serverTick` returns right after `refreshDefinition()` when `definition().isEmpty()`, before `firstMatch`, `adoptRestored` and `runner.tick` |
| Slicer refuses FE under a removed definition | met | the base close-down applies to it; `silicon_machines_fail_closed_without_a_definition` asserts capacity 0 and `insert == 0` on the slicer |
| Test-only constructors kept | met | every overload is unchanged; the fail-closed path only runs when data is absent |
| Fail-closed runtime regression | met (not run) | `silicon_machines_fail_closed_without_a_definition`: part-way run, matching input and a full buffer, then no progress, no FE, no output, no consumption, no insert, and operation again once the data is back |
| Registry swap is temporary and restored | met | `withoutDefinitions`/`withRecipes` write the loader field and put the previous value back in a `finally`; no production seam was added |
| Real cooking lookup | met | `silicon_cooking_recipes_load_and_match`: `getRecipeFor` for `SMELTING` and `BLASTING`, holder keys `lithmod:silicon_from_{smelting,blasting}`, `assemble` → `lithmod:silicon`, `cookingTime()` 200/100 from the JSON |
| Optional vanilla fields permitted | met | the test reads the loaded recipe, not a key set; `group` and friends may be added to the JSON without touching it |
| Food/time/XP stay in JSON | met | no number moved into Java; the test reads them back out of the loaded recipe |
| Beam's wafer clamp | not applied, deliberately | `FabSettings` clamps `wafersPerBoule` to `[1, 64]`; documented in `WaferSlicing` and `docs/testing.md` |
| Restored run resumes its own recipe | met | `adoptRestored` compares the saved `MachineRecipeSpec` with the candidate's; equality is required |
| Changed recipe inherits nothing | met | a missing or different spec returns false and the caller resets; asserted by `crystal_puller_resumes_only_the_same_recipe` |
| Running cost stays frozen | met | `loadState` restores `energy_per_tick` and `adoptRestored` leaves it alone; the reload path never rewrites it, so reload and uninterrupted cost agree |
| Live reload keeps the selected recipe | met | `tick` only consults the candidate list when `current == null`; `current` is the resolved recipe for the whole run |
| No aliases or duplicate runner types | met | `adopt` became `adoptRestored`; no new runner class |
| Menu shift-click assertion | met | `player.getInventory().getItem(2)` — the player slot the test put the ingot in; the refusal checks around it are untouched |
| Beam integration review | incomplete | no final text was returned; treated as no verdict |

## 5. Not verified

- **Nothing in this pass has been compiled or run by me.** Every "met" above rests on reading
  the source. The compile and runtime evidence in section 3 is for the checkpoint, not for the
  new tree.
- Two API details in the new cooking test rest on the coordinator's signature extracts plus
  long-stable API rather than on anything I could read here: `RecipeHolder#id()` returning
  `ResourceKey<Recipe<?>>`, and `Recipe#assemble(input)` with one argument (the latter is in
  `build/api-reference/recipe-manager-signatures.txt`, the former is not). If `id()` differs in
  26.3 the failure is a compile error in one assertion, and `byKey` from the same extract is
  the fallback.
- `RecipeManager.getRecipeFor(SMELTING, sand, level)` returns the first matching recipe. If any
  other mod in the coordinator's combined server registers a sand smelting or blasting recipe,
  the test fails loudly rather than silently; the by-id assertion is what would catch it.
- The definition and recipe swaps reach private fields by reflection from the test class. They
  are synchronous and restored in a `finally`, but they are not a data reload: a real
  `/reload` mid-run is still untested.
- Whether the three framework edits disturb the M1-02 and M1-03 game tests was not checked by
  running them. What I read: `test_machine.json` declares the same capacity, insert limit and
  per-tick cost as `TestMachineBlockEntity`'s fallbacks; `MachineGameTests.save_load_round_trip`
  runs the ingot recipe, which declares no result components, so its spec round-trips through
  `MachineRecipeSpecCodec.CODEC` unchanged and `adoptRestored` matches it; and no existing test
  relies on `MachineRecipeRunner.adopt`, which is gone.
- A spec with result components is not covered by a round-trip test. The component text is
  re-serialised compactly, so a component-bearing spec could decode to text that differs
  byte-for-byte from the original; that fails closed (the run resets), not open.
- The two screen classes and `SiliconMachineScreen` were never loaded; the client binding is a
  single `RegisterMenuScreensEvent` listener and was not touched this pass.
- `arcforge:creosote_moves` is another mod's optional failure and is outside this scope.

## 6. Deviations

| spec says | what I did | why |
|---|---|---|
| "add a focused runtime regression ... if the existing loader supports it" | a test-local reflective swap of the loader's registry field | `MachineDefinitionLoader` keeps its registry on the loader instance and exposes no setter, and that file is outside the allowed paths; the swap is synchronous, restored in a `finally` and confined to the test class |
| "persist the running recipe spec using ... `MachineRecipeSpecCodec.CODEC`" | done, under the key `spec` in the existing `recipe` save node | no new field is introduced; a save with no `spec` simply resumes nothing |
| "Keep the running cost frozen" | kept frozen across both reload and load | the brief also rejects silently re-pricing only the restored cost, which would make a reload differ from an uninterrupted run |
| Beam: add a `max` clamp to the wafer count | not added | redundant with `FabSettings`'s clamp; a second bound is a second place for the same number to change |
| Beam: restore the current definition's cost | not adopted | it would make a resumed run cost differently from one that was never interrupted |

## 7. Open questions

1. Should `MachineRecipeRunner` re-check inputs a third time on the completing tick? Not a
   product decision; the current form checks at the top and again before writing.
2. Both machine definitions declare `energy_capacity 20000` and `max_receive 2000`. They are
   placeholders. A datapack override would be better evidence that the numbers really are per
   machine, but that is a data decision for the coordinator.
3. `silicon_energy_follows_machine_definitions` still simulates a *changed* definition by
   applying a hand-built one. Now that `silicon_machines_fail_closed_without_a_definition`
   swaps the real registry, it could use the same swap; that is a test-only cleanup, not a
   repair, so I left it.

## 8. Handoff

Framework, all changed here:

- `FabMachineBlockEntity.withoutDefinition()` — private; capacity and insert limit to 0, machine
  cost to 0, run dropped, `DATA_CAPACITY` re-read from the buffer, stored FE kept.
- `FabMachineBlockEntity.refreshDefinition()` — `ifPresentOrElse(applyDefinition, withoutDefinition)`.
- `FabMachineBlockEntity.serverTick()` — returns before any recipe work when the definition is
  absent; otherwise restores progress only through `adoptRestored`.
- `MachineRecipeRunner.adoptRestored(MachineRecipe)` → `boolean`; requires the saved
  `MachineRecipeSpec` to equal the candidate's. `adopt` is removed.
- `MachineRecipeRunner.saveState` writes `progress`, `ticks`, `energy_per_tick`, `running` and
  `spec`; `loadState` reads them back and keeps the frozen cost and the pending spec.

Silicon chain, unchanged this pass:

- `CrystalPullerBlockEntity`: `MACHINE_ID = "crystal_puller"`, `INPUT_SLOT = 0`,
  `OUTPUT_SLOT = 1`.
- `WaferSlicerBlockEntity`: `MACHINE_ID = "wafer_slicer"`, `BOULE_SLOT = 0`, `FOUP_SLOT = 1`,
  `OUTPUT_SLOT = 2`, `SLICE_TICKS_PARAM = "slice_ticks"`; saved state under `slicer`
  (`slicing`, `progress`, `ticks`).
- `WaferSlicing.wafersFor(FabSettings, int)` returns `min(wafersPerBoule, capacity)`;
  `capacity < 1` throws `IllegalArgumentException`, null settings throws `NullPointerException`.

Game tests in `lithmod:silicon_chain`, 16 in all: the fourteen from the first pass plus
`lithmod:silicon_machines_fail_closed_without_a_definition` and
`lithmod:crystal_puller_resumes_only_the_same_recipe`, with
`lithmod:silicon_cooking_recipe_files_match_the_vanilla_schema` replaced by
`lithmod:silicon_cooking_recipes_load_and_match`.

Data, unchanged this pass: `machines/{crystal_puller,wafer_slicer}.json` (20000 FE, 2000 per
insert, 20 and 30 FE/tick, `slice_ticks: 200`), `machine_recipes/crystal_puller.json` (4
silicon → 1 boule, 400 ticks), `recipe/silicon_from_{smelting,blasting}.json` (sand →
`{"id": "lithmod:silicon"}`, 200 and 100 ticks).