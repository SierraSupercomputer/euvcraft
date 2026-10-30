# Light source says Connect to scanner despite an assembled beamline

Status: repaired in 0.7.1 — automated checks pass; owner rendered recheck pending.
Reported: October 30, 2026.
Authorization: owner approved implementation on October 30, 2026.

Owner reports both machines formed, with a beamline from the scanner's rear exposure module to a rear light-source casing; source still says Connect to scanner. Calibration is blocked by a separate GUI issue.

That endpoint arrangement agrees with EuvBeamlineConnection: a short straight loaded line from an exposure module must terminate at plain source casing. The source menu's connection value uses hasScannerReservation(), which only checks pairedScanner. That field is first set during a successful paid exposure in payExposure; simply forming and connecting the machines does not establish this reservation. Thus the message does not establish that the physical beamline is wrong, and scanner calibration is not itself a physical-link condition.

Future repair: separate physical connection, reservation/ownership and operating readiness in status. Show linked scanner coordinates and specific missing conditions (scanner calibration, source calibration if later approved, vacuum, FE, hydrogen, tin, ambiguous line or unloaded endpoint). Preserve one-source-per-scanner exclusivity and transactional exposure accounting. Test a valid idle link before calibration/first exposure, disconnected and ambiguous lines, resource shortages, reload and chunk boundaries. Do not reserve or charge consumables merely to display a connection.

Implemented the reported repair alongside source calibration. Verified 1,424 units, all 353 native project cases and fixture-disabled shipping. See [the source walkthrough](../../manual-walkthrough-source-calibration.md) for the owner recheck; no graphical acceptance is claimed.
