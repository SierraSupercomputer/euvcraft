# Purifier water inlet integration

Accepted 2026-10-21T01:33:08.470Z. Reviewed source/report: `aec9ffc4a8f7567eae0adc6a004c1db953fed483`. Verified candidate/integrated code: `920511d5b3cdb766e6da82c2859f99d5f2c3c8ce`.

The optional Support Water Inlet replaces any purifier casing position and accepts ordinary water on all faces through the same transactional NeoForge API used by installed Arcforge conduits. Multiple inlets share the controller tank/journal; bucket-only cells and GUI buckets remain supported. Lithography/E-beam reject the inlet. Status labels now use container-local coordinates and bounded widths inside their windows.

The independently compiled identical-tree candidate passed 755 units / 86 classes, core imports/release jar, all 900 distinct complete server cases and all 230 project IDs, retaining all 225 prior project IDs. No failures/errors/skips. Both texture checks match 154 PNGs / 210 JSON assets; T3/recipe guides match 50 recipe files (48 production/two fixtures). Mod/test compilation used --no-build-cache and only three hash-verified dependency artifacts. The coordinator Node suite passed 24/24.

Independent attested zero-cost free reviews:

- opencode/beam-free, session `ses_ea3f69ab8ffeQxEldEyJSrJY5g`, message `msg_15c0cc914001oo5vLRbZXMfHL3`: approve.
- opencode/nemotron-3-ultra-free, session `ses_ea3f63d4affemSxCs11fKbFwS1`, message `msg_15c09f3dc001XdTc9sn2Gzy0Yf`: approve.

The real interoperability case transfers water from a registered Arcforge tank through an actual fluid conduit and verifies the source debit. Native cases cover placement, transactions, cache/lifecycle, bucket/process and persistence. Source verification and retained failed drafts/fixtures are described in [the source report](Purifier_water_inlet.md). The pre-existing legacy M1 reference-generator inventory limitation remains tracked separately; T3 and recipe guides are current.

Verified jar: `build/libs/lithmod-1.0.0.jar`, SHA-256 `16118b38ff4cebd536909c79c3e965231c7df4d603acf2e3db36d31ecc174172`. Its previous project build jar was backed up. No client/world install, dependency change, mk4 edit, push or daemon activation.

The owner confirmed the previous complete T3 walkthrough and both output-slot repairs. The new inlet appearance and corrected status labels remain rendered-client checks. See [placement and crafting](../purifier-water-inlet.md).
