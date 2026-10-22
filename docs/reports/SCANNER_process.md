# Scanner process hooks and frozen calibration

Coordinator-corrected integration of the preserved Nemotron process draft. The original interrupted draft remains at c37c1a06be380eb83c46dece3d65ad18110744c1; its completion checklist was not verification evidence.

The base ProcessCell controller has a protected typed/input-count constructor and an equipment(logicalSlot) view hook. The small cell retains eight actual inventory slots; the scanner has three. Both admission loadout construction and accepted-input matching use the equipment hook. The scanner derives item views from real module blocks and never stores phantom equipment stacks.

Small-cell supportsTier permits T1–T3; scanner permits T4. Initial admission and saved pending-run restoration enforce that boundary. readyForAdmission gates scanner calibration; the scanner Operating Panel syncs its public required-calibration status separately. Existing FOUP/mask/resist locks, top-up protections and Abort are reused through ProcessCellMenu's inventory-size constructor.

ProcessCellRunPersistence.Contract stores frozenYieldFactor, finite and in (0,1], with legacy constructor/codec default1. A paired map codec retains the original flat save fields without exceeding RecordCodecBuilder's 16-field arity. ProcessRunRequest was deliberately not extended: the persisted contract supplies the factor directly at layer application.

FabYieldService's explicit-factor overload preserves server/private-view/die checks. YieldRollEngine multiplies effective yield once when creating RunSpec and uses that same factored specification for the first and all later layer weights. Subsequent layers, packaging and high binning retain the stored final yield. Defect factor remains a distinct input. No private yield or wafer-roll data is exposed in the calibration menu.

Scanner yield math tests live under src/test/java/com/euvcraft/lithmod/core/ScannerYieldFactorTest.java so they can use the package-local reference tiers. Native authority, gating, power, persistence and conservation checks are in ScannerGameTests; the four T4 full-chain tests now use the scanner and actual calibration menu controls. Validation is coordinator-owned and still pending final whole-suite checks/review.
