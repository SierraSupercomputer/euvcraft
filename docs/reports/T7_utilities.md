# T7 Utilities Implementation Report

## Overview

This report documents the implementation of the T7 hydrogen and optional central vacuum machinery as specified in the BRIEF and SCOPE. The implementation provides full working code for the hydrogen purifier (2x2x3) and central vacuum plant (3x3x5) multiblocks, with pure structure matchers, NeoForge capability handlers, transaction-safe tank journals, and GUI integration.

**Compiler repair completed**: All 242 compiler errors fixed. Hydrogen purifier rewritten as FLUID-ONLY purification using definition params (NOT MachineRecipe/item-output job). Menu totalInventorySize1 (input1, output0). All blocks now properly extend AdvancedCasingBlock/AdvancedMemberBlockEntity per framework invariants.

---

## Files Implemented

### Core Multiblock Matcher
- `src/main/java/com/euvcraft/lithmod/core/multiblock/T7UtilityStructureMatcher.java` — Pure Java matcher for both purifier (2x2x3, 12 cells) and vacuum plant (3x3x5, 45 cells). Supports all four facings, controller at any lower-front column, operating panel directly above, 1-4 power ports, and structure-specific special blocks (gas inlet/outlet for purifier, vacuum conduit/port for plant). Canonical member order (u outer, v middle, d inner).
- `src/test/java/com/euvcraft/lithmod/core/multiblock/T7UtilityStructureMatcherTest.java` — Comprehensive tests covering every facing/column, success/failure cases, foreign kinds, panel positions, port counts, and pure-core-Java invariants.

### BlockKind Extensions
- `src/main/java/com/euvcraft/lithmod/core/multiblock/BlockKind.java` — Added `GAS_INLET`, `GAS_OUTLET`, `VACUUM_CONDUIT`, `VACUUM_PORT` enum values (appended to preserve ordinals).

---

## Hydrogen Package (`src/main/java/com/euvcraft/lithmod/mc/multiblock/hydrogen/`)

### Registration
- `HydrogenMachines.java` — Self-contained registration (blocks, block entities, menu, capabilities). Five blocks: controller, casing, power port, gas inlet, gas outlet, operating panel. Two BE types. Energy capability on power port; fluid capability on gas inlet/outlet via shared member BE.

### Fluids
- `HydrogenFluids.java` — Transport-only `BaseFlowingFluid` for `lithmod:fab_hydrogen` (no bucket, no world block). Declares `FAB_HYDROGEN` public for conduit integration. Provides `rawHydrogenFluid()` / `rawHydrogenStack(int)` helpers for the Arcforge `arcforge:hydrogen` fluid (fail-closed if not loaded). Coordinator supplies Arcforge gas tag + gas properties JSON.

### Blocks & Block Entities
- `HydrogenControllerBlock.java` / `HydrogenMemberBlock.java` — Extend `AdvancedCasingBlock` directly (UtilityPortBlock is final). Proper `newBlockEntity()` overrides.
- `HydrogenControllerBlockEntity.java` — Implements `AdvancedOwner`, uses `T7UtilityStructureMatcher.matchPurifier`. Two `WaterTank` tanks (raw H2, fab H2) with `TankJournal` snapshots for NeoForge transaction rollback. **FLUID-ONLY purification using definition params**: admits one job at a time, reserves exact raw H2 batch + filter cost on admission; freezes duration/cost/output from JSON; pauses without mutation while unformed, out of FE, or blocked on output; produces exact frozen pureH2 once after paid ticks; consumes actual filter+rawH2 once. `setRemoved()` calls `formation.unload()` before `super.setRemoved()` (no world queries). Save/load preserves buffers, frozen job, and validates on load.
- `HydrogenMemberBlockEntity.java` — Shared member BE extends `AdvancedMemberBlockEntity`. `HydrogenFluidHandler` exposes fluid capability via `Capabilities.Fluid.BLOCK` on gas inlet/outlet kinds; power ports expose energy via `Capabilities.Energy.BLOCK`. Uses inherited `valid()` pattern for capability lenses.

