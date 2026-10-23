# Pick-and-place formed appearance repair

The controller ID is `pick_and_place`. Controller, casing and power-port blockstates now select distinct formed models for every facing; they intentionally ignore `part` and `large`, so all cube positions resolve. Existing unformed block and item models remain unchanged. Formed models reuse the approved green starter controller/casing/power-port art. This is a simple formation indicator, not new joined panel geometry.

`tools/lib/assembly-assets.mjs`, called by `node tools/generate-starter-textures.mjs`, reproducibly generates all six changed/new JSON assets. Resource tests enumerate every facing, formation flag, shared large flag and all 45 part values, require exactly one model, verify rotation and validate opaque formed textures and unchanged item defaults.

The free Nemotron draft is preserved at f4a74a6. It used an unregistered controller ID, part=0-only variants and an incompatible manifest extension; its requested edits to an out-of-scope test were correctly denied. The coordinator replaced those assets and authored the generator/tests. No executed-test claim in the original draft is accepted as evidence. Actual check receipts are recorded separately.

No recipe, machine behavior, calibration, cleanroom or save-format change. Owner visual confirmation remains pending.
