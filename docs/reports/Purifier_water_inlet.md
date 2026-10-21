# Purifier water inlet and GUI status verification

Functional source: `56034dd4345c7b3aad723cf7146ec2e6db001fdf`. The owner confirmed the complete prior T3 walkthrough and
both standalone output-slot arrangements before this follow-up.

The optional Support Water Inlet replaces any purifier casing. Existing bucket-only cells still
form; one controller and one or two power ports remain required. All inlet faces accept ordinary
water only and never extract. E-beam and lithography do not admit the inlet. The existing controller
tank remains authoritative for pipes, buckets, processing, menus and save/load. Every inlet/side
shares one controller SnapshotJournal; nested rollback restores the amount without clamping water
after capacity shrink. Retained lenses check world/member/controller identity and actual formation.
Member attachment invalidates capability caches. Transactions are synchronous server-thread operations.

The installed NeoForge 26.3 API is `ResourceHandler<FluidResource>` with `Capabilities.Fluid.BLOCK`.
Installed Arcforge bytecode confirms its conduits use that same API. The interoperability case uses
a registered wrought fluid tank and actual fluid conduit network, respects the source tank's transfer
rate, and checks 1,000 mB arriving in the purifier with an exact source debit.

Process Cell cabinet/panel and support-machine labels now use container-local coordinates: vanilla
already translates the label pose. Widgets/backgrounds retain screen coordinates. Long labels are
bounded inside the window, including the E-beam selector, ingredients and FE text beside Start.

## Executed checks

- 755 unit tests / 86 classes; no failures, errors or skips. Core imports and release jar pass.
- All 900 distinct complete server cases pass, including all 230 project IDs and all 225 prior project IDs. No optional failures/skips.
- New actual server cases: `lithmod:water_inlet_geometry`, `lithmod:water_inlet_transactions`, `lithmod:water_inlet_lifecycle`, `lithmod:water_inlet_process_reload`, `lithmod:water_inlet_arcforge_pipe`.
- Geometry covers all ten casing positions in the one-port fixture across four facings and two anchor columns. Transaction coverage includes two inlets, nested commit/abort, full/partial/overfull tanks, non-water/flowing/component-bearing water and denied extraction. Lifecycle checks actual capability cache invalidation, broken structures, removed/replaced members and retained owner lenses. Processing/reload covers bucket sharing, JSON water consumption, pure-boule output and persisted water.
- Both texture check entry points match 154 PNGs and 210 generated JSON assets. Recipe/T3 references pass, with 50 recipe files (48 production, two fixtures). Exact original bytes are restored after Windows checkout normalization during checks.
- Coordinator Node suite: 24/24 pass.

The inlet's teal water-drop markings use the existing editable joined-panel generator. Old panel
pixels/models remain included. Crafting stays JSON-driven; pickaxe/tool-tier tags, loot, item model,
creative ordering and all 52 state selectors are represented in the checked resources.

## Provenance and retained failures

The first Nemotron implementation was interrupted after producing obsolete/invented fluid APIs.
Draft `af6ee2dbcd7d9977297e66cf8e6acd96e8d62c2b` and its logs are preserved; it is not an accepted
worker completion. The coordinator implemented the actual transactional adapter and tests. A separate
scoped pinned-free Nemotron resource/manual draft was attested through saved session
`ses_ea419bf95ffeTD458smv1vKpb6`, message `msg_15bec3e1300198162ZQMr6qAVo`, with zero inference costs.
Its API/bucket/texture claims were corrected against actual source; two probe files remain preserved
in draft `052ddbe74dd46853521d973695d6d640eaccc4ac` and are absent from the final implementation.

Earlier checks retain four then one strict inventory failures. The first focused server run passed
three cases and exposed two fixture errors: live controller replacement needed the normal member
tick, and the Arcforge source tank limits each transfer. Corrected fixtures pass in the full run.
No dependency code was changed or failure hidden. The older M1 reference generator still expects
six machine definitions and rejects the already-accepted T3 inventory of eight; its attempted run
aborted before writing. That pre-existing guide-generator limitation is tracked separately. T3 and
recipe references are current.

Independent exact-source free reviews and a separately compiled identical-tree integration candidate
follow this source report. Their outcomes are recorded in the final integration record.
Rendered status placement and inlet appearance remain owner checks; automated server/resource
verification does not claim client rendering. No T4, item automation, push, client/world install,
mk4 change or daemon activation is included. See [the placement guide](../purifier-water-inlet.md).
