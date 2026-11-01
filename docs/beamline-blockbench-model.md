# Blockbench beamline pipe

Owner's north–south (Z-axis) model and separate 32×32 PNG are preserved in tools/models/beamline_pipe.json and beamline_pipe.png. The T7 asset generator installs them as the EUV beamline model/texture and supplies X/Y/Z blockstates. Artist geometry, compound rotations and UVs are unchanged; resource references are remapped locally. No TFMG or animation dependency is needed.

Click a support face to place a pipe along that face's axis. A unique neighboring pipe or source/scanner endpoint axis takes precedence, so extending a straight run aligns automatically. With ambiguous neighbors, clicked-face orientation is retained. Beamline routing retains the existing straight loaded-line rules; this visual change adds no elbows, junctions or branching transport.

Selection and collision use the union of the model's 14 rotated cuboids. Existing straight pipes align from loaded neighboring pipes/endpoints on their first server tick; neighbor updates maintain alignment afterward. Isolated saved pipes default to north–south. No world replacement is required.

Model revision workflow: retain the Blockbench source export and PNG in tools/models, update collision cuboids if geometry changes, run node tools/generate-t7-assets.mjs, then node tools/check-t7-resources.mjs and the model/native checks. The native model test independently reconstructs compound rotations with Minecraft's JOML order to detect collision drift.

Client check: view a north–south, east–west and vertical run, extend a line from a machine face, inspect flange joins and inventory rendering, and confirm an existing EUV lot still exposes normally. Actual rendered appearance remains for owner confirmation.
