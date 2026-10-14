# M0-08: FOUP item with hidden per-wafer data (task review)

- Date: 2026-10-14
- Branch reviewed: `m0-08-foup-item`
- Commits reviewed: `fe0fb2d` (implementation), `a7246ed` (report); base `443be6c`
- Reviewed with Java on `PATH`: Temurin 21.0.12.1; the Gradle toolchain pinned in
  `build.gradle` (Java 25) provisions the compile and test JVM
- Build and test commands run for this review:
  - `.\gradlew.bat test --offline --console=plain --rerun-tasks`
  - `.\gradlew.bat checkCoreImports --offline --console=plain`
  - `.\gradlew.bat runGameTestServer --offline --console=plain`
- API verification: NeoForge `26.3.0.69-beta` sources and the patched Minecraft
  `26.3.0.69-beta` sources (both read from the local Gradle/ModDevGradle caches)

## 1. Summary

Verdict: **accept with fixes**. The reviewed design is sound: the persistent and
network codecs are genuinely different shapes, the hidden values cannot reach a
client through any vanilla packet path, and the core package is free of Minecraft
imports. All three commands pass; the unit test totals and the game test log lines
in `docs/reports/M0-08_foup-item.md` match my own runs exactly, so the report is
not padded. Three majors remain. Two are in the creative-mode spike: the exploit is
worse than the report describes (a client-replaced lot comes back with a cumulative
roll of `1.0f`, which is a guaranteed pass, not a destroyed value), and the spike
misses the server-side creative clone path, which duplicates a real lot without any
client involvement. The third major is an unmet Done-when criterion: the inventory /
chest / ground / relog walkthrough was not run, and the headless substitute that
could have closed most of it was not written. Everything else is minor. No blockers.

## 2. Files changed

None. This is a review; no source file was modified.

The only file added by this review is this document,
`docs/reports/M0-08_foup-item_review.md`. It is written into the report directory
because that is the only report location the project has, and it is left uncommitted
so that it does not land on the branch that carries the reviewed implementation.

## 3. Verification

```
> Task :test
BUILD SUCCESSFUL in 32s
6 actionable tasks: 6 executed
```

Unit test totals, aggregated by me from `build/test-results/test/*.xml` after the run:

| test class group | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| `core.FoupContentsTest` (+ 6 nested groups) | 31 | 0 | 0 | 0 |
| `core.FabSettingsTest` (+ 10 nested groups) | 43 | 0 | 0 | 0 |
| `core.TierRegistryTest` | 34 | 0 | 0 | 0 |
| `core.ContextualRngTest` | 14 | 0 | 0 | 0 |
| `core.StatModelVerificationTest` | 10 | 0 | 0 | 0 |
| `core.GenerationMatrixTest` | 8 | 0 | 0 | 0 |
| `core.DieRulesTest` (+ nested) | 8 | 0 | 0 | 0 |
| `core.CoreTest` | 2 | 0 | 0 | 0 |
| `mc.FoupContentsCodecTest` | 12 | 0 | 0 | 0 |
| `mc.FabRandomServiceTest` | 8 | 0 | 0 | 0 |
| `mc.FabConfigTest` | 3 | 0 | 0 | 0 |
| `mc.data.*` (7 classes) | 9 | 0 | 0 | 0 |
| **Total** | **182** | **0** | **0** | **0** |

This is identical to the table in `docs/reports/M0-08_foup-item.md`.

```
> Task :checkCoreImports
Core import check passed: no Minecraft/NeoForge imports found in core package.
BUILD SUCCESSFUL in 4s
```

