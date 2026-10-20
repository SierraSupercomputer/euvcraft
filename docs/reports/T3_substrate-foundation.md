# T3 sealed-lot substrate foundation

Phase only; the new multiblocks, pure-boule production, slicer item integration, T3 admission and complete T3 gameplay remain later work. This separate scoped branch is not integrated into mk5. Final whole-T3 acceptance must cover it and its test supplement.

`WaferSubstrate` defines ordinary/G1 and pure/G2. `FoupContents` stores the authoritative grade; old three/four-argument constructor APIs default to ordinary. The explicit-grade factory avoids ambiguous null RunSpec calls. Immutable mutations and rolling preserve grade. Pure slicing has an explicit overload; existing callers remain ordinary.

`FoupPublicView` adds only grade to the original three counts. Persistent absence defaults ordinary; invalid explicit grade returns a codec error. The public stream sends those counts and the stable grade ID, retaining nil-UUID/untrusted reconstruction, no run, default placeholder rolls and no flags. Tooltip shows type or Empty, counts and layer/progress. No private identity, calibration, flags or rolls are added to the wire/tooltip.

## Actual execution

- Unit source `3908c676d62270389a5925adfc1ae0ae9d1e1e07`:616 tests /82 classes, zero failures/errors/skips; core imports and jar pass.
- Server test source `c5e99072612782fe9fcbc8c3ffaeebf936c03d94`: all13 FOUP cases pass, including two new IDs `lithmod:foup_substrate_persistence` and `lithmod:foup_substrate_public_trust`. Only GameTest code changed after the successful unit source; production blob comparison confirms applicability.
- Persistence case covers actual registered ItemStack save/load with hidden state and pure grade, old-field absence remaining ordinary, and invalid explicit grade rejection.
- Public/trust case varies private ID/rolls/flags/run parameters but gets identical public bytes. Pure public information survives; actual trustedContentsOf replaces the pure client placeholder with a fresh empty ordinary server lot. Real pure lots remain trusted.

- Fault `legacy-auto-upgrade` at `d7808140e157ec5bd317eefecadd25de4c603cb4` caused exactly its intended actual server assertion; unchanged tests and exact tree restored at `6fda2af26061043eb5fc51e726373090e6da6f19`.
- Fault `pure-client-trust` at `ee5667ebdd0aa055e2e06605c0461cd3f3315296` caused exactly its intended actual server assertion; unchanged tests and exact tree restored at `cecb395dc8e56b8fd9ccedd76c28f5c1e2836817`.

## Preserved draft/verification outcomes

Nemotron authored the foundation and unit/report drafts. The coordinator repaired three stale constructor calls and a missing Objects import, then six test API errors (NBT UUID and ByteBuf writers). Invalid-grade parsing was changed from a thrown enum conversion to a normal codec error. The first executed616-case unit run had three expectation failures: the old public-record shape, Optional string access, and a reversed ordinary/pure string-length comparison. The old schema test was updated under a separately immutable one-file coordinator supplement, preserving its method and strengthening exact public field names/types; both other assertions were repaired.

The coordinator added the two actual server cases. The first13-case run passed12 and failed the new trust assertion because it incorrectly expected null. Existing gameplay correctly returns a fresh empty ordinary lot; the corrected test verifies that exact contract and all13 then pass. No trust production code was changed for this fixture repair. All failed attempts remain preserved.

Supplement `euvcraft-t3-public-view-compat` owns only core/FoupContentsTest.java, omitted from the original narrow foundation scope. Its reflection/string test admits precisely capacity/waferCount/lotLayer/grade of int/int/int/WaferSubstrate; private fields remain prohibited. Final parent T3 reviews/candidate include both tasks.

Actual rendered tooltip inspection remains owner-pending. All workers pinned-free; no dependency/client/world/mk4 changes, paid image API, push or daemon activation.
