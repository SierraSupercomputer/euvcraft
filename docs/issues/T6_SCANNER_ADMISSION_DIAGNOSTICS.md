# Scanner reports generic tier failure when UPW is missing

Status: implemented — automated checks passed; explicit owner confirmation pending.
Reported: 2026-10-26 (America/Chicago).
Source baseline: 9e4f05dfdff54abc0aef41043d73d0ad5f11748d.
Authorization: owner approved diagnosis and repair on October26; reflow mechanics excluded.

## Report

The scanner debug hint said "This line cannot run this tier" when the actual missing supply was ultrapure water (UPW). The hint did not provide a useful diagnosis.

## Original acceptance criteria

Distinguish missing UPW from unsupported tier/equipment/calibration and other admission prerequisites. Inspect both debug hints and visible machine status. Retain specific refusal information instead of collapsing water shortages into the tier message.

Current repair: Scanner admission reports specific prerequisites, including missing UPW, instead of reducing all failures to a tier refusal.

Recheck using the [current guide](../playtest-progression.md#troubleshooting). This card remains pending owner confirmation; it is not an assertion that the defect is still present.