```
> Task :runGameTestServer
[01:36:49] [Server thread/INFO] [minecraft/GameTestRunner]: Running test environment 'lithmod:foup_default' batch 0 (5 tests)...
[01:36:50] [co.eu.li.mc.EUVCRAFTMod/]: FOUP network form: 25 wafers in 3 bytes
[01:36:50] [co.eu.li.mc.EUVCRAFTMod/]: FOUP of 25 wafers: 456 bytes NBT, 3 bytes on the wire
[01:36:50] [co.eu.li.mc.EUVCRAFTMod/]: FOUP of 64 wafers: 924 bytes NBT, 3 bytes on the wire
[01:36:50] [co.eu.li.mc.EUVCRAFTMod/]: FOUP debug commands are disabled; set debugCommands=true in the config to test them
[01:38:24] [Server thread/INFO] [minecraft/GameTestServer]: ========= 678 GAME TESTS COMPLETE IN 1.679 min ======================
[01:38:24] [Server thread/INFO] [minecraft/GameTestServer]: All 678 required tests passed :)
BUILD SUCCESSFUL in 3m 55s
```

Scope checks run by me:

```
git diff --name-only 443be6c..HEAD -- src     -> only src/**/lithmod/**
git diff --name-only 443be6c..HEAD -- build.gradle gradle.properties settings.gradle libs -> (empty)
Select-String src/main/java/.../core/*.java '^\s*import\s+(net\.minecraft|net\.neoforged|com\.mojang|io\.netty)' -> (no matches)
```

Encoded Logistics and Arcforge are untouched, no new dependencies were added, and
the core package imports nothing from Minecraft, NeoForge, Mojang or Netty.

NeoForge and Minecraft API used by the task, checked against the local sources rather
than assumed:

| API | where verified |
|---|---|
| `DeferredRegister.createItems(String)` | `net/neoforged/neoforge/registries/DeferredRegister.java:145` |
| `Items.registerSimpleItem(String, UnaryOperator<Item.Properties>)` | same file, line 747 |
| `DeferredRegister.createDataComponents(...)` | same file, line 172 |
| `DataComponents.registerComponentType(String, UnaryOperator<Builder<D>>)` | same file, line 788 |
| `TooltipAppender.createComponentAppender(DataComponentType<T>, Getter<T>)` | `net/neoforged/neoforge/common/tooltip/TooltipAppender.java:26` |
| `DataComponentType.Builder.persistent` / `.networkSynchronized(StreamCodec<? super RegistryFriendlyByteBuf, T>)` | `net/minecraft/core/component/DataComponentType.java:55` and `:60` |
| `UUIDUtil.CODEC = Codec.INT_STREAM.xmap(...)` (confirms the int-array UUID claim) | `net/minecraft/core/UUIDUtil.java:23` |
| `FOUP_CONTENTS.get()` is safe inside `RegisterTooltipAppendersEvent` | `net/neoforged/neoforge/registries/GameData.java:113` calls `ItemTooltipHandler.init()` after registry loading |
| `ItemTooltipHandler` posts the event | `net/neoforged/neoforge/common/tooltip/ItemTooltipHandler.java:83` |

Every source line number cited by the reviewed report for the creative-mode spike
resolves to the code the report describes: `handleSetCreativeModeSlot` at
`ServerGamePacketListenerImpl.java:2128`, `handleContainerClick` at `:2042`,
`CreativeModeInventoryScreen.java` lines 241, 273 and 312.

## 4. Spec conformance

Findings are numbered and carry a severity. No blocker was found, so no fix is
required before the next task can start.

1. **[major] The creative-mode spike understates the exploit, and its recommended mitigation does not answer it.**
   File `src/main/java/com/euvcraft/lithmod/mc/FoupContentsCodec.java:70` and `:146`.
   I traced the accept path: `ServerboundSetCreativeModeSlotPacket.java:14` decodes
   with `ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC`, which uses
   `DataComponentPatch.DELIMITED_STREAM_CODEC` (`DataComponentPatch.java:54-58`), which
   calls `DataComponentType.streamCodec()`, that is, the task's own `STREAM_CODEC`. A
   creative client therefore cannot choose roll values, but the server rebuilds the
   lot with `CLIENT_VIEW_ROLL = Wafer.NO_ROLLS = 1.0f` on every wafer. The report calls
   this "destroy the hidden rolls"; the actual effect is the opposite: any creative
   inventory action on a FOUP rewrites it to a lot whose wafers pass every yield check
   at every tier, at an attacker-chosen capacity, count and layer. The report's remedy,
   "move the rolls into a `SavedData` keyed by lot id", is not presented as the answer
   to this case.
   Fix: state the `1.0f` placeholder and its consequence explicitly in the spike, and
   specify that the server must refuse to treat `FoupContentsCodec.isClientView(...)`
   as real data, for example by substituting a freshly drawn server lot or by refusing
   to process the lot at all.

