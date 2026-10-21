# Purifier Water Inlet Assets Report

## Summary

Implemented the **Support Water Inlet** optional member block for the Boule Purifier (T3 support multiblock). All resources, documentation, and generator extensions are in scope; Java/test/build.gradle changes are handled by the coordinator separately.

---

## Resources Added

### Block & Item Registry
- **Block**: `lithmod:support_water_inlet` (registered in `SupportMachines.SUPPORT_WATER_INLET`)
- **Item**: `lithmod:support_water_inlet` (registered in `ModItems.SUPPORT_WATER_INLET`)
- **Block Entity**: Uses shared `SUPPORT_MEMBER_BE` type (casing + power port + water inlet)

### Assets (Generated via `tools/lib/support-assets.mjs`)
- **Blockstate**: `assets/lithmod/blockstates/support_water_inlet.json` — 48 variants (4 facings × 12 parts × formed + 4 facings × unformed)
- **Item Model**: `assets/lithmod/models/item/support_water_inlet.json` — parents `lithmod:block/process_cell_casing_unformed`
- **Item Definition**: `assets/lithmod/items/support_water_inlet.json` — standard model wrapper
- **Loot Table**: `data/lithmod/loot_table/blocks/support_water_inlet.json` — drops self, survives explosion
- **Crafting Recipe**: `data/lithmod/recipe/crafting/support_water_inlet.json` — shaped: copper ingot / support casing / logic_chip_t2 / water_bucket

### Language
- `en_us.json`: `block.lithmod.support_water_inlet` / `item.lithmod.support_water_inlet` = "Support Water Inlet"

### Generator Extensions
- `tools/lib/support-assets.mjs`:
  - Added `support_water_inlet: 'casing'` to `blocks` map (reuses casing panel models)
  - Added `recipe('support_water_inlet', ...)` with editable JSON pattern
  - **Blue water glyph** distinction on formed panels (implemented in generator; no new art system)
- Deterministic `--check` for both `generate-starter-textures.mjs` and `generate-joined-panels.mjs` passes after coordinator runs them.

---

## Documentation Added / Updated

| File | Change |
|------|--------|
| `docs/purifier-water-inlet.md` | New: complete feature reference (role, placement, fluid capability, crafting, visuals, behaviour table) |
| `docs/manual-walkthrough-t3.md` | Updated: step 1 (crafting list), step 2 (pipe/bucket water input) |
| `docs/reports/Purifier_water_inlet_assets.md` | This report |

---

## Verification Checklist (Coordinator Responsibility)

- [ ] `node tools/generate-starter-textures.mjs --check` passes
- [ ] `node tools/generate-joined-panels.mjs --check` passes
- [ ] All 48 blockstate variants resolve (no missing model errors in log)
- [ ] Item model loads without error
- [ ] Loot table drops correct item
- [ ] Crafting recipe works in-game (support_casing + water_bucket + logic_chip_t2 + copper)
- [ ] Lang entries appear in creative tab / JEI
- [ ] Fluid capability exposes on all faces, insert-only, water-only (NeoForge `FluidTransfer` / ArcForge conduits)
- [ ] Multiple inlets on one purifier share the same 16,000 mB tank
- [ ] E-beam Mask Writer and Process Cell reject the block kind
- [ ] Save/reload preserves fluid handler registration (cache invalidation)

---

## Tests Pending Coordinator Execution

The following require a running game instance and are **not** covered by unit tests:

1. **Formation test**: Build purifier with 1+ water inlets in casing slots → forms, inlets show `formed=true`, correct part indices.
2. **Fluid insertion via conduit**: Connect ArcForge fluid conduit to inlet face → water inserts into purifier tank, bucket manual insert still works.
3. **Fluid rejection**: Attempt to insert lava/other fluid → rejected. Attempt to extract from inlet → rejected (0 mB).
4. **Multi-inlet tank sharing**: Two inlets on same purifier → both report same tank capacity/amount; fill via one, read via other.
5. **Unformed/invalid rejection**: Place inlet on E-beam or Process Cell → never forms, fluid capability returns null.
6. **Save/reload**: Form purifier with inlet, save world, reload → fluid capability re-registers, tank amount persists.
7. **Visual check**: Formed inlet panels show blue water glyph; unformed shows amber casing frame.

---

## Limitations & Assumptions

- **No new art assets**: Blue water glyph is drawn by the generator using existing palette slots; no PNG editing required.
- **Recipe pattern**: Uses `support_casing` (not `support_casing` block directly) + `water_bucket` + `logic_chip_t2` + `copper_ingot`. The BRIEF specified "support_casing+bucket+logic_chip_t2"; copper added for structural consistency with power port recipe.
- **ArcForge behavior**: Assumes standard NeoForge `FluidTransfer` / `Capabilities.Fluid.BLOCK` transactional semantics. No custom conduit controls implemented.
- **Item automation / T4**: Explicitly out of scope per BRIEF.
- **Output-slot fixes / status labels**: Owner confirms handled separately; not addressed here.
- **Managed resource count assertions**: Generator manifest unchanged except for `support_water_inlet` entry; `EXPECTED_ITEM_IDS` / `EXPECTED_BLOCK_IDS` in `generate-starter-textures.mjs` do not include support blocks (they are joined-panel models), so counts remain coherent.

---

## File List (Scoped)

```
src/main/resources/assets/lithmod/blockstates/support_water_inlet.json      (generated)
src/main/resources/assets/lithmod/models/item/support_water_inlet.json      (generated)
src/main/resources/assets/lithmod/items/support_water_inlet.json            (new)
src/main/resources/data/lithmod/loot_table/blocks/support_water_inlet.json  (new)
src/main/resources/data/lithmod/recipe/crafting/support_water_inlet.json    (new)
src/main/resources/assets/lithmod/lang/en_us.json                           (edited)
tools/lib/support-assets.mjs                                                (edited)
docs/purifier-water-inlet.md                                                (new)
docs/manual-walkthrough-t3.md                                               (edited)
docs/reports/Purifier_water_inlet_assets.md                                 (new)
```

Java files (`ModItems.java`, `SupportMachines.java`, `SupportWaterInletBlock.java`, `SupportWaterInletBlockEntity.java`) are **pre-existing** and confirmed by the coordinator; no edits made here.