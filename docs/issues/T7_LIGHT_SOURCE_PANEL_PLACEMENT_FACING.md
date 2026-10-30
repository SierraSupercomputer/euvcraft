# Light-source operating panel initially faces north

Status: open — owner reproduced; placement code inspected, no rendered reproduction performed by coordinator.
Reported: October 30, 2026.
Authorization: record and plan only; do not implement yet.

The operating panel initially places facing north regardless of player direction, then rotates correctly when the light-source multiblock forms. EuvLightSourceMemberBlock defaults FACING to NORTH and has no getStateForPlacement override. Formation later assigns the controller facing, consistent with the observed behavior.

Future repair: use the established player-facing placement convention, audit sibling EUV member/controller placement and rotate/mirror behavior, and preserve formation's authoritative orientation. Verify actual placement in all four horizontal directions before and after formation. Do not change dimensions or build requirements.
