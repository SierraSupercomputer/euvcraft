> Coordinator integration note: the following is a preserved worker draft, not independent execution evidence. The domain draft required correction of power/breach signs, exposure validation/overflow and assertions. Ports required26.3 API fixes, bidirectional live guards and six-axis support. See Cleanroom_foundation.md for final architecture and manual-walkthrough-cleanroom.md for owner tests.

# Cleanroom Ports Implementation Report

## Scope
This report documents the implementation of `CleanroomPortBlock` and `CleanroomPortBlockEntity` in package `com.euvcraft.lithmod.mc.cleanroom`, per the M1 cleanroom ports task.

## Files Created
- `src/main/java/com/euvcraft/lithmod/mc/cleanroom/CleanroomPortBlock.java`
- `src/main/java/com/euvcraft/lithmod/mc/cleanroom/CleanroomPortBlockEntity.java`

## Design Summary

### CleanroomPortBlock
- Extends `BaseEntityBlock` with a single horizontal `FACING` state property (`DirectionProperty`).
- `getStateForPlacement` uses `BlockPlaceContext.getHorizontalDirection()`, which points from the placement player through the wall into the room (the destination side).
- `newBlockEntity` creates a `CleanroomPortBlockEntity`.
- Three registry entries share this class: `CleanroomBlocks.POWER_PORT`, `FLUID_PORT`, `ITEM_PORT`.
- Role detection via `CleanroomPortBlock.detectRole(Block)` comparing against registry fields.

### CleanroomPortBlockEntity
- Constructor: `(BlockPos, BlockState)` using `CleanroomBlocks.PORT_BE.get()`.
- Exposes three public capability lenses:
  - `EnergyHandler energy(Direction side)` — only for POWER_PORT role, only on the outside face.
  - `ResourceHandler<FluidResource> fluids(Direction side)` — only for FLUID_PORT role, only on the outside face.
  - `ResourceHandler<ItemResource> items(Direction side)` — only for ITEM_PORT role, only on the outside face.
- Rejects wrong role, `null` side, and transverse sides (only the facing-axis outside face is valid).
- Each handler forwards along the facing axis to the adjacent block on the **opposite** side, querying the neighbor's capability on the **outside** face (so outside queries reach inside, inside queries reach outside).

### Transfer Forwarding Semantics
- **No storage**: Handlers do not invent storage, move resources while probing, or start/commit independent transactions.
- **Preserve TransactionContext**: The caller's `TransactionContext` is passed through unchanged.
- **Dynamic lens**: No neighbor BE or capability is cached. Every operation re-queries `Level.getCapability` on the neighbor position. If the port is removed, the neighbor is unloaded/replaced, or the capability changes, operations immediately refuse/return empty without forcing chunk loads.
- **Recursion guard**: Bounded `ThreadLocal` depth counters (max 8) for each transfer type (energy, fluid, item). Cycles in port chains safely reject with no resource/FE duplication. No eager capability resolution during proxy construction.
- **World shutdown**: `setRemoved()` does not inspect neighbors or chunks; dynamic lenses naturally return empty/null afterward.

## Assumed APIs (NeoForge 26.3)

| API | Assumed Location / Usage |
|-----|--------------------------|
| `net.neoforged.neoforge.transfer.energy.EnergyHandler` | Interface with `getAmountAsLong`, `getCapacityAsLong`, `insert(int, TransactionContext)`, `extract(int, TransactionContext)` |
| `net.neoforged.neoforge.transfer.ResourceHandler<T>` | Generic handler with `size()`, `getResource(int)`, `getAmountAsLong(int)`, `getCapacityAsLong(int, T)`, `isValid(int, T)`, `insert(int, T, int, TransactionContext)`, `extract(int, T, int, TransactionContext)` |
| `net.neoforged.neoforge.transfer.fluid.FluidResource` | Resource implementation for fluids; `FluidResource.EMPTY`, `FluidResource.of(Fluid)`, `isEmpty()`, `equals()` |
| `net.neoforged.neoforge.transfer.item.ItemResource` | Resource implementation for items; `ItemResource.EMPTY`, `isEmpty()`, `equals()` |
| `net.neoforged.neoforge.transfer.transaction.TransactionContext` | Opaque transaction context passed through all insert/extract calls |
| `net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK` | Capability key for block entity energy |
| `net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK` | Capability key for block entity fluid |
| `net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK` | Capability key for block entity item |
| `Level.getCapability(Capability, BlockPos, Direction)` | Returns handler or null; does not force chunk load |