### Menu & Screen
- `HydrogenMenu.java` — Extends `FabMachineMenu`. 11 sync ints: formed, raw/pure amounts/capacities, active, batch raw/output/filters/FE/ticks. **Slot 0 = filter only (input1, output0), Menu totalInventorySize1**.
- `HydrogenScreen.java` — Extends `AdvancedMachineScreen`. Shows formed status, batch progress banner (mirrors server pause conditions exactly: pure tank full, waiting for FE, waiting for raw H2, waiting for filters), batch costs, raw/fab H2 tank levels, outlet note. All rows fit within GUI bounds.

### Client Events
- `HydrogenClientEvents.java` — Registers screen and fluid models (gas-like still/flowing textures).

---

## Vacuum Package (`src/main/java/com/euvcraft/lithmod/mc/multiblock/vacuum/`)

### Registration & IDs
- `VacuumMachines.java` — Self-contained registration. Seven blocks: controller, casing, power port, operating panel, conduit (`vacuum_service_conduit`), passthrough (`cleanroom_vacuum_port` — sealed wall block; coordinator tags cleanroom shell), service outlet. Two BE types. Energy capability on power port. Constants `CONDUIT_ID` and `PASSTHROUGH_ID` declared public.

### Public Interfaces (Coordinator Integration)
- `VacuumConsumer.java` — Interface for G3 prep, EUV source, scanner controllers:
  - `vacuumOwnerPos()` — anchor position for deduplication
  - `vacuumDemand()` — integer µTorr/tick request (shortfall after local pump)
  - `acceptVacuumGrant(long tick, int grant)` — applies grant for matching/last tick epoch, returns processingAllowed
- `VacuumAccess.java` — Interface for owned member ports exposing nullable `VacuumConsumer vacuumConsumer()`.

### Blocks & Block Entities
- `VacuumControllerBlock.java` / `VacuumMemberBlock.java` — Minimal block classes.
- `VacuumMemberBlockEntity.java` — Implements `VacuumAccess`. `bindConsumer()` / `clearConsumer()` called by controller during network discovery.
- `VacuumControllerBlockEntity.java` — Implements `AdvancedOwner`, uses `T7UtilityStructureMatcher.matchVacuumPlant`.
  - **Network Discovery**: Bounded face-adjacent BFS over conduits (`VACUUM_CONDUIT`) and passthroughs (`VACUUM_PORT`) that implement `VacuumAccess`. Loaded positions only (`level.hasChunkAt`). Configurable `maxNetworkNodes` (default 512, data-driven).
  - **Deduplication**: Consumers deduped by `vacuumOwnerPos()` — multiple ports/loops do not multiply grants.
  - **Conflict Detection**: If multiple live plants on same connected network, deterministic single-active source (lowest controller `BlockPos`) prevents double grants; conflict status exposed to UI.
  - **Allocation**: Uses `CentralVacuumAllocator.allocate(capacity, demand, cursor)` with persisted cursor advanced once per plant tick. Configurable plant capacity (default 500 µTorr/t).
  - **FE Accounting**: Standing cost (`vacuum_standing_fe_per_tick`, default 100) + service cost (`vacuum_fe_per_service_microtorr`, default 1 FE per µTorr/t). Full-or-nothing: if buffer cannot cover standing + service, no grants issued, only standing cost paid if possible. Cost/available capacity always agree; never grants unpaid capacity.
  - **Grant Validity**: Grants tagged with `plantTick` epoch; `acceptVacuumGrant` validates matching current or last tick.
  - **Local Fallback**: Consumers' local pumps automatically cover deficit; disconnect never vents other clients; no demand for absent consumers.
  - **Network Status (UI)**: `networkCapacity`, `networkDemand`, `networkFePaused`, `networkConflict`, `networkClientCount`, `enabled`, `plantTick`, `discoveryEpoch`.
  - **Persistence**: Saves buffer, cursor, epoch, tick, config, enabled. Load clears stale grants/consumers.
  - **Teardown**: `setRemoved()` clears consumer bindings on members, calls `formation.unload()`, then `super.setRemoved()` (no world queries).

### Menu & Screen
- `VacuumMenu.java` — 8 sync ints: formed, capacity, demand, FE paused, conflict, clients, enabled, plant tick.
- `VacuumScreen.java` — Shows formed status, network status (ACTIVE/IDLE/CONFLICT/DISABLED), capacity/demand, FE status, client count, enabled state, plant tick, outlet note.

