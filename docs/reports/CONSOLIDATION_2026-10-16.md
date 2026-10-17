# Existing-work consolidation

This report supersedes the integration/API claims in the historical M1-02c, M1-07, and M1-08 reports. Scope is the user's request to finish and merge already-started work, without starting the remaining M1 tasks. The October 14 task pack remains authoritative; later core-count choices begin at T6.

## History and scope

Starting master: `61094f4717ace5e2cc13bcf70ae81731b9f60740`. Work was reconciled in an isolated checkout on `reconcile/existing-work`, then verified before integration into the independent `euvcraftmk5` copy. Original `euvcraftmk4nemo` and its OpenCode worktrees were not edited. No GitHub push was made.

| Existing work | Resolution |
| --- | --- |
| `m1-02c-contents-drop` / `5e4b968` | Merged; repaired hopper bypass, client/server slot parity and placement rules, loot-condition syntax, and drop fixture ownership. |
| `m1-07-line-components` / `602dfd2` | Merged once; preserved both item registry imports during the sole content conflict. Kept the canonical positional reader, removed speculative aliases/unused overloads, enforced component kinds per slot, and rejected non-finite maintenance. |
| `m1-08-process-run-state-machine` / `c44325b` | Merged after its M1-07 ancestry; repaired actual energy payment, request dependencies, invalid layer results, restore consistency, and terminal-event invariants. |
| `throwaway/m1-02-m1-03-uncommitted` / `fdb0ccb` | Reviewed and resolved by a history-only merge: the later machine/multiblock implementations supersede its 44 draft paths. No duplicate controller or framework was restored. |
| Preserved stash / `2fd2a8794d523e3981f130f25d9b38a96074e6a1` | Recovered unfinished core/codec separation and missing Mojang/Netty import guards. Kept the current, newer FOUP/yield tests and die-type-aware save schema. History-only merge records reconciliation; original stash remains recoverable. |

The obsolete draft's client screens now live in `mc/machine/screen`, FOUP slots in `mc/machine`, Process Cell blocks/entities in `mc/multiblock`, menu registration in `ModMenus`, and energy handling in the implemented machine framework. Existing core matcher/shape types, tests, registration, and corrected resource paths supersede the draft versions. Retained branch refs and bundles preserve the old source for audit.

No silicon machines, mask generator, Process Cell processing adapter, packaging machine, recipes, or final M1 presentation/gating tasks were started. Finishing the core state machine does not make the full playable M1 loop complete.

## Correctness and simplification

- Hand-fed machines reject vanilla hopper insertion/extraction through `Container`; manual menu slots retain their intended placement rules. Client and server menus have identical slot counts, indices, and placement behavior.
- Production removal code exposes an empty extra-content extension point. Only the test fixture owns the extra test inventory; the redundant list-backed adapter and redundant post-drop clear were removed. Loot tables use the correct `conditions` array.
- A process advances only after both simulated and actual FE withdrawals succeed. Resist is charged once per layer and survives pauses and restore. The environment must return the same lot identity, capacity, size, requested layer, and matching frozen run spec.
- Snapshots retain their six-field shape, but reject invalid progress, inconsistent terminal flags, mismatched persisted lot/layer, and missing paid resist. Finished state derives terminal flags and cannot emit mask/finish events again. Restore requires the current persisted lot and the same settings, not the original fresh lot.
- Minecraft codecs for RunSpec and four machine records live in `mc`, preserving field names and defaults. Core records retain validation/logic. Removed the unused decoder that omitted the frozen run spec. The import guard now rejects ordinary and static Minecraft/NeoForge/Mojang/Netty/mc imports.

## Current public interfaces

Signatures were verified against compiled classes with `javap`.

```java
ProcessStartResult ProcessRun.start(ProcessRunRequest request);
// sealed result: ProcessRun or RefusalReason
ProcessTickResult ProcessRun.tick(ProcessEnvironment environment);
ProcessRun.Snapshot ProcessRun.snapshot();
ProcessRun ProcessRun.restore(ProcessRunRequest currentLotRequest, ProcessRun.Snapshot snapshot);

// argument order:
new ProcessRunRequest(lot, dieType, tier, cores, usesRemaining, lineStats,
    lineCanRunTier, dieMakeable, installedGenerations, generationMatrix, settings, energyPerTick);

LineLoadout LineLoadoutReader.read(lightSource, optics, stage, cleanroom,
    metrology, resist, maskTier);
```

