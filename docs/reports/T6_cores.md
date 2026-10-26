# T6 Cores — Test & Package machinery (cores-finish task)

Task: `build/t6/cores-finish/BRIEF.txt`, scope `build/t6/cores-finish/SCOPE.json`.
Worker: edit-only, no commands, no Git, no network, no delegation. Nothing in this
report claims compilation, tests or any other execution.

## Changed files (exactly the two scoped files)

1. `src/main/java/com/euvcraft/lithmod/mc/machine/packaging/TestPackageBlockEntity.java`
2. `docs/reports/T6_cores.md` (this report; it did not exist before this task — no
   prior draft was present)

## What was already present in the worktree (prior `build/t6/cores` draft)

Reading the file before editing showed the earlier cores task was partially
implemented but incomplete:

- Private field `private int designCores;` (designCore freeze, doc comment explaining
  T6-or-later compute only, T5→T6 high-binning inherits the T5 count of 1).
- Private constant `CORES_KEY = "cores"`.
- `startLot(...)` froze `this.designCores = RunSpec.coresOrZero(spec.coresOrNull());`
  at admission, after the judged plan is built and before the FOUP slot is replaced.
- `saveAdditional(...)` wrote `state.putInt(CORES_KEY, this.designCores);`.
- rolls / queue / accounting / cost code was untouched in this task.

## What this finish task added

All inside `TestPackageBlockEntity.java`:

- **Import**: added `import com.euvcraft.lithmod.mc.registry.ModDataComponents;`
  next to the existing `ModItems` import (only new import; the `DieTypes` import and
  the `Item`/`ItemStack` imports already existed).