2. **[major] The spike misses the server-side duplicate-`lotId` path.**
   File `docs/reports/M0-08_foup-item.md`, section 7 item 1. It cites only the client's
   `ContainerInput.CLONE` branch in `CreativeModeInventoryScreen`. The authoritative
   path is server-side: `AbstractContainerMenu.java:511-514` handles `CLONE` with
   `Slot.safeClone(player)`, and `Slot.java:156-159` is
   `item.copyWithCount(item.getMaxStackSize())`. Creative middle-click inside a
   container therefore duplicates the server's own stack, carrying the real hidden
   rolls and the same `lotId`, with no client involvement at all.
   Fix: add this path to the spike write-up and to the mitigation notes, since
   `lotId` is the key every later mitigation is likely to use.

3. **[major] The Done-when criterion "move it between inventory, a chest and the ground, relog, inspect identical rolls" is not met.**
   Files `docs/reports/M0-08_foup-item.md` section 4 (marked "not met") and section 5
   ("not run"). The disclosure is honest, so this is a scope gap rather than a
   misreport, but the criterion is open and the agent left it entirely open even where
   a headless substitute exists.
   Fix: extend `FoupGameTests.stackRoundTrip`
   (`src/main/java/com/euvcraft/lithmod/mc/gametest/FoupGameTests.java:122`) to move
   the stack through a chest `Container` via `ContainerHelper.saveAllItems` and
   `loadAllItems`, through a `DroppedItemEntity` pickup, and through
   `Player.getInventory().setItem`, comparing every roll at each hop. A full level
   save and reopen still cannot be automated cheaply and should be named as such.

4. **[minor] A gameplay-adjacent number is hard-coded.**
   File `src/main/java/com/euvcraft/lithmod/mc/FoupDebugCommand.java:45`,
   `private static final int MAX_WAFERS = 64;`. It duplicates `MAX_FOUP_CAPACITY` in
   `core/FabSettings.java` and the `1, 64` range in `mc/FabConfig.java:57`, against the
   project rule that nothing numeric is hard-coded. This is the only such literal I
   found in the new production code.
   Fix: use `IntegerArgumentType.integer(1, Integer.MAX_VALUE)`; the `requested >
   capacity` guard already at `FoupDebugCommand.java:96` enforces the real limit.

5. **[minor] Speculative and test-only public API in core.**
   `core/Wafer.java:82` `atLayer(int)` has no caller in main code or tests.
   `core/WaferPacking.java:194` `pack(List)`, `:114` `Packed.rollBits()`,
   `:131` `Packed.fromRollBits(...)` and `:169` `Packed.sameContents(...)` are used
   only by tests; the production codec calls `packLayers`, `packRollBits`, `packFlags`,
   `unpackRollBits` and `unpack` directly. The test
   `src/test/java/com/euvcraft/lithmod/core/FoupContentsTest.java:293`
   (`rollBitsAreLossless`) compares `rollBits()` against `Float.floatToRawIntBits`,
   which is near-tautological.
   Fix: trim to what the next task will actually call, and delete `atLayer` until it is
   needed.

6. **[minor] Redundant array copying on every decode.**
   File `core/WaferPacking.java:273-284`. `unpack` constructs a `Packed`, whose
   constructor clones the three arrays, and then reads them back through accessors that
   clone again, so six copies per decode for a 64-wafer lot.
   Fix: iterate the three arrays directly inside `unpack` instead of routing through
   `Packed`.

