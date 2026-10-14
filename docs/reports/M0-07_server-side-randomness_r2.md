# M0-07b: Server-side randomness fix pass

- Date: 2026-10-13
- Branch: `m0-06-die-type-definitions` (the shared checkout; M0-07 was merged into it as `9b1e7f7`)
- Commits: this work plus the r2 report, on top of `9b1e7f7`
- Java version used: Java 25 via the Gradle toolchain pinned in `build.gradle`
- Build and test commands run:
  - `.\gradlew.bat test --offline --console=plain`
  - `.\gradlew.bat checkCoreImports --offline --console=plain --rerun-tasks`
  - `.\gradlew.bat runGameTestServer --offline --console=plain`
  - a standalone `javac` probe (see section 3) to settle the codec question

## 1. Summary

Fix pass over M0-07. First, `TierCodec` and `DieRuleCodec` were checked: both already
use `RecordCodecBuilder.create(instance -> instance.group(...).apply(instance, ...))`,
and a direct `javac` probe confirmed that this form compiles against the
DataFixerUpper 10.0.21 jar on this project's compile classpath, so the r1 conclusion
that the idiomatic form was unavailable was wrong and `FabRandomData.CODEC` has been
rewritten in that same style. Second, the report paragraph that prescribed the roll
model for M0-09 was removed. Third, every salt accessor is now package private and the
two call sites that read a salt were rewritten to compare rolls instead. Fourth,
decoding a missing or malformed salt now logs a warning that carries no salt value
and marks the data dirty so the replacement is persisted.

## 2. Files changed

| path | new/modified/deleted | purpose |
|---|---|---|
| src/main/java/com/euvcraft/lithmod/mc/FabRandomData.java | modified | `CODEC` rewritten with `RecordCodecBuilder` + `group` + `apply`; `salt()` made package private; new private `restore(Optional<Long>)` that regenerates, warns and marks dirty |
| src/main/java/com/euvcraft/lithmod/mc/FabRandomService.java | modified | `salt(Level)` made package private |
| src/main/java/com/euvcraft/lithmod/core/ContextualRng.java | modified | `salt()` made package private |
| src/main/java/com/euvcraft/lithmod/mc/gametest/FabRandomGameTests.java | modified | No longer reads a salt; proves the shared salt and the save/load round trip by comparing rolls |
| src/test/java/com/euvcraft/lithmod/core/ContextualRngTest.java | modified | Dropped the `salt()` assertion |
| src/test/java/com/euvcraft/lithmod/mc/FabRandomServiceTest.java | modified | `codecToleratesMissingSalt` now asserts the dirty flag; new `codecRoundTripLeavesDataClean` |
| docs/reports/M0-07_server-side-randomness.md | modified | Open question 4 corrected in place; the M0-09 roll-model paragraph replaced with the required sentence |
| docs/reports/M0-07_server-side-randomness_r2.md | new | This report |

## 3. Verification

Codec question, settled directly with the compiler rather than by reading class files.
`javap` on `com.mojang.serialization.codecs.RecordCodecBuilder$Instance` from
`datafixerupper-10.0.21.jar` reported no `group` method, which is what r1 believed. A
probe compiled with the project's own compile classpath:

```java
package scratch;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
public final class GroupProbe {
    public record Holder(long salt) {}
    public static final Codec<Holder> CODEC = RecordCodecBuilder.create(instance -> instance
        .group(Codec.LONG.fieldOf("salt").forGetter(Holder::salt))
        .apply(instance, Holder::new));
}
```

```
$ javac -nowarn -cp <project compile classpath> -d out GroupProbe.java
exit=0
```

A negative control using `groupNoSuchMethod` in the same position failed as it should:

```
GroupProbe2.java:7: error: cannot find symbol
        .groupNoSuchMethod(Codec.LONG.fieldOf("salt").forGetter(Holder::salt))
  symbol:   method groupNoSuchMethod(RecordCodecBuilder<Holder,Long>)
  location: variable instance of type Instance<Holder>
1 error
```

`javap` on the project's own `build/classes/java/main/.../TierCodec.class` shows
`RecordCodecBuilder$Instance.group` in the emitted bytecode, and the rewritten
`FabRandomData.CODEC` now compiles.

Commands and relevant output:

```
.\gradlew.bat test --offline --console=plain
BUILD SUCCESSFUL in 13s

.\gradlew.bat checkCoreImports --offline --console=plain --rerun-tasks
Core import check passed: no Minecraft/NeoForge imports found in core package.

.\gradlew.bat runGameTestServer --offline --console=plain
[13Oct2026 23:33:58.106] GameTestRunner: Running test environment 'lithmod:fab_random_default' batch 0 (2 tests)...
[13Oct2026 23:33:58.389] EUVCRAFTMod: Fab random salt stored at
    gametestserver\gametestworld\.\dimensions\minecraft\overworld\data\lithmod\fab_random.dat
[13Oct2026 23:33:59.261] GameTestServer: ========= 673 GAME TESTS COMPLETE IN 1.072 min ======================
[13Oct2026 23:33:59.262] GameTestServer: All 673 required tests passed :)
[13Oct2026 23:33:59.262] GameTestServer: 1 optional tests failed
BUILD SUCCESSFUL in 2m 32s
```

