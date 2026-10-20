# T3 Purification — implementation report (worker)

Task: implement working Boule Purifier jobs and actual pure-boule slicing; NOT E-beam jobs.
Basis: `build/t3/purification/BRIEF.txt` and `SCOPE.json`. All framework sources referenced
below were read before editing (core `WaterTank`, `FoupContents`, `FoupContentsCodec`,
`WaferSubstrate`, `WaferSlicing`, `Wafer`, `MachineDefinition`, `MachineRecipeSpec`/codes,
`FabMachineBlockEntity`, `MachineRecipeRunner`, `MachineRecipeLoader`/`JsonResourceDataLoader`,
support foundation classes, both slicer/menu/screen sources, `SupportMultiblockGameTests`
(read, not edited), `EUVCRAFTMod`, registries, lang, gametest patterns).

## What was implemented (scoped files only)

### Data model — `recipe/SupportRecipe.java` (new)
Immutable record: `machine`, `input`, optional `secondary`, `outputItem`, `outputCount`,
`duration`, `energyPerTick`, `waterMb`. Constructor is the whole validator:
- machine must be a registered `SupportMachineKind` id (`ofMachine`, pure lookup);
- purifier: input locked to `lithmod:silicon_boule`, output locked to
  `lithmod:pure_silicon_boule`, water strictly `1..WaterTank.MAX_CAPACITY`, no secondary;
- e-beam: `water_mb == 0` (shared loader, no premature e-beam behaviour);
- `duration` in `1..1_000_000`, `energyPerTick` in `1..1_000_000`, counts positive,
  output namespaced — anything else throws (fail closed, no invented fallback costs).

### Loader — `data/SupportRecipeLoader.java` (new)
Separate directory `data/lithmod/support_recipes/`, deliberately distinct from
`machine_recipes/` (support jobs pin their own duration/water/FE and must not be retuned by
the generic runner files). Two-stage reload like `MachineRecipeLoader`: parse via
`SupportRecipeLoader.CODEC` (JSON ops), resolve items at registry build (`buildResolved`
sorts by file id, resolves and validates every named item, fails loudly naming file+field),
published through the project's `JsonResourceDataLoader` so a bad parse keeps the previous
value and a resolution failure keeps the previous registry. `getRegistry()` returning null
means "no support machine may run".

Registered in `EUVCRAFTMod` (`NeoForge.EVENT_BUS.register(SupportRecipeLoader.class)`,
`AddServerReloadListenersEvent` listener id `lithmod:support_recipes`).

### Data — `data/lithmod/support_recipes/boule_purifier.json` (new)
One recipe: 1 `lithmod:silicon_boule` + `1000` mB water -> `1`
`lithmod:pure_silicon_boule`, `400` ticks, `80` FE/tick. Purifier default job cost is
therefore 400 x 80 = **32 000 FE**, one bucket per boule (1000 mB), tank capacity 16000 mB
from the machine definition.

### Jobs + bucket exchange — `multiblock/support/SupportMachineBlockEntity.java`
- Purifier native slots preserved: 0 ordinary boule in, 1 water bucket in, 2
  empty-bucket-out, 3 pure-boule-out (`SLOT_*` constants named).
- Outdated comment fixed: the tank **starts EMPTY** with 16000 mB capacity; no free water.
- Bucket adapter (`tryBucketRefill`): full water bucket -> exact 1000 mB
  (`WaterTank.fillExact(1000, simulate)` on the tank) + exactly one empty bucket into
  slot 2, simulated first, committed once, and only while the cell is formed (documented
  routine consistent choice). Anything else (wrong fluid, full tank, blocked/overflowing
  slot 2) changes nothing. Tank fill/drain stays independent (`WaterTank` ops) so future
  pipes can adapt; still no fluid/item capability.
- Job loop (`tickPurifierJob`/`advanceJob`/`completeJob`): starts only when formed with a
  valid loaded recipe, complete input, tank water >= recipe water and output room; pins
  `JobSnapshot(duration, energyPerTick, waterMb, outputItem, outputCount, inputItem,
  inputCount, waterPinned)` and the exact amounts. Pause rule order per tick: unformed ->
  tamper (cancels, no FE refund) -> output full (pause, progress kept) -> FE all-or-zero
  draw through the existing buffer (`energySource()`, so a partial draw never charges).
  Output room is re-checked before commit; completion consumes 1 boule and the pinned water
  exactly once and then inserts the product into slot 3. Inputs remain until completion.
  The generic `runner()` field stays at rest: **no generic runner call in the support BE**.
- Persistence: running jobs are saved in their own `purifierJob` child (running, progress,
  duration, energy_per_tick, water_mb, output item/count, input item/count, water_pinned)
  through the normal `ValueOutput`; restore re-reads only saved fields over the live
  registries, so a reload can never retroactively change an active job's cost, length,
  water or output, and any implausible field (negative, zero, progress >= duration,
  unknown item id) drops the job and logs — fail closed. Water save/restore unchanged;
  capacity-shrink still preserves the stored amount and blocks further bucket fill until
  room returns. `DATA_PROGRESS`/`DATA_TICKS` published to the menu every tick.
- Formation hooks `onFormed`/`onUnformed` were left as the foundation had them (pause logic
  keys off `isFormed()` directly).

### Items — `registry/ModItems.java`, `registry/ModCreativeTabs.java`, `lang`
- `ModItems.PURE_SILICON_BOULE` (`lithmod:pure_silicon_boule`, plain item, honest javadoc:
  no crafting recipe, on purpose).
