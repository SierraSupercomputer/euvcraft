# T6 reflow: calibration UI + client registration + matcher test

Task: `build/t6/reflow-ui/BRIEF.txt`, scope `build/t6/reflow-ui/SCOPE.json`.
Worker: free-only model, no commands, no Git, no network, no delegation. Anything below that
claims an edit was made, not that it was executed.

## Actually changed files (all within scope)

### New: `src/main/java/com/euvcraft/lithmod/mc/multiblock/reflow/ReflowCalibrationScreen.java`

- `AbstractContainerScreen<ReflowCalibrationMenu>`, fixed 320x238 like the scanner
  `DiagnosticScreen` (same 26.3 `GuiGraphicsExtractor` extraction API: `extractBackground` for
  panel/graph/slot wells, `extractLabels` for text). Opaque panel fill; menu slots drawn as
  18x18 wells.
- Graph box 290x60 at local (15,24), temperature window 20.00..320.00 C (centi-degrees as
  synced: every menu curve point is already `round(temp * 100)`, mapped lineally and clamped).
  Buttons, unlike the scanner, use the menu's existing public click ids
  (`BUTTON_HEATER_MINUS..BUTTON_AIRFLOW_PLUS`, `BUTTON_TEST`, `BUTTON_ACCEPT`) via
  `handleInventoryButtonClick`; there is no new packet or protocol.
- Six -/+ `Button` rows for Heater/Conveyor/Airflow below the graph, labels showing the synced
  0..100 values; Test and Accept buttons sit in the free right margin (x 232..310) beside the
  inventory. Coupon slot at (283,127), labeled "Coupon"; player inventory title at (8,144) over
  the 79..223 x 160..230 slot block exactly as the menu lays it out.
- Button enablement each tick: -/+ only when formed and not locked; Test only when formed, not
  locked, `CAL_COUPON_OK == 1`, `CAL_TEST_FE > 0` and synced energy >= test cost; Accept only
  when `CAL_ACCEPT_READY == 1`. Server-side validation in
  `ReflowControllerBlockEntity.testThermal/acceptThermal/adjustThermalControl` is untouched and
  stays authoritative.
- `CAL_TESTED == 0`: gray live nominal preview only (server recomputes `previewCurve()` after
  every +/-; the client adds nothing). `CAL_TESTED == 1`: last paid measured curve drawn in
  cyan on top. Bands readout shows the exact synced rule bounds; measured four-band readout
  (R/P/D/C) only after a paid test; measured Q vs min Q and the heating/peak/dwell/cooling
  problem list from the synced problem bit mask.
- Amber visual guidance: shaded envelope between a fast/hot and a slow/cool guide profile and
  the exact synced peak band lines, both computed only from `CAL_MIN_PEAK`/`CAL_MAX_PEAK`
  (drawn only when those bounds are sane), with fixed normalized-time phase fractions
  documented in-source as visual guidance, not a definition read.
- All curves are drawn column-wise and clamped inside the graph box; every text line is
  `plainSubstrByWidth`-clipped so nothing crosses the window, the inventory label or the coupon
  slot.

### New: `src/main/java/com/euvcraft/lithmod/mc/multiblock/reflow/ReflowClientEvents.java`

`@EventBusSubscriber(modid = lithmod, value = Dist.CLIENT)` registering
`CONTROLLER_MENU -> ReflowControllerScreen`, `OPERATING_MENU -> ReflowOperatingScreen`
(both existed unregistered) and the new `CALIBRATION_MENU -> ReflowCalibrationScreen`, same
pattern as `ScannerClientEvents`.

### Edited: `src/main/java/com/euvcraft/lithmod/mc/multiblock/reflow/ReflowCalibrationMenu.java`

- Added ten public `ContainerData` indices between `CAL_MEASURED_AIRFLOW` (28) and the curve
  blocks: `CAL_MIN_RAMP/CAL_MAX_RAMP/CAL_MIN_DWELL/CAL_MAX_DWELL/CAL_MIN_COOLING/CAL_MAX_COOLING`
  (`CAL_PREVIEW_START` moved 29 -> 39, all existing indices remain constant-relative) and
  `CAL_MEASURED_RAMP/PEAK/DWELL/COOLING`. Bounds come server-side from the controller's
  `thermalSettings()` record getters (datapack JSON hundredths; peak in centi-degrees to match
  the existing `minPeakHundredths()`), measured metrics from the controller's persisted
  measured values, all in hundredths. No client global definition read, no seed/roll exposure;
  the client only ever sees data-slot ints.
- No change to slot layout (coupon 283,127; inventory 79,160..214), `stillValid`,
  `clickMenuButton` server validation or `quickMoveStack`.

### Edited: `src/test/java/com/euvcraft/lithmod/core/multiblock/ReflowStructureMatcherTest.java`

The pre-existing pure test already covered: all four facings x three controller columns, the
18-member canonical order (width 3, height 3, depth 2), exactly one operating panel above the
controller, calibration panel anywhere else on the middle layer, 1 and 2 power ports, missing/
extra/misplaced panels, duplicate controller, every foreign `BlockKind`, determinism and the
geometry oracle. Added on top:

- `shape_isTheFixed3x3x2Box`: explicit 3x3x2 / 18-cell dimension assertion.
- `match_acceptsPowerPortsAnywhereInCasingCells`: power ports at arbitrary non-panel cells.
- `match_rejectsWrongDimensions`: a 3x3x1 world (back layer as `OTHER`) must not match.

No main matcher source was touched.

## Not done / limitations (no execution was claimed or run)

- `ThermalProfile.java` does not exist in this worktree (T6 core helper is a separate task per
  `build/t6/THERMAL-CONTRACT.txt`), so it was never read. The menu deliberately avoids it: bands
  come exclusively from the controller's public `ThermalSettings` record getters, and the screen
  uses only synced menu values.
- Layout is coordinate-arithmetic against `DiagnosticScreen`, the menu slot constants and
  `FabMachineScreen`'s `imageHeight - 94` inventory-label convention; pixel-perfect overlap of
  e.g. long problem lists against the coupon-label column was mitigated by clipping but cannot
  be verified without rendering.
- The shaded target envelope is intentionally approximate guidance (`GUIDE_HEAT_END`,
  `GUIDE_COOL_START` constants); only the peak band lines and the numerical readouts are exact
  to the synced JSON bounds, per the brief's allowance.
- `Math.clamp(int,int,int)`, `String.format(Locale.ROOT, ...)`, translucent ARGB fills and
  method-reference lookups of the `ThermalSettings` accessors were checked against repository
  usage patterns (e.g. `DiagnosticScreen`, `ScannerDashboardScreen`), not compiled. Compile,
  tests and any visual check belong to the coordinator.
- No block/item/economy/JSON changes, per the brief; screen strings are plain literals like the
  neighboring T6 screens (no new lang keys).