The one optional failure is `(optional) arcforge:creosote_moves failed ... Armor stand
did not drift through creosote ... on tick 602`. It is an Arcforge test in another
mod's namespace, it is optional, and the same test failed in one of the M0-07 runs and
passed in two others, so it is treated as a pre-existing flake. See section 7.

Unit test table, read from `build/test-results/test/*.xml`:

| test class | tests | failures | errors | skipped | total |
|---|---:|---:|---:|---:|---:|
| ContextualRngTest | 14 | 0 | 0 | 0 | 14 |
| FabRandomServiceTest | 8 | 0 | 0 | 0 | 8 |
| CoreTest | 2 | 0 | 0 | 0 | 2 |
| DieRulesTest (+ nested Validation) | 8 | 0 | 0 | 0 | 8 |
| FabSettingsTest suites | 43 | 0 | 0 | 0 | 43 |
| GenerationMatrixTest | 8 | 0 | 0 | 0 | 8 |
| StatModelVerificationTest | 10 | 0 | 0 | 0 | 10 |
| TierRegistryTest | 34 | 0 | 0 | 0 | 34 |
| DieRuleDataLoaderFallbackTest | 1 | 0 | 0 | 0 | 1 |
| DieRuleJsonDataTest | 2 | 0 | 0 | 0 | 2 |
| JsonResourceDataLoaderReloadTest | 1 | 0 | 0 | 0 | 1 |
| SupportLineDataLoaderFallbackTest | 1 | 0 | 0 | 0 | 1 |
| SupportLineJsonDataTest | 1 | 0 | 0 | 0 | 1 |
| TierDataLoaderFallbackTest | 2 | 0 | 0 | 0 | 2 |
| TierJsonDataTest | 1 | 0 | 0 | 0 | 1 |
| FabConfigTest | 1 | 0 | 0 | 0 | 1 |
| Total | 137 | 0 | 0 | 0 | 137 |

Game tests, from `run/logs/latest.log`:

| test | environment | result |
|---|---|---|
| lithmod:trivial | lithmod:default | passed (pre-existing) |
| lithmod:fab_random_salt_round_trip | lithmod:fab_random_default | passed |
| lithmod:fab_random_client_side_rejected | lithmod:fab_random_default | passed |

## 4. Spec conformance

| item | met/partly/not met | evidence |
|---|---|---|
| 1. Check whether `TierCodec` and `DieRuleCodec` use `RecordCodecBuilder.create` with `instance.group` | met | `TierCodec.java:16`, `DieRuleCodec.java:15`, both `RecordCodecBuilder.create(instance -> instance.group(...).apply(instance, ...))` |
| 1. If they do, rewrite `FabRandomData.CODEC` in the same idiomatic way | met | `FabRandomData.java:48` now `RecordCodecBuilder.create(instance -> instance.group(Codec.LONG.optionalFieldOf(KEY_SALT).forGetter(...)).apply(instance, ...))` |
| 1. Correct open question 4 in the report | met | `M0-07_server-side-randomness.md` section 7 item 4, replaced with the correction and a pointer to this report |
| 2. Delete the paragraph telling M0-09 to multiply per-step draws | met | the four-line paragraph in the r1 handoff is gone |
| 2. Replace it with the given sentence | met | r1 handoff now ends "For M0-09 (yield roll engine): M0-09 defines the roll model; obtain a ContextualRng via FabRandomService.rngFor(level, lotId, waferIndex, stepIndex)." |
| 3. Make `ContextualRng.salt()` package private | met | `ContextualRng.java` `long salt()`, no modifier |
| 3. Make `FabRandomService.salt(Level)` package private | met | `FabRandomService.java` `static long salt(Level level)` |
| 3. Make `FabRandomData.salt()` package private | met | `FabRandomData.java` `long salt()` |
| 3. Or remove them if tests do not need them | met | kept, because `FabRandomServiceTest` lives in `com.euvcraft.lithmod.mc` and uses them |
| 3. The salt must never be logged or sent to a client | met | no call site outside `com.euvcraft.lithmod.mc` can read a salt; the game test and the core test no longer read one; `ContextualRng.toString` and `FabRandomData` print no salt; the regeneration warning carries only the data id |
| 4. Log a warning when a malformed or missing salt is decoded and a new one is generated | met | `FabRandomData.restore`, `EUVCRAFTMod.LOGGER.warn("EUVCRAFT fab random salt was missing or malformed in {}; generated a replacement", ID)` |
| 4. The warning does not include the value | met | the only placeholder is `ID`, the `lithmod:fab_random` data id |
| 5. Write `docs/reports/M0-07_server-side-randomness_r2.md` in the standard format | met | this file |
| Build and tests pass | met | `test` 137/0/0/0, `checkCoreImports`, `runGameTestServer` 673/673 required passed |

