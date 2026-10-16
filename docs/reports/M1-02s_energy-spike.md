# M1-02s: 26.3 energy capability and registration spike

Date: 2026-10-15
Branch: `m1-02s-energy-spike`
Commit: `f5a7504` — *M1-02s: 26.3 energy capability and registration spike*
Base: `abf867d` (master, M1-00 + M1-01 + M1-01b merged)
NeoForge `26.3.0.69-beta`, Minecraft `26.3`, Java toolchain 25 (Eclipse Adoptium 25.0.4.1)

## 1. Summary

Two stalled tasks were blocked on three unknown 26.3 facts. This spike answers all three
with code that runs on a real server level, and every answer is copied from a decompiled
Arcforge or Encoded Logistics source file, not guessed.

| Unknown that blocked M1-02 / M1-02b | Answer |
|---|---|
| How does a block entity expose FE in 26.3? | `Capabilities.Energy.BLOCK` (not `Capabilities.EnergyStorage.BLOCK`), registered with `RegisterCapabilitiesEvent.registerBlockEntity(...)` on the **mod** event bus. The handler is `net.neoforged.neoforge.transfer.energy.EnergyHandler`; `SimpleEnergyHandler` with `maxExtract = 0` refuses to give energy back. |
| How do blocks register without "Block id not set"? | `DeferredRegister.Blocks.registerBlock(name, Function<Properties, B>, UnaryOperator<Properties>)`. The factory receives the `BlockBehaviour.Properties` the helper has **already stamped with the registry id**. The block constructor must pass those properties straight to `super`. |
| How do block entities, menus and persistence work? | `new BlockEntityType<>(factory, block)`; `IMenuTypeExtension.create(Supplier-with-buf)` for the menu type; `ContainerHelper.loadAllItems/saveAllItems(ValueInput/ValueOutput, NonNullList)` for the inventory and `SimpleEnergyHandler.serialize/deserialize` for energy. |

The spike also found three things that cost real debugging time and are worth writing down:
`AbstractContainerMenu.quickMoveStack` and `stillValid` are **abstract** in 26.3;
`SimpleEnergyHandler.set(int)` is on the concrete class, not the `EnergyHandler` interface;
and `Level.getCapability(Capabilities.Item.BLOCK, ...)` returns `null` when nothing was
registered, which is exactly the assertion the machine framework needs.

## 2. What was done

- Decompiled `arcforge-2.5.1+26.3.jar`, `encodedlogistics-1.2.1+26.3.jar` and
  `arcforge-api-1.2.0+26.3.jar` with Vineflower 1.12.0 (taken from the Gradle cache). No
  decompiled sources existed in the repository root or `libs/` beforehand.
- Read the NeoForge 26.3 **sources jar** for `Capabilities`, `RegisterCapabilitiesEvent`,
  `EnergyHandler`, `SimpleEnergyHandler`, `IMenuTypeExtension`, `Transaction`.
- Wrote a standalone `javac` probe against the real 26.3 compile classpath (obtained through
  a `-I` init script, so no build file changed) to confirm every signature before writing
  any production code.
- Added a minimal spike block, block entity, menu, capability registration and three game
  tests under `com.euvcraft.lithmod.mc.spike`, wired into `EUVCRAFTMod`.
- Nothing else in the repository was touched.

## 3. Exact class and method names used, and where the pattern came from