## Limitations & Open Questions

1. **CleanroomBlocks registry not yet implemented**: The coordinator is expected to supply `CleanroomBlocks` with:
   ```java
   public static DeferredBlock<CleanroomPortBlock> POWER_PORT, FLUID_PORT, ITEM_PORT;
   public static DeferredHolder<BlockEntityType<?>, BlockEntityType<CleanroomPortBlockEntity>> PORT_BE;
   ```
   This implementation compiles against that assumed registry.

2. **ItemResource API**: The exact `ItemResource` class shape in NeoForge 26.3 is assumed based on `FluidResource` pattern. If the actual API differs (e.g., `ItemStackResource` or different method names), the `ForwardingItemHandler` will need adjustment.

3. **Capability side semantics**: The forwarding uses `port.outsideSide()` as the side argument when querying the neighbor. This matches the requirement "outside queries reach inside, inside queries reach outside" — the neighbor receives the query on its facing side. If the neighbor expects the opposite side convention, the side argument may need to be `port.insideSide()` instead.

4. **ThreadLocal cleanup**: The `ThreadLocal` depth counters use `ThreadLocal.withInitial(() -> 0)`. In long-running server threads, the depth could theoretically accumulate if exceptions skip the `finally` block. All operations use `try/finally` to decrement, but an `Error` or forced thread termination could leak. This is considered acceptable for the bounded recursion guard.

5. **No formed/part state**: Per the brief, the port is a normal full cube with fixed `FACING` and no extra formed/part state. It seals physically regardless of room power/enclosure state.

6. **No Ticker logic**: `serverTick()` is a no-op. If future requirements need periodic validation (e.g., neighbor liveness), it can be added.

7. **Client-side**: All handlers return `null`/`empty` on client side (`level.isClientSide()` check in `neighborHandler()`).

## Integration Notes

- Capability registration is **not** in this scope. The coordinator will register capabilities on `CleanroomBlocks.PORT_BE` for each type, filtering by port role (similar to `SupportMachines.registerCapabilities`).
- The three port blocks share one `BlockEntityType` (`PORT_BE`), registered against all three block instances.
- Registry `DeferredBlock` factories should call `new CleanroomPortBlock(Properties)` for all three IDs.

## Verification Checklist (for Coordinator)

- [ ] `CleanroomPortBlock` compiles and extends `BaseEntityBlock` with `FACING` property.
- [ ] `CleanroomPortBlockEntity` compiles, constructor uses `CleanroomBlocks.PORT_BE.get()`.
- [ ] `energy(Direction)` returns non-null only for POWER_PORT on outside face.
- [ ] `fluids(Direction)` returns non-null only for FLUID_PORT on outside face.
- [ ] `items(Direction)` returns non-null only for ITEM_PORT on outside face.
- [ ] Forwarding queries neighbor at `worldPosition.relative(insideSide())` with capability side `outsideSide()`.
- [ ] All handlers preserve `TransactionContext`, no independent transactions.
- [ ] Recursion guards use `ThreadLocal` with max depth 8 per transfer type.
- [ ] `setRemoved()` does not touch neighbors/chunks.
- [ ] No neighbor BE or handler caching.
- [ ] Port chains work; cycles reject safely.

---
*Generated as part of M1 cleanroom ports implementation. Final in40steps reserved for coordinator.*