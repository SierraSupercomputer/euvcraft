> Historical branch report. Current integration status, corrected APIs, and fresh verification evidence are in [the consolidation report](CONSOLIDATION_2026-10-16.md).

# M1-02c: Machine contents drop when the block is removed

Date: 2026-10-16
Branch: `m1-02c-contents-drop`
Base: `b991c48` (master, with M1-02b and M1-03d already merged)
NeoForge `26.3.0.69-beta`, Minecraft `26.3`, Java toolchain 25

## 1. Starting state

```
=== git worktree list ===
C:/Users/ethan/OneDrive/Documents/Minecraft/euvcraftmk4nemo                    b991c48 [master]
C:/Users/ethan/.local/share/opencode/worktree/fef470/m1-03d-multiblock-blocks  b991c48 [m1-03d-multiblock-blocks]
C:/Users/ethan/AppData/Local/Temp/opencode/m1-02c-contents-drop                b991c48 [m1-02c-contents-drop]

=== git branch --show-current ===   (in the new worktree)
m1-02c-contents-drop

=== git status --short ===
(empty = clean)

=== HEAD ===
b991c48 M1-03d: git proof and concerns for review
```

`master` already had both merges, so nothing was merged here:

```
158c820 Merge M1-02b: machine framework
b5fab34 M1-03d report: the multiblock blocks in the running game
4f37944 M1-03d: Process Cell multiblock blocks in the running game
```

## 2. The premise is half wrong, and the half that is wrong is mine

The task opens with *"26.3 removed `BlockBehaviour.onRemove`, so
`FabMachineBlockEntity.dropContents()` is never called"*. Taking the first clause on trust
would have produced another wrong report, so both clauses were checked against the patched
jar and the vanilla sources.

**What is true.** Neither `onRemove` nor `Containers.dropContentsOnDestroy` exists in
`minecraft-patched-26.3.0.69-beta.jar`:

```
=== BlockBehaviour: affectNeighborsAfterRemoval + neighbours ===
    protected void onPlace(BlockState, Level, BlockPos, BlockState, boolean);
    protected void affectNeighborsAfterRemoval(BlockState, ServerLevel, BlockPos, boolean);
    protected java.util.List<ItemStack> getDrops(BlockState, LootParams$Builder);
    protected void spawnAfterBreak(BlockState, ServerLevel, BlockPos, ItemStack, boolean);
=== Block: affectNeighborsAfterRemoval / spawnAfterBreak / playerWillDestroy ===
    (no onRemove)
```

`onRemove` was **renamed** to `affectNeighborsAfterRemoval`, and
`Containers.dropContentsOnDestroy` was deleted. `ChestBlock` in 26.3 has no `onRemove`
override at all.

**What is false.** There is a replacement hook, and it needs no block override. The real
defect was that M1-02b never wired `dropContents()` to anything: when an `onRemove`
override failed to compile, I deleted the call instead of finding the replacement, and
shipped a no-op `spawnAfterBreak` override in its place. The M1-02b report then stated
"26.3 removed `BlockBehaviour.onRemove`, so there is no block-subclass hook left" as a fact
about the engine. It was a fact about my own mistake.

There is a second, quieter trap in the same area, worth recording because it is how the
M1-02b error was made. The vanilla **source** jar in the Gradle cache still contains the
old code:

```
$ Select-String vanilla_sources/net/minecraft/world/level/block/ChestBlock.java -Pattern "dropContentsOnDestroy"
  line 241:    Containers.dropContentsOnDestroy(state, newState, level, pos);
$ Select-String vanilla_sources/net/minecraft/world/level/block/state/BlockBehaviour.java -Pattern "void onRemove"
  line 193:  protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
```

That jar is a NeoForm intermediate (`sourcesAndCompiledWithNeoForge_*.jar`) holding the
**unpatched** vanilla sources. NeoForge's patch is applied afterwards when moddev builds
`minecraft-patched-*.jar`. So reading the cached sources answers a different question than
the one the code actually runs against. Everything below is read from the patched jar.

## 3. Task 1 — the hook, from the patched jar

### 3.1 The base implementation

`net.minecraft.world.level.block.entity.BlockEntity`:

```java
public void preRemoveSideEffects(BlockPos pos, BlockState state) {
   if (this instanceof Container container && this.level != null) {
      Containers.dropContents(this.level, pos, container);
   }
}
```

`net.minecraft.world.Containers` (patched 26.3) — `dropContentsOnDestroy` is gone, these
remain:

```
public static void dropContents(Level, BlockPos, Container);
public static void dropContents(Level, Entity, Container);
public static void dropContents(Level, BlockPos, NonNullList<ItemStack>);
public static void dropItemStack(Level, double, double, double, ItemStack);
public static void updateNeighboursAfterDestroy(BlockState, Level, BlockPos);
```

### 3.2 Who calls it

Scanning every class in the patched jar for the `preRemoveSideEffects` constant gives ten
hits: `BlockEntity` itself plus the container block entities that override it
(`AbstractFurnaceBlockEntity`, `CampfireBlockEntity`, `CreakingHeartBlockEntity`,
`JukeboxBlockEntity`, `LecternBlockEntity`, `SculkShriekerBlockEntity`,
`ShulkerBoxBlockEntity`, `PistonMovingBlockEntity`) and exactly one caller:
**`LevelChunk`**.

Decompiled from the patched jar, `LevelChunk.setBlockState(BlockPos, BlockState, int)`:

```java
boolean blockChanged = !oldState.is(newBlock);
boolean movedByPiston = (flags & 64) != 0;
boolean sideEffects = (flags & 256) == 0;
if (blockChanged && oldState.hasBlockEntity() && !state.shouldChangedStateKeepBlockEntity(oldState)) {
   if (!this.level.isClientSide() && sideEffects) {
      BlockEntity blockEntity = this.level.getBlockEntity(pos);
      if (blockEntity != null) {
         blockEntity.preRemoveSideEffects(pos, oldState);   // <-- the drop
      }
   }
   this.removeBlockEntity(pos);
}
```

`Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS` is the flag at `256`, confirmed against the
compiled constants:

```
UPDATE_NEIGHBORS = 1;   UPDATE_CLIENTS = 2;   UPDATE_INVISIBLE = 4;   UPDATE_IMMEDIATE = 8;
UPDATE_KNOWN_SHAPE = 16;   UPDATE_SUPPRESS_DROPS = 32;   UPDATE_MOVE_BY_PISTON = 64;
UPDATE_SKIP_SHAPE_UPDATE_ON_WIRE = 128;   UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS = 256;
UPDATE_SKIP_ON_PLACE = 512;   UPDATE_NONE = 260;   UPDATE_ALL = 3;
UPDATE_ALL_IMMEDIATE = 11;   UPDATE_SKIP_ALL_SIDEEFFECTS = 816;   UPDATE_LIMIT = 512;
```

### 3.3 Chests, hoppers and furnaces

| Block | How its contents drop in 26.3 | Evidence |
|---|---|---|
| **Chest** | `ChestBlockEntity` is a `Container` and does **not** override the hook, so the base implementation drops it. `ChestBlock` overrides `affectNeighborsAfterRemoval` only to split a double chest: `Containers.updateNeighboursAfterDestroy(state, level, pos)` | decompiled `ChestBlock.java:184-185` |
| **Hopper** | `HopperBlockEntity extends RandomizableContainerBlockEntity` (a `Container`), no override at all. `HopperBlock.affectNeighborsAfterRemoval` calls `Containers.updateNeighboursAfterDestroy`, which is about neighbour updates, not contents | decompiled `HopperBlock.java:116-117`, `HopperBlockEntity.java` |
| **Furnace** | `AbstractFurnaceBlockEntity.preRemoveSideEffects` calls `super` (which drops the slots) and then awards the smelting recipe's experience | decompiled `AbstractFurnaceBlockEntity.java:388-389` |

The furnace is the shape to copy for the extension point, and `Containers.dropContents`
confirms it only ever moves `ItemStack`s:

```java
private static void dropContents(Level level, double x, double y, double z, Container container) {
   for (int i = 0; i < container.getContainerSize(); i++) {
      dropItemStack(level, x, y, z, container.getItem(i));
   }
}
```

The chest loot table drops only the block, not the contents, which is why a loot table and
the `Container` implementation are two separate jobs:

```json
{ "type": "minecraft:block", "pools": [ { "entries": [ { "type": "minecraft:item",
  "name": "minecraft:chest" } ] } ] }
```

## 4. Task 2 — wiring it for `FabMachineBlockEntity`

The change is one word on the class declaration plus the `Container` methods:

```java
public abstract class FabMachineBlockEntity extends BlockEntity implements MenuProvider, Container {
```