`Snapshot` fields: `(state, currentLayer, progressTicks, resistPaid, maskUseConsumed, finishEventsEmitted)`. Tick result is `(state, events)`. Event types are `LAYER_COMPLETED`, `MASK_USE_CONSUMED`, and `FINISHED`; there is no invented unified `ProcessRunResult` or start/tick alias API. Serialization consumers use `RunSpecCodec` and `mc.data.Machine*Codec`, rather than codec fields on core records.

## Verification evidence

Runtime source checkpoint: `aba1efd5381458f9d94c37bd1283bcb1efef2333`. Review follow-up `7e21118` adds only two pure-core snapshot guards and their regressions; no Minecraft code uses ProcessRun yet. Its final unit/import/package run is recorded separately below. Later report-only commits do not change the verified source.

1. Fresh pre-merge runtime baseline: **705 runtime checks, all required checks passed**.
2. Negative drop regression: **712 runtime checks**, exactly **2 required failures**, reproducing hopper access and client/server slot-count mismatch. Repaired candidate then completed **712 runtime checks with all required checks passing**.
3. Negative component regression: **716 runtime checks**, exactly **1 required failure**, reproducing wrong-kind component acceptance. Menu/hopper follow-up checks passed in that run. Separate unit regression reproduced non-finite maintenance acceptance.
4. Negative process/import regression: **31 unit tests, 11 failures**: eight process defects and three missing import guards.
5. Combined repaired unit/package run: **400 unit tests in 67 XML suites, 0 failures, 0 errors, 0 skipped**; strengthened `checkCoreImports` and `jar` passed.
6. Independent core compilation: all core sources compile with only the neutral SLF4J logging API, without Minecraft, NeoForge, Mojang, or Netty on the classpath. The first no-dependency probe identified the existing SLF4J dependency; a sandbox cache-access diagnostic was resolved by rerunning with proper access.
7. Final combined runtime run: **BUILD SUCCESSFUL; all 716 required tests passed** (GameTest server report). One optional co-mod test, `arcforge:creosote_moves`, failed because its armor stand did not drift. No required failure remains. Runtime source is `aba1efd`; the only subsequent source changes are the two pure-core guards and their unit tests in `7e21118`, with no Minecraft callers.
8. Final review follow-up: `7e21118` rejects impossible RUNNING/zero-progress/paid-resist and PAUSED_NO_POWER/unpaid-resist snapshots, with positive controls for their valid alternatives. **402 unit tests in 67 XML suites, 0 failures, 0 errors, 0 skipped**; core import check and packaged JAR passed again. No Minecraft adapter changed after the runtime run.
9. Independent free Beam reviews of the final supplied source snapshots both returned **APPROVE**, for process/menu and component/persistence/machine codecs. Review evidence is in `CONSOLIDATION_2026-10-16.review.json`. The approved free DeepSeek fallback returned no final text; it was not counted as an approval. No paid worker/model was used.

The retained stash-method audit confirms all 16 FOUP methods and all eight yield methods from the stash exist in the current implementation. The remaining on-disk old M1-03d worktree is clean and its commit is already integrated; the other old worktree registrations point to missing directories. They were not pruned or changed. The orchestrator has no unmerged branches or unfinished queued tasks, and its 16 regression tests passed during this consolidation.

Logs and free-model review outputs are retained in the coordinator workspace's `.orchestrator/reconciliation/`. Important logs: `baseline-gametest.log`, `drop-negative-control.log`, `drop-verified.log`, `component-runtime-negative.log`, `component-unit-negative.log`, `process-checker-negative.log`, `consolidated-unit-build.log`, `consolidated-final.log`, and `pure-core-verified.log`. Deliberate negative runs are evidence, not remaining failures.

The initial broad Beam review reached its step limit and was not accepted as approval. Its unused-decoder finding was fixed. Its menu-count concern was checked against actual slot routing and parity tests; different input and total-machine counts are intentional. Its import-parser concern is bounded by the check's stated purpose and the separate dependency-limited compilation. A focused review then found the two invalid snapshot combinations; both were fixed before the final process approval. One adapter attempt returned no verdict and was not accepted; the bounded final source-snapshot review completed and approved.

## Backups and limits

The coordinator retains a verified pre-consolidation bundle, another bundle before advancing master, and a final bundle after integration, under `.orchestrator/backups/`. Branches and the original stash remain preserved; no reset/clean was used. The original mk4 checkout remains at its starting commit.

Runtime tests include the bundled co-mods; optional Arcforge failures are reported separately and do not become hidden mod failures. No visual client walkthrough was performed. No claim is made that the unstarted M1 tasks or the complete player loop have been implemented or verified.
