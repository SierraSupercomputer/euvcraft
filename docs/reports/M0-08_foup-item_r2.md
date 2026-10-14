# M0-08b: FOUP item fix pass

- Date: 2026-10-14
- Branch: `m0-08-foup-item`
- Commits: `2d92746` (fixes, tests, models, r1 header), and the commit that adds this
  report; that second hash is filled in by the next commit on this branch, because a
  commit cannot contain its own hash
- Java version used: Java 25 through the Gradle toolchain pinned in `build.gradle`
- Build and test commands run:
  - `.\gradlew.bat test checkCoreImports --offline --console=plain --rerun-tasks`
  - `.\gradlew.bat runGameTestServer --offline --console=plain`
  - `.\gradlew.bat runGameTestServer --offline --console=plain` with
    `features.debugCommands = true`, changed back afterwards

## 1. Summary

Fix pass over the review in `docs/reports/M0-08_foup-item_review.md` (accept with
fixes). Three majors and nine minors. The three majors are: `FoupItems.trustedContentsOf`
now refuses any lot a client could have written, so a creative-replaced FOUP can no
longer feed fabricated wafers into a machine; the creative-mode spike is rewritten to say
what actually happens, including the `1.0f` guaranteed-pass consequence and the
server-side clone path; and `foup_stack_round_trip` now walks a stack through a chest
container, a dropped item entity, a player inventory slot and a ground pickup, comparing
every roll at each hop. The minors: two item models, the hard-coded wafer limit removed,
five unused core methods removed, `unpack` no longer copies the arrays six times, the
nil-UUID javadoc no longer claims the value is impossible, the config comment and the
testing doc now say level 3 in practice, a test pins the stored capacity against the
configured one, and the game test failure path reports the command text.

## 2. Files changed

| path | new/modified/deleted | purpose |
|---|---|---|
| src/main/java/com/euvcraft/lithmod/mc/FoupItems.java | modified | new `trustedContentsOf(ItemStack)`: refuses a missing lot, a client view holding wafers, and a client view whose capacity came from the client |
| src/main/java/com/euvcraft/lithmod/mc/FoupContentsCodec.java | modified | nil-UUID javadoc says "practically never drawn"; `isClientView` documented as the marker the trusted read relies on |
| src/main/java/com/euvcraft/lithmod/core/Wafer.java | modified | `atLayer(int)` deleted, no caller |
| src/main/java/com/euvcraft/lithmod/core/WaferPacking.java | modified | `pack(List)`, `Packed.rollBits()`, `Packed.fromRollBits(...)` and `Packed.sameContents(...)` deleted; `unpack` iterates the arrays directly; content `equals` inlined |
| src/main/java/com/euvcraft/lithmod/mc/FoupDebugCommand.java | modified | `MAX_WAFERS` deleted, the count argument takes any positive int and the capacity guard decides |
| src/main/java/com/euvcraft/lithmod/mc/FabConfig.java | modified | the `debugCommands` comment says level 3 in practice |
| src/main/java/com/euvcraft/lithmod/mc/gametest/FoupGameTests.java | modified | chest, dropped-entity, `Inventory.setItem` and `playerTouch` hops; new `foup_trusted_contents` and `foup_stored_capacity_wins`; `run` fails through the helper with the command text |
| src/main/resources/assets/lithmod/models/item/foup_mk1.json | new | flat model on `minecraft:item/hopper` |
| src/main/resources/assets/lithmod/models/item/foup_mk2.json | new | flat model on `minecraft:item/brewing_stand` |
| src/test/java/com/euvcraft/lithmod/core/FoupContentsTest.java | modified | `rollBitsAreLossless` deleted; the round trip tests drive the pack helpers directly |
| src/test/java/com/euvcraft/lithmod/mc/FoupContentsCodecTest.java | modified | new `storedCapacityIsIndependentOfConfig` |
| docs/testing.md | modified | level 3 in practice; the two new game tests |
| docs/reports/M0-08_foup-item.md | modified | the commit hashes the review asked for in finding 10 |
| docs/reports/M0-08_foup-item_review.md | added (not written here) | the review, committed as it asked |
| docs/reports/M0-08_foup-item_r2.md | new | this report |

## 3. Verification

