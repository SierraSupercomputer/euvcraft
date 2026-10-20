# Editable starter and joined textures

Run `node tools/generate-starter-textures.mjs` to regenerate both sets. The joined-only named script
is an alias for the same complete operation. `--check` compares every managed production PNG/JSON
without writing; optional previews are ignored. Normal Gradle builds do not regenerate art.

The manifest keeps the original item/block recipes and adds `joinedPanels`: an opaque palette,
six full-face recipes and three small functional markings. Rectangles/frames are
`[kind,x,y,width,height,color]`; dots are `["dot",x,y,color]`. Coordinates are integer pixels.
North/south canvases are32×32, west/east48×32, up/down32×48. The generator crops16×16 tiles from
these continuous canvases, keeping borders on machine edges. Edit these recipes/palette, or edit
the shipped PNGs directly; a later manual regeneration replaces direct PNG changes.

Only the formed fixed2×2×3 lithography cell uses these joined panels. Each separate cell has its own
border. Cabinet, operating screen and power fittings stay distinct. The unformed view stays separate.
This is a finite native model set, with no extra connected-texture dependency.

The canonical north-facing tile mapping for local(u,v,d) is:

| Face | Tile x | Tile y |
|---|---|---|
| north |1-u|1-v|
| south |u|1-v|
| west |d|1-v|
| east |2-d|1-v|
| up |u|d|
| down |u|2-d|

These flips follow installed Minecraft26.3 FaceBakery default UVs. Part=u×6+v×3+d. Model rotations
are north0°, east90°, south180°, west270°, without UV lock. Unformed selectors ignore part.

After regeneration, `build/joined-panels-preview.png` shows front/back/left on the top row and
right/top/underside on the bottom row, using a cabinet with its required panel and two rear ports.
This assembled pixel preview does not prove in-game client rendering.

All validation and buffer computation finish before any file write. Destinations are fixed in code;
the manifest cannot choose output paths. Existing23 non-block sprites and their models remain in
the original set. The new block item inherits the unformed operating-panel block model.