**Inventory only, never energy.** `Container` moves `ItemStack`s and nothing else, so the
energy buffer cannot be dropped by this path even by accident. Stored FE is lost when the
block goes away, which is the intended behaviour. The energy class is untouched.

**`MachineContainer` deleted.** It was a `Container` adapter over the block entity's slot
list, which now is itself the `Container`. Keeping both would mean two routes to the same
slots; the menu now takes the block entity directly.

**Extension point.** `dropExtraContents` is the documented hook, on the block entity rather
than the block so it fires for any removal path, including explosions and pistons:

```java
@Override
public void preRemoveSideEffects(BlockPos pos, BlockState state) {
    // Drops this machine's item slots because it is a Container. Never drops energy.
    super.preRemoveSideEffects(pos, state);
    dropExtraContents(this.level, pos);
}

protected void dropExtraContents(@Nullable Level level, BlockPos pos) { ... }

/** Drops one extra container's contents at {@code pos}; the helper for subclasses. */
protected static void dropContainer(@Nullable Level level, BlockPos pos, Container container) {
    if (level == null || level.isClientSide()) {
        return;
    }
    Containers.dropContents(level, pos, container);
}
```

A subclass holding extra storage — the Process Cell controller when it grows an inventory —
overrides `dropExtraContents` and calls `dropContainer` once per extra `Container`. This is
the furnace's shape: `super` first, then the subclass's own work. `machine_extra_inventory_
drops_on_break` covers it so the hook is not documentation alone.

## 5. Task 3 — loot tables and mineable tags

`test_machine` had **no block item at all**, so it could never drop itself. All four blocks
are registered `requiresCorrectToolForDrops()`, so they also needed a tool tier.

| File | Purpose |
|---|---|
| `ModItems.TEST_MACHINE` (new) | the missing block item, via `registerSimpleBlockItem` |
| `data/lithmod/loot_table/blocks/test_machine.json` | drops `lithmod:test_machine` |
| `data/lithmod/loot_table/blocks/process_cell_controller.json` | drops `lithmod:process_cell_controller` |
| `data/lithmod/loot_table/blocks/process_cell_casing.json` | drops `lithmod:process_cell_casing` |
| `data/lithmod/loot_table/blocks/process_cell_power_port.json` | drops `lithmod:process_cell_power_port` |
| `data/minecraft/tags/block/mineable/pickaxe.json` | all four are pickaxe-mined |
| `data/minecraft/tags/block/needs_stone_tool.json` | all four need a stone-tier tool |
| `data/lithmod/tags/block/machine.json` | a namespace-local tag for the four, so a datapack can target them |
| `assets/lithmod/{items,models/item,models/block,blockstates}/test_machine.json` | client assets, required by the existing `ItemAssetResourceTest` |
| `assets/lithmod/lang/en_us.json` | `item.lithmod.test_machine` |

The loot tables drop the block only. Contents come from the `Container` implementation, so
there is no `contents` loot entry and no double drop.

## 6. Task 4 — game tests

`lithmod:machine_drop`, 6 tests on a real server level with real registries and the real
data pack. Breaking goes through `ServerPlayerGameMode.destroyBlock` — the method a player
in survival reaches by mining. `GameTestHelper.destroyBlock` is deliberately **not** used: it
calls `Level.destroyBlock(pos, false, null)` with `dropBlock = false`.

| Test | Proves | Negative control |
|---|---|---|
| `machine_drops_contents_on_break_in_survival` | A FOUP in the input, 3 gold in the output and 5 iron in storage all drop on a survival break; the block item drops; exactly 4 stacks / 10 items; the FOUP comes back carrying its 4 wafers | An empty, charged machine drops only its block (1 stack); no stray item anywhere in a 24-block volume |
| `machine_drops_contents_when_block_replaced` | `setBlock(STONE)` drops the FOUP and 7 iron, and the block entity is gone | Replacing drops neither the old block item nor the new one |
| `machine_contents_do_not_duplicate` | World totals before and after: delta exactly `11 + 4 + 1 + 1 = 17`; per-item counts unchanged | A second identical break adds exactly `11 + 1` again, and iron totals 22 — two sets, not one set doubled |
| `machine_unloading_drops_nothing` | `Level.removeBlockEntity` drops nothing and leaves the block; the removed instance still holds its contents, a save/reload/break round trip returns all 13 iron and the FOUP | `setBlock` with `UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS` drops nothing — the gate is the flag, not a coincidence |
| `machine_extra_inventory_drops_on_break` | The documented extension point drops a second inventory (9 emerald, 2 diamond) alongside the machine's own storage slot | The machine's own storage slot still drops, so a broken hook cannot look like "nothing drops" |
| `process_cell_blocks_drop_themselves` | All three Process Cell blocks drop exactly themselves, one stack each | Vanilla stone drops exactly one of itself, so the method demonstrably sees a 1 and is not hard-wired to zero |