7. **[minor] An absolute claim that is not true.**
   File `mc/FoupContentsCodec.java:61-62`: the javadoc says the nil UUID is one that
   "a server-side `UUID#randomUUID()` can never draw". It is reachable at probability
   2^-122, so "cannot" is wrong. It matters because the mitigation in finding 1 should
   not depend on the value being impossible.
   Fix: reword to "practically never drawn", and make the server-side check rely on
   the lot having no server-side record rather than on the UUID value.

8. **[minor] User-facing text states a permission level that is not the effective one.**
   Files `mc/FabConfig.java:102` ("permission level 2") and `docs/testing.md`, section
   "Debug commands". The code and the game test are right: vanilla already owns
   `/debug` at `Commands.LEVEL_ADMINS` and the merged node keeps that requirement, so
   the effective gate is level 3. Only the prose is wrong.
   Fix: say "level 3 in practice (vanilla owns `/debug`)" in the config comment and in
   `docs/testing.md`.

9. **[minor] No item model.**
   Directory `src/main/resources/assets/lithmod/` contains only `lang/en_us.json`, so
   both FOUPs render with the missing-model placeholder in the creative tab. Milestone
   M0 has no textures, so this is not a spec violation, but a two-line
   `models/item/foup_mk1.json` and `models/item/foup_mk2.json` pointing at a vanilla
   texture would cost nothing and would unblock the manual walkthrough in finding 3.
   Fix: add the two model files, or record the decision in `docs/testing.md`.

10. **[minor] Report header omits a commit hash.**
    File `docs/reports/M0-08_foup-item.md` line 5: it gives `fe0fb2d` and then "the
    commit that adds this report" instead of `a7246ed`.
    Fix: write both hashes.

11. **[minor] A game-test failure path loses its message.**
    File `mc/gametest/FoupGameTests.java:361-372`. `run` catches `CommandSyntaxException`
    into a `helper_fail` helper that only logs, so the failure surfaces later as
    "returned 0" with no command text. The snake_case name also breaks local convention.
    Fix: thread the `GameTestHelper` into `run` and call `helper.fail` with the command
    and the exception.

12. **[minor] No test for "existing FOUPs keep the capacity stored in their data".**
    `mc/gametest/FoupGameTests.java:240` covers newly created tab lots only.
    Fix: add a test that builds a `FoupContents` with a capacity other than the
    configured one and shows no code path rewrites it from
    `EUVCRAFTMod.settings().foupCapacity()`.

Checklist results for the rules that are not per-bullet:

| rule | result | evidence |
|---|---|---|
| core package free of Minecraft and NeoForge | holds | `checkCoreImports` passed; an independent grep for `net.minecraft`, `net.neoforged`, `com.mojang`, `io.netty` in `core/*.java` found nothing |
| no gameplay numbers hard-coded | holds except finding 4 | single literal `MAX_WAFERS = 64` in the new production code |
| server-authoritative | holds | capacity, `lotId` and rolls are written only by `FoupItems.with`, `FoupDebugCommand.fill` and `FoupDebugCommand.fillContents`, all server-side; the only client-side writer is the creative tab, disclosed |
| Encoded Logistics and Arcforge untouched | holds | `git diff --name-only 443be6c..HEAD -- src` lists only `lithmod` paths |
| no unreported new dependencies | holds | `build.gradle`, `gradle.properties`, `settings.gradle` and `libs` unchanged in the branch diff |
| no alias classes, duplicate accessors or unused speculative code | fails in part | finding 5 |
| tests meaningful, covering boundaries | holds with one weak spot | capacity 1 and 3, packing at 0/1/25/64, roll 0, 1 and 1.0001, `Float.MIN_VALUE` and `0.9999999f`, hostile network input; the one weak spot is `rollBitsAreLossless`, see finding 5 |
| test expected numbers match REFERENCE DATA | holds | nothing in this task uses tier numbers; `FabSettings.DEFAULT_FOUP_CAPACITY` (25) and the `1..64` capacity range come from the M0-02 config defaults, not from inline literals |
| data that could be lost or duplicated | risk found | findings 1, 2 and 3 |
| hidden yield values that could reach a client | does not hold | the persistent and network codecs are separate and the network form is three varints; no vanilla path sends the persistent form to a client |

