# T6 repair ui: E-beam core-selector overlap + Utility Plant batch progress

Task: `build/t6-repairs/ui/BRIEF.txt`, scope `build/t6-repairs/ui/SCOPE.json`.
Worker: free-only model, no commands, no compilation, no Git, no network, no delegation.
Everything below claims an edit was written into the listed files, not that it was executed
or built. The coordinator runs verification and client review.

## Repair 1 — E-beam Mask Writer core selector vs FE readout

### Root cause (read from actual coordinates)

`SupportMachineScreen` (one class serves the Purifier and E-beam shells; E-beam branch only)
was already repaired once for the core picker: start (x8..96) and the cores cycle button
(x100..168) share the row at y200..216 — but the batch FE total was still drawn with
`right(..., 204)` right-aligned to x=168, i.e. directly on top of the cores button:
FE text pixels (y204..212, ending at x=168) vs button pixels (y200..216, x100..168).
The `right()` helper even special-cased `y == 204` to clip 68px, evidence the collision was
papered over rather than moved.

### Reflow (all inside the opaque 176x220 window; hotbar slot well ends at y159)

E-beam lower band, local coordinates (local = leftPos/topPos offsets):

| y            | content                                                    |
|--------------|------------------------------------------------------------|
| 166..174     | status line, right-aligned to x=168 (was 168)              |
| 176..184     | batch FE total `compact(ebeamTotalEnergy) + " FE"`, right-aligned (was 204, on the button) |
| 184..200     | selector row: `<` at x8..28, `>` at x148..168 (was y180..196) |
| 188..196     | design text, centered, clipped 116 → x30..146, between the arrows (was 184) |
| 202..218     | start x8..96 and `Cores: N` x100..168 (was y200..216; 1px clearance to the 219 border row) |

- Purifier branch unchanged (formed label y168, water y178; no buttons).
- `right()` no longer special-cases `y == 204`; it clips 160 uniformly (FE strings are short,
  so the wider clip changes nothing visually).
- Mechanics untouched: same button ids (`EBEAM_DESIGN_BUTTON_BASE..START`, `CORE_BUTTON=1000`),
  same `send()` route, same `updateButtons()` enablement from server-synced fields
  (`ebeamCoresSelectable`, `ebeamSelectedCores`, `ebeamDesignAvailable`). Coordinates are the
  only change; every widget stays inside the window and no two controls share pixels.

## Repair 2 — Utility Plant batch progress

### Root cause

`UtilityControllerBlockEntity` owns the batch (progress/cost/pendingOutput fields + its own
`sync()` into the base `ContainerData` — no second runner was needed or added), and its own
`sync()` was already updating `DATA_PROGRESS`/`DATA_TICKS` every server tick. But
`UtilityScreen` overrides `extractLabels` entirely and never drew them, so a running
purification batch was invisible. The menu also exposed no per-batch costs, so a status line
would have been forced to hardcode "8000 mB".

### Sync design (menu-local tank `ContainerData`, indices extended 5 → 11)

Indices 0..4 are byte-for-byte the original tank wire (formed, raw amount/capacity, pure
amount/capacity), so every existing reader keeps its meaning. New viewer-facing indices:

| idx | meaning | server source |
|-----|---------|----------------|
| 5   | active (admitted batch present) | `hasJob()` i.e. `pendingOutput > 0` |
| 6   | batch water in mB | settings `water_per_batch_mb` (0 without a definition) |
| 7   | batch output mB | admitted `pendingOutput` while running, else settings `pure_per_batch_mb` |
| 8   | batch filters | settings `filters_per_batch` |
| 9   | batch FE per tick | admitted `cost` while running, else `definition.energyPerTick(null, settings)` |
| 10  | batch ticks total | admitted `duration` while running, else settings `duration` |

- Costs come from the admitted job (frozen at admission: `pendingOutput`, `duration`, `cost`)
  or from the same `definition().paramOrDefault`/`energyPerTick(null, settings)` calls the
  admission check itself uses — nothing hardcoded client-side, no recipe/balance change.
- "Accurate": the controller's `sync()` now publishes `DATA_PROGRESS`/`DATA_TICKS` as 0 while
  the plant is unformed or definitionless, instead of the stale numbers of a saved job;
  sync is display-only, fields and save paths are untouched (a re-formed plant resumes the
  admitted batch exactly as before).
