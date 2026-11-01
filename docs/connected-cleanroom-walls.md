# Connected cleanroom walls

Owner confirmed the T7 walkthrough completed without obvious issues on October 31, 2026.

Adjacent Cleanroom Wall blocks now join into continuous opaque panels on all six faces. Borders remain against floors, windows, ports and other blocks. Floors retain the original tiled texture, model and blockstate. Window connections are unchanged. No CTM dependency is required.

Existing room shells refresh their visual connections during the controller's normal loaded-shell validation, including after loading a previous save. This does not reset cleanliness or alter enclosure rules. New placements, removals, rotations and mirrors update local connection flags. Standalone walls from older saves outside a controller's shell can be refreshed by updating neighboring blocks. No world replacement is required.

Visual check: view a 2×2 wall panel from both sides, then extend a row and remove its middle block. Internal borders should disappear and exposed borders return. Check a ceiling and corner, then confirm the floor remains tiled and windows/ports retain their framing.
