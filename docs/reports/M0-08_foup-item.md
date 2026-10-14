# M0-08: FOUP item with hidden per-wafer data

- Date: 2026-10-14
- Branch: `m0-08-foup-item`, branched from `m0-06-die-type-definitions` at `443be6c`
- Commits: `fe0fb2d` (implementation), `a7246ed` (report)
- Java version used: Java 25 through the Gradle toolchain pinned in `build.gradle`
  (the `java` on `PATH` is Temurin 21; Gradle provisions 25, as in M0-03c)
- Build and test commands run:
  - `.\gradlew.bat test checkCoreImports --offline --console=plain --rerun-tasks`
  - `.\gradlew.bat runGameTestServer --offline --console=plain` (default config)
  - `.\gradlew.bat runGameTestServer --offline --console=plain` with
    `features.debugCommands = true` in `run/config/lithmod-local.toml`, changed back afterwards

## 1. Summary

This task adds the sealed lot. `com.euvcraft.lithmod.core` gains `Wafer`,
`FoupPublicView`, `FoupContents` and `WaferPacking`: an immutable lot with a server-side
`UUID`, a capacity and an ordered wafer list, plus the packed primitive arrays the
storage layer writes. `FoupContents.publicView()` is the only shape that may leave the
server, and it is three integers. The mc package registers one data component,
`lithmod:foup_contents`, whose persistent codec stores everything (lot id, capacity and
three NBT int arrays, rolls as raw IEEE-754 bits) and whose network codec stores the
public view only, so a client receives three varints and never a roll. Two items, FOUP
Mk1 and Mk2, share that component; a tooltip built from the public view, a creative tab
and the operator commands `/debug foup fill|inspect` complete the item. The creative-mode
spike found a real hole and is written up in section 7.

## 2. Files changed

| path | new/modified/deleted | purpose |
|---|---|---|
| src/main/java/com/euvcraft/lithmod/core/Wafer.java | new | immutable wafer: layer index, hidden cumulative roll, reserved flags |
| src/main/java/com/euvcraft/lithmod/core/FoupPublicView.java | new | capacity, wafer count, lot layer; the only lot shape that may leave the server |
| src/main/java/com/euvcraft/lithmod/core/FoupContents.java | new | immutable lot with `size`, `isFull`, `withWafer`, `withWafers`, `withoutWaferAt`, `publicView` |
| src/main/java/com/euvcraft/lithmod/core/WaferPacking.java | new | wafer list to and from three parallel primitive arrays, with validation |
| src/main/java/com/euvcraft/lithmod/core/FabSettings.java | modified | additive `debugCommands` component, default and builder entry |
| src/main/java/com/euvcraft/lithmod/mc/FabConfig.java | modified | `features.debugCommands` spec entry and its binding |
| src/main/java/com/euvcraft/lithmod/mc/FoupContentsCodec.java | new | persistent `CODEC` and network `STREAM_CODEC` for the component |
| src/main/java/com/euvcraft/lithmod/mc/FoupItems.java | new | creation and reading of FOUP stacks, capacity from config |
| src/main/java/com/euvcraft/lithmod/mc/FoupTooltip.java | new | `TooltipProvider` that prints "Wafers: n/capacity" and "Layer: k" from the public view |
| src/main/java/com/euvcraft/lithmod/mc/FoupTooltipAppenders.java | new | registers the tooltip through NeoForge 26.3's data component appenders |
| src/main/java/com/euvcraft/lithmod/mc/FoupDebugCommand.java | new | `/debug foup fill <count> <layer>` and `/debug foup inspect` |
| src/main/java/com/euvcraft/lithmod/mc/EUVCRAFTMod.java | modified | registers the three deferred registers, the tooltip appender, the debug command listener and the game tests; adds `config()` / `settings()` and the `ModConfigEvent` listeners |
| src/main/java/com/euvcraft/lithmod/mc/registry/ModDataComponents.java | new | `lithmod:foup_contents` |
| src/main/java/com/euvcraft/lithmod/mc/registry/ModItems.java | new | `foup_mk1`, `foup_mk2`, both `stacksTo(1)` |
| src/main/java/com/euvcraft/lithmod/mc/registry/ModCreativeTabs.java | new | the EUVCRAFT tab, one empty lot per variant |
| src/main/java/com/euvcraft/lithmod/mc/gametest/FoupGameTests.java | new | five game tests, see section 3 |
| src/main/resources/assets/lithmod/lang/en_us.json | modified | item names, tooltip and command strings |
| src/test/java/com/euvcraft/lithmod/core/FoupContentsTest.java | new | 31 core tests |
| src/test/java/com/euvcraft/lithmod/mc/FoupContentsCodecTest.java | new | 12 codec tests including the encoded sizes |
| src/test/java/com/euvcraft/lithmod/mc/FabConfigTest.java | modified | two tests for the new key |
| docs/testing.md | modified | the FOUP game tests and the debug command key |