- Progress/total reuse the base data slots (`menu.progress()`/`menu.ticks()`) — the existing
  synced values, no duplicate wire and no second runner.
- Fail-closed display: without a definition all batch values sync as 0 and the screen says
  so rather than pretending ready (matches `serverTick`, which cannot admit without one).
- Client menu path (`machine == null`) got `SimpleContainerData(11)` to match the count;
  `ContainerData` carries full ints on this 26.3 path (same route the e-beam uses, no 16-bit
  clamp).

New public surface only: `UtilityControllerBlockEntity.batchWaterMb/batchOutputMb/
batchFilters/batchFePerTick/batchTicks` (plus existing `hasJob`) and
`UtilityMenu.active/batchWaterMb/batchOutputMb/batchFilters/batchFePerTick/batchTicks/
filterCount`. `filterCount()` reads machine slot 0 of this side's slot list (client mirrors
the server stack through normal slot sync), used only for the ready check.

### Screen layout (opaque 176x238 window; hotbar well ends at y159; border row at 237)

| y        | content |
|----------|---------|
| 6        | title (unchanged) |
| 23       | Filter / Water / Bucket slot labels (unchanged) |
| 57       | `energy / capacity FE` (unchanged) |
| 72       | player inventory label (unchanged) |
| 170      | formed / `Unformed: check structure` (unchanged) |
| 182      | batch status, only while formed |
| 194      | batch cost line: `Batch: N filter(s), W mB raw -> X mB pure, Y FE` (k/M/B compacted, long math) |
| 206 / 218 | Water / Ultrapure tank rows (moved below the batch block while formed) |
| 228      | `Pure outlet to scanner inlet` — the two old rows 213/224 merged into one line |
| unformed | tank rows return to their original 184/198 positions |

Status grammar (always a full-width-clipped `line`, never outside the window):

- active + batch params invalid → `Paused: settings not loaded`;
- active + pure free < output → `Paused: pure tank needs X mB more free` — mirrors the
  server's stall condition `pure.capacity()-pure.amount() >= pendingOutput`;
- active + buffer < admitted FE/t → `Paused: waiting for N FE/t` — mirrors
  `energySource().draw(cost)==cost` (full-or-zero, so `energy() >= fe` matches "advancing";
  the T5 game test's no-power pause reads the same way);
- active and unblocked → `screen.lithmod.progress` → `Progress: p / t`, the exact synced
  base values;
- idle → readiness in admission order (raw water short, filter short, pure output space,
  FE short, else `Ready: next batch queued`) or `Idle: settings not loaded` when the
  definitions are absent. Idle readiness is a display-level mirror of the controller's
  admission checks from synced values; the server remains the only authority that actually
  admits a batch.

## Files touched (all within SCOPE.json)

- `src/main/java/com/euvcraft/lithmod/mc/multiblock/support/SupportMachineScreen.java`
  — coordinate reflow only (documented above).
- `src/main/java/com/euvcraft/lithmod/mc/multiblock/utilities/UtilityControllerBlockEntity.java`
  — public batch accessors + display-only accuracy fix in `sync()`.
- `src/main/java/com/euvcraft/lithmod/mc/multiblock/utilities/UtilityMenu.java`
  — tank wire 5→11 with batch indices + accessor methods.
- `src/main/java/com/euvcraft/lithmod/mc/multiblock/utilities/UtilityScreen.java`
  — batch status/cost lines and lower-band reflow.
- `docs/reports/T6_repair_ui.md` — this file.

## Not done / limitations

- No commands, no build, no tests run, no Git operations; compile/runtime verification is the
  coordinator's. Nothing here is confirmed to launch — only that the edits are written.
- Status strings on the utility screen are English literals, matching that screen's existing
  style ("Formed", "Water: ... mB"); no new translation keys were added because the brief's
  scope is display repair, not localization. The running line reuses the existing
  `screen.lithmod.progress` key.
- `UtilityMenu.filterCount()` depends on vanilla slot mirroring for the client-side filter
  count; if slots are not yet synced (first frames after open) the ready check may briefly
  say "insert filter". Server decisions are unaffected.
- Paused/running inference compares values the server synced at its last tick; between ticks
  the display can lag the server by one tick's worth of energy. It never invents state the
  server did not send.
- The E-beam FE text row keeps its old data source (`ebeamTotalEnergy()`); no new sync was
  added there because none was missing.
