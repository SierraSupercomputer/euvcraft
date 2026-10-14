# M0-07: Server-side randomness and state

- Date: 2026-10-13
- Branch: `m0-07-server-randomness` (git worktree at `.local/share/opencode/worktree/4b213d/m0-07-server-randomness`)
- Base commit: `826eaaa` (M0-05b)
- Work commit: `9efbf82`, report committed as `M0-07 report`
- Java version used: Java 25 via the Gradle toolchain pinned in `build.gradle`
- Build and test commands run:
  - `.\gradlew.bat test --offline --console=plain`
  - `.\gradlew.bat checkCoreImports --offline --console=plain --rerun-tasks`
  - `.\gradlew.bat runGameTestServer --offline --console=plain` (run twice)

## 1. Summary

Added the randomness foundation of milestone M0-07. The core package gained a
one-method `Rng` contract, a SplitMix64 generator, and `ContextualRng`, which derives
its stream from the tuple `(salt, lotId, waferIndex, stepIndex)` and maps every draw
into the half-open-safe range `(0, 1]`. The Minecraft-facing package gained
`FabRandomData`, a world-scoped `SavedData` holding one 64-bit salt drawn from
`SecureRandom` on first use, and `FabRandomService`, the single server-side entry
point that hands out `ContextualRng` instances and refuses to run on a client level.
Fourteen JUnit tests cover determinism, context separation, the range contract, the
mean and the absence of correlation; seven more cover the guard and the saved-data
codec; two GameTests cover the on-disk save/load round trip and the client-side
rejection. All unit tests, the core import check and the game test server pass.

## 2. Files changed

| path | new/modified/deleted | purpose |
|---|---|---|
| src/main/java/com/euvcraft/lithmod/core/Rng.java | new | One-method random source contract, `double nextDouble()` documented as uniform over `(0, 1]` |
| src/main/java/com/euvcraft/lithmod/core/SplitMix64.java | new | SplitMix64 generator, `nextLong()`, `nextDouble()`, static `mix64` finalizer |
| src/main/java/com/euvcraft/lithmod/core/ContextualRng.java | new | Context-bound generator built from `(salt, lotId, waferIndex, stepIndex)`; carries the determinism rationale in its javadoc |
| src/main/java/com/euvcraft/lithmod/mc/FabRandomData.java | new | `SavedData` holding the world salt, its `Codec`, its `SavedDataType`, and `rngFor` |
| src/main/java/com/euvcraft/lithmod/mc/FabRandomService.java | new | Server-only accessor for the salt plus the `requireServerSide` guard |
| src/main/java/com/euvcraft/lithmod/mc/gametest/FabRandomGameTests.java | new | Two GameTests: `lithmod:fab_random_salt_round_trip`, `lithmod:fab_random_client_side_rejected` |
| src/main/java/com/euvcraft/lithmod/mc/EUVCRAFTMod.java | modified | Registers `FabRandomGameTests` on the mod event bus (one added line) |
| src/test/java/com/euvcraft/lithmod/core/ContextualRngTest.java | new | 14 core tests for determinism, separation, range, mean, correlation |
| src/test/java/com/euvcraft/lithmod/mc/FabRandomServiceTest.java | new | 7 unit tests for the guard and the saved-data codec |
| docs/testing.md | modified | Documents the two new GameTests and the on-disk salt location |
| docs/reports/M0-07_server-side-randomness.md | new | This report |

## 3. Verification

Commands and the relevant output lines:

```
.\gradlew.bat test --offline --console=plain
ContextualRngTest > adjacent contexts show no obvious correlation PASSED   (see test table below)
BUILD SUCCESSFUL in 13s

.\gradlew.bat checkCoreImports --offline --console=plain --rerun-tasks
Core import check passed: no Minecraft/NeoForge imports found in core package.
BUILD SUCCESSFUL in 13s

.\gradlew.bat runGameTestServer --offline --console=plain
[13Oct2026 22:39:41.354] GameTestRunner: Running test environment 'lithmod:fab_random_default' batch 0 (2 tests)...
[13Oct2026 22:39:43.005] com.euvcraft.lithmod.mc.EUVCRAFTMod: Fab random salt stored at
    gametestserver\gametestworld\.\dimensions\minecraft\overworld\data\lithmod\fab_random.dat
[13Oct2026 22:41:28.990] GameTestServer: ========= 673 GAME TESTS COMPLETE IN 1.822 min ======================
[13Oct2026 22:41:28.991] GameTestServer: All 673 required tests passed :)
BUILD SUCCESSFUL in 4m 58s
```

