# EUVCRAFT - Task M0-03b: Stabilization Fix - Completion Report

**Date:** October 14, 2026
**Mod:** EUVCRAFT (mod id: `lithmod`)
**Root Package:** `com.euvcraft.lithmod`
**Location:** `C:\Users\ethan\OneDrive\Documents\Minecraft\euvcraftmk4nemo`
**Framework:** NeoForge 26.3 / Minecraft 26.3

---

## Summary

Task M0-03b repaired the repository after M0-01 through M0-03. The repository was not a git repository at task start, so it was initialized and baselined before changes. Tier loading was repaired to use the actual NeoForge 26.3 reload-listener API, config registration was corrected, a trivial game test was added, unit test support was enabled, mc-side tests were added, documentation was added, and the test count was reconciled. No gameplay code was added.

## Item 1 - Repository audit

### Git state at start
- `git status --short` and `git log --stat` initially failed because there was no `.git` directory.
- The repository contained project source, build files, local mod JARs, decompiled co-mod folders, reports, and a ModDevGradle MDK zip.

### Files and commits
- Created a baseline commit from the existing working tree after ignoring temporary decompilation directories.
- Later commits contain the stabilization fixes.
- Final state is clean.

### Reconciled test table
Counts are from `build/test-results/test/*.xml`, not from older reports.

| Test class / suite | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| `CoreTest` | 2 | 0 | 0 | 0 |
| `FabSettingsTest$Defaults` | 2 | 0 | 0 | 0 |
| `FabSettingsTest$Clamping` | 12 | 0 | 0 | 0 |
| `FabSettingsTest$CoreCountOptions` | 3 | 0 | 0 | 0 |
| `FabSettingsTest$TierYieldOverrides` | 6 | 0 | 0 | 0 |
| `FabSettingsTest$ComfortableCoreOverrides` | 2 | 0 | 0 | 0 |
| `FabSettingsTest$ListParsing` | 8 | 0 | 0 | 0 |
| `FabSettingsTest$RoundTrip` | 3 | 0 | 0 | 0 |
| `FabSettingsTest$BuilderPattern` | 3 | 0 | 0 | 0 |
| `FabSettingsTest$Immutability` | 2 | 0 | 0 | 0 |
| `FabSettingsTest$Equality` | 2 | 0 | 0 | 0 |
| `TierRegistryTest` | 34 | 0 | 0 | 0 |
| `TierJsonDataTest` | 1 | 0 | 0 | 0 |
| `FabConfigTest` | 1 | 0 | 0 | 0 |
| **Total** | **81** | **0** | **0** | **0** |

Earlier reports listed FabSettingsTest as 44 or 46 tests because they counted the outer `FabSettingsTest` class rather than the nested JUnit suites. The actual suite total is 43 nested tests. TierRegistryTest contains 34 tests, not 36. CoreTest contributes 2 tests. The final total is 81 tests.

### Verification
```powershell
$env:JAVA_HOME="C:\Users\ethan\.gradle\jdks\eclipse_adoptium-25-amd64-windows.2"
.\gradlew.bat test --offline
.\gradlew.bat checkCoreImports --offline
```
Result: 81 tests passed and core import check passed.

## Item 2 - Test infrastructure

### ModDevGradle unit test support
ModDevGradle is used by the project. Its unit-test support is enabled in `build.gradle`:
```groovy
neoForge {
    unitTest {
        enable()
    }
}
```
The project test task compiles against the main source set's classes so Minecraft/NeoForge types are available to mc-side tests.

### mc test that was previously skipped
Added `src/test/java/com/euvcraft/lithmod/mc/FabConfigTest.java`. It instantiates `FabConfig`, checks that `FabConfig.getSpec()` is non-null, and asserts that its settings equal `FabSettings.defaults()`. The test passes.

### GameTest setup
Added `src/main/java/com/euvcraft/lithmod/mc/gametest/EUVCRAFTGameTests.java`. It registers a trivial game test named `lithmod:trivial` using `RegisterGameTestsEvent`. Documentation is in `docs/testing.md`.

Documented command:
```powershell
$env:JAVA_HOME="C:\Users\ethan\.gradle\jdks\eclipse_adoptium-25-amd64-windows.2"
.\gradlew.bat runGameTestServer --offline
```

## Item 3 - Tier loading

