# FOUP preparation missing or still shown as G1

Status: repair implemented; automated acceptance and owner recheck tracked in ../manual-walkthrough-t6-repairs.md.
Reported: 2026-10-26 (America/Chicago).
Source baseline: 9e4f05dfdff54abc0aef41043d73d0ad5f11748d.
Authorization: owner approved diagnosis and repair on October26; reflow mechanics excluded.

## Report

The FOUP tooltip does not visibly show G2 preparation after deposition/etch. After the FOUP goes through the scanner, preparation still says G1. These are two observed stages of the report; a common cause has not been established.

## Future investigation / requested outcome

Trace actual preparation state through etch output, tooltip/public view, scanner admission and completed scanner output. Distinguish a display/synchronization defect from a state-propagation defect. Compare Creative and Survival; do not assume the scanner should create preparation itself.

Repair details and recheck: [T6 repairs](../manual-walkthrough-t6-repairs.md). Keep this record pending until owner confirmation.
