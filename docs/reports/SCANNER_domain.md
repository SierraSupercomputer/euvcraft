# Scanner domain and calibration

Nemotron's original domain draft is preserved at ed109dc1d6b596d4fb8331e965d4bbe543c3a30b. Coordinator repairs added constructor bounds, a strictly positive multiplier range, and corrected contradictory draft tests / fixture mutations. Original completion claims were not verification evidence.

ScannerStructureMatcher.match(MultiblockPos, MultiblockDirection, Function<MultiblockPos,BlockKind>) returns existing MatchResult. Geometry is 3×3×5, with a lower-front controller in any of three columns, operating panel directly above, and all four horizontal facings. The middle layer has seven special blocks: operating panel, calibration panel and five distinct physical modules. Eight other middle positions accept casing or allowed power ports. One or two ports may occupy other available frame positions. All other cells are casing; total casing count is36 with one port or35 with two. The matcher rejects missing/extra modules, panels, controllers, water inlets, incorrect layers and port counts. Returned45 positions follow canonical u/v/d order.

BeamAlignment is immutable and validates all six constructor values. Knobs x/y/focus lie in[-10,10], targets in[-5,5]. Seeded creation is deterministic, bounded and solvable. Controls0/1 decrement/incrementX,2/3Y,4/5focus. Spot offsets are2*(x-targetX)+(y-targetY), and(x-targetX)-2*(y-targetY); focus error is absolute focus deviation. Quality is clamp(100-4*(abs(spotX)+abs(spotY))-10*focusError,0,100).

multiplier(min,max) requires finite 0<min<=max<=1 and returns min+(max-min)*quality/100. Zero is intentionally unsupported: accepted process contracts require a positive factor. The upper bound1.0 is supported and tested. No hidden lot yield, private rolls, timers, reflexes or random equipment failure are involved.

At frozen integration b82438cff7c3b5b210e9ae3b92a56a879acc75bb, all1009 units and all24 selected T4/scanner native cases passed. Final source additionally clarifies documentation, restores detailed operating status/FE and tests accepted work through reload while a frame is missing. Final whole-suite and independent review receipts remain coordinator-owned.
