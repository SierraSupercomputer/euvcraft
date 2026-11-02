# EUVCRAFT T5–T7 Linear Playtest Walkthrough

Use fresh T5–T7 batches. Old processed lots lack the new paid-route evidence and cannot automatically count as coated/cleaned. The ordinary-water/FE **FOUP Cleaner** discards wafers (one Wafer Scrap per wafer); the new **Wet-Clean Station** processes good wafers and keeps them for packaging. Existing worlds and panel arrangements remain usable. New stage artwork reuses existing assets; rendered GUI/model appearance awaits playtesting.

## T4 Baseline (already verified)
1. Run pure boule → slicer → deposition → T4 scanner (resist inside) → etch → package. The owner accepted this checkpoint on November 2.

## T5 Full Route (Pure Boule)
2. Pure boule → slicer → G2Deposition (3×3×5) → CoatingTrack (3W×2H×5D) → Immersion Scanner → G2Etch (3×3×5) → WetCleanStation (2×2×3) → AdvancedTestPackage.
3. Supply T5 resist in the coater: 42/lot by default, 1/layer, 5 coating ticks/layer. Resist is consumed gradually; T6/T7 need refilling beyond a stack of 64.
4. Scanner resist slot empty (paid upstream); FE/time/UPW split coat/scan/clean once, frozen receipt carries cost.

## T6 Full Route (Electronic-Grade Boule)
5. Same external path as T5; Electronic-Grade boule, 70 resist/lot, multi-patterning module + cores → existing reflow CPU/DIMM downstream.

## T7 Full Route (Electronic-Grade Boule + Cleanroom)
6. Electronic-grade boule → slicer → G3 Deposition (5×5×7) → **same Coating Track, 80 T7 resist** → EUV Scanner (5×5×7) + calibrated source/H₂/vacuum → G3 Etch (5×5×7) → **same Wet-Clean Station** → advanced package → existing reflow. The separate light source receives no FOUP.
7. **All T7 stages require fully enclosed cleanroom.** Coater/cleaner only hard-require it for T7; T5/T6 outside allowed with yield penalty.
8. No new minigames; keep beam/diagnostic/source pulse calibrations.

## Hardware Layout Rules (Coater & WetCleaner)
9. Advanced Casing + 1–2 Advanced Power Ports (anywhere) + upper Advanced Operating Panel immediately above lower controller; controller any lower front column, any horizontal facing.
10. Coater: optional Fab UPW Inlet for T7 repairs. WetCleaner: **requires** one Fab UPW Inlet (any casing position), UPW only via Arcforge pipes.
11. Upper Coater GUI: slot 0 FOUP, 1 resist, 2 redstone (service), 3 output. Tier = resist grade (T5/T6/T7), no mask in coater.
12. Tier commits after coating; family/cores set at mask exposure.

## Etch & Yield
13. G2/G3 Etch: 1 Etchant Charge/lot; G3 uses existing G3 casing/panel/power/deposition/rinse/vacuum/water module kit as placeholder.
14. Etch preserves exposure rolls. T7: half quality budget per stage (not double penalty); room yield finalized **once** after WetClean.

## Process Guards
15. Wrong-step refusals must **not** consume inputs.
16. Packaging etched T5+ must request WetClean first.
17. Test power/water/resist shortage → pause → reload → one completion; one mask use per lot.

## T7 Breach & Repair
18. Breach during an active T7 run contaminates the FOUP and stops operation. Discard its scrapped wafers with the FOUP Cleaner. New coater/cleaner controllers retain a repair condition; their default repair cost is 2 Utility Filters + 16 Redstone + 8 000 UPW + 200 000 FE (configurable JSON). Existing G3/scanner repair workflows remain.
19. Repair a new coater/cleaner: seal room → put filters in input 1, redstone in input 2 → supply UPW and FE → press Repair in the upper panel. Coater may need its optional UPW inlet installed for repair.
20. **Repairs never restore contaminated wafers.**

## Automation & Ports
21. Optional FabItemPort replaces AdvancedCasing; G3FabItemPort for G3 frames, EUVFabItemPort for EUV scanner.
22. Finished outputs + empty return-pack FOUP extractable; active/queued wafers & modules protected.
23. Use actual Arcforge pull/push settings + cleanroom wall ports.

## Verification Checklist
24. Run 2 successive FOUPs through each tier (T5, T6, T7).
25. Confirm items appear in Creative tabs: Resources / FabBlocks / Items.

## Progression Gates
26. High-bin logic/compute T3→4; source route T3.
27. Logic/compute T4→5: source T4 etch only, **no** new T5 coating/cleaning.
28. T5 compute→6: source T5 full route.
29. T6 compute→7: source T6 full route + target T7 cleanroom.
30. No T7→8, no NAND assembly, recycler, server hardware, T8, model bay, reflow redesign.
31. No GitHub push/release/client restart. GUI/model polish pending; final JAR from coordinator.