Two supporting changes beyond the five items, both required by item 3:

| supporting change | why |
|---|---|
| `FabRandomData.restore` marks the regenerated data dirty | `SavedDataStorage.readSavedData` does not mark decoded data dirty, so without this a regenerated salt would be regenerated on every server start instead of being written once |
| `FabRandomServiceTest.codecRoundTripLeavesDataClean` added | pins that an intact load does not schedule a rewrite, so the dirty flag is not set on every read |

## 5. Not verified

- The regeneration warning was not observed in a log. The game test world already
  contains a valid `fab_random.dat` from previous runs, so the missing-salt branch is
  not exercised by `runGameTestServer`. The branch is covered by
  `FabRandomServiceTest.codecToleratesMissingSalt`, which asserts the decoded data is
  dirty but does not assert on the log line.
- A real `ClientLevel` still cannot be constructed on a dedicated test server; the
  client-side guard is unchanged from r1 and is still verified through the boolean
  overload and the null branch. Not run.
- A full stop/start world reload was not simulated; the game test still performs the
  equivalent at the file level.
- Whether the `javap` output was wrong because of a stale tool, a jar-handling quirk
  or something else was not determined. The `javac` probe and the successful project
  build are the evidence; the reason for the disagreement is unexplained. Do not rely
  on `javap` for this classpath.

## 6. Deviations

| spec says | what you did | why |
|---|---|---|
| Make salt accessors package private "or remove them if tests do not need them" | Kept them, package private | `FabRandomServiceTest` is in the same package and uses `FabRandomData.salt()` to assert the codec round trip and that two `SecureRandom` draws differ; removing the accessor would have weakened those checks |
| Replace the M0-09 paragraph in the report | Edited the r1 report rather than only restating it in r2 | The paragraph was factually wrong advice sitting in the handoff section later tasks read; leaving it and only writing elsewhere would not remove it |

## 7. Open questions

1. `arcforge:creosote_moves` is an optional Arcforge game test that has now failed in
   two of four game test runs on this machine, including one with no lithmod code
   involved beyond the merged base. Is it known to be flaky here? Outside this repo,
   not investigated.
2. Should a regenerated salt also be re-broadcast or otherwise marked as a new "save
   identity" (for example, a version counter in the same file) so a player can tell
   that their rolls were reseeded? Today the warning is the only signal.

## 8. Handoff

Where the salt is stored, unchanged from r1 and unchanged by this pass:

- `com.euvcraft.lithmod.mc.FabRandomData extends SavedData`, attached to the
  **overworld's** `SavedDataStorage` via
  `FabRandomService.storage(level)` → `requireServerSide(level).getServer().overworld().getDataStorage()`.
- Type id `lithmod:fab_random`, so the file is
  `<world>/dimensions/minecraft/overworld/data/lithmod/fab_random.dat`, gzip
  compressed NBT `{"DataVersion": <int>, "data": {"salt": <long>}}`.

API after this pass (this listing supersedes the r1 handoff where they differ):

- `com.euvcraft.lithmod.core.Rng` — `double nextDouble()`.
- `com.euvcraft.lithmod.core.SplitMix64` — `SplitMix64(long seed)`, `long nextLong()`,
  `double nextDouble()`, `long state()`, `static long mix64(long)`,
  `public static final long GAMMA`.
- `com.euvcraft.lithmod.core.ContextualRng implements Rng` —
  `ContextualRng(long salt, UUID lotId, int waferIndex, int stepIndex)`,
  `long nextLong()`, `double nextDouble()`, `UUID lotId()`, `int waferIndex()`,
  `int stepIndex()`,
  `static long seedFor(long salt, UUID lotId, int waferIndex, int stepIndex)`.
  `long salt()` is package private. Rejects a null `lotId` (NPE) and negative
  indexes (IAE).
- `com.euvcraft.lithmod.mc.FabRandomData` — `KEY_SALT`, `ID`, `CODEC`, `TYPE`,
  `FabRandomData(long salt)`, `static FabRandomData fresh()`,
  `static long randomSalt()`,
  `ContextualRng rngFor(UUID lotId, int waferIndex, int stepIndex)`.
  `long salt()` is package private.
- `com.euvcraft.lithmod.mc.FabRandomService` —
  `static SavedDataStorage storage(Level level)`,
  `static FabRandomData data(Level level)`,
  `static ContextualRng rngFor(Level level, UUID lotId, int waferIndex, int stepIndex)`,
  `static ServerLevel requireServerSide(Level level)`,
  `static void requireServerSide(boolean clientSide, String levelDescription)`.
  `static long salt(Level level)` is package private.

Config and data files: this pass adds none.

For M0-09 (yield roll engine): M0-09 defines the roll model; obtain a ContextualRng
via FabRandomService.rngFor(level, lotId, waferIndex, stepIndex).