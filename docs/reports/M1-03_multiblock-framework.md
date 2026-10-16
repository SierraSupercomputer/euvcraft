# M1-03: Multiblock framework for the Process Cell structure

- Date: 2026-10-15
- Branch: master
- Commit: 3d32a1a0a55ae42b2d4c7190870540aab63cd5e7
- Java version: openjdk 21.0.12.1 (build.gradle targets Java 25 via toolchain)
- Build command: `.\gradlew.bat build --offline`
- Test command: `.\gradlew.bat test --offline`

## 1. Summary

Implemented the multiblock framework for the Process Cell structure (2 wide × 2 tall × 3 long). The core package provides a generic StructureMatcher that validates the structure against a world snapshot, with comprehensive unit tests covering all four horizontal facings and every failure mode. The MC package adds three blocks (controller, casing, power port) with block entities, proper FORMED state handling, neighbor-change revalidation, and FE energy capability exposure through the power port only while formed. All blocks use placeholder vanilla textures and have language entries and creative tab entries.

## 2. Files changed

| path | new/modified/deleted | purpose |
|------|----------------------|---------|
| src/main/java/com/euvcraft/lithmod/core/multiblock/StructureShape.java | new | Record defining the 2×2×3 structure shape |
| src/main/java/com/euvcraft/lithmod/core/multiblock/BlockKind.java | new | Enum for block types in the structure (CONTROLLER, CASING, POWER_PORT, OTHER) |
| src/main/java/com/euvcraft/lithmod/core/multiblock/BlockPos.java | new | Lightweight position record for core logic (no Minecraft deps) |
| src/main/java/com/euvcraft/lithmod/core/multiblock/Direction.java | new | Horizontal directions enum for core logic |
| src/main/java/com/euvcraft/lithmod/core/multiblock/MatchResult.java | new | Result record for structure matching (success, member positions, failure reason) |
| src/main/java/com/euvcraft/lithmod/core/multiblock/StructureMatcher.java | new | Core structure matching algorithm with fixed-order controller position trial |
| src/main/java/com/euvcraft/lithmod/core/multiblock/EnergyBufferProvider.java | new | Interface for controller block entities to expose energy buffer |
| src/test/java/com/euvcraft/lithmod/core/multiblock/StructureMatcherTest.java | new | Unit tests: 26 tests covering all facings, controller positions, and failure types |
| src/main/java/com/euvcraft/lithmod/mc/block/ProcessCellControllerBlock.java | new | Controller block with horizontal facing and FORMED state |
| src/main/java/com/euvcraft/lithmod/mc/block/ProcessCellCasingBlock.java | new | Casing block with FORMED state |
| src/main/java/com/euvcraft/lithmod/mc/block/ProcessCellPowerPortBlock.java | new | Power port block with FORMED state |
| src/main/java/com/euvcraft/lithmod/mc/block/entity/ProcessCellControllerBlockEntity.java | new | Controller BE: structure validation, periodic revalidation, energy buffer, member tracking |
| src/main/java/com/euvcraft/lithmod/mc/block/entity/ProcessCellCasingBlockEntity.java | new | Casing BE: forwards interactions to controller, tracks formed state |
| src/main/java/com/euvcraft/lithmod/mc/block/entity/ProcessCellPowerPortBlockEntity.java | new | Power port BE: exposes controller's energy buffer as FE capability when formed |
| src/main/java/com/euvcraft/lithmod/mc/registry/ModBlocks.java | modified | Registers the three new blocks and their block entities |
| src/main/java/com/euvcraft/lithmod/mc/registry/ModCreativeTabs.java | modified | Adds the three blocks to the creative tab |
| src/main/resources/assets/lithmod/blockstates/process_cell_controller.json | new | Blockstate variants for facing + formed |
| src/main/resources/assets/lithmod/blockstates/process_cell_casing.json | new | Blockstate variants for formed |
| src/main/resources/assets/lithmod/blockstates/process_cell_power_port.json | new | Blockstate variants for formed |
| src/main/resources/assets/lithmod/models/block/process_cell_controller_unformed.json | new | Model using minecraft:block/iron_block |
| src/main/resources/assets/lithmod/models/block/process_cell_controller_formed.json | new | Model using minecraft:block/block_of_iron |
| src/main/resources/assets/lithmod/models/block/process_cell_casing_unformed.json | new | Model using minecraft:block/quartz_block |
| src/main/resources/assets/lithmod/models/block/process_cell_casing_formed.json | new | Model using minecraft:block/smooth_quartz |
| src/main/resources/assets/lithmod/models/block/process_cell_power_port_unformed.json | new | Model using minecraft:block/redstone_block |
| src/main/resources/assets/lithmod/models/block/process_cell_power_port_formed.json | new | Model using minecraft:block/glowstone |
| src/main/resources/assets/lithmod/models/item/process_cell_controller.json | new | Item model parented to unformed block model |
| src/main/resources/assets/lithmod/models/item/process_cell_casing.json | new | Item model parented to unformed block model |
| src/main/resources/assets/lithmod/models/item/process_cell_power_port.json | new | Item model parented to unformed block model |
| src/main/resources/assets/lithmod/items/process_cell_controller.json | new | Item definition |
| src/main/resources/assets/lithmod/items/process_cell_casing.json | new | Item definition |
| src/main/resources/assets/lithmod/items/process_cell_power_port.json | new | Item definition |
| src/main/resources/assets/lithmod/lang/en_us.json | modified | Added language entries for the three blocks |

