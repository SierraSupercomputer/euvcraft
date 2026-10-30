# EUV light-source pulse calibration — proposal

Status: owner approved this proposal and all three issue repairs on October 30, 2026. Implemented in 0.7.1; automated checks pass and owner graphical playtesting remains pending. The subsequent owner request authorizes publication as 0.7.1 after checks pass. Earlier planning-only language below is retained as design history.
Written: October 30, 2026.

## Goal and physical inspiration

Introduce a source-specific interactive challenge alongside the scanner's beam alignment and quadratic diagnostics. Real laser-produced-plasma sources feed tin droplets past a pre-pulse that reshapes them, then a stronger pulse creates EUV-emitting plasma. This proposal models droplet/laser synchronization and main-pulse amplitude, rather than wafer tin deposition. See ASML's primary explanation: https://www.asml.com/en/technology/lithography-principles/light-and-lasers . Real pulse frequencies and plasma dynamics are represented by a small deterministic gameplay model.

## Recommended player interaction

Add one physical pulse-control module and one separate Source Calibration Panel, freely placed in unused middle-layer positions of the existing 3x3x5 source. Keep operating and equipment interfaces uncluttered; no dimension increase is proposed. These block additions and their editable recipes are recommendations for review, not settled requirements.

The panel resembles a slow oscilloscope. It shows tin-arrival markers, the pre-pulse reference, an adjustable main-pulse marker and the last measured EUV response, with a target band for stable output. There is no reflex-clicking or latency-sensitive timing requirement.

Three controls have distinct roles:

1. Droplet phase aligns tin arrival with the pre-pulse reference.
2. Main-pulse delay controls when the main laser hits the expanding tin target after the pre-pulse.
3. Main-pulse amplitude controls laser strength; too little or too much moves output away from the desired response.

Use coarse/fine adjustments. Player changes settings, then presses Test Pulse. A short paid sample produces a measured trace and feedback such as pre-pulse misses droplet, main pulse too early or overdriven. Aim for good overlap, sufficient output and a consistent response, then Accept Calibration. Last-tested settings and quality remain clearly distinct from untested knob changes. Do not reveal a hidden perfect-settings tuple or require matching displayed coefficients again.

## Costs and scoring

Each test batch pays FE, a small amount of Fab Hydrogen and a bounded amount of existing saved tin exposure credit. Avoid charging one entire feedstock item for every small adjustment; a configurable pulse-credit cost can share the existing conserved tin budget. No paper or wafer is needed. Keep test costs, control ranges/steps, target generation, tolerance bands, minimum accepted quality and multiplier bounds editable in machine JSON.

A deterministic model scores pre-pulse overlap, main-pulse timing and amplitude response, combining them into a visible 0-100 quality. Targets are server-generated for the installed source hardware and stable across reopen/save/reload. Initial recommended yield range is 0.85-1.00, subject to owner balance review; expose acceptance threshold and bounds in JSON. Poor calibration should reduce output quality, not cause explosions, new persistent damage or an unrelated random failure roll in this increment.

## Production integration

Recommend requiring an accepted source calibration before production. The scanner keeps its existing alignment and diagnostics. Show explicit readiness messages pointing to the correct panel.

Calibration trials must work without a connected or calibrated scanner: require the source itself to be formed, powered, pumped down and supplied with tin/hydrogen. This avoids a source/scanner calibration dependency cycle. Do not add a new cleanroom rule for the source through this feature.

Recommend adding accepted source quality as one fixed multiplier to wafer yield, frozen once at lot admission with the source installation identity, scanner calibration and other existing lot factors. Apply it once; do not reroll wafers, multiply once per layer or reduce mask uses as well. Display the individual and combined factors. Ordinary shortages pause without changing accepted calibration or lot quality. Switching sources or calibrating midway must not improve an admitted lot for free; prefer pausing with an explicit original-source requirement rather than silently rebinding to a new quality.

New pulse-module installation/replacement or a source rebuild invalidates acceptance and generates a new target. Existing accepted settings survive normal shutdown, chunk unload, resource refill and reconnection of an unchanged installation. Do not require source recalibration on every mask or add passive drift in this increment. Relevant repair can invalidate calibration if later repair mechanics change source hardware; no new source repair system is proposed here.

## Implementation sequence after approval

1. Repair the three recorded playtest issues first: placement facing, calibration control synchronization and beamline status. Confirm the current T7 route is usable.
2. Add the pure pulse model, saved source calibration state and editable parameters. Keep target generation and resource payments server-authoritative and deterministic.
3. Add pulse module/panel blocks, editable recipes, straightforward art, tooltips and the scope display. Calibration input/acceptance must validate the actual player, menu, machine, module and unchanged last-tested settings.
4. Integrate the source admission gate and one-time lot multiplier. Pay trials atomically; missing resources or an invalid machine must not partially consume tin/FE/gas. Lock tuning while the source serves an active lot.
5. Verify target solvability, coarse/fine controls, no quality increase by amplitude spam, no rerolls/reopen exploits, persistence, module invalidation, independent calibration bootstrap, exclusive source binding, frozen lot quality, exact resource conservation, pause/resume and existing T7 behavior. Render-check controls under delayed sync and resizing; update the walkthrough with a worked calibration example.

## Review decisions

Recommended defaults to confirm before implementation: dedicated module plus panel within the current source dimensions; three controls; FE/hydrogen/tin-credit test cost; required accepted source calibration; one frozen yield multiplier with proposed 0.85-1.00 bounds. Exact recipe/material costs, sample costs, acceptance threshold and final multiplier bounds remain balance choices. This plan does not authorize implementing them.
