# M1-10 final acceptance

Accepted 2026-10-18T10:16:04.763Z. Original mk4 remains untouched; nothing was pushed. Both packaging branches are ancestors of the integrated commit.

| Evidence | Result |
| --- | --- |
| Reviewed task commit | `256b9aa9573bc17db728d6ff15f4659bcaeedf0c` |
| Full merge-candidate check | `73a4d77c3a8e5eda22057086b0501a9bb0b51c23`;509unit tests/74classes and775distinct server cases,0failures/errors/skips |
| Explicit project inventory | All105registered `lithmod:*` IDs present and passing |
| Core production controls | All59new methods;48executions,47valid,39distinctfault labels; exact restoration |
| Runtime production controls | All24new IDs;11completed runs/17mutation entries; exact restoration |
| Reviewed/tested/merged content | Tree`2ce93a431fc3ebd984e4c843c388b08e8cd3af06` identical across reviewed task, tested candidate and final history-consolidating merge |
| Integrated commit | `3ea79f32bb764ce38dc53e9a8fa96a88c4a91767` |
| Child checkpoint included | `d76337956e3f40ab820eca0deb7cc808da16b187`; identical fixture correction consolidated without a file change |
| Verified code/state backup | `C:\Users\ethan\OneDrive\Documents\CodexProjects\OpenCode Orchestrator\.orchestrator\backups\euvcraft-2026-10-18T10-16-04-767Z-400088619596300.bundle` |

[Conservation review](M1-10_review-conservation.md) and [conformance review](M1-10_review-conformance.md) are fresh, distinct Beam sessions, both approve with no findings. The task report records exact APIs, parsed per-class totals, each new test’s failed control and limits. An initial crashing synthetic fault and preflight-only stops were excluded.

The fresh candidate check ran after all deliberate faults were restored. Its source/report tree exactly matched the reviewed task. A subsequent merge included the child’s duplicate fixture-fix commit; Git tree equality proved every tested file unchanged before master fast-forward. Only factual acceptance documentation follows this verified integration; it changes no tested source/resources/tests.

Optional dependency result: rcforge:creosote_moves failed (armor stand did not sink far enough at tick602), represented as skipped in XML. It passed the earlier full source run; no Arcforge code or test was changed, disabled or retried to obtain a green count. Build exit0 and all project/required tests passed. This optional result remains disclosed for M1-12 repetitions.

Remaining milestones:M1-09 Process Cell wiring,M1-11 recipes/presentation,M1-12 real end-to-end/repeated-server/client gate. No full playable-M1 or client visual claim is made.