A baseline `runGameTestServer` on the unmodified base commit reported
`All 671 required tests passed :)`. The new run reports 673, i.e. the two tests added
here are part of the required set and passed. A second `runGameTestServer` after the
work commit repeated the result:

```
[13Oct2026 22:48:02.907] GameTestServer: ========= 673 GAME TESTS COMPLETE IN 1.737 min ======================
[13Oct2026 22:48:02.909] GameTestServer: All 673 required tests passed :)
BUILD SUCCESSFUL in 4m 5s
```

Unit test table, read from `build/test-results/test/*.xml`:

| test class | tests | failures | errors | skipped | total |
|---|---:|---:|---:|---:|---:|
| ContextualRngTest | 14 | 0 | 0 | 0 | 14 |
| FabRandomServiceTest | 7 | 0 | 0 | 0 | 7 |
| CoreTest | 2 | 0 | 0 | 0 | 2 |
| DieRulesTest (+ nested Validation) | 8 | 0 | 0 | 0 | 8 |
| FabSettingsTest suites | 43 | 0 | 0 | 0 | 43 |
| GenerationMatrixTest | 8 | 0 | 0 | 0 | 8 |
| StatModelVerificationTest | 10 | 0 | 0 | 0 | 10 |
| TierRegistryTest | 34 | 0 | 0 | 0 | 34 |
| DieRuleDataLoaderFallbackTest | 1 | 0 | 0 | 0 | 1 |
| DieRuleJsonDataTest | 1 | 0 | 0 | 0 | 1 |
| JsonResourceDataLoaderReloadTest | 1 | 0 | 0 | 0 | 1 |
| SupportLineDataLoaderFallbackTest | 1 | 0 | 0 | 0 | 1 |
| SupportLineJsonDataTest | 1 | 0 | 0 | 0 | 1 |
| TierDataLoaderFallbackTest | 2 | 0 | 0 | 0 | 2 |
| TierJsonDataTest | 1 | 0 | 0 | 0 | 1 |
| FabConfigTest | 1 | 0 | 0 | 0 | 1 |
| Total | 135 | 0 | 0 | 0 | 135 |

Game test table, from `run/logs/latest.log`:

| test | environment | result |
|---|---|---|
| lithmod:trivial | lithmod:default | passed (pre-existing, base run) |
| lithmod:fab_random_salt_round_trip | lithmod:fab_random_default | passed (this run) |
| lithmod:fab_random_client_side_rejected | lithmod:fab_random_default | passed (this run) |

## 4. Spec conformance

| item | met/partly/not met | evidence |
|---|---|---|
| interface `Rng` with `nextDouble()` uniform in `(0, 1]`, never exactly 0 | met | `core/Rng.java`; `ContextualRngTest.valuesStayInRange` (100000 samples, all `> 0` and `<= 1`) |
| `ContextualRng` created from `(salt: long, lotId: UUID, waferIndex: int, stepIndex: int)` | met | `core/ContextualRng.java` constructor; `ContextualRngTest.sameInputsGiveSameSequence` asserts the four fields round-trip |
| uses a well-known 64-bit mixing generator (SplitMix64) | met | `core/SplitMix64.java`; `ContextualRngTest.mix64SpreadsNeighbouringValues` |
| same inputs always produce the same sequence | met | `ContextualRngTest.sameInputsGiveSameSequence`, `sameInputsGiveSameRawStream` |
| different inputs produce unrelated sequences | met | `ContextualRngTest.differentLotGivesDifferentSequence`, `differentWaferIndexGivesDifferentSequence`, `differentStepIndexGivesDifferentSequence`, `differentSaltGivesDifferentSequence`, `seedDependsOnEveryComponent` |
| `FabRandomService` on the server, backed by world-scoped saved data | met | `mc/FabRandomService.java`; `mc/FabRandomData.java extends SavedData` |
| stores one random 64-bit salt, created on first use and persisted | met | `FabRandomData.fresh()` via `computeIfAbsent`; `SavedDataStorage.scheduleSave`/`saveAndJoin`; GameTest log line naming the written file |
| uses the overworld's data storage so every dimension shares the one salt | met | `FabRandomService.storage(Level)` returns `requireServerSide(level).getServer().overworld().getDataStorage()`; GameTest asserts `FabRandomService.salt(nether) == salt` |
| `rngFor(serverLevel, lotId, waferIndex, stepIndex)` returns a `ContextualRng` | met | `FabRandomService.rngFor(Level, UUID, int, int)` |
| `requireServerSide(level)` throws a clear exception on a client level, called from `rngFor` | partly | `FabRandomService.requireServerSide(Level)` throws `IllegalStateException` with a message naming the rule and the level; `rngFor` calls `data(level)` which calls `storage(level)` which calls it. Not exercised with a real `ClientLevel` instance, see section 5 |
| salt must not be derived from the world seed or from anything a client receives | met | `FabRandomData.randomSalt()` uses a private `SecureRandom`; the class javadoc states the exclusion; nothing in the construction path reads world seed, session id or time |
| rationale kept in a code comment (deterministic per context, unpredictable without the salt) | met | `ContextualRng` class javadoc paragraph "Why this exists" |
| core test: same inputs give the same sequence | met | `ContextualRngTest.sameInputsGiveSameSequence` |
| core test: different lot/wafer/step values give different sequences | met | `ContextualRngTest.differentLot/Wafer/StepGivesDifferentSequence` |
| core test: over 100000 samples the mean is within 0.01 of 0.5 | met | `ContextualRngTest.meanIsHalf` (SAMPLES = 100000, `Math.abs(mean - 0.5) < 0.01`) |
| core test: no value is 0, none exceeds 1 | met | `ContextualRngTest.valuesStayInRange` |
| core test: adjacent contexts show no obvious correlation | met | `ContextualRngTest.consecutiveDrawsAreUncorrelated` (threshold 0.05 over 100000 pairs), `adjacentContextsAreUncorrelated`, `adjacentStepContextsAreUncorrelated` (threshold 0.2 over 200 pairs) |
| mc test: the salt survives a save and load round trip | met | GameTest `lithmod:fab_random_salt_round_trip`: `setDirty()` + `SavedDataStorage.saveAndJoin()`, then the file is located under the world folder, read with `NbtIo.readCompressed` and decoded with `FabRandomData.CODEC` |
| mc test: the same inputs after a reload give the same sequence | met | same GameTest replays 8 rolls from the reloaded data and asserts bit-identical doubles |
| mc test: a client-side call throws | partly | GameTest `lithmod:fab_random_client_side_rejected` and JUnit `FabRandomServiceTest.clientSideLevelIsRejected` assert the guard throws; see section 5 for what is not covered |
| Done when: build and tests pass | met | `test`, `checkCoreImports` and `runGameTestServer` output above |
| Done when: report states exactly where the salt is stored | met | section 8 and `FabRandomData` javadoc |
| Out of scope: yield math, FOUP items, per-step weights | met | no yield, FOUP or weight code was added |

