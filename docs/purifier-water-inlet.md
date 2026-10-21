# Purifier water inlet

Replace any Support Casing in a Boule Purifier's 2x2x3 structure with a **Support Water Inlet**.
Top, bottom, front, back and side positions all work. Keep the controller and one or two power
ports. Inlets are optional; existing bucket-only purifiers still form. Multiple inlets share the
same controller tank (16,000 mB by default, editable in the machine definition).

Connect an Arcforge fluid conduit to any exposed inlet face. Configure the source side to extract
water and the purifier side to insert. The inlet accepts ordinary water only and never permits
extraction. E-beam and lithography structures reject it. A broken/unformed machine accepts no water.

Buckets still go in the purifier GUI's water-bucket input slot. Each bucket adds exactly 1,000 mB
when the full amount and returned empty bucket fit. Right-clicking an inlet is not a bucket-fill action.
Pipe water and bucket water feed the same pure-boule recipes and survive controller save/reload.

Craft one inlet using this editable JSON recipe:

```text
C B C    C = copper ingot
L W L    B = support casing
C B C    L = T2 logic chip; W = water bucket
```

Recipe: `data/lithmod/recipe/crafting/support_water_inlet.json`. Vanilla crafting returns the
water bucket's empty container. The inlet has a teal water-drop marking on all faces, with joined
panels while formed. Mining requires the same pickaxe/tool tier as support casing.

The native adapter uses NeoForge 26.3's `ResourceHandler<FluidResource>` and
`Capabilities.Fluid.BLOCK`, the same transactional API as installed Arcforge conduits.
All inlets share one rollback journal on the controller. Transactions are synchronous operations
on the server thread. No item automation or higher content tier is added.

The owner confirmed the earlier T3 walkthrough and standalone output-slot fixes. After this
follow-up, check the inlet's appearance and the corrected in-window machine status labels in game.
