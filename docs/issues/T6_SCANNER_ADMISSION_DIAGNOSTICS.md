# Scanner reports generic tier failure when UPW is missing

Status: repair implemented; automated acceptance and owner recheck tracked in ../manual-walkthrough-t6-repairs.md.
Reported: 2026-10-26 (America/Chicago).
Source baseline: 9e4f05dfdff54abc0aef41043d73d0ad5f11748d.
Authorization: owner approved diagnosis and repair on October26; reflow mechanics excluded.

## Report

The scanner debug hint said "This line cannot run this tier" when the actual missing supply was ultrapure water (UPW). The hint did not provide a useful diagnosis.

## Future investigation / requested outcome

Distinguish missing UPW from unsupported tier/equipment/calibration and other admission prerequisites. Inspect both debug hints and visible machine status. Retain specific refusal information instead of collapsing water shortages into the tier message.

Repair details and recheck: [T6 repairs](../manual-walkthrough-t6-repairs.md). Keep this record pending until owner confirmation.
