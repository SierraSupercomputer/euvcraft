# M1-09 final acceptance

Accepted 2026-10-18T21:03:27.599Z. Original mk4 remains untouched; nothing was pushed.

| Evidence | Result |
| --- | --- |
| Reviewed source and report | `6c2b35f9d598bc2633925016ca48511be50f7572` |
| Independently checked merge candidate / integrated commit | `66e5317c21b241847519795f1d89d48fead82d56` |
| Reviewed and tested tree | `e51b2d30b15996178cf9194f0d68ccacfac1371e`; identical before master fast-forward |
| Unit XML | 509 tests in 74 classes; zero failures, errors or skips |
| Server XML | 791 distinct cases: 790 passed; no required failures; 1 optional case recorded as skipped |
| Explicit project inventory | All 121 registered project IDs present and passing |
| New Process Cell server cases | All 16 have completed production-fault assertion evidence |
| Production controls | 12 runs: five backend groups, six menu groups, one combined five-fault safety run; exact restoration |
| Verified pre-integration backup | `euvcraft-2026-10-18T21-03-21-727Z-438925581458600.bundle` |
| Verified integrated code/state backup | `euvcraft-2026-10-18T21-03-27-599Z-438931452045200.bundle` |

[Final review one](M1-09_review_one.md) and [final review two](M1-09_review_two.md) are distinct fresh read-only Nemotron sessions, both approve with only optional low observations. Earlier Beam phase approvals are supplemental; Beam final attempts and cooldown retry were quota-limited. Both final sessions were attested with explicit zero costs under the approved free-only policy. The first reviewer corrected an inconsistent initial severity/verdict before acceptance; that rejected response was never treated as approval.

The independent candidate ran after all faults were restored and used exactly the reviewed tree. Build, core-import check and packaging passed. Optional dependency result: `arcforge:creosote_moves` failed and is represented as skipped in XML. It passed in the earlier full production run. No Arcforge code/test was changed, disabled, or rerun to hide the result. Required and project tests passed; the M1-12 repeated-run gate remains responsible for recording this dependency behavior across three independent runs.

Runtime testing found and verified fixes for valid stripped carrier admission, occupied resist top-ups, private single-slot drag dispatch, and malformed saved flags restarting work. Abort and top-up behavior follows the user decisions. Exact class rows, registered IDs, source APIs and fault receipt labels remain in the task report.

Only factual acceptance/review documentation follows verified integration. M1-11 recipes/presentation and M1-12 whole-chain, privacy, repeated-server and real-client gates remain. No complete playable-M1 or actual client rendering claim is made.
