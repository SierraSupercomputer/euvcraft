# M1-12 final acceptance

Accepted 2026-10-20T02:00:09.768Z. Original mk4 is untouched; nothing was pushed.

| Evidence | Result |
| --- | --- |
| Frozen reviewed source/documentation | `1ffe81841fd7dc0e567e52564f9c075ba9b5f8c2` |
| Independently checked candidate / integrated commit | `81d624f29da78f13165a099fdd71116b410bd83b` |
| Identical reviewed/tested tree | `e5d77e52c1366ef06b4080b680ca913642c29d11` |
| Candidate unit XML | 517 tests / 76 classes; zero failures/errors/skips |
| Candidate complete server XML | 810 distinct cases; 810 passed; zero required failures; 0 optional result represented as skipped |
| Candidate project inventory | All 140 ordinary IDs present and passing |
| Three required full server processes | 810/810 each; Arcforge creosote failed 0/3 |
| Fixtures-disabled actual server probe | Exactly one passing required case; real fixture registry absence and production/creative positive controls |
| Candidate release jar | Exact eight fixture JSON entries absent; production resources and three shared tags retained |
| Generated handoff | Byte-exact Check passes again against candidate freshly compiled classes; original M0 output preserved |
| Owner client execution | Initial singleplayer launch confirmed; complete manual/model/menu/save-resume walkthrough pending |

Candidate verification used --no-build-cache to execute Java/JUnit compilation/tests in its separate checkout. Only three exact hash-verified Minecraft/NeoForge dependency jars were reused. The earlier repeated server runs used normal Gradle compileJava cache states (FROM-CACHE and UP-TO-DATE); each server process and all recorded server cases executed. The earlier metadata-bootstrap failure reached no tests and is excluded from the three-run denominator. The first full-run wrapper bookkeeping collision was reconciled from saved successful Gradle/XML output without rerunning Minecraft.

Windows Git autocrlf initially checked out docs/m1-slice.md with CRLF, so the raw-byte guard rejected it before Check ran. Original checkout bytes and the failed guard receipt are retained. Running the approved generator against the freshly compiled candidate restored the expected LF bytes while leaving HEAD and the reviewed Git tree unchanged; the subsequent non-mutating Check is the accepted result. After a Windows checkout with autocrlf, regenerate this reference before its byte-exact Check. No tracked line-ending rule was added outside the approved task scope. The candidate is an additional full server outcome, separate from the three required runs. Candidate optional Arcforge results: none. No Arcforge code was changed or failure hidden through a selective retry.

Source-phase failed/incomplete worker drafts, compiler failures, boundary drop-contamination regression and initial fixtures-off registry-loading failure are preserved. Nemotron authored implementation/documentation drafts; approved free GLM completed bounded fallback drafts after provider failures; coordinator repaired and verified actual APIs, test isolation and generators. Focused Beam reviews supplement the two final whole-task approvals.

Recoverable bundles: `euvcraft-2026-10-20T02-00-04-042Z-4466035018800.bundle` before integration, `euvcraft-2026-10-20T02-00-07-604Z-4469594429700.bundle` after code integration. Full final state/documentation bundle is recorded in coordinator acceptance evidence.

No new task beyond the defined M1 pack was started. Complete player walkthrough remains in `../manual-walkthrough-m1.md`; automated acceptance does not claim rendered client observations.
