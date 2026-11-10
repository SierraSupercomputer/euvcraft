# Light-source operating panel initially faces north

Status: repaired in 0.7.1 — automated checks pass; owner rendered recheck pending.
Reported: October 30, 2026.
Authorization: owner approved implementation on October 30, 2026.

The operating panel initially places facing north regardless of player direction, then rotates correctly when the light-source multiblock forms. EuvLightSourceMemberBlock defaults FACING to NORTH and has no getStateForPlacement override. Formation later assigns the controller facing, consistent with the observed behavior.

Future repair: use the established player-facing placement convention, audit sibling EUV member/controller placement and rotate/mirror behavior, and preserve formation's authoritative orientation. Verify actual placement in all four horizontal directions before and after formation. Do not change dimensions or build requirements.

Implemented the reported repair alongside source calibration. Verified 1,424 units, all 353 native project cases and fixture-disabled shipping. See [the source walkthrough](../../playtest-progression.md#calibration) for the owner recheck; no graphical acceptance is claimed.
