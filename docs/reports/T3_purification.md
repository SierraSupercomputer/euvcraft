# T3 purification

The Boule Purifier now runs separate JSON support recipes, consumes water, and produces pure silicon boules. The existing Wafer Slicer accepts both boule grades and stamps the resulting lot from the actual boule, independently of its empty FOUP carrier.

Default recipe: one ordinary boule + 1,000 mB water -> one pure boule; 400 processing ticks at 80 FE/tick (32,000 FE). The tank starts EMPTY with 16,000 mB capacity. A water bucket transfers exactly 1,000 mB and returns one empty bucket only if both tank and output have room. Tank operations remain independent of bucket transport; no pipe capability is exposed yet.

Recipes live in data/lithmod/support_recipes, separately from the generic runner that cannot represent fluid costs. Jobs freeze recipe quantities, FE, water and duration. Required inputs and water are consumed once on completion; power loss, missing structure and blocked output pause. Input withdrawal cancels without an FE refund. Adding more input or water preserves paid progress. Saved jobs validate item identities and bounds. Legacy slicer jobs remain ordinary; unknown grade saves are rejected. FOUP public payloads expose grade/count/layer, without private rolls or identity.

Coordinator verification at source 417e04640ba3c3f5977b17511273d023e8ccfbb2: all eight purifier server cases pass, including the extra real 2-input -> 3-output JSON recipe and bucket top-up regression, exact water/FE charging, pauses, save/load with changed recipes, tamper rejection and malformed registered-output rejection. Production/test compilation passed at preceding combined 77717c8; the server run compiled the later regression source. Three new slicer cases and the combined unit suite are next; no whole-T3 or client rendering acceptance is claimed here.

The initial free GLM draft is preserved at c797c22f. Coordinator fixes cover quantities/refills, bounded save restoration, component-aware output merging, actual NeoForge imports and Optional APIs, fixture geometry, genuine fresh controller reload and restoring temporary recipe registries in finally. Draft completion is not test evidence. Checked starter resources are attached from 05fa13b; new item assets and names are present.

Remaining parent phases: E-beam mask selection/jobs and G2 provenance, T3 admission/content, final generated references/manual, whole-task reviews and independent candidate checks.