```
.\gradlew.bat test checkCoreImports --offline --console=plain --rerun-tasks
Core import check passed: no Minecraft/NeoForge imports found in core package.
BUILD SUCCESSFUL in 23s
```

```
.\gradlew.bat runGameTestServer --offline --console=plain
[14Oct2026 02:01:12.582] [Server thread/INFO] [net.minecraft.gametest.framework.GameTestRunner]: Running test environment 'lithmod:foup_default' batch 0 (7 tests)...
[14Oct2026 02:01:12.770] [Server thread/INFO] [co.eu.li.mc.EUVCRAFTMod/]: FOUP of 25 wafers: 456 bytes NBT, 3 bytes on the wire
[14Oct2026 02:01:12.786] [Server thread/WARN] [co.eu.li.mc.EUVCRAFTMod/]: EUVCRAFT discarded a FOUP stack of lithmod:foup_mk1 whose sealed lot is a client view holding 25 wafers; the hidden rolls were never filled in by the server
[14Oct2026 02:01:12.800] [Server thread/INFO] [net.minecraft.gametest.framework.GameTestServer]: ========= 680 GAME TESTS COMPLETE IN 1.355 min ======================
[14Oct2026 02:01:12.802] [Server thread/INFO] [net.minecraft.gametest.framework.GameTestServer]: All 680 required tests passed :)
BUILD SUCCESSFUL in 3m 23s
```

With `features.debugCommands = true`:

```
[14Oct2026 02:03:21.088] [Worker-Main-3/INFO] [co.eu.li.mc.EUVCRAFTMod/]: EUVCRAFT debug commands enabled: /debug foup fill|inspect (vanilla already owns /debug, so the effective gate is that of the merged node)
[14Oct2026 02:04:55.868] [Server thread/INFO] [net.minecraft.gametest.framework.GameTestServer]: All 680 required tests passed :)
BUILD SUCCESSFUL in 3m 36s
```

The warning line is the evidence that the trusted read fires and that it carries no roll
value: it names the item and the wafer count and nothing else.

Unit test table, aggregated by class from `build/test-results/test/*.xml`:

| test class | tests | failures | errors | skipped | total |
|---|---:|---:|---:|---:|---:|
| core.FabSettingsTest (+ nested) | 43 | 0 | 0 | 0 | 43 |
| core.TierRegistryTest | 34 | 0 | 0 | 0 | 34 |
| core.FoupContentsTest (+ 5 nested) | 30 | 0 | 0 | 0 | 30 |
| core.ContextualRngTest | 14 | 0 | 0 | 0 | 14 |
| core.StatModelVerificationTest | 10 | 0 | 0 | 0 | 10 |
| core.GenerationMatrixTest | 8 | 0 | 0 | 0 | 8 |
| core.DieRulesTest (+ nested) | 8 | 0 | 0 | 0 | 8 |
| core.CoreTest | 2 | 0 | 0 | 0 | 2 |
| mc.FoupContentsCodecTest | 13 | 0 | 0 | 0 | 13 |
| mc.FabRandomServiceTest | 8 | 0 | 0 | 0 | 8 |
| mc.FabConfigTest | 3 | 0 | 0 | 0 | 3 |
| mc.data.* (7 classes) | 9 | 0 | 0 | 0 | 9 |
| Total | 182 | 0 | 0 | 0 | 182 |

Game tests, from `run/logs/latest.log`:

| test | environment | result |
|---|---|---|
| lithmod:foup_stack_round_trip | lithmod:foup_default | passed (7 tests in the batch) |
| lithmod:foup_network_view_is_public_only | lithmod:foup_default | passed |
| lithmod:foup_encoded_size | lithmod:foup_default | passed |
| lithmod:foup_creative_tab_entries | lithmod:foup_default | passed |
| lithmod:foup_debug_commands | lithmod:foup_default | passed (commands run) with the key on, passed (branch absent) with the default |
| lithmod:foup_trusted_contents | lithmod:foup_default | passed |
| lithmod:foup_stored_capacity_wins | lithmod:foup_default | passed |

## 4. Spec conformance