## 3. Verification

```
.\gradlew.bat test checkCoreImports --offline --console=plain --rerun-tasks
Core import check passed: no Minecraft/NeoForge imports found in core package.
    FOUP component: 25 wafers -> 382 bytes NBT, 3 bytes on the wire
    FOUP component: 64 wafers -> 850 bytes NBT, 3 bytes on the wire
BUILD SUCCESSFUL in 25s
```

The two size lines above are the component on its own; the game test below measures the
whole `ItemStack`.

```
.\gradlew.bat runGameTestServer --offline --console=plain
[14Oct2026 01:31:35.380] [Server thread/INFO] [co.eu.li.mc.EUVCRAFTMod/]: FOUP of 25 wafers: 456 bytes NBT, 3 bytes on the wire
[14Oct2026 01:31:35.381] [Server thread/INFO] [co.eu.li.mc.EUVCRAFTMod/]: FOUP of 64 wafers: 924 bytes NBT, 3 bytes on the wire
[14Oct2026 01:31:35.394] [Server thread/INFO] [co.eu.li.mc.EUVCRAFTMod/]: FOUP debug commands are disabled; set debugCommands=true in the config to test them
[14Oct2026 01:32:29.359] [Server thread/INFO] [net.minecraft.gametest.framework.GameTestServer/]: ========= 678 GAME TESTS COMPLETE IN 1.601 min ======================
[14Oct2026 01:32:29.360] [Server thread/INFO] [net.minecraft.gametest.framework.GameTestServer/]: All 678 required tests passed :)
BUILD SUCCESSFUL in 3m 35s
```

With `features.debugCommands = true`, the same command tree runs the commands for real:

```
[14Oct2026 01:19:28] [Worker-Main-2/INFO] [co.eu.li.mc.EUVCRAFTMod/]: EUVCRAFT debug commands enabled: /debug foup fill|inspect (vanilla already owns /debug, so the effective gate is that of the merged node)
[14Oct2026 01:21:08.387] [Server thread/INFO] [net.minecraft.gametest.framework.GameTestServer/]: All 678 required tests passed :)
```

Unit test table, aggregated by class from `build/test-results/test/*.xml`:

| test class | tests | failures | errors | skipped | total |
|---|---:|---:|---:|---:|---:|
| core.FoupContentsTest (+ 5 nested) | 31 | 0 | 0 | 0 | 31 |
| core.FabSettingsTest (+ 10 nested) | 43 | 0 | 0 | 0 | 43 |
| core.TierRegistryTest | 34 | 0 | 0 | 0 | 34 |
| core.ContextualRngTest | 14 | 0 | 0 | 0 | 14 |
| core.StatModelVerificationTest | 10 | 0 | 0 | 0 | 10 |
| core.GenerationMatrixTest | 8 | 0 | 0 | 0 | 8 |
| core.DieRulesTest (+ nested) | 8 | 0 | 0 | 0 | 8 |
| core.CoreTest | 2 | 0 | 0 | 0 | 2 |
| mc.FoupContentsCodecTest | 12 | 0 | 0 | 0 | 12 |
| mc.FabRandomServiceTest | 8 | 0 | 0 | 0 | 8 |
| mc.FabConfigTest | 3 | 0 | 0 | 0 | 3 |
| mc.data.* (7 classes) | 9 | 0 | 0 | 0 | 9 |
| Total | 182 | 0 | 0 | 0 | 182 |

