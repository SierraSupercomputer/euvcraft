# Creative FOUP preservation

Update the mod on both client and server. Existing saves remain compatible; no new world required. Already damaged processed FOUP public summaries cannot recover missing rolls/run data: recreate those lots.

1. In Survival, slice electronic-grade wafers into a Mk2 FOUP, prepare G2 in deposition/etch, and process them through the calibrated T6 scanner with UPW. Note the lot information from /debug foup inspect.
2. Switch to Creative while holding that FOUP. Open the E inventory; move it between slots and hotbar, put it on the cursor and back, and use Creative clone. Inspect each copy: lot identity, rolls, frozen run/core count, preparation and room history should match the original. Copying does not reroll.
3. Put a copy in Advanced Test & Package with FE and Chip Packages. Disable High Binning for T6; no T7 transition exists. It should process normally.
4. Repeat a processed lot move with Mk1. Check normal machine-menu click and shift transfer, and Creative hotbar/clone handling.
5. Switch back to Survival. Server-side debug inspection should retain the exact lot; ordinary client data returns to the public projection. Repeat scanner-to-packager without Creative as a regression.
6. Save/reload and check the preserved lot. Repeat on a dedicated server if used.

Creative is intentionally privileged to inspect/copy the full immutable lot. Survival clients receive only public fields and cannot submit authoritative full lots. No permanent lot registry is used. GUI/gameplay owner acceptance is pending; automated evidence is recorded by the coordinator.