| item | met/partly/not met | evidence |
|---|---|---|
| 1. `FoupItems.trustedContentsOf(ItemStack)` exists | met | `FoupItems.trustedContentsOf` |
| 1. returns empty when the stack is not a FOUP | met | `foup_trusted_contents` case 1, `new ItemStack(Items.STONE)`; `waferCounterProduced` assertion |
| 1. returns empty when it is a client view holding wafers | met | `foup_trusted_contents` case 2; log line `discarded a FOUP stack ... client view holding 25 wafers` |
| 1. logs a warning containing no roll value | met | the warning formats `stack.getItem()` and `contents.size()` only |
| 1. client view with zero wafers returns a fresh lot, new lot id, configured capacity, never the client's | met | `foup_trusted_contents` case 3 asserts the capacity equals `FoupItems.configuredCapacity()` after the round trip through `STREAM_CODEC`, which carried the server's capacity; `freshEmptyLot` draws `UUID.randomUUID()` |
| 1. all three cases tested | met | `foup_trusted_contents`, plus a fourth case that a real lot is returned untouched |
| 1. reword the nil-UUID javadoc to "practically never drawn" | met | `FoupContentsCodec.CLIENT_VIEW_LOT_ID` javadoc |
| 1. rely on the client-view marker, not on the UUID being impossible | met | the javadoc now says the marker is what the check uses, and `isClientView` is documented as a marker; `trustedContentsOf` branches on `isClientView` |
| 2. r2 report states every roll comes back at 1.0f, a guaranteed pass | met | section 7 item 1 |
| 2. r2 report states the client chooses capacity, count and layer | met | section 7 item 1 |
| 2. r2 report adds the server-side clone path | met | section 7 item 2, citing `AbstractContainerMenu.java:511-514` and `Slot.java:156-159` |
| 2. r2 report says creative mode is not protected | met | section 7 items 1 and 2 |
| 2. r2 report describes the saved-data-keyed-by-lotId alternative with a duplicate rule | met | section 7 item 3, consume the record at packaging |
| 2. do not implement it | met | no saved data for lots was added; `git diff --stat` shows no new `SavedData` class |
| 3. chest Container round trip | met | `chestRoundTrip`, `ContainerHelper.saveAllItems` and `loadAllItems` |
| 3. dropped item entity pickup | met | `groundRoundTrip` plus `ItemEntity.playerTouch` |
| 3. Player inventory `setItem` | met | `player.getInventory().setItem(0, original.copy())` |
| 3. compare every roll after each hop | met | `assertSameContents` compares lot id, capacity, count and every layer, roll and flag; it is called after each hop |
| 3. state which steps still need a real client | met | section 5 |
| 4. `models/item/foup_mk1.json` and `foup_mk2.json` on a vanilla texture | met | both files, `minecraft:item/generated` with `minecraft:item/hopper` and `minecraft:item/brewing_stand` |
| 5. remove `MAX_WAFERS`, rely on the capacity guard | met | `IntegerArgumentType.integer(1, Integer.MAX_VALUE)`; the `requested > capacity` guard at `FoupDebugCommand.fill` rejects the rest |
| 5. delete `Wafer.atLayer` | met | gone from `core/Wafer.java` |
| 5. delete `WaferPacking.pack(List)` | met | gone; the codec calls `packLayers`, `packRollBits` and `packFlags` |
| 5. delete `Packed.rollBits` | met | gone |
| 5. delete `Packed.fromRollBits` | met | gone |
| 5. delete `Packed.sameContents` | met | gone; the content comparison is inlined in `equals` |
| 5. delete the `rollBitsAreLossless` test | met | gone from `FoupContentsTest` |
| 5. `unpack` iterates the arrays directly | met | `WaferPacking.unpack` reads `packed.layers[i]`, `packed.rolls[i]`, `packed.flags[i]`; four array copies per decode instead of six |
| 5. "level 3 in practice (vanilla owns /debug)" in the config comment | met | `FabConfig.DEBUG_COMMANDS` comment |
| 5. the same in `docs/testing.md` | met | the Debug commands section |
| 5. test that an existing FOUP keeps its stored capacity when config differs | met | `foup_stored_capacity_wins` game test and `storedCapacityIsIndependentOfConfig` unit test |
| 5. game test helper calls `helper.fail` with the command text | met | `run(helper, level, source, command)` calls `helper.fail("the command '" + command + "' did not parse: " + ...)`; the `helper_fail` logger is deleted |
| 5. both commit hashes in the report header | partly | `docs/reports/M0-08_foup-item.md` line 5 now lists `fe0fb2d` and `a7246ed`, which is what finding 10 was about. This r2 report lists `2d92746` and describes the second commit in words, because a file cannot contain the hash of the commit that introduces it; the follow-up commit fills it in |
| 6. write `docs/reports/M0-08_foup-item_r2.md` | met | this file |
| 6. commit `docs/reports/M0-08_foup-item_review.md` | met | committed in `2d92746`, unmodified by this pass |