## 5. Not verified

- A real `net.minecraft.client.multiplayer.ClientLevel` was never constructed, so
  `requireServerSide(Level)` was never executed with `level.isClientSide() == true`.
  The dedicated game test server cannot produce one. The check was exercised through
  the `requireServerSide(boolean clientSide, String levelDescription)` overload that
  `requireServerSide(Level)` delegates to on its first line, and through the null
  branch of the `Level` overload. An integrated-client game test run was not run.
- Whether `Arcforge:creosote_moves` (an optional game test in another mod's namespace)
  failed because of this change was not investigated. It failed on one of the two runs
  with this change present (`1 optional tests failed`) and not on the baseline run, but
  a second run with this change present reported no optional failures. Nothing in this
  change touches movement, blocks or entities, and the test is not in the `lithmod`
  namespace. Treated as an unrelated Arcforge flake; see section 7.
- Long-run behaviour across a real world reload (stop the server, start it again, run
  the same step) was not simulated; the GameTest performs the equivalent at the file
  level, reading back the exact bytes the game wrote.
- Multi-threaded use of a single `ContextualRng` was not tested; `SplitMix64` is not
  thread safe and the class is not documented as thread safe.

## 6. Deviations

| spec says | what you did | why |
|---|---|---|
| Back the salt with saved data using the familiar `SavedData.Factory` + `save(CompoundTag, HolderLookup.Provider)` shape | Used the Minecraft 26.3 API instead: `SavedDataType<FabRandomData>` with a `Codec<FabRandomData>`, and `SavedDataStorage.computeIfAbsent(type)` | The 26.3 `SavedData` class no longer has `Factory`, `save` or `HolderLookup.Provider`; verified in `net/minecraft/world/level/saveddata/SavedData.java` of the patched 26.3 artifact. The 1.21-era shape does not compile |
| Use a `RecordCodecBuilder` for the saved-data codec (the idiomatic choice) | Built the codec with `Codec.LONG.optionalFieldOf("salt").codec().xmap(...)` | In DataFixerUpper 10.0.21, the version on this project's compile classpath, `MapCodec` no longer implements `Codec`, and `javap` of `RecordCodecBuilder$Instance` on that jar shows no `group` method, even though the patched 26.3 `MapIndex` bytecode calls one. That discrepancy is unexplained (see section 7); the chosen form produces the same `{"salt": <long>}` document |
| `rngFor(serverLevel, ...)` | Parameter typed as `Level`, with the guard inside | Typing it as `ServerLevel` would make the client check a compile-time impossibility and would leave `requireServerSide` untested by callers. A `ServerLevel` argument can still be passed directly |
| Test "a client-side call throws" in mc | Tested through the boolean overload plus the null-level branch, in both JUnit and a GameTest | No `ClientLevel` exists in a dedicated-server game test; see section 5 |

## 7. Open questions

1. Should `ContextualRng` reject negative `waferIndex` and `stepIndex`? It does today
   (both are indices into a lot and into a step list). If a later task needs to roll a
   "pre-step" sentinel such as `-1`, that constructor needs relaxing.
2. `arcforge:creosote_moves` is an optional Arcforge game test that failed on one of
   three runs and passed on the other two (baseline, and a repeat run with this
   change). Is that test known to be flaky in this environment? It is outside this
   repo and was not investigated.
3. Should the salt be logged (hashed) at server start for debugging, or left silent?
   Today it is only logged by the game test, which prints the file path but never the
   salt value.
4. Corrected in `M0-07_server-side-randomness_r2.md`: the original text of this
   question was based on a bad `javap` reading. `RecordCodecBuilder.create(instance ->
   instance.group(...).apply(instance, ...))` compiles fine against the DataFixerUpper
   10.0.21 jar on this project's compile classpath, verified with `javac` directly and
   confirmed by the project's own `TierCodec` and `DieRuleCodec`. `FabRandomData.CODEC`
   has been rewritten in that idiomatic form.

## 8. Handoff

Where the salt is stored, exactly:

- Class: `com.euvcraft.lithmod.mc.FabRandomData extends SavedData`.
- Attached to the **overworld's** `SavedDataStorage`, obtained via
  `FabRandomService.storage(level)` →
  `requireServerSide(level).getServer().overworld().getDataStorage()`.
- Type id: `Identifier lithmod:fab_random` (`FabRandomData.ID`), which makes
  `SavedDataStorage` resolve the file to
  `<world>/dimensions/minecraft/overworld/data/lithmod/fab_random.dat`.
- File contents: gzip compressed NBT `{"DataVersion": <int>, "data": {"salt": <long>}}`,
  the long being NBT `TAG_Long`. Confirmed on disk during the game test run; the log
  line `Fab random salt stored at ...\dimensions\minecraft\overworld\data\lithmod\fab_random.dat`
  comes from the test itself.
- Created on first call to `FabRandomService.data(level)` / `rngFor(...)`; a missing
  or malformed `salt` field decodes to a freshly generated `SecureRandom` salt.

Public API later tasks will use:

- `com.euvcraft.lithmod.core.Rng` — `double nextDouble()`.
- `com.euvcraft.lithmod.core.SplitMix64` — `SplitMix64(long seed)`, `long nextLong()`,
  `double nextDouble()`, `long state()`, `static long mix64(long)`,
  `public static final long GAMMA`.
- `com.euvcraft.lithmod.core.ContextualRng implements Rng` —
  `ContextualRng(long salt, UUID lotId, int waferIndex, int stepIndex)`,
  `long nextLong()`, `double nextDouble()`, `long salt()`, `UUID lotId()`,
  `int waferIndex()`, `int stepIndex()`,
  `static long seedFor(long salt, UUID lotId, int waferIndex, int stepIndex)`.
  Rejects a null `lotId` (NPE) and negative indexes (IAE).
- `com.euvcraft.lithmod.mc.FabRandomData` — `KEY_SALT`, `ID`, `CODEC`, `TYPE`,
  `FabRandomData(long salt)`, `static FabRandomData fresh()`,
  `static long randomSalt()`, `long salt()`,
  `ContextualRng rngFor(UUID lotId, int waferIndex, int stepIndex)`.
- `com.euvcraft.lithmod.mc.FabRandomService` —
  `static SavedDataStorage storage(Level level)`,
  `static FabRandomData data(Level level)`,
  `static long salt(Level level)`,
  `static ContextualRng rngFor(Level level, UUID lotId, int waferIndex, int stepIndex)`,
  `static ServerLevel requireServerSide(Level level)`,
  `static void requireServerSide(boolean clientSide, String levelDescription)`.

Config and data files: this task adds none. No config key exists for the salt; it is
world state by design, so it cannot be configured, copied between worlds, or read by a
client. Game tests are `lithmod:fab_random_salt_round_trip` and
`lithmod:fab_random_client_side_rejected`, environment `lithmod:fab_random_default`.

For M0-09 (yield roll engine): M0-09 defines the roll model; obtain a ContextualRng
via FabRandomService.rngFor(level, lotId, waferIndex, stepIndex).