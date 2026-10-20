# M1 client polish

The owner passed the earlier Survival walkthrough, including power pause/resume, mask use/depletion
and sections 7–11. The earlier Process Cell startup failure was corrected assembly. Layer-0 rolls
of 1 before processing were expected. This follow-up addresses GUI visibility, Creative empty-carrier
failure and missing textures. It adds no progression beyond M1.

## Behavior

Four screen classes use opaque label colors. The shared `FabMachineScreen` draws an opaque beveled
panel and wells at actual menu slots. Background coordinates include `leftPos`/`topPos`; labels use
the container's later pose translation, confirmed against installed Minecraft 26.3 source.
Menus, Abort behavior and slot coordinates stay unchanged.

`FoupItem` repairs only an actual Creative `ServerPlayer`'s registered Mk1/Mk2 stack of exactly one,
with a present empty client-view component and no run. It replaces that component with a new server
UUID and configured capacity. Names, unrelated components, sealed lots, Survival/non-player owners,
overstacks and filled client views are preserved. The private stream codec and strict machine admission
remain unchanged. Filled Creative copies cannot recreate private wafer data; use actual Survival lots.

Three required server cases cover actual packet encode/decode and handler paths, inventory lifecycle,
Mk1/Mk2 capacity and stable identity, real Slicer completion/energy and negative guards. Sealed-data
preservation deliberately uses direct server-held lots, since network copies omit that private data:

- `lithmod:client_polish_creative_empty_roundtrip`
- `lithmod:client_polish_creative_untrusted_guards`
- `lithmod:client_polish_creative_sealed_preservation`

## Verification and preserved failures

The focused three-case server run passed. Two isolated production faults each caused only its intended
case to fail: removing empty initialization broke `empty_roundtrip`; admitting filled views broke
`untrusted_guards`. Both restored exact source bytes with unchanged tests; faulty/restoration commits remain.

Nemotron's initial Java draft had compiler/API errors; GLM repaired it. The first coordinator runtime
invocation failed before server execution because PowerShell splatted a string into character arguments;
typed-array dispatch fixed it. The first full check ran 524 units with one failure: a new resource test
incorrectly expected base Process Cell model parents. The coordinator corrected it to the existing
`_unformed` parents and checked that each inherited model exists. All failed outputs remain.

Beam's focused UI review initially treated an RGB `Style.withColor` value as an ARGB draw color.
Installed tooltip source showed the separate opaque `-1` draw color; the same reviewer withdrew that
finding and approved. That focused approval is supplemental to final whole-task reviews.

The next full run passed 524 units but failed one of 813 server cases: the legacy multiblock asset
test only accepted vanilla textures and `cube_all`. A separate immutable task,
`euvcraft-m1-asset-test-compatibility`, narrowly repaired its resolution helpers. They now require
actual local PNGs, or actual vanilla jar entries, and verify allowed `cube_all`/`orientable` parent
JSONs in that jar. Existing IDs, assertions and gameplay test bodies remain. Its free Nemotron
draft is embedded for the same final reviews and candidate verification; the original task scope
was not widened. Replacing the controller front reference with a nonexistent local texture caused
the sole selected server case to fail. Its test source stayed unchanged, and the exact original
model/tree was restored in a new commit. The control's fault/restoration commits and XML remain.

## Editable art

Free Nemotron and GLM authored recipes and generator code; the coordinator repaired boundary
validation and the no-op orphan-item assertion, executed the generator and visually inspected its
complete 804×504 atlas. Available pinned workers produce text, not native images. No image API was used.

There are 23 item icons and 14 block-face PNGs for 10 block models, all original simple 16×16 art.
PNG signature, CRC, dimensions, filter/decompression and alpha checks pass. Items have transparent
backgrounds and visible pixels; block tiles are opaque. Resource tests cover model references and
pixel-distinct formed/unformed states. Repeated generation from different working directories is
byte-identical. `--check` detects missing/stale assets without writing; invalid manifests fail before
writes; controls restored exact bytes. Fixture assets remain unchanged.

See [starter-textures.md](../starter-textures.md). Edit palettes/recipes or PNGs; explicit regeneration
overwrites direct PNG edits. Normal builds do not regenerate artwork. Atlas inspection does not prove
actual Minecraft rendering.

## Final gates and limitations

Source `fb345451d8afe0e5934f8f5394fc3aeb11125019` passed 524 units in 77 classes, core-import and
release-jar checks, all 143 project server IDs and all 813 distinct server cases. There were no
failures, errors, unit skips or optional server skips. All 140 earlier project IDs remain, plus
the three listed Creative cases. Fresh mod/test compilation used `--no-build-cache`; only three
hash-verified Minecraft/NeoForge dependency jars were reused. No dependency edits or selective
rerun hid an outcome. The two earlier failed checks and their logs/XML remain preserved.
All 24 coordinator Node tests also pass.

Two fresh independent whole-task reviews, the separately tested merge candidate and integration
remain pending at report authoring time. Final exact heads, verdicts and candidate results belong
in [M1_client-polish_acceptance.md](M1_client-polish_acceptance.md) after acceptance.

The owner still needs to restart and inspect post-change screens, formed/unformed cues and a fresh
empty Creative FOUP. No actual post-change client render is claimed. There was no paid inference,
higher-tier work, push, original mk4 change or generic daemon activation.