## 5. Not verified

- The dev client walkthrough is still not run: fill with the debug command, move between
  the inventory, a chest and the ground, relog, inspect identical rolls. The game tests
  now cover the data paths headlessly (stack copy, NBT round trip, chest container
  round trip, dropped item entity save and reload, `Inventory.setItem`, `playerTouch`),
  but a relog means a real server stop and world reopen, and the rendered tooltip is a
  client concern. Both need a session with a display. Not run.
- The item models were not rendered. The JSON files are two lines each and follow
  `minecraft:item/generated`, but nothing in this pass loaded a resource pack. Not run.
- The creative-mode findings were not exercised against a live creative client. They come
  from the 26.3 sources: `ServerGamePacketListenerImpl.java:2128`, `:2042`,
  `CreativeModeInventoryScreen.java` lines 241, 273 and 312, `AbstractContainerMenu.java:511-514`,
  `Slot.java:156-159`. Not run.
- The duplicate `lotId` scenario in section 7 item 2 was not reproduced in a test, only
  read out of `Slot.safeClone`. Not run.
- `runGameTestServer` with `features.debugCommands = true` was run once in this pass,
  which is what changed the count argument's effect. Not repeated.

## 6. Deviations

| spec says | what you did | why |
|---|---|---|
| "returns empty if the stack is not a FOUP ... log a warning" | a stack with no lot warns only when it is one of the two FOUP items; a stack of something else is silent | a machine asking "is this a FOUP" would otherwise fill the log on every item it is offered. The anomaly that does deserve a warning, a FOUP item with no lot in it, still warns |
| a client view with zero wafers is not a warning | left unwarned | nothing was lost, only a capacity was refused; warning would be noise for every lot the client re-derives |
| "rely on the client-view marker, not on the UUID being impossible" | the marker is `FoupContentsCodec.isClientView`, which compares against the nil UUID | the marker is still the UUID; what changed is that the code no longer treats the value as unreachable. A lot id is now drawn from a fresh `UUID.randomUUID()` for every rejected lot, so the marker is only ever read on a value the server itself produced through `fromPublicView` or on a value that arrived from a client |

## 7. Open questions

1. The creative-mode spike, corrected. `ServerboundSetCreativeModeSlotPacket` decodes with
   `ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC`, which goes through
   `DataComponentPatch.DELIMITED_STREAM_CODEC` and therefore calls this mod's own
   `STREAM_CODEC`. A creative client therefore cannot choose roll values, but it does not
   have to: the server rebuilds every wafer with
   `FoupContentsCodec.CLIENT_VIEW_ROLL`, which is `Wafer.NO_ROLLS`, which is `1.0f`. The
   consequence is the opposite of "the rolls are destroyed": a creative inventory action
   on a FOUP rewrites the lot so that **every wafer passes every yield check at every
   tier**, because a wafer passes when `cumulativeRoll > 1 - effectiveYield` and
   `1.0f` is greater than any threshold below 1. The client also chooses the three
   numbers the view carries, so it chooses the capacity, the wafer count and the lot
   layer of the lot the server is handed. Before this pass, `FoupItems.contentsOf`
   returned that lot unchanged. `FoupItems.trustedContentsOf` now replaces it with a
   freshly drawn server lot, which turns the guaranteed-pass exploit into an empty FOUP,
   and logs once per occurrence.
