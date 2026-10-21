# Native T4 full-chain coverage

All four actual server chains passed at source `faa430464c01f45f55d4ac1655b6bda18f40736d`: `lithmod:t4_full_chain_logic`, `lithmod:t4_full_chain_memory`, `lithmod:t4_full_chain_compute` and `lithmod:t4_full_chain_nand`.

Each starts from ordinary boule plus water and uses the real purifier, slicer, G2 preparation chamber, E-beam writer, lithography cell and G2 packager. It checks lot conservation, unprepared PURE refusal, whole-cell save/reload, one mask use per lot, 32 layers, fixed one-core compute, G1 refusal and exact normal chip/scrap judgment. Fixtures use three wafers and two ticks per T4 layer and restore server settings afterward. The coordinator authored runtime tests after interrupted free-worker attempts returned no edits.

See [the owner walkthrough](../manual-walkthrough-t4.md) for default gameplay and [source acceptance](T4_acceptance.md) for evidence and retained failures. Actual client operation remains owner-pending.