- **`private ItemStack productStack(Item item)`** — the one helper both output paths
  now build their product/reference stack through. It returns a plain vanilla
  `ItemStack`; it calls `product.set(ModDataComponents.CORE_COUNT.get(),
  this.designCores)` only when **all** of these hold:
  - `this.designTierIndex >= 6` (T6 or later, same tier-index rule the e-beam
    writer uses: `SupportMachineBlockEntity` line ~572),
  - `DieTypes.COMPUTE.equals(this.designDieType)`,
  - `this.designCores >= 1`,
  - `item == ChipCatalog.chipFor(this.designDieType, this.designTierIndex).orElse(null)`
    (identity against the frozen design's own catalog chip).
  Scrap (`ModItems.CHIP_SCRAP`) and any other item therefore never gets a core tag,
  and a queue whose design isn't compute T6+ produces component-free stacks exactly
  as before.
- **`bankRoom(...)`** now builds its reference stack via
  `ItemStack product = productStack(item);`, so the room preflight answers
  `ItemStack.isSameItemSameComponents` against a stack that already carries the
  queued design's `CORE_COUNT` component. Two queues accepted with different core
  counts can no longer merge into each other's slots or mis-state the free capacity
  of a part-filled slot.
- **`insertIntoBank(...)`** also builds `product = productStack(item);` and, on an
  empty slot, writes `setSlot(index, product.copyWithCount(placed));` instead of a
  fresh `new ItemStack(item, placed)`. `copyWithCount` preserves every component, so
  a written T6 compute chip keeps its `CORE_COUNT`, and it stays
  component-identical to the preflight reference (matching other call-sites in the
  codebase: `SupportMachineBlockEntity` lines 303/430,
  `ProcessCellControllerBlockEntity` line 375). Slot growth of a part-filled stack
  still uses `setCount`, unchanged.
- **`loadAdditional(...)`** now restores the frozen core count:
  - Legacy default: `state.getIntOr(CORES_KEY, computeDesign ? 1 : 0)` — a save
    without the key restores **1 for a compute design, 0 for everything else**,
    matching the pre-T6 implicit count (and the T5 side of an old pre-cores T5→T6
    snapshot). Unknown/invalid die types nothing else already threw for still drop
    the snapshot before this default matters.
  - Validation inside the existing `try`, so any failure drops the whole snapshot
    through the existing fail-closed `catch → clearQueue()` (no partial recovery,
    no lot restoration, no logging change):
    - stored count must be in **0..256** (inclusive), else `IllegalArgumentException`;
    - a **compute design at tier index >= 6** must have frozen **>= 1** core.
  - On success: `this.designCores = cores;` assigned together with the other design
    fields after all snapshot validation passed (including the existing
    high-binning / source-tier checks, which stayed byte-for-byte as they were).
  - Parsing/validation is placed after the existing
    "must name a die type the catalog resolves" throw, so the compute default and
    the `DieTypes.COMPUTE` test only see an already-known die type string.
- **`clearQueue()`** now also resets `this.designCores = 0;` — called at the top of
  `loadAdditional` and again in its catch, so stale core counts never outlive the
  queue that owned them.

## Compile-sensitivity checks actually performed (by reading source)

- `RunSpec.coresOrZero` **static signature verified**:
  `public static int coresOrZero(Integer cores)` at
  `src/main/java/com/euvcraft/lithmod/core/RunSpec.java` line 103 — used by the
  pre-existing `startLot` freeze for `spec.coresOrNull()` (an `Integer`), so the
  call compiles as written.
- `ModDataComponents.CORE_COUNT` **verified registered** at
  `src/main/java/com/euvcraft/lithmod/mc/registry/ModDataComponents.java` line 86 as
  `DataComponentType<Integer>` with `Codec.intRange(1, 256)` persistent and
  `ByteBufCodecs.VAR_INT` network codecs. Bounds match the 0..256 snapshot rule
  (only a positive count is ever set, so the codec's 1..256 range is never
  violated).
- `ItemStack.copyWithCount(int)` precedent confirmed in three other machine classes.
- `Codec.intRange` import already present in `ModDataComponents` (not touched;
  outside scope).

## Behaviour explicitly not changed

- Rolls, plan flags, queue accounting, energy costs, package consumption, output
  conservation: untouched.
- `PackagingRun`, `PackagingPlan`, `FabYieldService.evaluateLot`, `HighBinningRules`:
  untouched.
- No output rerolls, no tier-7 content, no export/setSlot semantics changes beyond
  the component-carrying stack inside `insertIntoBank`/`bankRoom`.
- Callers stay `Item`-based (`bankRoom(int,int,Item)` / `insertIntoBank(int,int,Item,int)`
  signatures unchanged); the helper centralises the product build rather than
  changing any signature.

## Coordinator note requested by the brief

- `ProcessCellRunPersistence.java` actual path found by glob:
  `src/main/java/com/euvcraft/lithmod/mc/machine/process/ProcessCellRunPersistence.java`
  (it is *not* under `multiblock/`). Not touched — not in scope for this task.

## Limitations / omissions (honest list)

- **No compilation, no tests, no gametests were run.** This task had no command
  execution. The signature/registration checks above were reading-only.
- The report `docs/reports/T6_cores.md` did not exist previously; this is the first
  version, written afresh — there was no draft report to merge.
- A compute snapshot from before the cores work whose run spec carried a
  non-default positive count would restore as 1 (no key) — by design per brief
  (legacy default 1 for compute). A saved *validate-range* count outside 0..256, or
  0 cores on compute tier-6+, drops the whole queue snapshot (the wafers are lost),
  which is the file's existing fail-closed answer for malformed tags.
- The product helper keys off fields frozen at admission
  (`designDieType/designTierIndex/designCores`); it never re-reads live config, so
  a config change mid-queue cannot alter or depopulate already-queued output
  components.
- The `CORE_COUNT` component is set with the deferred-holder `.get()`; as with the
  e-beam writer's identical pattern this runs only server-side on real stacks.
- Step budget: task finished within the 24-step budget (roughly 14 steps used for
  reads/edits; the report write being the final step).