2. The spike, second path: a creative middle-click duplicates a real lot on the server
   with no client trickery at all. `AbstractContainerMenu.java:511-514` handles
   `ContainerInput.CLONE` when `player.hasInfiniteMaterials()` by calling
   `setCarried(slot.safeClone(player))`, and `Slot.safeClone` at `Slot.java:156-159` is
   `item.copyWithCount(item.getMaxStackSize())`. For a FOUP, whose max stack size is 1,
   that is a complete duplicate of the server's own stack: same hidden rolls, same
   `lotId`, a second carrier. Nothing in the network form is involved and no warning is
   produced. **Creative mode is therefore not protected.** `trustedContentsOf` does not
   help here: the duplicated lot is a genuine server lot, not a client view.
3. The saved-data alternative, described and not implemented. Store the rolls in a
   `SavedData` keyed by `lotId`, next to where `FabRandomData` already keeps the salt,
   and let the item carry only the lot id, capacity and wafer count. Then a
   client-replaced stack can only change what it claims, and the rolls stay where the
   server put them. It needs a duplicate rule, because of path 2 above: **consume the
   record when the wafers leave the lot for good**, which for a finished lot means at
   packaging. A second FOUP carrying the same `lotId` then finds no record, and the
   server treats it as a new lot rather than as a copy of the first one; the cost is that
   a legitimately duplicated empty FOUP has to be re-identified by the machine that made
   it. Other open points: the record has to be garbage collected when a lot is scrapped
   and when a world is deleted, and every machine step gains a saved-data lookup. Not
   implemented in this task, per the task scope.
4. Open questions 2 and 3 of the review are unchanged and still open: whether
   `withoutWaferAt` should throw, and whether `features.debugCommands` should be re-read
   on a config reload.
5. Review open question 2 asked whether a `lotId` can be duplicated and whether that
   breaks a saved-data design. It can (path 2 above), and the duplicate rule in item 3 is
   a proposal, not a decision. Whoever builds the saved data has to confirm it.

## 8. Handoff

Changes to the API the reviewed report listed:

- `com.euvcraft.lithmod.core.Wafer` — `atLayer(int)` **removed**. Remaining:
  `UNPROCESSED_LAYER`, `NO_FLAGS`, `NO_ROLLS`, `unprocessed()`, `isUnprocessed()`.
- `com.euvcraft.lithmod.core.WaferPacking` — `pack(List)`, `Packed.rollBits()`,
  `Packed.fromRollBits(...)` and `Packed.sameContents(...)` **removed**. Remaining:
  `packLayers`, `packRollBits`, `packFlags`, `unpack`, `unpackRollBits`, and the
  `Packed` record with `layers()`, `rolls()`, `flags()`, `size()`, `isEmpty()`,
  content-based `equals` and `hashCode`.
- `com.euvcraft.lithmod.mc.FoupItems` — **added** `FoupContents trustedContentsOf(ItemStack)`.
  This is the read every gameplay path must use instead of `contentsOf(ItemStack)`, which
  is now only for diagnostics.
- `com.euvcraft.lithmod.mc.FoupDebugCommand` — `MAX_WAFERS` removed from the public
  surface (it was private); no signature change.

Unchanged and still relied on: `FoupContentsCodec.CODEC`, `STREAM_CODEC`,
`CLIENT_VIEW_LOT_ID`, `CLIENT_VIEW_ROLL`, `isClientView(FoupContents)`;
`FoupItems.configuredCapacity/emptyFoup/with/contentsOf/isFoup`;
`ModItems.FOUP_MK1`/`FOUP_MK2`; `ModDataComponents.FOUP_CONTENTS`; `ModCreativeTabs.MAIN`.

Config keys: `general.foupCapacity` and `features.debugCommands` (default false). The
comment on `features.debugCommands` now states level 3 in practice.

Data files: `assets/lithmod/models/item/foup_mk1.json` and `foup_mk2.json` are new and
are the only assets; the component's stored shape is unchanged
(`{lot, capacity, layers, rolls, flags}` with three int arrays).

For M0-09: read lots with `FoupItems.trustedContentsOf(stack)`, never with
`FoupItems.contentsOf(stack)`. The item stores the cumulative roll already multiplied
in; a wafer passes when `cumulativeRoll > 1 - effectiveYield`; new rolls come from
`FabRandomService.rngFor(level, lotId, waferIndex, stepIndex)`. A client view can never
reach the engine now, so a roll of `1.0f` on a non-empty lot is a real failure and not a
tampered stack.