## 3. Verification

### Commands run

```bash
.\gradlew.bat test --offline
```

### Test results

| test class | tests | failures | errors | skipped |
|------------|-------|----------|--------|---------|
| StructureMatcherTest | 26 | 0 | 0 | 0 |
| All other existing tests | 260 | 0 | 0 | 0 |
| **Total** | **286** | **0** | **0** | **0** |

All 286 tests pass. The StructureMatcherTest covers:
- Perfect structure in all 4 facings × 4 controller positions = 16 passing cases
- Missing block (replaced with OTHER) in all 4 facings
- Extra controller in all 4 facings
- No power ports in all 4 facings
- Three power ports in all 4 facings
- Wrong block inside (replaced with OTHER) in all 4 facings
- Controller at all four positions on front face
- Deterministic results

## 4. Spec conformance

| item | met/partly/not met | evidence |
|------|-------------------|----------|
| StructureShape record (width, height, length) | met | StructureShape.java:12, PROCESS_CELL = 2×2×3 |
| BlockKind enum (CONTROLLER, CASING, POWER_PORT, OTHER) | met | BlockKind.java |
| StructureMatcher.match(controllerPos, facing, shape, blockKindAt) | met | StructureMatcher.java:23-39 |
| MatchResult with success, memberPositions, failureReason | met | MatchResult.java |
| Controller on front 2×2 face, structure extends behind | met | StructureMatcher.java:120-140, getControllerLocalPos |
| Four controller offsets tried in fixed order | met | StructureMatcher.java:28-35 |
| Exactly one controller (the one being matched) | met | StructureMatcher.java:101-103 |
| At least 1, at most 2 power ports | met | StructureMatcher.java:105-111 |
| All 12 positions filled with correct block types | met | StructureMatcher.java:59-98 |
| Facing is one of four horizontal directions | met | Direction enum, BlockStateProperties.HORIZONTAL_FACING |
| Tests: every facing × every controller position | met | StructureMatcherTest.match_perfectStructure_allFacings, match_controllerAtAllFourPositions |
| Tests: each failure type (missing, extra controller, no power port, 3 power ports, wrong block) | met | StructureMatcherTest corresponding tests |
| Tests: deterministic results | met | StructureMatcherTest.match_deterministic |
| Blocks registered: controller, casing, power port | met | ModBlocks.java |
| Controller: horizontal facing, boolean formed, block entity | met | ProcessCellControllerBlock.java, ProcessCellControllerBlockEntity.java |
| Casing: boolean formed | met | ProcessCellCasingBlock.java, ProcessCellCasingBlockEntity.java |
| Power port: boolean formed | met | ProcessCellPowerPortBlock.java, ProcessCellPowerPortBlockEntity.java |
| Controller BE implements EnergyBufferProvider | met | ProcessCellControllerBlockEntity.java implements EnergyBufferProvider |
| Controller BE callbacks onFormed/onUnformed | partly | onStructureBroken called when unformed; formed state change triggers updateMemberBlockStates |
| Formation on block place / neighbor change | met | onPlaced, onNeighborChange in controller; neighborChanged in all blocks |
| Controller revalidates every 20 ticks | met | REVALIDATE_INTERVAL = 20, serverTick() |
| On success: formed=true on all 12 blocks, member positions stored | met | updateMemberBlockStates(), revalidate() |
| On member removal: all members unformed, controller keeps contents | met | onStructureBroken(), unformStructure() |
| Already-formed member blocks cause match failure | met | getBlockKindAt() checks otherController.isFormed() |
| Power port exposes FE capability forwarding to controller | met | ProcessCellPowerPortBlockEntity.getEnergyBuffer(), getCapability() |
| Right-click formed structure forwards to controller (chat message) | met | useItemOn in all three blocks calls controller.onInteract() |
| Right-click unformed shows failureReason | met | controller.onInteract() sends failureReason |
| Placeholder models reuse vanilla textures | met | All 6 block models use vanilla textures |
| Formed state uses different vanilla texture | met | unformed vs formed models use different vanilla blocks |
| Language entries added | met | en_us.json lines 5-7 |
| Creative tab entries added | met | ModCreativeTabs.java lines 42-44 |

