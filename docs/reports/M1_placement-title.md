# M1 Placement & Title Repair — Reviewer Handoff

**Head:** `a4d0fb38a3284416a527bf70c7588ed8b122ade5`  
**JAR:** `lithmod-1.0.0.jar` (SHA-256 `4fe7ef426c6032ed3b09e8f111e1c0751c8a7df2fc90b7d4687dcfa2f165931e`)  
**Authoring time:** 2026-10-20 (source and fault controls verified; final reviews/candidate integration pending)

---

## Summary

Fixed the owner crash (`IllegalArgumentException: cannot set horizontal facing to UP`) in `ProcessCellControllerBlock.getStateForPlacement` line 62. The old code used `context.getNearestLookingDirection()`, which returns a vertical direction when the player looks steeply up or down. The repair installs the 26.3 API `context.getHorizontalDirection().getOpposite()`, guaranteeing one of the four horizontal facings for every placement context.

Verified the Process Cell controller's dynamic display name uses the translation key `block.lithmod.process_cell`, and the bundled `en_us.json` resolves that key to **"Lithography Machine"**. A JUnit resource test enforces that every production machine definition on the classpath (`crystal_puller`, `wafer_slicer`, `pattern_generator`, `test_package`, `process_cell`) has a non-blank `block.lithmod.<id>` title, with `process_cell` pinned to the exact string.

No overridden direction methods, no `lookAt` resetting requested pitch, absolute vs relative world coordinates corrected. Cleanup `finally` blocks clear inventory, controller block entity, and support stone — no server connection or player list pollution.

---

## Source Changes (Measured)

| Area | File | Change |
|------|------|--------|
| Placement facing | `ProcessCellControllerBlock.java:62` | `defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())` |
| Display name | `FabMachineBlockEntity.java:548-549` | `Component.translatable("block.lithmod." + machineId())` (unchanged logic, verified) |
| Language resource | `assets/lithmod/lang/en_us.json` | Added missing `"block.lithmod.process_cell": "Lithography Machine"` |
| Game tests | `PlacementAndTitleGameTests.java` | Three new game-test functions registered under `lithmod:` namespace |
| JUnit resource test | `MachineTitleResourceTest.java` | Validates 5 production machine IDs + titles; missing fails, no MC bootstrap |

---

## Test Execution Facts (from SOURCE-RESULTS.json)

**Unit tests:** 525 tests, 78 classes, 0 failures, 0 errors, 0 skipped  
**Server / game tests:** 816 unique case records, all passed; all 146 project IDs present/passing, retaining all 143 earlier IDs  
**Coordinator tests:** 24 passed  
**Jar verification:** release mode, 453 entries, production entries present, passed  

**New game-test IDs (registered in `EUVCRAFTMod` normal-fixture branch):**

| Test ID | Purpose | Iterations |
|---------|---------|------------|
| `lithmod:placement_title_vertical_contexts` | 4 yaws × 3 pitches (−89.9, 0, +89.9) → asserts facing = horizontal opposite of yaw; steep pitch makes `getNearestLookingDirection()` vertical (UP/DOWN) matching pitch sign | 12 |
| `lithmod:placement_title_actual_placement` | Real `BlockItem.place(context)` with real support stone + hit, 4 yaws × 2 steep pitches, clears each iteration in `finally` | 8 |
| `lithmod:placement_title_localized_name` | Placed controller's `getDisplayName()` is a `TranslatableContents` with key `block.lithmod.process_cell`; bundled `en_us.json` resolves to "Lithography Machine" | 1 |

**JUnit resource test:** `MachineTitleResourceTest.productionMachineTitlesExistInLanguageFile` — reads `data/lithmod/machines/` from classpath, asserts exactly the 5 production IDs, each has non-blank title in `en_us.json`, `process_cell` equals "Lithography Machine".

---

## Controls & Reviews (Status at Authoring Time)

- **Old-placement control:** restoring `getNearestLookingDirection().getOpposite()` failed exactly the vertical-context and actual-placement cases; the title case passed.
- **Missing-title control:** removing `block.lithmod.process_cell` from the language resource failed exactly the localized-name case; both placement cases passed.
- Both actual three-case server processes completed with those intended failures. Test source stayed unchanged; exact production/resource bytes and the full original tree were restored in new commits. Fault and restoration histories/XML are retained.
- **Optional dependency outcomes:** zero optional skips/failures in this source run; no dependency edits or selective rerun hid an outcome
- **Two fresh independent reviews:** pending
- **Separate candidate / master integration:** pending
- **Earlier compile failures:** preserved in history (Nemotron first draft `f8b95d6` had 4 production-test compiler errors; GLM rewrite `0e3e194` had one checked `IOException` from `try`-with-resources close; coordinator added narrow `IOException`→`IllegalStateException` while retaining `finally` cleanup)

---

## Scope Boundaries (Owner-Confirmed)

- **Authorized subsequent task (not part of this repair):** Equipment Cabinet below / Operating Panel above, fixed 2×2×3 joined textures
- **Unchanged:** existing run/inventory/energy private boundaries, facing property, default formed flag, rotation and formation behavior
- **No:** panel implementation, higher-tier expansion, dependency changes, paid inference, push, MK4/client/world edits, daemon activation
- **All prior IDs / source cases untouched**

---

## Limitations & Open Items

1. Fault controls are verified; final candidate outcomes and exact review/integration heads will be recorded in `M1_placement-title_acceptance.md` after acceptance.
2. Two independent reviews and candidate/master integration have not occurred; this report is a factual handoff, not an acceptance claim.
3. Owner client placement/title confirmation still pending.
4. The free writer did not execute commands or verification; the coordinator owns those recorded checks and Git operations.
