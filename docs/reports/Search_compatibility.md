# Creative search compatibility repair

The owner's client log identifies Encoded Logistics 1.2.1 `ItemInfoTooltips.onTooltip` calling `StringSplitter.splitLines` during vanilla `SessionSearchTrees.updateCreativeTooltips` asynchronous indexing. With Shift held, a missing glyph uploads its bitmap and RenderSystem rejects the background thread, poisoning the shared search future.

A real-client forced-Shift test then exposed the same failure in Arcforge 2.5.1 ItemInfoTooltips.onTooltip:57 after the Encoded Logistics failure was removed. Both dependency bytecodes call the identical FormattedText splitter overload. A client-only multi-target mixin redirects exactly that one wrapping invocation in each handler. On the render thread it delegates unchanged to Minecraft's splitter. On background threads it returns the complete description as one line; search retains its words without asking the font to measure glyphs. Other tooltip contributions and vanilla name/tag indexing remain in place. No dependency jar was changed. The required injection makes incompatible future dependency changes fail visibly rather than silently losing this repair.

The unit test runs the background path with a glyph measurer that fails on any access, and checks real render-path width wrapping separately. A disposable, nonshipping probe exercises vanilla async SessionSearchTrees against all registered items with Shift forced, then searches vanilla/mod/dependency names and tags and checks normal extended tooltips. Its actual outcome is retained in coordinator receipts; do not infer a client pass from unit tests. Owner walkthrough remains required.

No world or existing save migration is needed. No cleanroom implementation.

