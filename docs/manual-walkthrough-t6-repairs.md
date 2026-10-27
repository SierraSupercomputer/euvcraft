# T6 repair recheck

October27 follow-up: processed Creative copies are now preserved for intact lots via Creative-only full transport. Earlier fresh-only limitations below describe the prior repair. See [Creative FOUP walkthrough](manual-walkthrough-creative-foup.md). Already lost results remain unrecoverable.

Reflow oven mechanics are unchanged and its reported controls/ranges remain deferred. The old jar is backed up before publication. Restart with the updated project jar. Use a newly sliced FOUP for the Creative recheck, since an earlier client replacement may already have lost preparation metadata.

1. In Creative, slice an Electronic-Grade Boule into a Mk2 FOUP. Move it through the player inventory and into the etch machine's upper panel, including an immediate carried click and shift-click. With the normal silicon and FE supplies it should start. Repeat with Mk1 and in Survival. Real server lots and unrelated item metadata should stay intact.
2. Etch output should show electronic-grade substrate and preparation G2. Complete scanner processing and check G2 remains in the tooltip. Grade and preparation are separate values.
3. In the E-beam writer, choose T6 compute and cycle cores. FE storage/top bar and total batch FE row should be readable above the controls; cores/start should remain usable.
4. For a calibrated T5/T6 scanner, remove UPW. It should say to supply UPW rather than claiming the line cannot run the tier. Refill and check processing starts/resumes. Incomplete structure, calibration and unavailable room chunks have distinct public reasons.
5. During a scanner layer, watch the Exposure ticks/percentage and thin progress bar advance before the layer number increments. Power/UPW shortages should freeze progress, and refill should resume it.
6. Utility plant should show an active batch's progress and admitted output volume (8,000mB by default). Observe halfway through a batch, a power pause, an output-full pause and save/reload. Inputs/FE/output costs remain unchanged; no hardcoded bucket-count admission was added.
7. In a dirty/outside T6 scanner, let paid processing reach the contamination threshold. The current lot should finish with its original frozen calibration. For the next lot, beam position/target and the quadratic target should differ; the previous solutions should fail the default quality floors, and the previous paid fit should be invalid. Reopen/reload and check targets stay fixed. Accept new beam alignment and a new paid diagnostic fit to resume. Custom very permissive fit floors can allow approximate old solutions by design.

Privacy limit: only fresh-layer Creative summaries are repaired. Processed client summaries cannot recover secret wafer rolls and remain untrusted; move processed FOUPs through normal server-managed container transfers. No perfect-wafer reconstruction was introduced.

Native/Gradle receipts and model attestations are retained by the coordinator. The development client capture attempt failed during native OpenGL startup before any GUI screenshot; do not treat source layout inspection as an in-game render pass.