## 5. Not verified

- The dev client walkthrough was not run. No display is available to this session, so
  fill, move between inventory, chest and ground, relog and inspect were not
  performed, by me or, per its own report, by the agent. Not run.
- The tooltip was not seen rendered on a screen. Not run.
- The item in a creative inventory was not exercised against a real client
  connection. The creative findings above are derived from the 26.3 sources and from
  what the network codec provably sends, not from a live session. Not run.
- `runGameTestServer` with `features.debugCommands = true` was not repeated by me. The
  reviewed report shows both runs; I repeated only the default run. Not run.
- Whether a client-side `general.foupCapacity` different from the server's shows a
  different capacity on a creative tab entry was not tested. Not run.

## 6. Deviations

Deviations the reviewed report declared, and whether each reason holds up.

| spec says | what was done | reason holds up |
|---|---|---|
| `lotId` is a random UUID assigned server side at creation | `FoupItems.emptyFoup` draws `UUID.randomUUID()` on whichever side calls it, and the creative tab builds entries on the client too | yes. The creative tab has to produce a stack carrying a lot; a tab entry with no lot id would be worse, and the tab is rebuilt per session so no cross-side identity is relied on. The disclosure is explicit and the game test `foup_creative_tab_entries` pins both behaviours |
| debug commands at permission level 2 | the mod's own requirement is `LEVEL_GAMEMASTERS` on both literals, but `/debug foup ...` is effectively gated at level 3 | yes. Vanilla already owns `/debug` at `LEVEL_ADMINS` and brigadier keeps the existing node's requirement when two literals merge. I confirmed the merge semantics are asserted by `FoupGameTests.debugCommands` and that all 678 game tests pass. Level 3 is a subset of level 2, so no operator gains access. Only the prose is wrong, see finding 8 |
| the persistent codec stores the packed arrays | rolls are stored as raw IEEE-754 bit patterns in an integer array | yes. `float` has no DFU codec that maps to an NBT primitive array, raw bits are exact, and `FoupContentsCodecTest.persistentCodecKeepsExtremeRolls` plus the `Float.MIN_VALUE` and `0.9999999f` cases prove it |
| debug commands only registered when the key is true | the flag is read once at `RegisterCommandsEvent`, so toggling needs a restart | yes, and it is the only sane place to read it. Recorded as open question 3 |
| the M0-02 config is consumed as it stands | the two `ModConfigEvent` listeners that `FabConfig.onConfigLoad` and `onConfigReload` were written for are now connected in `EUVCRAFTMod` | yes. Without them `FabSettings` never left its defaults, so `foupCapacity` and `debugCommands` had no effect and the `foup_debug_commands` game test reported "disabled" even with the key set to true. This is a real fix to a defect left by M0-02, disclosed in the commit message and in report section 6 |

Deviations I raised that the report did not declare: findings 1, 2 and 8.

## 7. Open questions

1. Should the server treat a `FoupContentsCodec.isClientView(...)` lot as no lot at
   all (substitute a fresh server lot), or as a corrupt lot (refuse to process it and
   log)? This decides whether the creative exploit is a data-loss bug or a
   guaranteed-pass exploit, and it should be settled before the yield engine treats any
   component value as authoritative.
2. Is `lotId` the right identity for a lot? The spike assumes it is. Creative clone
   duplicates it, and the report's own recommendation for the spike is a `SavedData`
   keyed by it. If a lot id can be duplicated, a saved-data design needs an explicit
   duplicate rule.
3. Does `withoutWaferAt` throwing on an out-of-range index suit the first machine that
   pops "the last processed wafer", or should it be a no-op? Raised by the agent as
   open question 2 and not resolved here.
4. Should `features.debugCommands` be re-read on reload so a config reload takes effect
   without a restart? Acceptable as is for a developer key; the game test asserts the
   current behaviour, so changing it means changing the test.

