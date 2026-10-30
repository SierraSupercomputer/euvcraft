# EUV component placement crash

Reported October 30, 2026. Patched locally; owner client recheck pending.

Placing an EUV Scanner Operating Panel at (6, -59, -61) crashed the integrated server while ticking EuvScannerMemberBlockEntity.serverTick. The same faulty pattern existed in light-source member ticks. Controllers and members separately created identically named formed/part properties, but member tick and formation code used controller property instances. The panel's block state did not contain those instances, so reading formed threw IllegalArgumentException. Guarded formation updates also silently skipped members.

Scanner and light-source members now share their controller's exact formed/part instances. This fixes initial placement and orphaned saved-state cleanup, and restores joined formed visuals. Light-source detachment also clears its controller's visual state, matching scanner behavior.

Two native regression cases cover every registered EUV member in all four horizontal facings, unformed ticking, stale formed states across block-entity save/reload, later world ticks, complete scanner/source formed visuals and unique panel indices, and clearing controller/member visuals after a frame breach.

Verified 1,402 unit cases, all 349 project GameTests (347 previous cases retained plus two regressions), core/JAR checks and a separate fixture-disabled shipping probe on NeoForge 26.3.0.99. Original crash report and failed test-helper compile attempts remain preserved in the coordinator evidence. No player world was edited. Existing blocks retain the same serialized property names/ranges; no new world or block replacement is required for this fix. Restart runClient after closing the crashed client normally. Public v0.7.0 is not replaced by this local patch.

Development JAR SHA-256: 5c6f06c791f58fd51561383a79def8001eac8d331543c5f3ece262c7f848c9c4.
