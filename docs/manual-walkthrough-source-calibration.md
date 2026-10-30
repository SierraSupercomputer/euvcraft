# EUV source pulse calibration walkthrough — 0.7.1

The source remains 3 wide x 3 tall x 5 deep. Upgrade an existing source by replacing two unused middle-layer casings with exactly one **EUV Pulse Control Module** and one **EUV Source Calibration Panel**. They may occupy any otherwise unused middle-layer positions. Keep the operating panel immediately above the controller. The top/bottom frame requirements and other modules/ports are unchanged. The controller's Shift tooltip lists the new requirements.

## Prepare the source independently

Supply FE, Fab Hydrogen and tin feedstock through the existing inputs, then allow its vacuum pump to reach operating pressure. Open the separate Source Calibration Panel. The scanner does not need to be calibrated or connected to run pulse trials.

Starting defaults per Test Pulse are **12,000 FE + 50 mB Fab Hydrogen + 25 tin-credit ticks**. One feedstock item provides 1,000 credit ticks, so its remaining credit can fund later tests or exposure; a test does not consume a whole item every time. Pumps have their own continuing FE cost. All values and control steps are editable in data/lithmod/machines/euv_light_source.json.

## Tune and accept

1. Press Test Pulse for the first paid measurement. Markers and the amber waveform show that last test, not a free prediction of untested settings. The green output band marks the desired response.
2. Adjust Droplet Phase to synchronize tin with the pre-pulse; use the increase/decrease feedback and coarse/fine buttons.
3. Adjust Main-pulse Delay to time the stronger pulse after shaping the droplet.
4. Adjust Pulse Amplitude to produce the desired response. More power is not automatically better: overdriving reduces quality.
5. Test again after changing settings. Accept requires a fresh test of the current settings at quality 60 or better by default. Aim for a higher score to improve yield.

At default settings, accepted quality 60 maps to a source yield multiplier of 0.85; quality 100 maps to 1.00. Intermediate scores scale between those bounds. This source factor combines once with scanner alignment/diagnostics and the existing wafer/room factors when a lot starts. The scanner's operating panel shows the source factor; its menu reports the frozen factor during an active lot.

Calibration and target survive closing the screen, normal save/reload, resource refill and reconnecting an unchanged installation. Replacing the physical Pulse Control Module or rebuilding the source requires fresh calibration. Settings cannot be changed or tested while the source is reserved by an active lot. Abort a lot before changing source hardware. No new passive drift, mask-triggered reset, paper requirement or source repair minigame is added.

## Link and operate

The beamline must be straight and loaded, from the scanner Exposure Module to plain light-source casing. The source now displays a linked scanner's coordinates before the first exposure, separately from its operating readiness. Calibrate the source and the scanner's existing beam/diagnostic panels, supply FE/UPW/hydrogen/tin and pump both chambers down.

The light-source and scanner calibration controls are created before synchronization; they should appear on first open and become enabled when live formed data arrives. Test first-open, resize and reopening. Place unformed light-source panels in all four directions and confirm they face the player before assembly.

## Regression checks for playtesting

- Reopen/reload after acceptance: same target, settings and accepted quality; no free reroll.
- Try a test without FE, hydrogen or tin credit: refusal with no partial payment.
- Check the tin credit after several tests; verify the remainder also pays normal exposures.
- Start a lot with deliberately imperfect accepted calibration, then interrupt FE/hydrogen supply: it pauses and keeps its admitted source factor.
- A different replacement source cannot improve an active lot by substituting a new quality; the scanner requests its original source. Abort explicitly if changing installations.
- Continue the existing T7 walkthrough, including all layers, mask decrement, final packaging and reflow.

The published 0.7.0 source did not require these two new blocks. Existing T7 sources need the upgrade before they form; finish/abort in-flight T7 runs before updating. Already completed FOUPs and lower-tier progression retain their data. No new world is required by the property/GUI repairs.