Game tests, from `run/logs/latest.log`:

| test | environment | result |
|---|---|---|
| lithmod:foup_stack_round_trip | lithmod:foup_default | passed |
| lithmod:foup_network_view_is_public_only | lithmod:foup_default | passed |
| lithmod:foup_encoded_size | lithmod:foup_default | passed |
| lithmod:foup_creative_tab_entries | lithmod:foup_default | passed |
| lithmod:foup_debug_commands | lithmod:foup_default | passed (commands run) with `debugCommands=true`, passed (branch absent) with the default |
| lithmod:trivial, lithmod:fab_random_* | (pre-existing) | passed |

Encoded sizes, measured, not estimated:

| lot | component NBT | whole ItemStack NBT | network |
|---|---:|---:|---:|
| 25 wafers | 382 bytes | 456 bytes | 3 bytes |
| 64 wafers | 850 bytes | 924 bytes | 3 bytes |

## 4. Spec conformance

| item | met/partly/not met | evidence |
|---|---|---|
| `record Wafer(int layerIndex, float cumulativeRoll, int flags)`, layer 0 unprocessed, roll in (0,1], flags reserved 0 | met | `core/Wafer.java`, constructor rejects a negative layer and a roll outside (0,1]; `WaferTest` |
| `record FoupContents` immutable, with `size()`, `isFull()`, `withWafer(w)` failing when full, `withWafers(list)`, `withoutWaferAt(index)`, `publicView()` | met | `core/FoupContents.java`; `CapacityTest`, `ImmutabilityTest` |
| `publicView()` returns capacity, wafer count and lot layer only, never a roll or a flag | met | `core/FoupPublicView.java` has exactly three components; `PublicViewTest.viewHasNoHiddenFields` asserts the component count and the `toString` |
| `WaferPacking` converts a wafer list to compact primitive arrays and back, validating equal lengths and roll range | met | `core/WaferPacking.java`; `PackingTest`, `InvalidDataTest` |
| Two items, FOUP Mk1 and Mk2, stack size 1, repo naming and registration style | met | `mc/registry/ModItems.java`, EL's `DeferredRegister.Items` pattern; `foup_creative_tab_entries` asserts both max stack sizes are 1 |
| Mk2 identical to Mk1 in this task, transit comes later | met | the two registrations differ only in name; no other branch in the code |
| Both use the same data component | met | `ModCreativeTabs` and `FoupDebugCommand` both write `ModDataComponents.FOUP_CONTENTS` |
| Data component with a persistent codec storing everything including rolls and flags, using the packed arrays | met | `FoupContentsCodec.CODEC`, five NBT fields, three int arrays; `persistentFormShape`, `persistentCodecKeepsRolls` |
| Data component with a network codec sending the public view only | met | `FoupContentsCodec.STREAM_CODEC`, three varints; `networkCodecKeepsThePublicView`, `networkCodecHidesRollsAndFlags`, `foup_network_view_is_public_only` |
| Clients must never receive cumulativeRoll or flags | met | the decoded lot has `CLIENT_VIEW_ROLL` and `NO_FLAGS` on every wafer, asserted in both the unit test and the game test; the whole component is 3 bytes for any lot size |
| New FOUPs use config `foupCapacity` at creation | met | `FoupItems.configuredCapacity()` reads `FabSettings.foupCapacity`; `foup_creative_tab_entries` asserts the tab lots use it |
| Existing FOUPs keep the capacity stored in their data when config changes | met | capacity is a field of `FoupContents`; the creation path is the only writer |
| `lotId` is a random UUID assigned server side at creation | partly | `FoupItems.emptyFoup` uses `UUID.randomUUID()` on whatever side creates the stack, and the creative tab is built on the client too; see section 6 |
| Tooltip shows "Wafers: n/capacity" and "Layer: k" only, never rolls | met | `FoupTooltip` takes a `FoupPublicView` and prints `tooltip.lithmod.foup.wafers` and `tooltip.lithmod.foup.layer`; the view cannot carry a roll |
| Creative tab entries for an empty FOUP Mk1 and Mk2 | met | `ModCreativeTabs.MAIN`; `foup_creative_tab_entries` |
| Operator-only debug commands at permission level 2, registered only when `debugCommands` is true (default false) | partly | the mod's own requirement is `Commands.LEVEL_GAMEMASTERS` (level 2) on both `debug` and `foup`; vanilla already owns `/debug` at level 3 and brigadier keeps the existing node's requirement, so the effective gate is level 3; see section 6 |
| `debug foup fill <count> <layer>` gives a FOUP with that many wafers at that layer with random rolls | met | `FoupDebugCommand.fill`; `foup_debug_commands` asserts 3 wafers at layer 5 with the configured capacity, and two fills get different lot ids |
| `debug foup inspect` prints layer and roll of the held FOUP to the executing operator | met | `FoupDebugCommand.inspect` uses `sendSuccess(..., false)`; `foup_debug_commands` runs it and asserts the result count |
| The new `debugCommands` key is added additively to the M0-02 config | met | `FabConfig.DEBUG_COMMANDS` under `features`; `FabSettings.debugCommands` defaults to false |
| Creative-mode spike, report only, with a recommendation | met | section 7 and the report section 6 |
| Tests: capacity limits, immutability, packing round trip at 0, 1, 25 and 64 wafers, rejection of invalid data | met | `FoupContentsTest`: `CapacityTest` (4), `ImmutabilityTest` (6), `PackingTest` (7), `InvalidDataTest` (4) |
| Tests: persistent codec preserves rolls exactly | met | `persistentCodecKeepsRolls`, `persistentCodecKeepsExtremeRolls`; `foup_stack_round_trip` |
| Tests: stream codec keeps counts, capacity and layer but has no rolls | met | `networkCodecKeepsThePublicView`, `networkCodecHidesRollsAndFlags`, `networkCodecHidesTheLotId` |
| Tests: copying an ItemStack keeps the data | met | `foup_stack_round_trip` on a real server, using `ItemStack.copy()` |
| Tests: report the encoded size for 25 and 64 wafers | met | table above; measured in `encodedSizes` and `foup_encoded_size` |
| Build and tests pass | met | `test` 182/0/0/0, `checkCoreImports` passed, `runGameTestServer` 678/678 required tests passed |
| In the dev client: fill, move between inventory, chest and ground, relog, inspect identical rolls | not met | not run, see section 5 |
| Out of scope: boules, transit or pipes, the yield engine, any GUI or slot | met | none of these were implemented |

