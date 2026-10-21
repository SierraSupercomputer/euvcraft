# Purifier Water Inlet

The **Support Water Inlet** is an optional support-machine member block that replaces a Support Casing position in the **Boule Purifier** multiblock. It provides a fluid insertion point for water, allowing automated water supply via pipes/conduits in addition to manual bucket insertion.

---

## Role & Placement

- **Optional**: The Boule Purifier forms and runs without any water inlets. Add them only when you want piped water input.
- **Replaces casing**: Any of the 10 casing positions (top/bottom/sides/back/front — all non-controller, non-power-port positions) may be a water inlet instead of a Support Casing.
- **Multiple allowed**: You can place several inlets on the same purifier (e.g., one on each face). All inlets connect to the **same shared 16,000 mB water tank** inside the controller.
- **Purifier only**: The E-beam Mask Writer and the Process Cell reject this block kind entirely. A water inlet placed on those structures will never form.

---

## Fluid Capability

- **Insert-only (fill)**: Water inlets accept fluid from pipes/conduits on **all six faces**. Extraction (drain) is always rejected.
- **Water only**: Only `minecraft:water` (ordinary water) is accepted. Other fluids — including modded waters — are rejected.
- **Shared tank**: All inlets on a formed purifier delegate to the controller's single `WaterTank` (16,000 mB capacity, matching the bucket default).
- **Transactional**: Uses NeoForge's `FluidTransfer` / `IFluidHandler` API. ArcForge 26.3 fluid conduits (and other NeoForge-compatible pipes) work natively. Configure your conduit for **extraction from the source** (e.g., a tank or infinite water setup) and **insertion into the inlet**. No special conduit controls are required beyond standard NeoForge transactional behavior.

---

## Manual Bucket Insertion (Unchanged)

- Right-clicking a water inlet (or the controller GUI) with a water bucket still works exactly as before: 1 bucket = 1,000 mB, empty bucket returned.
- The "only when the full amount and returned bucket fit" rule still applies.
- Buckets and pipes are fully interchangeable; the tank neither distinguishes nor prioritises the source.

---

## Crafting

```
C B C
L W L
C B C
```

- **C** = Copper Ingot
- **B** = Support Casing
- **L** = Logic Chip T2
- **W** = Water Bucket

Yields 1 Support Water Inlet. The recipe is in `data/lithmod/recipe/crafting/support_water_inlet.json` and is fully editable.

---

## Visuals

- Uses the **same joined panel models** as Support Casing (12 parts × 4 facings, formed/unformed).
- The generator (`tools/lib/support-assets.mjs`) produces a distinct **blue water glyph** overlay on the formed inlet textures to differentiate them from plain casing at a glance.
- Unformed state reuses the casing unformed model (amber frame) — no new art assets.

---

## Block State & Models

- Block state: `facing` (horizontal), `formed` (boolean), `part` (0–11).
- Item model parents `lithmod:block/process_cell_casing_unformed` (same as casing).
- Block models reference `lithmod:block/process_cell_casing_part{0..11}_formed` with facing rotations.
- All 48 variants (4 facings × 12 parts × formed + 4 facings × unformed) resolve via the generator.

---

## Loot & Tags

- Loot table: drops `lithmod:support_water_inlet` (survives explosion).
- Mining tag: inherits casing hardness (2.5f / 5.0f), requires correct tool.
- No separate tag entries needed; the block is a distinct registry entry.

---

## Behaviour Summary

| Aspect              | Detail                                         |
|---------------------|------------------------------------------------|
| Registry ID         | `lithmod:support_water_inlet`                  |
| Block Entity        | `SupportWaterInletBlockEntity` (shared member BE type) |
| Fluid Capability    | `Capabilities.Fluid.BLOCK` on all faces, insert-only, water-only |
| Tank                | Delegates to purifier controller's `WaterTank` (16,000 mB) |
| Valid Machines      | Boule Purifier only                            |
| Valid Positions     | Any casing slot (top/bottom/sides/back/front)  |
| Multiple per Machine| Yes, all share the same tank                   |
| Bucket Interaction  | Unchanged (1,000 mB per bucket)                |
| Pipe Interaction    | NeoForge `FluidTransfer` / ArcForge conduits   |
| Visual Distinction  | Blue water glyph on formed panels              |

---

## Related Files

- Block: `SupportWaterInletBlock.java`
- Block Entity: `SupportWaterInletBlockEntity.java`
- Registry: `SupportMachines.SUPPORT_WATER_INLET`
- Item: `ModItems.SUPPORT_WATER_INLET`
- Assets Generator: `tools/lib/support-assets.mjs` (role `support_water_inlet` → `casing` panels + blue glyph)
- Crafting Recipe: `data/lithmod/recipe/crafting/support_water_inlet.json`
- Loot Table: `data/lithmod/loot_table/blocks/support_water_inlet.json`
- Lang: `assets/lithmod/lang/en_us.json` (block/item `support_water_inlet`)