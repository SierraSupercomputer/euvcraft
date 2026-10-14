# M0-04: Support-line generation registry

- Date: 2026-10-14
- Branch: m0-04-support-line-generations
- Commit hash: 1c132c2
- Java version used: Java 25 via Gradle toolchain (launcher JVM Eclipse Adoptium 21.0.12.1)
- Build and test commands: `.\gradlew.bat test --offline --rerun-tasks`, `.\gradlew.bat checkCoreImports --offline`

## 1. Summary

Cleaned the M0-04 implementation down to one canonical name per concept: `SupportLines`, `GenerationEntry`, `UnmetLine`, `GenerationMatrix`, and `SupportLineDataLoader`. Removed all alias records, alias loaders, and the abstract loader base by inlining loader logic into `SupportLineDataLoader`. The loader registers exactly one server reload listener. Added a reload-retention test and captured default plus data-pack-override startup log lines.

## 2. Files changed

| path | new/modified/deleted | purpose |
|---|---|---|
| src/main/java/com/euvcraft/lithmod/core/GenerationMatrix.java | modified | Removed nested alias records and duplicate accessors; canonical `GenerationMatrix` only |
| src/main/java/com/euvcraft/lithmod/mc/data/SupportLineDataLoader.java | modified | Inlined from removed base class; one accessor `getMatrix()` |
| src/main/java/com/euvcraft/lithmod/mc/EUVCRAFTMod.java | modified | Registers only `SupportLineDataLoader` |
| src/main/resources/data/lithmod/support_lines/matrix.json | kept | Bundled default matrix |
| Alias record/loader files | deleted | Removed speculative variants |
| src/test/java/com/euvcraft/lithmod/mc/data/JsonResourceDataLoaderReloadTest.java | new | Reload keeps previous data after failed reload |
| src/test/java/com/euvcraft/lithmod/mc/data/SupportLineDataLoaderFallbackTest.java | modified | Canonical loader only |
| src/test/java/com/euvcraft/lithmod/mc/data/SupportLineJsonDataTest.java | modified | Canonical loader only |

## 3. Verification

```powershell
.\gradlew.bat test --offline --rerun-tasks
.\gradlew.bat checkCoreImports --offline
```

Result: BUILD SUCCESSFUL in 47s, 94 tests completed, 0 failures, 0 errors; core import check passed.

Default dev server startup log:

```text
[21:49:16] [main/INFO] [co.eu.li.mc.EUVCRAFTMod/]: Loaded support-line generations: GenerationMatrix{T1{substrate=1, mask_shop=1, deposition_etch=1, utilities=1, test_packaging=1}, T2{substrate=1, mask_shop=1, deposition_etch=1, utilities=1, test_packaging=1}, T3{substrate=2, mask_shop=2, deposition_etch=1, utilities=1, test_packaging=1}, T4{substrate=2, mask_shop=2, deposition_etch=2, utilities=1, test_packaging=2}, T5{substrate=2, mask_shop=2, deposition_etch=2, utilities=2, test_packaging=2}, T6{substrate=3, mask_shop=2, deposition_etch=2, utilities=2, test_packaging=2}, T7{substrate=3, mask_shop=3, deposition_etch=3, utilities=3, test_packaging=2}, T8{substrate=3, mask_shop=3, deposition_etch=3, utilities=3, test_packaging=3}}
```

Overriding data pack startup log (substrate changed to all 1):

```text
[21:59:12] [main/INFO] [co.eu.li.mc.EUVCRAFTMod/]: Loaded support-line generations: GenerationMatrix{T1{substrate=1, mask_shop=1, deposition_etch=1, utilities=1, test_packaging=1}, T2{substrate=1, mask_shop=1, deposition_etch=1, utilities=1, test_packaging=1}, T3{substrate=1, mask_shop=2, deposition_etch=1, utilities=1, test_packaging=1}, T4{substrate=1, mask_shop=2, deposition_etch=2, utilities=1, test_packaging=2}, T5{substrate=1, mask_shop=2, deposition_etch=2, utilities=2, test_packaging=2}, T6{substrate=1, mask_shop=2, deposition_etch=2, utilities=2, test_packaging=2}, T7{substrate=1, mask_shop=3, deposition_etch=3, utilities=3, test_packaging=2}, T8{substrate=1, mask_shop=3, deposition_etch=3, utilities=3, test_packaging=3}}
```

Test table:

| test class | tests | failures | errors | skipped | total |
|---|---:|---:|---:|---:|---:|
| CoreTest | 2 | 0 | 0 | 0 | 2 |
| FabSettings suites | 43 | 0 | 0 | 0 | 43 |
| TierRegistryTest | 34 | 0 | 0 | 0 | 34 |
| GenerationMatrixTest | 8 | 0 | 0 | 0 | 8 |
| TierJsonDataTest | 1 | 0 | 0 | 0 | 1 |
| SupportLineJsonDataTest | 1 | 0 | 0 | 0 | 1 |
| JsonResourceDataLoaderReloadTest | 1 | 0 | 0 | 0 | 1 |
| FabConfigTest | 1 | 0 | 0 | 0 | 1 |
| TierDataLoaderFallbackTest | 2 | 0 | 0 | 0 | 2 |
| SupportLineDataLoaderFallbackTest | 1 | 0 | 0 | 0 | 1 |
| Total | 94 | 0 | 0 | 0 | 94 |

## 4. Spec conformance

| item | met/partly/not met | evidence |
|---|---|---|
| Exactly one canonical class per concept | met | Kept SupportLines, GenerationEntry, UnmetLine, GenerationMatrix, SupportLineDataLoader |
| Deleted speculative aliases | met | Git deletion commit 1c132c2 |
| Exactly one accessor `getMatrix()` | met | SupportLineDataLoader.getMatrix() |
| Exactly one matrix reload listener | met | one event.addListener in SupportLineDataLoader |
| Reload keeps previous after failure | met | JsonResourceDataLoaderReloadTest |
| Default matrix equals reference | met | SupportLineJsonDataTest plus GenerationMatrixTest |
| isSatisfied equal/above/below | met | GenerationMatrixTest |
| unmetLines output and order | met | GenerationMatrixTest.unmetLinesOrdering |
| Validation decreasing/missing | met | GenerationMatrixTest tests |
| T6 disabled unchanged | met | GenerationMatrixTest.t6DisabledMatrixUnchanged |
| Startup log prints matrix | met | log line at 21:49:16 |
| Data pack override changes after load | met | log line at 21:59:12 |

## 5. Not verified

- None beyond normal development environment.

## 6. Deviations

| spec says | what you did | why |
|---|---|---|
| Single JSON data file | `support_lines/matrix.json` root map | Keeps one override file and direct codec |

## 7. Open questions

none

## 8. Handoff

- Public core: `GenerationMatrix` (`build`, `requiredGeneration`, `isSatisfied`, `unmetLines`), `SupportLines`, `GenerationEntry`, `UnmetLine`.
- MC: `SupportLineDataLoader` with `getMatrix()` and bundled `data/lithmod/support_lines/matrix.json`.
- Behavioral tests: `JsonResourceDataLoaderReloadTest`.