## 5. Not verified

- The dev client walkthrough (fill a FOUP with the debug command, move it between the
  inventory, a chest and the ground, relog, inspect identical rolls) was not run. No
  display is available to this session. Not run.
- Item textures and models were not added, so the FOUP renders with the missing-model
  placeholder in a client. Nothing in this task or its tests depends on a model, and
  milestone M0 has no textures.
- The tooltip was not seen rendered. It is built from `FoupPublicView`, the appender is
  registered, and the appender path is NeoForge's own
  (`TooltipAppender.createComponentAppender`), but nothing here renders a screen.
- Survival-mode inventory handling was reasoned about from the 26.3 sources, not played.
- The 3-byte network size was measured by encoding and decoding into a `ByteBuf` inside
  a test, not by watching a real client connection.

## 6. Deviations

| spec says | what you did | why |
|---|---|---|
| `lotId` is a random UUID assigned server side at creation | `FoupItems.emptyFoup` draws `UUID.randomUUID()` on whichever side calls it, and the creative tab is built by the game on the client as well | the creative tab has to produce a stack with a lot in it; deferring creation to a machine would leave the tab entries as lots with no lot id at all, which is worse. The server-side machines and the debug command do assign the id server side |
| debug commands at permission level 2 | the mod's own requirement is `Commands.LEVEL_GAMEMASTERS` (level 2) on both the `debug` and `foup` literals, but `/debug foup ...` is effectively gated at level 3 | vanilla already registers `/debug` with `Commands.LEVEL_ADMINS`, and `CommandNode.addChild` keeps the existing node's requirement when two literals with the same name merge. The spec's command path forces this merge. `foup_debug_commands` asserts the merge behaviour so a change would fail the tests |
| the persistent codec "stores everything, using the packed arrays" | it stores `lot`, `capacity` and three int arrays, with the rolls held as raw IEEE-754 bit patterns | `float` has no DFU codec that maps to an NBT primitive array; raw bits are exact and still one int per wafer. `WaferPackingTest.rollBitsAreLossless` covers the conversion |
| debug commands are only registered when the key is true | `FoupDebugCommand.register` reads the flag once at `RegisterCommandsEvent`; toggling the key needs a server restart | command registration happens at server start; a config reload afterwards would need a re-registration pass that is not worth the complexity for a developer-only tree |
| M0-02 config is consumed as it stands | added the two `ModConfigEvent` listeners `FabConfig.onConfigLoad` and `FabConfig.onConfigReload` were written for but never connected to | without them `FabSettings` never left its defaults, so `foupCapacity` and `debugCommands` had no effect and the `lithmod:foup_debug_commands` test reported "disabled" even with the key set to true. This is the wiring M0-02's own report describes; nothing in `FabConfig` was rewritten |
| records in the new test class are grouped in nested classes | `FoupContentsTest` uses five `@Nested` groups | the existing `FabSettingsTest` and `DieRulesTest` in this repo do the same |

