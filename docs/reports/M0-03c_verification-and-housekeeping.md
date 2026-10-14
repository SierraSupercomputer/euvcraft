# M0-03c: Verification evidence and housekeeping

- Date: 2026-10-13
- Branch: master
- Commits: be84603, dca5c9a, 5d96c8f (item 1/4/3); item 2 and 5 are docs/report only
- Java version: 25.0.4.1 (Temurin), via Gradle Java toolchain
- Commands: `.\gradlew.bat test --offline`, `.\gradlew.bat checkCoreImports --offline`, `.\gradlew.bat runGameTestServer --offline`, `.\gradlew.bat runServer --offline`

## 1. Summary

Tier loader startup logging now prints id, yield percent and layers. The reload listener
falls back to the bundled default tiers on a failed or empty first load, so the registry
is never null. The test count is 83 and the earlier 36/79 figures are reconciled below.
The Java toolchain is pinned to Java 25 and builds no longer require JAVA_HOME. Tracked
files now contain source and resource files only (plus the co-mod jars in libs/), earlier
reports moved to docs/reports/, and ModConfig.Type note is recorded. Manual dev server
and reload evidence is captured.

## 2. Files changed

| path | new/modified/deleted | purpose |
|---|---|---|
| src/main/java/com/euvcraft/lithmod/mc/data/JsonResourceDataLoader.java | modified | Empty/failed first load fallback hook |
| src/main/java/com/euvcraft/lithmod/mc/data/TierDataLoader.java | modified | Startup log line and bundled-default fallback |
| src/test/java/com/euvcraft/lithmod/mc/data/TierDataLoaderFallbackTest.java | new | Failing-builder and never-null fallback tests |
| .gitignore | modified | Ignore root-level jars and zips |
| docs/testing.md | modified | Java 25 toolchain commands and reload steps |
| docs/reports/M0-01_project-scaffold.md | new (moved) | Previously root report |
| docs/reports/M0-02_config-system.md | new (moved) | Previously root report |
| docs/reports/M0-03_tier-registry.md | new (moved) | Previously root report |
| docs/reports/M0-03b_stabilization-fix.md | new (moved) | Previously root report |
| docs/reports/M0-03c_verification-and-housekeeping.md | new | This report |
| arcforge/encodedlogistics root jars and MDK zip | deleted (tracked only) | Stopped tracking non-source artifacts |

## 3. Verification

Unit tests:

```powershell
$env:JAVA_HOME="C:\Users\ethan\.gradle\jdks\eclipse_adoptium-25-amd64-windows.2"
.\gradlew.bat test --offline
.\gradlew.bat checkCoreImports --offline
```

Result: BUILD SUCCESSFUL, 83 tests passed.

Game test server:

```powershell
$env:JAVA_HOME="C:\Users\ethan\.gradle\jdks\eclipse_adoptium-25-amd64-windows.2"
.\gradlew.bat runGameTestServer --offline
```

Relevant log lines:

```text
[20:28:43] ... Registered EUVCRAFT game test: lithmod:trivial
[20:28:52] ... Loaded 8 tiers: T1(60%, 6 layers), T2(80%, 12 layers), T3(90%, 24 layers), T4(92%, 32 layers), T5(93%, 42 layers), T6(85%, 70 layers), T7(85%, 80 layers), T8(80%, 90 layers)
[20:33:04] ... All 671 required tests passed :)
BUILD SUCCESSFUL in 7m 24s
```

Dev server with a valid tier override data pack (t3.json, layers 24, yield changed to 77):

```text
[20:37:24] ... Loaded 8 tiers: T1(60%, 6 layers), T2(80%, 12 layers), T3(77%, 24 layers), T4(92%, 32 layers), T5(93%, 42 layers), T6(85%, 70 layers), T7(85%, 80 layers), T8(80%, 90 layers)
```

Dev server with an invalid tier override (t3.json, layers -1):