Real output, from the passing run:

```
[M1-02c] break_in_survival: dropped {lithmod:test_machine=1, minecraft:gold_ingot=3, lithmod:foup_mk1=1, minecraft:iron_ingot=5}
[M1-02c] break: the FOUP came back carrying 4 wafers
[M1-02c] break_in_survival: empty machine dropped only {lithmod:test_machine=1}
[M1-02c] replace: dropped {lithmod:foup_mk1=1, minecraft:iron_ingot=7}
[M1-02c] no_duplication: world total 0 -> 17, delta 17 (expected 17)
[M1-02c] unload: removed the block entity, 0 items dropped
[M1-02c] unload: save, reload and break produced {lithmod:test_machine=1, lithmod:foup_mk1=1, minecraft:iron_ingot=13}
[M1-02c] unload: the flagged setBlock dropped 0 items
[M1-02c] extension_point: both inventories dropped {lithmod:test_machine=1, minecraft:diamond=2, minecraft:emerald=9, minecraft:iron_ingot=4}
[M1-02c] process_cell: lithmod:process_cell_controller dropped {lithmod:process_cell_controller=1}
[M1-02c] process_cell: lithmod:process_cell_casing dropped {lithmod:process_cell_casing=1}
[M1-02c] process_cell: lithmod:process_cell_power_port dropped {lithmod:process_cell_power_port=1}
```

## 7. Real output

```
$ .\gradlew.bat test checkCoreImports --offline
Core import check passed: no Minecraft/NeoForge imports found in core package.
BUILD SUCCESSFUL in 42s

$ .\gradlew.bat runGameTestServer --offline
========= 711 GAME TESTS COMPLETE IN 1.422 min ======================
All 711 required tests passed :)
```

### 7.1 Game tests by environment

Read from the `Running test environment '<mod>:<name>' batch N (N tests)` lines.

| Environment | Tests |
|---|---|
| `lithmod:foup_default` | 11 |
| `lithmod:machine` (M1-02b) | 7 |
| `lithmod:multiblock` (M1-03d) | 7 |
| `lithmod:machine_drop` (this task) | 6 |
| `lithmod:yield_default` | 4 |
| `lithmod:chip_default` | 3 |
| `lithmod:fab_random_default` | 2 |
| `lithmod:default` | 1 |
| `minecraft:default` | 1 |
| `encodedlogistics:*` | 218 |
| `arcforge:*` | 451 |
| **Total** | **711** |

`lithmod` total: 41. The non-`lithmod` tests are untouched by this task, so the whole
change is in the `lithmod` column: 35 before (`lithmod` environments plus M1-03d's 7
multiblock tests) to 41 now. 670 + 41 = 711, which is the server's own total.

### 7.2 Unit tests

Read from `build/test-results/test/*.xml`, grouped by `classname` so `@Nested` suites roll
up. **27 classes, 360 tests, 0 failures** — the same 360 as before this task, because the
work was game tests, data and wiring. The `ItemAssetResourceTest` count is unchanged at 3
but its content grew: the new `test_machine` item is now covered by its three assertions.

```
$ .\gradlew.bat test checkCoreImports --offline
Core import check passed: no Minecraft/NeoForge imports found in core package.
BUILD SUCCESSFUL

TOTAL classes=27 tests=360 failures=0
```

### 7.3 Combined

| Suite | Tests | Failures |
|---|---|---|
| Game tests (`runGameTestServer`) | 711 | 0 |
| Unit tests (`test`) | 360 | 0 |
| **Total** | **1071** | **0** |

## 8. Three bugs this task found, and one wrong claim of its own

**A latent menu bug, exposed.** `FabMachineMenu.machineSlotCount` was read from
`blockEntity.slotCount()`, assuming one menu slot per block entity slot. Adding a storage
slot broke that assumption and shift-click routing started treating the player's own
inventory slots as machine slots, so an item "moved" from the hotbar into itself. The count
is now tallied as slots are added, and the mismatch is documented at
`FabMachineMenu.addMachineSlot(int, int, int, boolean)`. `MachineGameTests` had to move from
38 menu slots to 39 as a result.