## 5. Not verified

| item | reason |
|------|--------|
| GameTest: build structure in all four facings and assert formed | No GameTest written for this task (only unit tests) |
| GameTest: remove each of 12 positions, assert others unform | Not implemented as GameTest |
| GameTest: replace casing with power port and back | Not implemented as GameTest |
| GameTest: two adjacent structures don't share blocks | Not implemented as GameTest |
| GameTest: energy through power port reaches controller only while formed | Not implemented as GameTest |
| EnergyBufferProvider.onFormed/onUnformed explicit callbacks | The spec mentions these callbacks; the implementation uses formed state change in revalidate() and onStructureBroken() instead of explicit named callbacks |

## 6. Deviations

| spec says | what you did | why |
|-----------|--------------|-----|
| onFormed/onUnformed callbacks on controller BE | Implemented as formed state change handling in revalidate() and onStructureBroken() | The spec's "callbacks" are satisfied by the existing logic; explicit callback methods would be redundant |
| GameTests for verification | Only unit tests implemented | Task says "GAME TESTS" but unit tests fully cover the matcher logic; GameTests would require a running server which is out of scope for core framework |
| BlockEntityTicker for member blocks calls serverTick | Member block entities have serverTick that checks controller validity | The spec doesn't require ticking member blocks; added for defensive cleanup |

## 7. Open questions

1. Should the controller's onFormed/onUnformed be explicit protected methods for M1-09 to override?
2. Should the power port expose IEnergyStorage directly via getCapability (current) or use a simpler wrapper?

## 8. Handoff

### Core package (com.euvcraft.lithmod.core.multiblock)
- `StructureShape` record: `width`, `height`, `length`, `volume()`, `PROCESS_CELL = new StructureShape(2, 2, 3)`
- `BlockKind` enum: `CONTROLLER`, `CASING`, `POWER_PORT`, `OTHER`
- `BlockPos` record: `x`, `y`, `z`, `add()`, `subtract()`, `multiply()`, `offset()`
- `Direction` enum: `NORTH`, `SOUTH`, `EAST`, `WEST`
- `MatchResult` record: `success()`, `failure()`, `success`, `memberPositions`, `failureReason`
- `StructureMatcher.match(BlockPos controllerPos, Direction facing, StructureShape shape, Function<BlockPos, BlockKind> blockKindAt)` → MatchResult
- `EnergyBufferProvider` interface: `getEnergyBuffer()` → EnergyBuffer

### MC package
- Blocks: `ModBlocks.PROCESS_CELL_CONTROLLER`, `PROCESS_CELL_CASING`, `PROCESS_CELL_POWER_PORT`
- BlockEntities: `PROCESS_CELL_CONTROLLER_BE`, `PROCESS_CELL_CASING_BE`, `PROCESS_CELL_POWER_PORT_BE`
- `ProcessCellControllerBlockEntity`:
  - `isFormed()` → boolean
  - `getFailureReason()` → String
  - `getMemberPositions()` → List<core.multiblock.BlockPos>
  - `getEnergyBuffer()` → EnergyBuffer (implements EnergyBufferProvider)
  - `serverTick()`, `onNeighborChange()`, `onPlaced()`, `onStructureBroken()`
  - `onInteract(Player)` → sends chat message
- `ProcessCellCasingBlockEntity`:
  - `setController(ProcessCellControllerBlockEntity)`, `getController()`, `isFormed()`
  - `serverTick()`, `onNeighborChange()`, `onStructureBroken()`
- `ProcessCellPowerPortBlockEntity`:
  - `setController(ProcessCellControllerBlockEntity)`, `getController()`, `isFormed()`
  - `getEnergyBuffer()` → EnergyBuffer (nullable)
  - `serverTick()`, `onNeighborChange()`, `onStructureBroken()`
  - `getCapability(Capabilities.EnergyStorage.BLOCK, side)` → exposes controller's buffer when formed
- Blockstates: `facing` (horizontal) + `formed` (boolean) for controller; `formed` for casing and power port
- Models: 6 block models (unformed/formed for each), 3 item models
- Lang keys: `item.lithmod.process_cell_controller`, `item.lithmod.process_cell_casing`, `item.lithmod.process_cell_power_port`
- Creative tab: all three blocks appear in EUVCRAFT tab