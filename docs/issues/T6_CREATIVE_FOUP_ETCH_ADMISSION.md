# Creative-handled Mk2 FOUP does not start deposition/etch

Status: implemented — automated checks passed; explicit owner confirmation pending.
Reported: 2026-10-26 (America/Chicago).
Source baseline: 9e4f05dfdff54abc0aef41043d73d0ad5f11748d.
Authorization: owner approved diagnosis and repair on October26; reflow mechanics excluded.

## Report

A Mk2 FOUP filled with electronic-grade silicon wafers while handled in Creative did not start processing in the deposition/etch machine. The same process performed in Survival did start. This is a reported recurrence of the earlier Creative FOUP problem.

## Original acceptance criteria

Reproduce the same Mk2 carrier/substrate/preparation path in Creative and Survival, including inventory handling and insertion. Check carrier initialization, identity, trusted/public contents and admission before choosing a fix. Cause not established.

Current repair: Creative-only transport now preserves full private lot data and refreshes inventories on mode changes. Previously stripped processed lots cannot be reconstructed; use a fresh lot.

Recheck using the [current guide](../playtest-progression.md#troubleshooting). This card remains pending owner confirmation; it is not an assertion that the defect is still present.
