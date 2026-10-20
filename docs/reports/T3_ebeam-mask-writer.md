# E-beam Mask Writer

Seven selected designs share a blank-mask/redstone ingredient set, with quantities, duration and FE loaded from each named JSON file. The native formed support cell requires an explicit server-validated Write mask button. The GUI sends actual menu packets and displays selected costs. Missing recipes disable new jobs.

Jobs pin the chosen recipe, ingredient quantities, FE, duration, design and configured mask-use count at start. A paid snapshot survives save/load and completes after its recipe is removed; changes to live mask-use config do not change the accepted product. Formation, power and output blockage pause; input tampering cancels without manufacturing a mask. Completion consumes quantities once and produces one nonstackable generation-2 mask.

MaskData's optional saved shop_generation defaults to G1 for old masks. The actual old Pattern Generator normalizes a forged G2 recipe template to G1. Consuming a mask preserves provenance.

All 29 E-beam server cases passed in the complete source gate at `1bdd94f1a1fdabc768464ca25e9053749fd160bf`, alongside all 750 units and 895 server cases. The reload case uses different costs for the first and selected design, then removes recipes and changes uses from three to eleven; it verifies the selected compute-T3 product still costs 84 FE and retains three uses. Earlier drafts and failed fixture runs remain preserved. No rendered-client validation is claimed.

See [source verification](T3_acceptance.md), [the walkthrough](../manual-walkthrough-t3.md) and [editable balances](../t3-slice.md).
