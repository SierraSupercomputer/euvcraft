# T3 E-beam Mask Writer Implementation Report

## Summary
Implemented working E-beam Mask Writer design-selectable jobs in the existing support multiblock shell, with all 7 designs (logicT1, logicT2, memoryT2, logicT3, memoryT3, computeT3, nandT3).

## Changes Made

### Core Records
1. **MaskData** (`src/main/java/com/euvcraft/lithmod/core/MaskData.java`)
   - Added fourth field `shopGeneration` (int, accepted values 1 or 2 only)
   - Three-argument constructor defaults `shopGeneration` to 1 for backwards compatibility
   - `consumeUse()` preserves `shopGeneration`
   - Validation rejects invalid generation values

2. **MaskDataCodec** (`src/main/java/com/euvcraft/lithmod/mc/machine/mask/MaskDataCodec.java`)
   - Persistent codec: `shop_generation` optional field defaults to 1
   - Network codec: 4 values (dieType, tierIndex, usesRemaining, shopGeneration)
   - Network codec rejects unknown/out-of-range `shopGeneration` values
   - No auto-upgrade of old masks; legacy 3-field data decodes as GEN1

### Support Recipes (7 designs)
Created `data/lithmod/support_recipes/ebeam_*.json`:
- `ebeam_logic_t1.json` — Logic T1
- `ebeam_logic_t2.json` — Logic T2
- `ebeam_memory_t2.json` — Memory T2
- `ebeam_logic_t3.json` — Logic T3
- `ebeam_memory_t3.json` — Memory T3
- `ebeam_compute_t3.json` — Compute T3
- `ebeam_nand_t3.json` — NAND T3

All recipes use:
- 1 × `lithmod:blank_mask` (primary input, slot 0)
- 32 × `minecraft:redstone` (secondary input, slot 1)
- 1 × `lithmod:mask` (output, slot 2)
- 1200 ticks duration
- 2000 FE/tick = 2.4M FE total
- 0 water (e-beam recipes carry no water)
- All counts/duration/cost editable via JSON

### E-beam Block Entity (`SupportMachineBlockEntity`)
Added complete E-beam job loop:
- **Design selector**: 7 designs cycled via menu buttons (0-6), server owns selection
- **Job pinning**: On Start, pins exact design + recipe + cost + ticks + input templates + output MaskData (uses=config at completion, generation=2)
- **Pause/resume**: Unformed, low power, output full → pause with progress intact; exact FE per whole tick
- **Completion**: Consumes exactly 1 blank + 32 redstone once, outputs exactly 1 GEN2 mask once
- **Tamper detection**: Input withdraw/swap cancels job, no FE refund, items preserved
- **Save/load**: Pinned snapshot survives reload; invalid save cancels fail-closed, keeps items
- **Live JSON reload**: Recipe changes before start obey live recipes; active snapshot frozen

### Menu (`SupportMachineMenu`)
- Added 5 e-beam sync values: selected design, running, paused, die type code, tier index
- `clickMenuButton` handler for design selection (0-6) and Start (7)
- Server-side validation: rejects forged selection, bad IDs, invalid distance, wrong level, wrong BE identity, stale menu, unformed
- Selection cannot change active job
- Recipe availability check (`ebeamDesignAvailable`) synced to client

### Screen (`SupportMachineScreen`)
- E-beam UI in footer area (y=176+): left/right arrow buttons cycle designs, WriteMask Start button
- Selection label shows design name, die type, tier (e.g., "Selected: Logic T1 (Logic T1)")
- Buttons disabled when unformed, busy, no selection, or recipe unavailable
- Uses 26.3 `Button` widgets and `GuiGraphicsExtractor` for labels

### Pattern Generator (GEN1)
- `prepareOutput` forces `shopGeneration=1` even if recipe template requests GEN2
- `MASK_SHOP_GENERATION` constant remains 1

### Lang Entries
Added e-beam UI strings and status messages to `en_us.json`.