## 8. Handoff

Public API reviewed and relied on by later tasks, unchanged by this review:

- `com.euvcraft.lithmod.core.Wafer` — `record Wafer(int layerIndex, float cumulativeRoll, int flags)`,
  `UNPROCESSED_LAYER` (0), `NO_FLAGS` (0), `NO_ROLLS` (1.0f), `unprocessed()`,
  `isUnprocessed()`, `atLayer(int)` (unused, see finding 5). Rejects a negative layer and
  a roll outside `(0, 1]`.
- `com.euvcraft.lithmod.core.FoupPublicView` — `record FoupPublicView(int capacity, int waferCount, int lotLayer)`,
  `EMPTY_LOT_LAYER` (0), `empty(int)`, `isEmpty()`, `isFull()`.
- `com.euvcraft.lithmod.core.FoupContents` — `record FoupContents(UUID lotId, int capacity, List<Wafer> wafers)`,
  `empty(UUID, int)`, `size()`, `isEmpty()`, `isFull()`, `withWafer(Wafer)`,
  `withWafers(List<Wafer>)`, `withoutWaferAt(int)`, `lotLayer()`, `publicView()`.
- `com.euvcraft.lithmod.core.WaferPacking` — `pack`, `packLayers`, `packRollBits`,
  `packFlags`, `unpack`, `unpackRollBits`, `record Packed(int[], float[], int[])`.
- `com.euvcraft.lithmod.mc.FoupContentsCodec` — `CODEC`, `STREAM_CODEC`,
  `CLIENT_VIEW_LOT_ID`, `CLIENT_VIEW_ROLL`, `isClientView(FoupContents)`.
- `com.euvcraft.lithmod.mc.FoupItems` — `configuredCapacity()`, `emptyFoup(Item)`,
  `with(Item, FoupContents)`, `contentsOf(ItemStack)` (nullable), `isFoup(ItemStack)`.
- `com.euvcraft.lithmod.mc.FoupDebugCommand` — `ROOT` ("debug"), `register`,
  `fillContents(ServerLevel, UUID, int, int, int)`, `rollFor(ServerLevel, UUID, int)`.
- `com.euvcraft.lithmod.mc.registry.ModItems.ITEMS` with `FOUP_MK1` and `FOUP_MK2`.
- `com.euvcraft.lithmod.mc.registry.ModDataComponents.FOUP_CONTENTS`
  (`lithmod:foup_contents`).
- `com.euvcraft.lithmod.mc.registry.ModCreativeTabs.MAIN`.
- `com.euvcraft.lithmod.mc.EUVCRAFTMod.config()` and `EUVCRAFTMod.settings()`.

Config keys in play: `general.foupCapacity` (from M0-02) and
`features.debugCommands` (added by this task, default false, read once at command
registration).

Things the next task must know:

- M0-09 must not trust `FoupItems.contentsOf(stack)` unconditionally. Gate on
  `!FoupContentsCodec.isClientView(contents)` before using any component value, for the
  reasons in finding 1.
- The item stores the cumulative roll already multiplied in. A wafer passes when
  `cumulativeRoll > 1 - effectiveYield`; a new step roll is drawn from
  `FabRandomService.rngFor(Level level, UUID lotId, int waferIndex, int stepIndex)`, the
  same context the debug command uses.
- `lotId` is the natural key for anything that has to survive outside the item
  (saved data, duplicate detection, later Encoded Logistics transit), and creative
  duplication of `lotId` is a known, unresolved hazard.
- Config wiring now works: `ModConfigEvent.Loading` and `ModConfigEvent.Reloading` are
  connected in `EUVCRAFTMod`, so `FabSettings` reflects the TOML. Previous milestone
  reports that observed defaults only were describing unwired behaviour.
- Wafer index is stable across the lot's lifetime and is part of the roll context, so
  removing a wafer renumbers the ones after it. Any later change to
  `withoutWaferAt` semantics must account for that.