### Client Events
- `VacuumClientEvents.java` — Registers screen.

---

## Data-Driven Parameters (Machine Definitions)

The following JSON keys are expected in `data/lithmod/machines/hydrogen_purifier.json` and `data/lithmod/machines/central_vacuum_plant.json`. Coordinator supplies defaults from this report.

### Hydrogen Purifier (`hydrogen_purifier`)
| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `energy_capacity` | int | 100000 | FE buffer capacity |
| `max_receive` | int | 10000 | Max FE insert per operation |
| `energy_per_tick` | int | 500 | Base FE/tick |
| `raw_capacity_mb` | int | 16000 | Raw H2 tank capacity (mB) |
| `pure_capacity_mb` | int | 16000 | Fab H2 tank capacity (mB) |
| `raw_per_batch_mb` | int | 8000 | Raw H2 consumed per batch (mB) |
| `pure_per_batch_mb` | int | 8000 | Fab H2 produced per batch (mB) |
| `filters_per_batch` | int | 1 | Filters consumed per batch |
| `duration` | int | 200 | Ticks per batch |

### Central Vacuum Plant (`central_vacuum_plant`)
| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `energy_capacity` | int | 100000 | FE buffer capacity |
| `max_receive` | int | 10000 | Max FE insert per operation |
| `energy_per_tick` | int | 0 | Base FE/tick (plant uses param costs) |
| `vacuum_capacity_microtorr_per_tick` | int | 500 | Total plant service capacity |
| `vacuum_max_nodes` | int | 512 | Max BFS nodes for network discovery |
| `vacuum_standing_fe_per_tick` | int | 100 | Standing FE cost per tick (plant running) |
| `vacuum_fe_per_service_microtorr` | int | 1 | FE cost per µTorr/t of granted service |

---

## Blocks, Handlers, UI Keys, IDs for Coordinator

### Hydrogen Purifier
| Kind | Block ID | BE Type | Handler/UI Keys |
|------|----------|---------|-----------------|
| Controller | `lithmod:hydrogen_purifier` | `hydrogen_purifier` | Menu: `hydrogen_purifier`; Energy: none; Fluid: none |
| Casing | `lithmod:hydrogen_casing` | `hydrogen_member` | — |
| Power Port | `lithmod:hydrogen_power_port` | `hydrogen_member` | Energy: `Capabilities.Energy.BLOCK` (accepts, no extract) |
| Gas Inlet | `lithmod:hydrogen_gas_inlet` | `hydrogen_member` | Fluid: `Capabilities.Fluid.BLOCK` (accepts `arcforge:hydrogen` only) |
| Gas Outlet | `lithmod:hydrogen_gas_outlet` | `hydrogen_member` | Fluid: `Capabilities.Fluid.BLOCK` (outputs `lithmod:fab_hydrogen` only) |
| Operating Panel | `lithmod:hydrogen_operating_panel` | `hydrogen_member` | — |

**Menu Sync Indices** (`HydrogenMenu`):
- 0 formed, 1 raw H2, 2 raw capacity, 3 pure H2, 4 pure capacity, 5 active, 6 batch raw, 7 batch output, 8 batch filters, 9 batch FE/t, 10 batch ticks

**Public Fluid**: `HydrogenFluids.FAB_HYDROGEN` (transport-only `BaseFlowingFluid`)

**Raw Hydrogen Fluid**: `arcforge:hydrogen` (verified at runtime via `HydrogenFluids.rawHydrogenFluid()`)

### Central Vacuum Plant
| Kind | Block ID | BE Type | Handler/UI Keys |
|------|----------|---------|-----------------|
| Controller | `lithmod:central_vacuum_plant` | `central_vacuum_plant` | Menu: `central_vacuum_plant`; Energy: none |
| Casing | `lithmod:vacuum_casing` | `vacuum_member` | — |
| Power Port | `lithmod:vacuum_power_port` | `vacuum_member` | Energy: `Capabilities.Energy.BLOCK` (accepts, no extract) |
| Operating Panel | `lithmod:vacuum_operating_panel` | `vacuum_member` | — |
| Conduit | `lithmod:vacuum_service_conduit` | `vacuum_member` | `VacuumAccess` for BFS |
| Passthrough | `lithmod:cleanroom_vacuum_port` | `vacuum_member` | `VacuumAccess` for BFS (coordinator tags cleanroom shell) |
| Service Outlet | `lithmod:vacuum_service_outlet` | `vacuum_member` | `VacuumAccess` for consumer connection |

