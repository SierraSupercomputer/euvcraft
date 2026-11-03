# Contamination recalibration reuses previous targets

Status: implemented — automated checks passed; explicit owner confirmation pending.
Reported: 2026-10-26 (America/Chicago).
Source baseline: 9e4f05dfdff54abc0aef41043d73d0ad5f11748d.
Authorization: owner approved diagnosis and repair on October26; reflow mechanics excluded.

## Report

When contamination requires recalibration, the values/targets do not change, allowing the previous solution to be reused. The owner wants the quadratic target to change and the beam/focus optics to move to a newly shuffled position when contamination invalidates calibration.

## Original acceptance criteria

Refresh the diagnostic target and beam-alignment target on the contamination invalidation event, rather than allowing free rerolls through reopening/reloading. Preserve any already admitted lot's frozen calibration/yield contract. No request to change reflow thermal-response behavior was inferred.

Current repair: Contamination invalidation now renews beam and quadratic targets once, persists them across reload and preserves already admitted lot contracts.

Recheck using the [current guide](../playtest-progression.md#troubleshooting). This card remains pending owner confirmation; it is not an assertion that the defect is still present.