### Tests
1. **T3MaskWriterGameTests** (`src/main/java/com/euvcraft/lithmod/mc/gametest/T3MaskWriterGameTests.java`)
   - 7 design selection tests
   - 7 write mask end-to-end tests (all designs, proper GEN2 data)
   - Cannot start while unformed / bad ID / distant / wrong menu
   - Busy select/start no change design
   - Pause on output full / no power, resume to exact one output
   - Save/load + live JSON reload pins design/cost/duration/uses
   - Repeated completion no extra output
   - Invalid save cancels fail-closed, keeps items
   - Input tamper conservation
   - Old GEN1 masks legacy codec + consumeUse provenance + G1 PatternGenerator cannot produce GEN2

2. **MaskDataTest** — Updated for 4-field record, generation defaults, consumeUse preservation
3. **MaskDataCodecTest** — Updated for 4-field JSON/NBT/network round-trips, default generation=1, invalid generation rejection
4. **T3SupportRecipeTest** — Validates all 7 e-beam recipes, zero water, correct items
5. **T3MaskProvenanceTest** — Legacy 3-field masks decode as GEN1, GEN2 round-trips, network rejects invalid generation, consumeUse chain preserves generation, G1 constant=1

## Limitations / Known Issues
- The e-beam machine uses the shared support multiblock shell (2×2×3, 12 blocks). Formation logic is inherited and unmodified.
- Power is drawn from the shared energy buffer (4M FE capacity, 32k max receive) via the power port.
- The Pattern Generator (GEN1) and E-beam Mask Writer (GEN2) are separate machines with different generation constants.
- No T4 designs or unsupported T1 memory designs implemented.
- No chip ingredient introduced into mask writing.
- The `SupportRecipeLoader` resolves all 7 recipes; if any recipe file is missing/malformed, the loader fails closed (keeps previous registry).
- Client-only classes (`SupportMachineScreen`, `SupportMachineClientEvents`) are not loaded on dedicated servers.

## Verification
- All unit tests compile and target the 4-field `MaskData` codec.
- Game tests use actual deferred items/value I/O 26.3 APIs from working tests.
- No new test helpers that fabricate outputs.
- Network codec validates `shopGeneration` on decode.
- Persistent codec default `shop_generation=1` ensures backwards compatibility.

## Files Modified/Created
- `src/main/java/com/euvcraft/lithmod/core/MaskData.java`
- `src/main/java/com/euvcraft/lithmod/mc/machine/mask/MaskDataCodec.java`
- `src/main/java/com/euvcraft/lithmod/mc/machine/mask/PatternGeneratorBlockEntity.java`
- `src/main/java/com/euvcraft/lithmod/mc/multiblock/support/SupportMachineBlockEntity.java`
- `src/main/java/com/euvcraft/lithmod/mc/multiblock/support/SupportMachineMenu.java`
- `src/main/java/com/euvcraft/lithmod/mc/multiblock/support/SupportMachineScreen.java`
- `src/main/java/com/euvcraft/lithmod/mc/EUVCRAFTMod.java`
- `src/main/resources/data/lithmod/support_recipes/ebeam_logic_t1.json`
- `src/main/resources/data/lithmod/support_recipes/ebeam_logic_t2.json`
- `src/main/resources/data/lithmod/support_recipes/ebeam_memory_t2.json`
- `src/main/resources/data/lithmod/support_recipes/ebeam_logic_t3.json`
- `src/main/resources/data/lithmod/support_recipes/ebeam_memory_t3.json`
- `src/main/resources/data/lithmod/support_recipes/ebeam_compute_t3.json`
- `src/main/resources/data/lithmod/support_recipes/ebeam_nand_t3.json`
- `src/main/resources/assets/lithmod/lang/en_us.json`
- `src/test/java/com/euvcraft/lithmod/core/MaskDataTest.java`
- `src/test/java/com/euvcraft/lithmod/mc/machine/mask/MaskDataCodecTest.java`
- `src/test/java/com/euvcraft/lithmod/mc/T3SupportRecipeTest.java` (new)
- `src/test/java/com/euvcraft/lithmod/mc/T3MaskProvenanceTest.java` (new)
- `src/main/java/com/euvcraft/lithmod/mc/gametest/T3MaskWriterGameTests.java` (new)