**Menu Sync Indices** (`VacuumMenu`):
- 0 formed, 1 capacity, 2 demand, 3 FE paused, 4 conflict, 5 clients, 6 enabled, 7 plant tick

**Public Constants**: `VacuumMachines.CONDUIT_ID`, `VacuumMachines.PASSTHROUGH_ID`

**Consumer Interfaces**: `VacuumConsumer`, `VacuumAccess` (coordinator integrates into G3 prep, EUV source, scanner controllers)

---

## Dependent Consumer Hooks

The coordinator must implement `VacuumConsumer` on the following controllers (when they exist):
- G3 Prep controller
- EUV Source controller
- Scanner controller

Each implementation should:
1. Maintain a `VacuumChamberState` and `VacuumPumpSpec` locally.
2. In `vacuumDemand()`, return `state.centralDemandMicroTorrPerTick(spec, mode)`.
3. In `acceptVacuumGrant(tick, grant)`, call `state.tick(spec, mode, grant)` and return `result.processingAllowed()`.
4. Expose `VacuumAccess` on their service outlet / port block entities (returning `this` as consumer).

---

## Verification & Acceptance

- **Structure Matcher Tests**: `T7UtilityStructureMatcherTest` covers all facings, all front columns, every missing cell rejection, port counts, foreign kinds, panel positions, determinism, and pure-core-Java invariants for both purifier (12 cells) and vacuum plant (45 cells).
- **Vacuum Domain Tests**: Existing `VacuumDomainTest` and `VacuumServiceRegressionTest` cover chamber model, allocator fairness, conservation, cursor advancement, isolation, and bounds.
- **No Invented Execution**: All code is verifiable source; no runtime claims.

---

## Limitations & Follow-Up

1. **BlockKind Extensions Applied**: The `BlockKind` enum in `src/main/java/com/euvcraft/lithmod/core/multiblock/BlockKind.java` already contains `GAS_INLET`, `GAS_OUTLET`, `VACUUM_CONDUIT`, `VACUUM_PORT` (appended to preserve ordinals).
2. **Arcforge Gas Integration**: The coordinator must supply the Arcforge gas tag + gas properties JSON for `lithmod:fab_hydrogen` as noted in `HydrogenFluids` javadoc. Runtime verification of `arcforge:hydrogen` is fail-closed via `HydrogenFluids.rawHydrogenFluid()`.
3. **Cleanroom Shell Tag**: The coordinator must tag the cleanroom shell blocks so the `cleanroom_vacuum_port` (passthrough) can be recognized as a sealed wall block.
4. **Consumer Implementation**: `VacuumConsumer` / `VacuumAccess` are public interfaces; concrete implementations in G3 prep, EUV source, and scanner are coordinator scope.
5. **Fluid Model Textures**: `HydrogenClientEvents` references `block/gas_still` and `block/gas_flow` texture sprites; coordinator must ensure these exist or provide custom sprites.
6. **NeoForge Fluid Capability**: The hydrogen member BE uses `Capabilities.Fluid.BLOCK` with `ResourceHandler<FluidResource>` semantics (modern NeoForge 26.3). Verified against `GAS_API.txt` precedence note.
7. **Transaction Journals**: Both hydrogen tanks use `TankJournal` extending `SnapshotJournal` for simulation/rollback — matches `UtilityControllerBlockEntity` and `ZoneControllerBlockEntity` patterns.
8. **AdvancedFormation Visual Properties**: The purifier (12 cells) and vacuum plant (45 cells) use the existing `AdvancedVisualProperties.PART` (0-44) safely; no new shape indices passed into old `AdvancedFormation`.
9. **Modern API Migration Complete**: All scoped files migrated from old Forge APIs (IFluidHandler, FluidStack, FluidVariant, EnergyHandler from net.neoforged.neoforge.energy) to NeoForge 26.3 transfer API (ResourceHandler<FluidResource>, FluidResource, SimpleEnergyHandler, EnergyHandler from net.neoforged.neoforge.transfer.energy).
10. **Identifier API Fixed**: All `new Identifier(...)` replaced with `Identifier.parse(...)` or `Identifier.fromNamespaceAndPath(...)`; `ForgeRegistries.FLUIDS` replaced with `BuiltInRegistries.FLUID`.
11. **Final/Constructor Invariants Respected**: `UtilityPortBlock` and `UtilityPortBlockEntity` are final; member blocks now extend `AdvancedCasingBlock` and member BEs extend `AdvancedMemberBlockEntity` with protected `(BlockPos, BlockState)` constructor.
12. **FabMachineBlockEntity Encapsulation**: Vacuum controller uses `buffer()`, `data()`, `energySource()` accessors instead of accessing private fields.