- Creative tab: appended right after `SILICON_BOULE` with the proper id.
- Lang: `item.lithmod.pure_silicon_boule` = "Pure Silicon Boule".

### Slicing — `machine/silicon/WaferSlicerBlockEntity.java`, `WaferSlicerMenu.java`
- Slicer accepts ordinary **or** pure boules (`acceptsBouleItem`), same timing/FE/yield;
  `gradeOf(Item)` maps the pure item to `PURE` and the ordinary item to `ORDINARY`.
- Grade of the produced FOUP is frozen at job start: `slicingBouleItem` is pinned when the
  run starts and never re-read from the slot; the empty carrier's grade is never consulted.
  Ordinary carrier + pure boule -> `PURE` lot; pure carrier item + ordinary boule ->
  `ORDINARY` lot. Lot is built with `FoupContents.of(..., grade)` and sliced through
  `WaferSlicing.wafersFor(settings, capacity, grade)` (count unchanged by grade — the core
  helper already provides the overload; core files were not edited, out of scope).
- Mid-run grade swap cancels the run like a removed boule (before FE for that tick).
- Save/load: `slicer.bouleGrade` pins the run's grade; absent field (legacy saves) stays
  ORDINARY, no auto-upgrade; unknown id drops the run (fail closed). Private-wafer-roll and
  new-server-identity rules untouched; slot indices and pause order preserved.
- Menu: the boule slot rule is now a nested `EitherGradeBouleSlot` in `WaferSlicerMenu`
  delegating to `WaferSlicerBlockEntity.acceptsBouleItem` on both sides. `BouleSlot.java`
  was deliberately NOT edited (not in scope; its ordinary-only rule is what older tests
  pin) — see limitations.
- No pure-boule crafting recipe added.

### Tests
- `gametest/T3PurificationGameTests.java` (new, registered only under the existing fixture
  gate in `EUVCRAFTMod`). Ten registered hypotheses:
  purifier_refuses_without_water_output_or_fe,
  purifier_bucket_exchange_conserves, purifier_runs_full_job_1000_water_exactly_once,
  purifier_low_fe_pause_and_resume, purifier_output_full_pauses,
  purifier_reload_keeps_pinned_cost_and_tamper_cancels (includes a synchronous support
  registry swap: a mid-run datapack change must not retune a running job),
  purifier_malformed_save_rejected (progress/duration/energy/water/output_item/input_count
  all rejected), slicer_slices_both_grades_to_matching_foups, slicer_menu_accepts_both_grades,
  slicer_pure_grade_survives_reload_and_refuses_tamper. Grade assertions go through the
  public view / trusted read only — no fake direct-output helpers. The frozen
  `SupportMultiblockGameTests` was not touched.
- `src/test/java/com/euvcraft/lithmod/mc/T3SupportRecipeTest.java` (new): codec decode of the
  shipped recipe (400/80/1000, ordinary->pure), round-trip, and full rejection matrix
  (unknown machine, wrong input/output, secondary, water 0/negative/over-bound, e-beam
  water, ticks/cost/count bounds, non-namespaced output).
- `docs/reports/T3_purification.md`: this document.

## Known limitations (honest, no invented execution)

1. **No commands were run.** No compile, no tests, no Git. Everything above is source-level
   work consistent with the read framework sources; verification stays with the coordinator.
2. **Asset files were not added and were refused as out of scope.** `pure_silicon_boule`
   therefore has a lang entry but no `items/pure_silicon_boule.json`, no item model and no
   texture: in-game it will render as the missing-model cube (functional, ugly). The
   coordinator should authorize adding the two small JSON definitions (model can reuse the
   existing `silicon_boule` texture or a new purer-blue texture drawn by an artist). The
   scoped resource checks all still pass because a definition requires a model — none exists.
3. **Creative tab / presentation tests:** the appended tab entry changes the tab's display
   item count. Any production test pinning the exact tab sequence (e.g. the presentation
   suite) will need its pinned list updated by the coordinator; the tab ordering test is
   outside this task's scope so it was not edited here.
4. **`BouleSlot.java` untouched** (not in scope): the slicer menu now uses its own nested
   slot with the both-grades rule. `BouleSlot`'s ordinary-only rule remains valid-but-unused
   by this menu; a later cleanup may repoint or retire it.
5. **E-beam jobs are intentionally absent**: the loader accepts `ebeam_mask_writer` recipes
   with no water, but the e-beam kind starts no jobs and there is no recipe file for it.
6. **Water strictness:** a running job pins the whole tank amount at start; adding another
   bucket mid-run therefore counts as input tampering and cancels the job (no refund).
   That is the documented consequence of pinning `waterPinned`; if product later wants
   "water only increases" semantics, the tamper clause can be narrowed in one place.
7. **Recipe input count larger than 1** would need slot 0 to hold that many boules; the
   current shipped recipe is count 1 and the code supports counts generally.
8. **FE multiplier:** recipe `energy_per_tick` is used raw (80), deliberately not run
   through the machine-definition FE multiplier — the recipe pins its own priced run; the
   machine definition still owns buffer capacity/receive (100k/10k) and water capacity.
9. The game-test `withSupportRegistry` swap reaches one static field reflectively, the same
   pattern the existing silicon chain tests use for their registries.
10. Default numbers documented: purifier job = **32 000 FE default (400 ticks x 80 FE/tick),
    one water bucket (1000 mB) per pure boule**, tank 16000 mB, buffer 100k FE.

Remaining future work (not started here): E-beam recipes/jobs, T3 admission rules into
support lines, resource item definitions (limitation 2), and any production test updates for
the appended creative tab entry (limitation 3).
