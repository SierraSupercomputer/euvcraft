# M1 Operator Panels — Summary Report

**Source head:** `34eb4d385b68d708e933d6de171cd952a56e9d83`
**Units:** 579 | **Classes:** 80 | **Server cases:** 834/834 passed (no fails/errors/skips)
**Project IDs:** 164/164 (146 prior + 18 new) | **Core/jar:** pass
**Feature status:** NOT accepted/integrated — pending final reviews/candidate

---

## 1. Exact Changes Delivered

### Geometry & Block Requirements
- **Operating Panel now REQUIRED** in the Process Cell multiblock.
- Panel sits **immediately above the lower Equipment Cabinet** in the same 2×2×3 (12-block) cell.
- **Lower cabinet** (backing indices 3–7): authoritative inventory, run state, FE buffer (100 000 FE).
- **Upper cabinet** (backing indices 0–2): FOUP, mask, resist, components + **Abort button + status footer only**.
- Block counts: **1 controller, 1 power port, 1 operating panel, 9 casings** (12 total).
  - Optional second power port → 1 controller, 2 ports, 1 panel, 8 casings.
- Old controller block ID retained; no new block entity types added.

### Runtime & Control Facts (from EVIDENCE.json)
| Control | File | Test | Fault Head | Restored Head | Notes |
|---|---|---|---|---|---|
| lower-abort-role | `ProcessCellMenu.java` | `operator_panel_no_lower_abort` | `49d0d70c…` | `8b15c86a…` | Lower cabinet has no abort; valid expected failure |
| stale-upper-panel | `OperatingPanelMenu.java` | `operator_panel_invalid_after_removal` | `ae2c9cd2…` | `f8c2fbf3…` | Upper panel invalidated on removal; valid expected failure |
| wrong-upper-role | `OperatingPanelMenu.java` | `operator_panel_slot_mapping` | `ecc5ddeb…` | `65cac44f…` | Slot mapping correct only on upper; valid expected failure |
| constant-visual-part | `ProcessCellControllerBlockEntity.java` | `operator_panel_visual_parts` | `ddb7a1fb…` | `063e7f1f…` | Visual part constant; valid expected failure |
| stale-member-owner | `ProcessCellMemberBlockEntity.java` | `operator_panel_save_reload` | `a492180c…` | `8eb43d7b…` | Stale owner fixed; valid expected failure |
| vertical-panel-placement | `ProcessCellOperatingPanelBlock.java` | `operator_panel_horizontal_placement` | `f8886fc0…` | `4482123e…` | Panel placement vertical only; valid expected failure |

**Property sharing fix:** Initial native property crash was independent property instances despite same names; now **shared exact property instances across all 4 types**.

### Unit Controls (StructureMatcher / Joined Panel Resources)
- `required-panel-final`: StructureMatcher + OperatingPanelMatcher tests (4+4) — expected failure restored.
- `native-uv-oracle`: JoinedPanelResourceTest (2) — native UV/orientation oracle validated.

### Generator Output (tools/generate-joined-panels.mjs)
- **73 new block PNGs** / **110 total managed PNGs** / **138 JSON** (native UV + orientations).
- Generator checks all passed: `generate-starter-textures.mjs-check`, `generate-joined-panels.mjs-check`, missing-PNG, stale-PNG, invalid-manifest rejection, two-normal-generations byte-identical.
- Source hashes restored; preview hash `498cb71dba8c6473d6b7f37d5dfcd6f1f23946eb3b379ec8996624d356a7ab51`.

---

## 2. Required Upgrade (Existing Worlds)

1. **Manual replacement**: In each formed Process Cell, break the casing block **directly above the lower cabinet's center-back position** and place an Operating Panel.
2. **Top-row controllers** (built before panel requirement) **require full rebuild** — no automatic conversion.
3. **Saved inventories are preserved** — the lower cabinet retains its items, FE, and run state.
4. After upgrade, the cell forms with the new layout; upper cabinet shows Abort + footer only.

---

## 3. Walkthrough Impact (docs/manual-walkthrough-m1.md)

- Step 4 (supplies): +1 `process_cell_operating_panel`, casings 10 → 9 (or 8 with 2 ports).
- Step 12 (build): New diagram showing lower cabinet (equipment backing 3–7), required panel at TOP/MIDDLE above lower BACK/RIGHT, upper cabinet (backing 0–2).
- Step 13 (form/unform): Exclude panel from "break a casing" test.
- Step 23 (abort): Now explicitly "Abort button on the Operating Panel (upper cabinet screen)"; authoritative state remains in lower cabinet.
- Known limitations updated: client recheck pending for panel models/textures; upgrade path documented; feature status flagged as not yet accepted.

---

## 4. Development History (Preserved Failures)

| Phase | Failures | Resolution |
|---|---|---|
| Initial legacy | 8 cases | Property sharing, stale owner, fixture geometry |
| Mirrored fixture | 1 case | Panel placement orientation |
| Final | 0 | All 834 server cases pass |
| Generator draft | Provider 504 | Retried; all checks now pass |
| Test compiler drafts | Re-generated | Source hashes restored |

**Reviews:** Nemotron wrote code/docs/assets drafts. Free Beam focused review found stale owner. Coordinator fixed API/property sharing, stale owner, fixtures, generator, and test issues. Failed attempts preserved per policy.

---

## 5. Known Issues / Workarounds (Carried Forward)

- **Early-loading workaround**: Local client window workaround still required; multiplayer/dedicated server unverified.
- **Client visual pending**: Formed/unformed amber-green textures, Process Cell status labels, **and Operating Panel models/textures** must be verified in rendered client (distinct from automated resource tests).
- **Creative FOUP placeholder**: Fresh empty Creative FOUP needs server inventory tick (hold briefly in player inventory) before Slicer accepts it. Filled Creative copies remain rejected.
- **Abort label/timing**: Must be read in client; no legacy label or instant cancellation asserted.
- **Passing counts**: Lot-dependent; no exact pass count promised.

---

## 6. Acceptance Gate

This feature **has not passed final review/candidate gate**. Integration into a released build requires:
- Final review sign-off
- Candidate build verification
- Owner acceptance of post-polish rendering and Creative interaction

Until then, the walkthrough reflects the development state at source `34eb4d385b68d708e933d6de171cd952a56e9d83` only.