**`EntityType.ITEM` does not exist in 26.3.** The constants moved to a separate
`EntityTypes` holder; vanilla's own `GameTestHelper` uses `EntityTypes.ITEM`. The first
version of this test looked the type up in `BuiltInRegistries.ENTITY_TYPE` by id, which
compiled and silently returned nothing.

**`GameTestHelper.getEntities` takes a structure-relative position and calls
`absolutePos` on it.** Passing an already-absolute position looks somewhere else entirely.
This cost two runs, because the drop map came back `{}` while the items were plainly there.
A diagnostic distinguished the two cases:

```
[M1-02c] DIAG countItems at BlockPos{x=-5956580, y=6, z=13341519} r=8.0:
  helper=0 byType=4 levelAllEntities=10 levelItemEntities=4
```

`helper=0`, `byType=4`: the drop works, the query was wrong. `countItems` now queries the
level directly.

**A wrong premise in the task, repeated from M1-02b.** Covered in section 2: `onRemove` was
not removed without replacement, and the contents never dropped because nothing called
`dropContents()`. Both the M1-02b claim and the one this task was handed rested on my
earlier mistake, so the report leads with it rather than burying it.

## 9. Things this task did not do, or found surprising

| Item | Why |
|---|---|
| The Process Cell controller's inventory is not built | The task asks for a *documented extension point*, not the inventory itself. `dropExtraContents` + `dropContainer` is that point, and it has a test. When the controller grows an inventory it overrides the hook and drops its own `Container`s. |
| Explosion drops are argued, not tested | The hook fires from `LevelChunk.setBlockState`, and explosions route through that same path, so the mechanism covers them. Testing it needs an explosion setup that would add a second unrelated failure mode; the broken and replaced paths are tested directly and share the call site. |
| Unloading loses the contents of a *removed* block entity | `Level.removeBlockEntity` does not call the hook, so a same-tick break after it drops nothing: `Level.getBlockEntity` recreates an empty block entity for the still-present block. A real chunk unload saves first, so nothing is lost — the test proves that by saving the removed instance and showing the contents return. Worth knowing before anything relies on removing a block entity in place. |
| `yield_run_spec` still fails intermittently | Pre-existing and unrelated: `UUID.randomUUID()` per lot with a ±10-point band on 25 samples, ≈31% failure by construction, as quantified in the M1-02b report. It failed in runs 1-3 of this task and passed in the final run. Its code is untouched. |
| `arcforge:creosote_moves` fails | An Arcforge test reported as optional, so it does not fail the run. |

## 10. Files changed

| Path | Status |
|---|---|
| `src/main/java/com/euvcraft/lithmod/mc/machine/FabMachineBlockEntity.java` | `implements Container`, full `Container` implementation, `preRemoveSideEffects`, `dropExtraContents`, `dropContainer`, storage slots |
| `src/main/java/com/euvcraft/lithmod/mc/machine/FabMachineMenu.java` | uses the block entity as the container; `machineSlotCount` tallied, not assumed |
| `src/main/java/com/euvcraft/lithmod/mc/machine/MachineContainer.java` | **deleted** — the block entity is the container |
| `src/main/java/com/euvcraft/lithmod/mc/machine/test/TestMachineBlockEntity.java` | storage slot, `STORAGE_SLOT` |
| `src/main/java/com/euvcraft/lithmod/mc/machine/test/TestMachineMenu.java` | exposes the storage slot |
| `src/main/java/com/euvcraft/lithmod/mc/gametest/MachineDropGameTests.java` | new — the 6 tests |
| `src/main/java/com/euvcraft/lithmod/mc/gametest/MachineGameTests.java` | menu slot count 38 → 39 |
| `src/main/java/com/euvcraft/lithmod/mc/registry/ModItems.java` | `TEST_MACHINE` block item |
| `src/main/java/com/euvcraft/lithmod/mc/EUVCRAFTMod.java` | one line: register the new game tests |
| `src/main/resources/data/lithmod/loot_table/blocks/*.json` | new — 4 files |
| `src/main/resources/data/minecraft/tags/block/{mineable/pickaxe,needs_stone_tool}.json` | new — 2 files |
| `src/main/resources/data/lithmod/tags/block/machine.json` | new |
| `src/main/resources/assets/lithmod/{items,models/item,models/block,blockstates}/test_machine.json` | new — 4 files |
| `src/main/resources/assets/lithmod/lang/en_us.json` | one line |