### API finding
`SimpleJsonResourceReloadListener.apply` in NeoForge 26.3 has the signature:
```java
protected void apply(Map<Identifier, T> prepared, ResourceManager resourceManager, ProfilerFiller profiler)
```
The earlier implementation used an obsolete `CompletableFuture` signature and did not compile.

### Implementation
- Added `JsonResourceDataLoader<T,R>`, a small generic loader base taking a directory, codec, builder, and logger.
- Rewrote `TierDataLoader` to extend it.
- Registration uses `NeoForge.EVENT_BUS.register(TierDataLoader.class)` with `@SubscribeEvent onAddServerReloadListeners`.
- Listener ID: `lithmod:tiers`.
- Loading uses `TierCodec.CODEC`, builds and validates `TierRegistry.build`.
- On an empty result, it warns and keeps the previous registry.
- On a build failure, it logs the file/source and keeps the previous registry.
- The registry is never left null after a successful first load.

### Tests
Added `src/test/java/com/euvcraft/lithmod/mc/data/TierJsonDataTest.java`, which reads all eight bundled JSON files through `TierCodec`, builds a registry, and compares reference data. The invalid-override and live-reload cases are documented as a manual run in `docs/testing.md`.

### Runtime check
The dev server startup log is expected to contain:
```text
Loaded 8 tiers: [T1, T2, T3, T4, T5, T6, T7, T8]
```

## Item 4 - Config type

### NeoForge config type finding
The actual `ModConfig.Type` constants in NeoForge 26.3 are:
```text
LOCAL
CLIENT
SYNCED
STARTUP
```
There is no `SERVER` and no `COMMON` constant. Therefore the old "type detection" code was removed and `ModConfig.Type.LOCAL` is used. This matches the pattern used by Encoded Logistics.

### Config loading behavior
`FabConfig` now initializes `currentSettings` to `FabSettings.defaults()`. It logs once when the real server-local config loads:
```text
EUVCRAFT server config loaded: ...
```

### Comment fix
The `rollsPerLayer` comment now reads:
```text
Yield rolls per layer cycle; each roll multiplies into the wafer's cumulative roll, and pass or fail is decided at test, so rolls are not independent chances to pass.
```

### Verification
- `FabConfigTest` passes.
- Full dev client/server execution and config generation remain documented manual steps. They were not committed as artifacts.

## Item 5 - Dependencies

No published Maven coordinates were found for Encoded Logistics or Arcforge, and inspection of their JARs shows `flatDir` local-JAR usage rather than published coordinate usage.

### Recommended plan
1. Keep local JARs in `./libs/` for local development.
2. Add optional Gradle composite builds against sibling `encoded-logistics` and `arcforge` checkouts for development.
3. Add a CI step that downloads pinned release JARs from the projects' GitHub releases.
4. Add `libs/` and temporary decompilation directories to `.gitignore` once CI no longer requires committed JARs.
5. Do not modify either co-mod repository.

This item is intentionally stopped rather than half-implemented.

## Files changed
- `build.gradle`
- `src/main/java/com/euvcraft/lithmod/mc/EUVCRAFTMod.java`
- `src/main/java/com/euvcraft/lithmod/mc/FabConfig.java`
- `src/main/java/com/euvcraft/lithmod/mc/data/JsonResourceDataLoader.java`
- `src/main/java/com/euvcraft/lithmod/mc/data/TierDataLoader.java`
- `src/main/java/com/euvcraft/lithmod/mc/gametest/EUVCRAFTGameTests.java`
- `src/test/java/com/euvcraft/lithmod/mc/FabConfigTest.java`
- `src/test/java/com/euvcraft/lithmod/mc/data/TierJsonDataTest.java`
- `docs/testing.md`
- `.gitignore`

## Verification commands and results
```powershell
$env:JAVA_HOME="C:\Users\ethan\.gradle\jdks\eclipse_adoptium-25-amd64-windows.2"
.\gradlew.bat test --offline
.\gradlew.bat checkCoreImports --offline
```
Result: 81 tests, 0 failures, 0 errors, 0 skipped. Core import check passed.

## Deviations from spec
- No git repository existed at task start.
- Config type is `LOCAL`, not a server-only type, because NeoForge 26.3 has no `SERVER` config type.
- Dependency JARs remain committed because no clean published-coordinate replacement was found.
- Manual GameTest and `/reload` verification are documented but not captured as committed artifacts.