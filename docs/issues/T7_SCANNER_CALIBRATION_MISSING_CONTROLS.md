# EUV scanner calibration screen has no controls

Status: open — owner-reported walkthrough blocker; likely synchronization defect identified from source.
Reported: October 30, 2026.
Authorization: record and plan only; do not implement yet.

The formed EUV scanner's Calibration Panel GUI shows no adjustment buttons, blocking beam alignment and further playtesting. EuvScannerCalibrationScreen.init returns early when menu.value(0) is zero. The client menu starts with zeroed SimpleContainerData, so a formed-state packet arriving after init can leave the screen permanently without buttons. containerTick only changes existing button states; it never creates missing controls.

Future repair: create controls independently of initial synchronization, then enable/disable them from live formed/locked state. Preserve server authority, accepted alignment quality and active-lot locks. Audit related EUV diagnostic screens for the same initialization pattern and correct the calibration screen's inaccurate 5x7x5 message to actual width x height x depth 5x5x7. Verify delayed initial sync, opening while unformed then forming, resize/reopen and actual adjustment/acceptance packets. Treat the data race as the likely cause until reproduced in a rendered client.