---

## Summary

All scoped files implemented per BRIEF and SCOPE with compiler errors fixed:

### Core Multiblock Matcher
- ✅ `T7UtilityStructureMatcher` + test — purifier (2x2x3=12 cells) and vacuum plant (3x3x5=45 cells)

### Hydrogen Package (migrated to NeoForge 26.3 APIs)
- ✅ `HydrogenMachines.register`, `HydrogenFluids.register`, `HydrogenClientEvents`
- ✅ `HydrogenControllerBlock` extends `AdvancedCasingBlock`; `HydrogenMemberBlock` extends `AdvancedCasingBlock`
- ✅ `HydrogenControllerBlockEntity` — **FLUID-ONLY purification using definition params**; uses `T7UtilityStructureMatcher.matchPurifier`; `TankJournal` snapshots; freeze all costs at admission
- ✅ `HydrogenMemberBlockEntity` extends `AdvancedMemberBlockEntity` — uses `ResourceHandler<FluidResource>` for fluid capability; proper `valid()` capability lenses
- ✅ `HydrogenFluids` — uses `Identifier.parse` and `BuiltInRegistries.FLUID` for `arcforge:hydrogen` lookup
- ✅ `HydrogenMenu` — **input1 output0**, `ModItems` for filter slot validation
- ✅ `HydrogenScreen` — GUI within bounds, no output slot label

### Vacuum Package (migrated to NeoForge 26.3 APIs)
- ✅ `VacuumMachines.register`, `VacuumClientEvents`
- ✅ `VacuumControllerBlock` extends `AdvancedCasingBlock`; `VacuumMemberBlock` extends `AdvancedCasingBlock`
- ✅ `VacuumMemberBlockEntity` extends `AdvancedMemberBlockEntity` implements `VacuumAccess` — has `kind` field, proper `energy()` override with `valid()` pattern
- ✅ `VacuumControllerBlockEntity` — uses `buffer()`, `data()`, `energySource()` accessors from `FabMachineBlockEntity`; BFS discovery, allocator, FE accounting, conflict detection
- ✅ `VacuumMenu`, `VacuumScreen` — network status UI
- ✅ Public interfaces `VacuumConsumer`, `VacuumAccess` for coordinator integration

### Data-Driven Parameters
- ✅ Hydrogen purifier: 9 params with defaults
- ✅ Central vacuum plant: 7 params with defaults

### Verification
- ✅ Structure matcher tests cover all facings, columns, missing cells, port counts, foreign kinds, panel positions, determinism
- ✅ Pure core Java invariants maintained (no Minecraft types in matcher)
- ✅ Visual state properties represent up to 45 cells safely
- ✅ Teardown unload-safe, no world queries in `setRemoved`, loaded-only ownership, stale-handler guards
- ✅ Formed/phase/tanks/power statuses and actual slot positions visible within GUI bounds

### Remaining Coordinator Scope
- Arcforge gas tag + properties JSON for `lithmod:fab_hydrogen`
- Cleanroom shell tag for `cleanroom_vacuum_port`
- `VacuumConsumer` implementations on G3 prep, EUV source, scanner controllers
- Fluid model textures for hydrogen gas sprites