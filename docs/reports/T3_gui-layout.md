# T3 GUI Layout Fix — Crystal Puller & Wafer Slicer

## Summary
Fixed overlapping output slots in Crystal Puller and Wafer Slicer menus. The bottom output slots (previously at Y=71) overlapped the player inventory (starts at Y=84) by 5 pixels. Moving the output to Y=53 would have overlapped the progress bar (Y=52–68).

## Changes Made

### 1. CrystalPullerMenu.java
- **Output slot position**: Changed from `(MACHINE_SLOT_X, MACHINE_SLOT_Y + 36)` → `(80, 71)`  
  to `(MACHINE_SLOT_X + 24, MACHINE_SLOT_Y)` → `(104, 35)`
- Input slot remains at `(80, 35)`

### 2. WaferSlicerMenu.java
- **Output slot position**: Changed from `(MACHINE_SLOT_X, MACHINE_SLOT_Y + 36)` → `(80, 71)`  
  to `(MACHINE_SLOT_X + 24, MACHINE_SLOT_Y)` → `(104, 35)`
- Boule slot remains at `(62, 35)` (MACHINE_SLOT_X - 18)
- FOUP slot remains at `(80, 35)` (MACHINE_SLOT_X)

### 3. SiliconMachineScreen.java
- **Progress bar Y**: Changed from `52` → `54`  
  (Progress bar: X=78, Y=54, W=24, H=16 → spans Y=54–69)
- Energy bar unchanged at X=12, Y=18, W=12, H=52 (spans Y=18–69)
- Background size unchanged: 176×166
- Player inventory label position unchanged (computed from imageHeight)

## Layout Verification

### Slot Wells (18×18 centered on slot coordinates)
| Menu | Slot | Coordinates | Well Rectangle |
|------|------|-------------|----------------|
| Crystal Puller | Input | (80, 35) | X=79–97, Y=34–52 |
| Crystal Puller | Output | (104, 35) | X=103–121, Y=34–52 |
| Wafer Slicer | Boule | (62, 35) | X=61–79, Y=34–52 |
| Wafer Slicer | FOUP | (80, 35) | X=79–97, Y=34–52 |
| Wafer Slicer | Output | (104, 35) | X=103–121, Y=34–52 |

### Bar Rectangles
| Bar | Rectangle | Overlap Check |
|-----|-----------|---------------|
| Progress | X=78–101, Y=54–69 | No overlap with any machine slot well (machine wells end at Y=52) |
| Energy | X=12–23, Y=18–69 | No overlap with machine slot wells (machine wells at X=61–121) |

### Player Inventory
- First inventory row: Y=84 (INVENTORY_Y), wells at Y=83–101
- Hotbar: Y=142 (HOTBAR_Y), wells at Y=141–159
- **No overlap** with any machine slot well (all machine wells end at Y=52)

## Constructor Agreement & Quick-Move Preservation
- Slot **indices** unchanged: Crystal Puller (0=input, 1=output), Wafer Slicer (0=boule, 1=foup, 2=output)
- Slot **predicates** unchanged: input slots accept valid items, output slots `mayPlace=false`, `mayPickup=true`
- `quickMoveStack` routing unchanged: machine slots (indices < machineSlotCount) ↔ player slots
- Menu **constructor signatures** unchanged
- Client/server **slot count** identical: 38 (Crystal Puller), 39 (Wafer Slicer)
- Client/server **coordinates** identical (verified by new game test)

## Game Test Added
`silicon_menu_layout_nonoverlap_and_coords` (`MENU_LAYOUT` constant) verifies:
1. Client/server coordinate agreement for ALL slots (x, y, index) on both menus
2. Machine slot wells do not overlap player inventory wells
3. Machine slot wells do not overlap progress bar rectangle
4. Machine slot wells do not overlap energy bar rectangle
5. Exact expected coordinates for all machine slots on both menus

## Required Owner Render Check
**Manual verification needed**: Open each machine GUI in-game and confirm:
- Output slot visually sits to the right of input slots (same row, X=104)
- No visual overlap between output slot and progress bar
- No visual overlap between any machine slot and player inventory/hotbar
- Progress bar renders at Y=54 (slightly lower than before)
- Energy bar renders unchanged at left edge
- Shift-click behavior works: inputs shift-click → machine, output shift-click → inventory

## Files Modified (Scope Only)
- `src/main/java/com/euvcraft/lithmod/mc/machine/silicon/CrystalPullerMenu.java`
- `src/main/java/com/euvcraft/lithmod/mc/machine/silicon/WaferSlicerMenu.java`
- `src/main/java/com/euvcraft/lithmod/mc/machine/silicon/SiliconMachineScreen.java`
- `src/main/java/com/euvcraft/lithmod/mc/gametest/SiliconGameTests.java`
- `docs/reports/T3_gui-layout.md` (this file)

## Files NOT Modified (Per Scope)
- `FabMachineMenu.java` — shared framework, not in scope
- `FabMachineScreen.java` — shared framework, not in scope
- CrystalPullerScreen.java / WaferSlicerScreen.java — no changes needed (inherit from SiliconMachineScreen)
- BlockEntity classes — slot indices unchanged
- No client GUI classes loaded on server

## Limitations
- Tests not executed (per instructions: "do NOT claim any tests executed")
- Render verification requires manual in-game check
- Texture/background art not updated (wells derive from actual slot coordinates via `FabMachineScreen.drawSlotWells`)
- T3 multiblocks/content are separate later phases; not edited here

## Coordinator verification

Source `2e6dc98b368c2e5439b51922c5ad91e5b61eac8c`:579units/80classes pass with zero failures/errors/skips, core-import and release-jar checks pass. The complete server ran835distinctcases: 834passes and 1optional skips, zero required failures. All165projectIDs pass, retaining all164prior IDs; the new actual case is `lithmod:silicon_menu_layout_nonoverlap_and_coords`. The known optional Arcforge creosote failure is preserved; it was not selectively retried.

Original-layout fault `1f74559c23f41a0c8c8df0b76872342aff82769f` failed the new geometry case as intended with unchanged tests; exact tree restored at `a611fd801acbebf7a0e79d2448fa4ce6a4ad55d8`. Nemotron authored the draft. The first actual compilation failed on an undefined test-loop variable; the coordinator repaired only that typo. The failed draft/check are preserved. A coordinator receipt-script quoting defect was also repaired without rerunning successful game tests. All24coordinator Node tests pass. No machine processing logic, slot indices, inventory rules or unrelated screens changed. Final independent free review/candidate acceptance follows; owner-rendered GUI confirmation remains pending.
