# FOUP cleaner walkthrough

Existing worlds are supported; this adds a standalone machine and waste item. The successful cleanroom implementation is unchanged. Use the new project build/libs/lithmod-1.0.0.jar. This walkthrough destroys the input wafers intentionally.

1. Find/craft FOUP Cleaner (lithmod:foup_cleaner). Its recipe uses iron, buckets, redstone and glass; no chip packages, calibration, cleanroom or ultrapure water are needed. Wafer Scrap appears in creative search.
2. Place the single block, connect FE and ordinary water through any side using the existing fluid framework. Alternatively place a water bucket in Water; one empty bucket appears in Bucket. A full Bucket output must prevent consuming another water bucket. Ultrapure water is not accepted.
3. Insert a partly processed or finished FOUP in FOUP. Nothing happens automatically. With at least20 FE and1000mB water, press Discard & Clean. It discards ALL wafers without selecting good/bad rolls.
4. Input stays locked. Default work takes200 powered ticks (10 seconds),20FE/t,4000FE total. Water is consumed once at admission. Remove power: progress pauses and resumes when power returns. No extra water is charged.
5. Collect the same carrier type from Clean, now empty with a fresh lot ID, capacity/name retained. Scrap contains one Wafer Scrap per discarded wafer. No chips or chip packages are produced/consumed.
6. Repeat with Mk2, ordinary/pure/G2-prepared and completed lots. An empty carrier must refuse a job. The emptied FOUP must accept a new normal batch and have no old processing/calibration/cleanroom state.
7. Block Clean or fill Scrap after starting: finishing waits without extra FE. Clear outputs and collect the result. For lots above64 wafers, remove each full Scrap stack; the carrier is released only when every scrap item has been emitted.
8. Save/reload during work and while waste is blocked: paid progress, water and remaining waste persist. Breaking before disposal returns the original wafer lot (spent services are lost). Breaking after disposal returns one empty carrier plus all physical and pending scrap.
9. Check five slots, labels, button, energy/water/status and disposal warning fit the panel; player inventory starts below the machine slots. Existing creative search, other machines and cleanrooms should remain functional.

Balance lives in src/main/resources/data/lithmod/machines/foup_cleaner.json (duration, energy, water_mb, water_capacity_mb, scrap_per_wafer) and recipe/foup_cleaner.json. Coordinator-selected initial defaults are editable. No waste recycling is added. Automatic T7 contamination/breach aborts and a Contaminated FOUP producer remain later work; this is the disposal endpoint for any current wafer lot.
