# Contamination recalibration reuses previous targets

Status: repair implemented; automated acceptance and owner recheck tracked in ../manual-walkthrough-t6-repairs.md.
Reported: 2026-10-26 (America/Chicago).
Source baseline: 9e4f05dfdff54abc0aef41043d73d0ad5f11748d.
Authorization: owner approved diagnosis and repair on October26; reflow mechanics excluded.

## Report

When contamination requires recalibration, the values/targets do not change, allowing the previous solution to be reused. The owner wants the quadratic target to change and the beam/focus optics to move to a newly shuffled position when contamination invalidates calibration.

## Future investigation / requested outcome

Record this as the requested recalibration behavior, pending implementation authorization. Refresh the diagnostic target and beam-alignment target on the contamination invalidation event, rather than allowing free rerolls through reopening/reloading. Preserve any already admitted lot's frozen calibration/yield contract. No request to change reflow thermal-response behavior was inferred.

Repair details and recheck: [T6 repairs](../manual-walkthrough-t6-repairs.md). Keep this record pending until owner confirmation.