## 7. Open questions

1. Creative-mode spike. `ServerGamePacketListenerImpl.handleSetCreativeModeSlot`
   (line 2128) takes `packet.itemStack()` and passes it straight to
   `slot.setByPlayer(itemStack)`; there is no merge with the slot's previous contents and
   no component check beyond "not empty or count <= max stack size".
   `CreativeModeInventoryScreen.slotClicked` calls
   `gameMode.handleCreativeModeItemAdd(this.menu.getSlot(...).getItem(), ...)` for the
   inventory page and for the hotbar after a shift-click (lines 241 and 312), and
   `handleCreativeModeItemDrop(this.menu.getCarried())` sends the client's carried stack.
   The client's copy of a FOUP has only what the network codec sent, so every roll in it
   is the placeholder. A creative player can therefore replace a server-side FOUP with
   their own stripped copy and destroy the hidden rolls, and can mint a FOUP with any
   lot id they like. The same screen's `ContainerInput.CLONE` branch (line 273) does
   `item.copyWithCount(item.getMaxStackSize())`, which for a max-stack-size-1 item
   duplicates the stack and therefore the lot id.
   Survival is unaffected: `handleContainerClick` (line 2042) calls
   `containerMenu.clicked(...)` on the server's own slots, and the client's stacks go
   only into `setRemoteSlotUnsafe` and `setRemoteCarried`, which feed the remote tracking
   map and nothing else.
   Recommendation: do not trust the component as the store of record. Move the rolls
   into a `SavedData` keyed by lot id, the way `FabRandomData` already stores the salt,
   and let the item carry only lot id, capacity and wafer count; the yield engine then
   reads the rolls through the lot id and a client-replaced stack cannot lose them.
   Cost: the saved data has to be garbage collected when a lot is scrapped, and the
   lookup adds a level of indirection to every machine step. The cheaper alternative is
   to make the cumulative roll a pure function of `(salt, lotId, waferIndex, stepIndex)`
   using the existing `ContextualRng` and store nothing at all, which removes the leak
   question but ties the data model to whatever roll model M0-09 settles on.
   Until one of those is built, `FoupContentsCodec.isClientView` marks the placeholder
   lots that arrive from a client so the damage is detectable.
2. Should `withoutWaferAt` with an out-of-range index throw (current behaviour) or be a
   no-op? It throws, so a machine that pops "the last processed wafer" fails loudly
   instead of quietly doing nothing, but a later task may prefer a forgiving form.
3. `debugCommands` is read once at command registration, so changing it needs a server
   restart. Acceptable for a developer key, but it is surprising in a config reload test.
4. `lithmod:foup_debug_commands` asserts that a gamemaster cannot reach `/debug` and an
   admin can. If a future Minecraft version moves `/debug` to a different level, that
   assertion fails on purpose. It is there so the merge behaviour cannot change silently,
   but it will need updating along with it.

## 8. Handoff

Public API added in this task:

