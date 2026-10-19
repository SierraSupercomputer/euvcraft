# M1-11 final acceptance

Accepted 2026-10-19T02:12:33.777Z. Original mk4 remains untouched; nothing was pushed.

| Evidence | Result |
| --- | --- |
| Reviewed source and documentation | `794f620c1ac61755deb3f4ae55dcb5b09f46178f` |
| Independently tested merge candidate / integrated commit | `dceae2d09754874b18a72d10d2bda2fffd05959c` |
| Identical reviewed/tested tree | `df0de6fe102aea3807aa7f9d355f7619e2a544e8` |
| Unit XML | 513 tests in 75 classes; zero failures, errors or skips |
| Complete server XML | 801 distinct cases: 801 passed, zero required failures; 0 optional result represented as skipped |
| Explicit project inventory | All 131 registered IDs present and passing, including 10 presentation cases |
| Recipe guide | 30 actual JSON recipes: 28 production and 2 test fixtures; deterministic generator and stale-guide check verified |
| Verified pre-integration backup | `euvcraft-2026-10-19T02-12-31-351Z-457475205701400.bundle` |
| Verified integrated-code backup | `euvcraft-2026-10-19T02-12-33-038Z-457476890790400.bundle` |

[Two independent final reviews](M1-11_review.md) approve the exact tree used for the candidate. Build, core-import check and packaging passed. The source-phase 75-class table and registered IDs are in the [task report](M1-11_recipes-and-presentation.md).

Optional dependency outcome: `arcforge:creosote_moves` passed. No Arcforge code or test was changed, disabled or rerun to hide its outcome. The earlier source-phase run recorded this optional case as failed/skipped. M1-12 will report its behavior across three separate server runs.

Recipes, server FOUP initialization, solid GUI bars, creative ordering, language and asset references, reload/restoration, and real survival drops are covered by recorded checks. No actual client-render proof is claimed. M1-12 full-chain/statistical/privacy/hand-fed/reload/debug-energy/shipping-fixture/documentation and repeated-run gates remain. Acceptance adds only factual review and status documentation after the tested source.

Coordinator documentation correction during acceptance: `StructureShape.PROCESS_CELL` is explicitly width=2, height=2, length=3, and `StructureMatcher` allows one or two power ports. The progression guide’s incorrect “2×2 footprint, three layers tall” wording was corrected to “two blocks wide, two tall and three long.” The source tests already use the correct dimensions; no Java, recipe, resource, generator or test changed. This factual guide correction follows the frozen review/candidate and is disclosed separately from the reviewed tree. Optional cosmetic GLM findings are retained without an implementation change.