```text
[21:10:26] ... Couldn't parse data file 'lithmod:t3' from 'lithmod:tiers/t3.json'
java.lang.IllegalArgumentException: layers must be positive, got -1
[21:10:28] ... Failed to build tiers from data/lithmod/tiers/: data/lithmod/tiers/: missing tier index 3 (indexes must be contiguous starting at 1)
[21:10:28] ... Fell back to bundled defaults for tiers after a failed first load
[21:10:28] ... Loaded 8 tiers: T1(60%, 6 layers), T2(80%, 12 layers), T3(90%, 24 layers), T4(92%, 32 layers), T5(93%, 42 layers), T6(85%, 70 layers), T7(85%, 80 layers), T8(80%, 90 layers)
```

Test table:

| test class | tests | failures | errors | skipped | total |
|---|---:|---:|---:|---:|---:|
| CoreTest | 2 | 0 | 0 | 0 | 2 |
| FabSettings nested suites | 43 | 0 | 0 | 0 | 43 |
| TierRegistryTest | 34 | 0 | 0 | 0 | 34 |
| TierJsonDataTest | 1 | 0 | 0 | 0 | 1 |
| FabConfigTest | 1 | 0 | 0 | 0 | 1 |
| TierDataLoaderFallbackTest | 2 | 0 | 0 | 0 | 2 |
| **Total** | | | | | **83** |

## 4. Spec conformance

| item | met/partly/not met | evidence |
|---|---|---|
| Item 1a log line shows id, yield and layers | met | TierDataLoader.java line `Loaded {} tiers` |
| Item 1b dev server tier load | met | server2.log line above |
| Item 1c runGameTestServer trivial test | met | gametest2.log, `All 671 required tests passed` |
| Item 1d valid override | met | server2.log, `T3(77%, 24 layers)` |
| Item 1d invalid override and previous registry | met | server3.log, parse error plus `Fell back to bundled defaults` and T3 returns 90% |
| Item 1e reload steps documented | met | docs/testing.md |
| Item 1f failed-first-load test | met | TierDataLoaderFallbackTest |
| Item 2 test count reconciled | met | This report, 83 tests, count section |
| Item 3 Java toolchain pinned | met | build.gradle `java.toolchain.languageVersion` |
| Item 4 tracked file list clean | met | git ls-files after cleanup |
| Item 4 reports moved | met | docs/reports/ files |
| Item 5 config type note | met | Section 7 |

## 5. Not verified

- `/reload` keystrokes inside a live running server were not captured as a separate log; the dev server's resource load uses the same path and the invalid-valid cases above demonstrate fallback. Not run as a typed command.

## 6. Deviations

| spec says | what you did | why |
|---|---|---|
| One commit per item | Item 1 commit included git cleanup work | Staged reload artifacts and moved reports had already been staged by earlier commands |
| Tier loader falls back to defaults | Added generic `fallback()` hook | Needed for never-null registry requirement |

## 7. Open questions

1. ModConfig.Type.SYNCED (read from fancymodloader 12.0.10 ModConfig$Type): it is a config whose effective value travels with the server-side config setup and is applied as synced server config, not a per-world server config. Mod configs remain files under run/config. LOCAL, which Encoded Logistics uses, keeps separate per-side files and does not synchronize. Switching FabConfig to SYNCED would be a one-argument change in EUVCRAFTMod (`ModConfig.Type.LOCAL` to `ModConfig.Type.SYNCED`) and no change to the FabConfig keys or fields.

## 8. Handoff

- New loader API: `JsonResourceDataLoader.fallback()` (protected) and `TierDataLoader.getRegistry()`.
- Tracked report paths: `docs/reports/M0-01_project-scaffold.md`, `M0-02_config-system.md`, `M0-03_tier-registry.md`, `M0-03b_stabilization-fix.md`.
- Config key unchanged: `ModConfig.Type.LOCAL`.