- `com.euvcraft.lithmod.core.Wafer` — `record Wafer(int layerIndex, float cumulativeRoll, int flags)`,
  `UNPROCESSED_LAYER` (0), `NO_FLAGS` (0), `NO_ROLLS` (1.0f), `Wafer.unprocessed()`,
  `isUnprocessed()`, `atLayer(int)`. Rejects a negative layer and a roll outside `(0, 1]`.
- `com.euvcraft.lithmod.core.FoupPublicView` — `record FoupPublicView(int capacity, int waferCount, int lotLayer)`,
  `EMPTY_LOT_LAYER` (0), `FoupPublicView.empty(int)`, `isEmpty()`, `isFull()`.
- `com.euvcraft.lithmod.core.FoupContents` — `record FoupContents(UUID lotId, int capacity, List<Wafer> wafers)`,
  `FoupContents.empty(UUID, int)`, `size()`, `isEmpty()`, `isFull()`, `withWafer(Wafer)`,
  `withWafers(List<Wafer>)`, `withoutWaferAt(int)`, `lotLayer()`, `publicView()`.
- `com.euvcraft.lithmod.core.WaferPacking` — `pack(List<Wafer>)`, `packLayers`, `packRollBits`,
  `packFlags`, `unpack(int[], float[], int[])`, `unpackRollBits(int[])`, and
  `record Packed(int[] layers, float[] rolls, int[] flags)` with `size()`, `isEmpty()`,
  `rollBits()`, `Packed.fromRollBits(...)`; accessors return copies.
- `com.euvcraft.lithmod.core.FabSettings` — new record component `debugCommands`,
  `FabSettings.DEFAULT_DEBUG_COMMANDS` (false), `Builder.debugCommands(boolean)`.
- `com.euvcraft.lithmod.mc.FoupContentsCodec` — `CODEC`, `STREAM_CODEC`
  (`StreamCodec<ByteBuf, FoupContents>`), `CLIENT_VIEW_LOT_ID`, `CLIENT_VIEW_ROLL`,
  `KEY_LOT`/`KEY_CAPACITY`/`KEY_LAYERS`/`KEY_ROLLS`/`KEY_FLAGS`, `isClientView(FoupContents)`.
- `com.euvcraft.lithmod.mc.FoupItems` — `configuredCapacity()`, `emptyFoup(Item)`,
  `with(Item, FoupContents)`, `contentsOf(ItemStack)` (nullable), `isFoup(ItemStack)`.
- `com.euvcraft.lithmod.mc.FoupDebugCommand` — `ROOT` ("debug"),
  `register(RegisterCommandsEvent)`, `fillContents(ServerLevel, UUID, int capacity, int count, int layer)`,
  `rollFor(ServerLevel, UUID, int waferIndex)`.
- `com.euvcraft.lithmod.mc.registry.ModItems.ITEMS` with `FOUP_MK1` and `FOUP_MK2`
  (`DeferredItem<Item>`).
- `com.euvcraft.lithmod.mc.registry.ModDataComponents.FOUP_CONTENTS`
  (`DeferredHolder<DataComponentType<?>, DataComponentType<FoupContents>>`).
- `com.euvcraft.lithmod.mc.registry.ModCreativeTabs.MAIN` (`Supplier<CreativeModeTab>`).
- `com.euvcraft.lithmod.mc.EUVCRAFTMod.config()` and `EUVCRAFTMod.settings()`.

Config keys: `features.debugCommands` (boolean, default false, restart to take effect).
The only other key this task reads is the existing `general.foupCapacity`.

Data file paths: none added. The component's stored shape is
`{lot: int[4], capacity: int, layers: int[n], rolls: int[n], flags: int[n]}` inside the
item's own NBT.

For M0-09: a lot's hidden roll for wafer `i` is
`FoupItems.contentsOf(stack).wafers().get(i).cumulativeRoll()`; the roll that produced
it is drawn from `FabRandomService.rngFor(level, lotId, i, stepIndex)`, which is the
same context this task's debug command uses. A wafer passes when its cumulative roll is
greater than `1 - effectiveYield`; the item stores the roll already multiplied in, so
the engine never has to re-derive it.