| Point | Class and member used | Pattern taken from |
|---|---|---|
| Capability constants | `net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK` (also `.Item.BLOCK`, `.Fluid.BLOCK`) | `net/zagdrath/arcforge/registry/ModCapabilities.java` lines 11-13, imports `Capabilities.Energy` / `Fluid` / `Item` |
| Capability event type | `net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent` | same file, lines 9 and 121 |
| Capability registration call | `RegisterCapabilitiesEvent.registerBlockEntity(BlockCapability<T,C>, BlockEntityType<BE>, ICapabilityProvider<? super BE, C, T>)` | same file, line 125: `event.registerBlockEntity(Energy.BLOCK, ModBlockEntityTypes.COMBUSTION_PLANT.get(), CombustionPlantBlockEntity::getEnergyHandler)` |
| Capability listener hookup | `IEventBus.addListener(Consumer<RegisterCapabilitiesEvent>)` inside `ModCapabilities.register(IEventBus)`, called from the `@Mod` constructor | `net/zagdrath/arcforge/registry/ModCapabilities.java` lines 117-119; `net/zagdrath/arcforge/Arcforge.java` line 54 `ModCapabilities.register(modEventBus)` |
| `RegisterCapabilitiesEvent` is a mod bus event | `implements Event, IModBusEvent` | NeoForge sources jar, `net/neoforged/neoforge/capabilities/RegisterCapabilitiesEvent.java` class declaration |
| Energy handler interface | `net.neoforged.neoforge.transfer.energy.EnergyHandler` — `getAmountAsLong()`, `getCapacityAsLong()`, `insert(int, TransactionContext)`, `extract(int, TransactionContext)` | `net/zagdrath/arcforge/blockentity/logistics/MeterBlockEntity.java` lines 68-94 and 258-264 |
| Energy buffer implementation | `net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler(capacity, maxInsert, maxExtract, energy)`; subclass and override `protected void onEnergyChanged(int previousAmount)` | `net/zagdrath/arcforge/transfer/energy/TrackingEnergyHandler.java` line 5 `extends SimpleEnergyHandler`, line 10 `TrackingEnergyHandler(int capacity, int maxTransfer, Runnable onChanged)`; used at `MeterBlockEntity.java` line 92 with `this::setChanged` as the change callback |
| Refusing extraction | `maxExtract = 0` in the `SimpleEnergyHandler` constructor | NeoForge sources jar, `SimpleEnergyHandler.extract` returns `Math.min(energy, Math.min(amount, maxExtract))` |
| Transaction wrapper | `net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()`, `commit()`; try-with-resources | `net/zagdrath/arcforge/blockentity/logistics/MeterBlockEntity.java` lines 167 and 172 use `EnergyHandlerUtil.move(handler, target, cap, null)`, whose `cap` argument is the `TransactionContext` |
| Block registration | `DeferredRegister.createBlocks(String)`, `DeferredRegister.Blocks.registerBlock(String, Function<Properties,B>, UnaryOperator<Properties>)` returning `DeferredBlock<B>` | `net/zagdrath/arcforge/registry/ModBlocks.java` line 153 and line 166: `BLOCKS.registerBlock("firebox", FireboxBlock::new, p -> machineProperties(p, 13))` |
| Block constructor | `Block(Properties)` — pass the given properties to `super`, never build them with `Properties.of()` | `net/zagdrath/arcforge/block/machine/FireboxBlock.java` line 21 `public FireboxBlock(Properties properties)` |
| Block entity type registration | `DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, String)`; `new BlockEntityType<>(factory, Block...)` | `net/zagdrath/arcforge/registry/ModBlockEntityTypes.java` line 98 and lines 99-101 |
| Block entity persistence | `protected void loadAdditional(ValueInput input)` / `protected void saveAdditional(ValueOutput output)`, single argument, `net.minecraft.world.level.storage` | `net/zagdrath/arcforge/blockentity/logistics/MeterBlockEntity.java` lines 353-374 |
| Inventory persistence | `net.minecraft.world.ContainerHelper.loadAllItems(ValueInput, NonNullList<ItemStack>)` / `saveAllItems(ValueOutput, NonNullList<ItemStack>)` | `net/zagdrath/encodedlogistics/blockentity/LithographyPressBlockEntity.java` lines 205 and 212 |
| Energy persistence | `SimpleEnergyHandler.deserialize(ValueInput)` / `serialize(ValueOutput)`, which read and write the key `"energy"` | NeoForge sources jar, `SimpleEnergyHandler implements ValueIOSerializable` |
| Menu type registration | `DeferredRegister.create(Registries.MENU, String)`; `IMenuTypeExtension.create(IContainerFactory<T>)` returning `new MenuType<>(factory, FeatureFlags.DEFAULT_FLAGS)` | `net/zagdrath/arcforge/registry/ModMenuTypes.java` line 75 and line 106; `net/neoforged/neoforge/common/extensions/IMenuTypeExtension.java` |
| Menu constructors | server: `(int containerId, Inventory, BlockPos, ContainerData)`; client: `(int containerId, Inventory, RegistryFriendlyByteBuf)` | `net/zagdrath/arcforge/menu/logistics/MeterMenu.java` lines 42 and 38 |
| Menu abstract methods | `stillValid(Player)` and `quickMoveStack(Player, int)` are both abstract | found by compiling; `javap` on `AbstractContainerMenu` in the patched 26.3 jar |
| Menu provider | `BlockEntity implements MenuProvider`, `createMenu(int, Inventory, Player)` | `net/zagdrath/arcforge/blockentity/logistics/MeterBlockEntity.java` lines 62 and 300-302 |
| Block-side capability lookup used by the test | `Level.getCapability(BlockCapability<T, C>, BlockPos, C)` | `net/zagdrath/arcforge/gametest/MachineControlGameTests.java` lines 218 and 223 |
| Custom capability creation (not needed here, verified) | `BlockCapability.createSided(Identifier, Class)` | `net/zagdrath/arcforge/registry/ModCapabilities.java` lines 110-112 (Arcforge's own heat capability) |
| Game test registration | `DeferredRegister.create(Registries.TEST_FUNCTION, "lithmod")`, `RegisterGameTestsEvent.registerEnvironment/registerTest` | `com/euvcraft/lithmod/mc/gametest/FoupGameTests.java` lines 84-96 and 110-137 |
| Test player construction | `CommonListenerCookie.createInitial(GameProfile, boolean)`, `new ServerPlayer(...)`, `new Connection(PacketFlow.SERVERBOUND)`, `new EmbeddedChannel(connection)`, `getPlayerList().placeNewPlayer(...)` | `com/euvcraft/lithmod/mc/gametest/FoupGameTests.java` lines 436-451 |

## 4. Real output

### 4.1 Decompilation

```
$ java -jar vineflower-1.12.0.jar -dgs=1 libs/arcforge-2.5.1+26.3.jar  <out>/arcforge
INFO:  ... done
```

No decompiled tree existed before this task:

```
=== root listing ===
d----- .github  d----- docs  d----- gradle  d----- libs  d----- src  d----- tools
-a---- build.gradle  -a---- gradle.properties  -a---- gradlew  -a---- gradlew.bat
-a---- settings.gradle  -a---- README.md  -a---- TEMPLATE_LICENSE.txt

=== libs ===
arcforge-2.5.1+26.3.jar       6955674
arcforge-api-1.2.0+26.3.jar     35152
encodedlogistics-1.2.1+26.3.jar 5867001
```

### 4.2 Compile

```
$ .\gradlew.bat compileJava --offline
BUILD SUCCESSFUL
```

### 4.3 Game tests on a real server level

```
$ .\gradlew.bat runGameTestServer --offline
[20:30:22] [Worker-Main-3/INFO] [co.eu.li.mc.EUVCRAFTMod/]: Registered EUVCRAFT M1-02s spike game tests
[20:30:31] [Server thread/INFO] [minecraft/GameTestRunner]: Running test environment 'lithmod:spike' batch 0 (3 tests)...

[20:30:31] [Server thread/INFO] [co.eu.li.mc.EUVCRAFTMod/]: [M1-02s] energy_capability: offered=300 accepted=300 stored=300 capacity=10000
[20:30:31] [Server thread/INFO] [co.eu.li.mc.EUVCRAFTMod/]: [M1-02s] energy_capability: extract(offered=800) returned 0
[20:30:31] [Server thread/INFO] [co.eu.li.mc.EUVCRAFTMod/]: [M1-02s] save_load_round_trip: energy 400 -> 400 FE, item minecraft:iron_ingotx7 -> minecraft:iron_ingotx7
[20:30:32] [Server thread/INFO] [co.eu.li.mc.EUVCRAFTMod/]: [M1-02s] menu: block Block{lithmod:spike_machine} registered as lithmod:spike_machine
[20:30:32] [Server thread/INFO] [co.eu.li.mc.EUVCRAFTMod/]: [M1-02s] menu: registered MenuType -> SpikeMachineMenu | opened SpikeMachineMenu with 37 slots, energy handler true

[20:31:51] [Server thread/INFO] [minecraft/GameTestServer]: ========= 691 GAME TESTS COMPLETE IN 1.422 min ======================
[20:31:51] [Server thread/INFO] [minecraft/GameTestServer]: All 691 required tests passed :)
[20:31:51] [Server thread/INFO] [minecraft/GameTestServer]: 1 optional tests failed
[20:31:51] [Server thread/ERROR] [minecraft/GameTestServer]:    - arcforge:creosote_moves: Armor stand did not drift through creosote: x -1.00839095E7 -> -1.00839095E7 on tick 602
[20:31:51] [Server thread/INFO] [minecraft/GameTestServer]: ====================================================

BUILD SUCCESSFUL in 3m 11s
```

Reading the evidence lines:

- `offered=300 accepted=300 stored=300 capacity=10000` — the insert was accepted in full and
  the stored amount rose by exactly the accepted amount.
- `extract(offered=800) returned 0` — extraction of the whole stored amount was refused,
  because `maxExtract` is 0.
- `energy 400 -> 400 FE, item minecraft:iron_ingotx7 -> minecraft:iron_ingotx7` — both the
  energy buffer and the slot contents survived the value input/output round trip.
- `block Block{lithmod:spike_machine} registered as lithmod:spike_machine` — registration
  completed; there is no `Block id not set` anywhere in the log.
- `registered MenuType -> SpikeMachineMenu | opened SpikeMachineMenu with 37 slots` — the
  menu type factory works and the menu opens server-side.

The single optional failure, `arcforge:creosote_moves`, belongs to Arcforge and is
unrelated to this spike. It is also **not** a required test, so the run is green.

### 4.4 Unit tests and core import check

```
$ .\gradlew.bat test checkCoreImports --offline
Core import check passed: no Minecraft/NeoForge imports found in core package.
BUILD SUCCESSFUL in 57s
```

Totals read from `build/test-results/test/*.xml`:
`classes=48 tests=260 failures=0 errors=0 skipped=0`.

## 5. Test tables that sum

### 5.1 Game tests (`runGameTestServer`)

Counts are summed from the `Running test environment '<mod>:<name>' batch N (N tests)`
lines in the run log.

| Mod | Tests | Passed | Failed |
|---|---|---|---|
| `lithmod:default` | 1 | 1 | 0 |
| `lithmod:foup_default` | 11 | 11 | 0 |
| `lithmod:spike` | 3 | 3 | 0 |
| `lithmod:yield_default` | 4 | 4 | 0 |
| `lithmod:fab_random_default` | 2 | 2 | 0 |
| `minecraft:default` | 1 | 1 | 0 |
| `encodedlogistics:default` (5 batches) | 217 | 217 | 0 |
| `encodedlogistics:arcforge_reload` | 1 | 1 | 0 |
| `arcforge:*` (9 solar + 9 default batches) | 451 | 451 | 0 |
| **Total** | **691** | **691** | **0** |

The server printed `All 691 required tests passed :)`, which matches the table.

Of those 691, three are new in this task and named explicitly:

| Game test id | What it proves |
|---|---|
| `lithmod:spike_energy_capability` | FE inserted through a `Level.getCapability` lookup from a second position; stored rises by exactly the accepted amount; a second insert is clamped to `maxInsert`; extraction is refused; no item or fluid capability on any of the six sides nor on a null side; the block entity buffer agrees with the capability. |
| `lithmod:spike_save_load_round_trip` | Stored FE and the slot contents survive `saveWithId` → `buildResult` → `loadWithComponents` through `ValueOutput`/`ValueInput`. |
| `lithmod:spike_menu_opens_server_side` | `lithmod:spike_machine` is in the block registry under its expected id (no "Block id not set"), the menu type is in the menu registry, the registered `MenuType` factory builds a `SpikeMachineMenu`, `BlockEntity.createMenu` builds one bound to the block entity, and that menu opens server-side with 37 slots and a live energy handler. |

### 5.2 Unit tests (`test`)

Read from `build/test-results/test/*.xml`; the file has 48 `testsuite` entries, of which the
20 fully-qualified class names are the Java classes and the rest are JUnit `@Nested` and
`@DisplayName` groups.

| Source | Suites | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|---|
| `com.euvcraft.lithmod.core.*` | 9 | 80 | 0 | 0 | 0 |
| `com.euvcraft.lithmod.mc.*` | 11 | 44 | 0 | 0 | 0 |
| JUnit `@Nested` / `@DisplayName` groups | 28 | 136 | 0 | 0 | 0 |
| **Total** | **48** | **260** | **0** | **0** | **0** |

### 5.3 Combined

| Suite | Tests | Failures |
|---|---|---|
| Game tests (`runGameTestServer`) | 691 | 0 |
| Unit tests (`test`) | 260 | 0 |
| **Total** | **951** | **0** |

No new unit test class was added by this spike: the whole point of the task is that the
answers had to hold on a real server level with real registries, and a unit test cannot
exercise `RegisterCapabilitiesEvent`, the block registry or a block entity load. The three
game tests carry that evidence instead. The 260 unit tests are all pre-existing and still
pass, which shows the spike changed nothing that was already working.

## 6. Reference code, so the next task can copy it

### 6.1 Block registration that does not throw "Block id not set"

```java
public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);

public static final DeferredBlock<SpikeMachineBlock> SPIKE_MACHINE =
    BLOCKS.registerBlock("spike_machine",
        SpikeMachineBlock::new,                                   // receives Properties
        properties -> properties.strength(3.0f, 6.0f).requiresCorrectToolForDrops());

public static final class SpikeMachineBlock extends BaseEntityBlock {
    public SpikeMachineBlock(BlockBehaviour.Properties properties) {
        super(properties);                                        // never Properties.of()
    }
}
```

### 6.2 Block entity type

```java
public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
    DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);

public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpikeMachineBlockEntity>> BLOCK_ENTITY =
    BLOCK_ENTITIES.register("spike_machine",
        () -> new BlockEntityType<>(SpikeMachineBlockEntity::new, SPIKE_MACHINE.get()));
```

### 6.3 Energy capability registration on the mod bus

```java
public static void register(IEventBus modEventBus) {
    BLOCKS.register(modEventBus);
    BLOCK_ENTITIES.register(modEventBus);
    MENU_TYPES.register(modEventBus);
    modEventBus.addListener(SpikeRegistries::registerCapabilities);
}

private static void registerCapabilities(RegisterCapabilitiesEvent event) {
    event.registerBlockEntity(Capabilities.Energy.BLOCK, BLOCK_ENTITY.get(),
        (blockEntity, side) -> blockEntity instanceof SpikeMachineBlockEntity machine
            ? machine.energy()
            : null);
}
```

### 6.4 Energy buffer: accept from every side, extract from none

```java
public static final class TrackedEnergy extends SimpleEnergyHandler {
    TrackedEnergy(BlockEntity owner) {
        super(CAPACITY, MAX_INSERT, MAX_EXTRACT);   // MAX_EXTRACT == 0 refuses extraction
        this.owner = owner;
    }

    @Override
    protected void onEnergyChanged(int previousAmount) {
        this.owner.setChanged();
    }
}
```

### 6.5 Persistence through the 26.3 value input and output API

```java
@Override
protected void loadAdditional(ValueInput input) {
    super.loadAdditional(input);
    ContainerHelper.loadAllItems(input, this.items);
    this.energy.deserialize(input);
}

@Override
protected void saveAdditional(ValueOutput output) {
    super.saveAdditional(output);
    ContainerHelper.saveAllItems(output, this.items);
    this.energy.serialize(output);
}
```

### 6.6 Menu type

```java
public static final DeferredRegister<MenuType<?>> MENU_TYPES =
    DeferredRegister.create(Registries.MENU, MODID);

public static final DeferredHolder<MenuType<?>, MenuType<SpikeMachineMenu>> MENU =
    MENU_TYPES.register("spike_machine", () -> IMenuTypeExtension.create(SpikeMachineMenu::new));
```

### 6.7 Pushing and pulling FE in a game test

```java
EnergyHandler handler = level.getCapability(Capabilities.Energy.BLOCK, pos, side);
try (Transaction transaction = Transaction.openRoot()) {
    int accepted = handler.insert(300, transaction);
    transaction.commit();
}
```

## 7. Findings worth carrying into the machine framework

1. **`Capabilities.Energy.BLOCK`, never `Capabilities.EnergyStorage.BLOCK`.** The nested
   classes are `Capabilities.Energy`, `Capabilities.Item` and `Capabilities.Fluid`, each with
   a `BLOCK`, an `ENTITY` and an `ITEM` constant.
2. **`EnergyHandler` has no `receiveEnergy`.** It is
   `getAmountAsLong()` / `getCapacityAsLong()` / `insert(int, TransactionContext)` /
   `extract(int, TransactionContext)`. `net.neoforged.neoforge.energy.IEnergyStorage` does
   not exist in 26.3, and neither does `LazyOptional`.
3. **`maxExtract = 0` is the whole "extract nothing" requirement.** No wrapper class needed.
4. **`registerBlockEntity` is keyed on the `BlockEntityType`, not the `Block`.** Arcforge
   registers `Item.BLOCK` for one type and `Energy.BLOCK` for another on the same machine,
   so the machine framework registers only `Energy.BLOCK` and gets the "no item handler, no
   fluid capability" property for free — `Level.getCapability` returns `null` for the
   unregistered capabilities, which the game test asserts on every side.
5. **`RegisterCapabilitiesEvent` is a mod bus event** (`implements IModBusEvent`), so it goes
   on the bus handed to the `@Mod` constructor.
6. **`Properties` from `registerBlock` already carry the id.** This is the fix for the
   "Block id not set" crash both stalled tasks hit.
7. **`IMenuTypeExtension.create` is how a menu type gets its client constructor.** The plain
   `new MenuType<>(MenuSupplier, FeatureFlagSet)` constructor takes a two-argument
   `MenuSupplier` and cannot construct the client menu from a `RegistryFriendlyByteBuf`.
8. **`AbstractContainerMenu.stillValid` and `quickMoveStack` are abstract in 26.3**, so every
   `FabMachineMenu` subclass must implement both. `SimpleContainer` does not exist;
   `net.minecraft.world.Container` is the interface a menu slot needs.
9. **A game test cannot call `Player.openMenu`.** The NeoForge `IPlayerExtension.openMenu`
   override sends `neoforge:advanced_open_screen`, and the fake test connection rejects it
   with `Payload neoforge:advanced_open_screen may not be sent to the client!`. Server-side
   menu tests must either install the menu on the public `Player.containerMenu` field or call
   `MenuType.create(int, Inventory, RegistryFriendlyByteBuf)` directly.
10. **`SimpleEnergyHandler.insert` clamps per call, not per transaction.** Inserting more than
    `maxInsert` in one call returns exactly `maxInsert`; a transaction does not raise that.

## 8. Not verified

| Item | Why |
|---|---|
| Client-side rendering of a machine screen | The spike registers no screen; the task asked only for a registered menu that opens server-side. |
| `BlockCapability.createSided` for a custom capability | The signature compiles against the real 26.3 classpath, but Arcforge's heat capability is the only example and the spike registers no custom capability. |
| Interaction through the block (`useItemOn`) | The code path is present and compiles, but no game test clicks the block. |
| `arcforge:creosote_moves` | Fails in this run as an optional Arcforge test; not caused by and not investigated as part of this spike. |
| Anything about recipes, machine definitions or the multiblock framework | Out of scope for this spike. |

## 9. Files added or changed

| Path | Status | Purpose |
|---|---|---|
| `src/main/java/com/euvcraft/lithmod/mc/spike/SpikeRegistries.java` | new | Block, block entity type, menu type and energy capability registration |
| `src/main/java/com/euvcraft/lithmod/mc/spike/SpikeMachineBlock.java` | new | Block whose constructor takes the properties `registerBlock` supplies |
| `src/main/java/com/euvcraft/lithmod/mc/spike/SpikeMachineBlockEntity.java` | new | Energy buffer, one slot, value input/output persistence |
| `src/main/java/com/euvcraft/lithmod/mc/spike/SpikeMachineMenu.java` | new | Server and client constructors over a `Container` view |
| `src/main/java/com/euvcraft/lithmod/mc/spike/SpikeGameTests.java` | new | The three game tests |
| `src/main/java/com/euvcraft/lithmod/mc/EUVCRAFTMod.java` | modified | Two lines: `SpikeRegistries.register(modEventBus)` and `SpikeGameTests.register(modEventBus)` |

No build file, no resource file and no existing mod class other than `EUVCRAFTMod` was
changed. The Gradle init script used to print the compile classpath and the `javac` probe
were both written outside the repository, under
`C:\Users\ethan\AppData\Local\Temp\opencode`.
