# Scanner does not show progress within a layer

Status: implemented — automated checks passed; explicit owner confirmation pending.
Reported: 2026-10-26 (America/Chicago).
Source baseline: 9e4f05dfdff54abc0aef41043d73d0ad5f11748d.
Authorization: owner approved diagnosis and repair on October26; reflow mechanics excluded.

## Report

The scanner GUI shows the layer number incrementing, but the player cannot see per-layer progress.

## Original acceptance criteria

Review public progress synchronization and the operating-panel presentation. Make progress within the current layer/pass understandable alongside the layer count. No implementation choice made in this report.

Current repair: The scanner exposes current pass/layer progress as well as the layer number.

Recheck using the [current guide](../playtest-progression.md#troubleshooting). This card remains pending owner confirmation; it is not an assertion that the defect is still present.
