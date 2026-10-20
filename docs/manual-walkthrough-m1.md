# M1 Owner Walkthrough (Manual, Single-Player)

Status: **owner execution pending**. This is a manual checklist, not evidence that any step was
run. It covers a single-player Creative world with cheats, using the already-confirmed local
early-window workaround. Multiplayer, dedicated-server and automated client execution are out of
scope. Use the shipped defaults below; a config override can change them, and an accepted run
keeps the values frozen when it started.

- FOUP capacity 25; wafers per boule 25; chips per passing wafer 4; mask uses 4 (one use per
  whole lot); resist per layer 1.
- Crystal Puller: 4 silicon -> 1 boule, 400 ticks, 20 FE/tick, 8000 FE.
- Wafer Slicer: 1 boule + 1 empty sealed FOUP -> 25 wafers, 200 ticks, 30 FE/tick, 6000 FE.
- Process Cell: buffer 100000 FE. T1 logic: 1 layer, 600 ticks, 20 FE/tick, 12000 FE, about
  30 s at 20 TPS. T2 memory: 12 layers, 100 ticks/layer, 40 FE/tick, 48000 FE, about 60 s.
- Test and Package: 10 ticks/wafer, 20 FE/tick, 200 FE/wafer; four chip outputs, two scrap.

Keep every item transfer manual. This checklist does not test hoppers, pipes, cables or fluid
automation. Do not use GameTest fixture flags for a normal client launch.

## 1. Launch and enable the debug command

1. Close any running client. From the repository root containing `gradlew.bat`, run:
   ```powershell
   .\gradlew.bat runClient
   ```
   **Pass:** the client reaches the title screen without a crash.
2. Create or open a **single-player Creative** world with **cheats enabled**.
   **Pass:** `/give @s minecraft:stone 1` works.
3. Save and quit, then close the client. Edit `run/config/lithmod-local.toml`:
   ```toml
   [features]
   debugCommands = true
   ```
   Restart the client and the same world. **Pass:** `/debug fab energy` is accepted with
   operator permission level 3 (single-player cheats provides it). If it is unknown, re-check
   the section and spelling, then restart; the command tree is built at registration.

## 2. Give the test supplies

4. Run these commands. All mod IDs use `lithmod:`.
   ```text
   /give @s lithmod:crystal_puller 1
   /give @s lithmod:wafer_slicer 1
   /give @s lithmod:pattern_generator 1
   /give @s lithmod:test_package 1
   /give @s lithmod:process_cell_controller 1
   /give @s lithmod:process_cell_power_port 1
   /give @s lithmod:process_cell_casing 10
   /give @s lithmod:foup_mk1 2
   /give @s minecraft:quartz 8
   /give @s minecraft:coal 2
   /give @s minecraft:furnace 1
   /give @s minecraft:blast_furnace 1
   /give @s lithmod:blank_mask 2
   /give @s minecraft:copper_ingot 1
   /give @s minecraft:redstone 1
   /give @s lithmod:chip_package 64
   /give @s lithmod:resist_t1 64
   /give @s lithmod:resist_t2 64
   ```
   **Pass:** four standalone machines, one controller, one power port, ten casings, two FOUPs
   and all listed consumables are in the inventory.
5. Do **not** give yourself a bare `lithmod:mask`: it has no valid mask data. The Pattern
   Generator creates the real masks in steps 10-11. Use only the `/give` FOUPs as carriers; a
   FOUP taken from the Creative inventory can be a client-view placeholder and the Slicer
   refuses it. **Pass:** each given FOUP tooltip shows `0/25` wafers and layer `0`.

## 3. Make silicon by two physical routes

6. Place the Furnace and Blast Furnace. Put 4 quartz + 1 coal in the Furnace and the other
   4 quartz + 1 coal in the Blast Furnace. Wait for both.
   **Pass:** 8 `lithmod:silicon` total, 4 smelted and 4 blasted. Keep the groups separate.

## 4. Crystal Puller and Wafer Slicer

7. Place the Crystal Puller, right-click it, and put 4 silicon in its input slot. Look at the
   puller from within 6 blocks and run `/debug fab energy`.
   **Pass:** the buffer fills, and after about 20 s the output slot holds 1
   `lithmod:silicon_boule`. If work pauses for energy, aim at the puller and repeat the command.
8. Place the Wafer Slicer. Slots: 0 boule input, 1 FOUP input, 2 FOUP output. Put the boule in
   slot 0 and one given FOUP in slot 1. Aim at the slicer and run `/debug fab energy`.
   **Pass:** after about 10 s slot 2 holds a FOUP whose tooltip reads `25/25` wafers, layer `0`.
   The input carrier is consumed and the returned lot is a new server-verified identity.
9. Repeat step 8 with the second boule group and second FOUP.
   **Pass:** two independent 25-wafer lots exist, each `25/25` at layer `0`.

## 5. Pattern Generator: make both masks

10. Place the Pattern Generator. Slots: 0 blank mask, 1 design item, 2 mask output. Put a blank
    mask and 1 copper ingot in slots 0-1 and run `/debug fab energy`.
    **Pass:** after about 10 s the output is a patterned mask whose tooltip names **logic T1**
    and shows **4 uses**. Copper creates logic T1.
11. Repeat with the second blank mask and 1 redstone.
    **Pass:** the mask names **memory T2** and shows **4 uses**. Iron is an optional extra check
    for logic T2, not part of the two required batches.

## 6. Build the Process Cell (2 wide x 2 tall x 3 long)

12. The cell is exactly 12 blocks: 1 controller, 1 power port, 10 casings. It is two 6-block
    layers, each 3 rows of 2 columns. Stand on the side the controller's front face points
    toward; the cell extends away from you. **FRONT** is the front-face plane and **BACK** is
    three blocks behind it. Build the bottom layer first:
    ```text
    BOTTOM   FRONT    [Controller] [Casing]
             MIDDLE   [Power Port] [Casing]
             BACK     [Casing]     [Casing]

    TOP      FRONT    [Casing]     [Casing]
             MIDDLE   [Casing]     [Casing]
             BACK     [Casing]     [Casing]
    ```
    The controller may occupy any of the four front-face corner positions; the diagram uses the
    front-bottom-left corner. A second power port is optional: place it in any casing cell,
    leaving 9 casings. Do **not** carve a hollow chamber or build a 3-tall grid; all 12 cells
    must be controller, port or casing. Right-click the controller.
    **Pass:** the footer says **Formed** with no failure reason.
13. Test form/unform before processing. Break one top-layer casing that is neither controller
    nor port. **Pass:** the controller says **Unformed** and work pauses. Replace it.
    **Pass:** it says **Formed** again, keeps its stored FE, and work resumes.

## 7. Batch A: logic T1 through the Process Cell

14. Right-click the formed controller. Slots are row 1: FOUP, mask, resist, light source; row 2:
    optics, stage, cleanroom, metrology. Load by hand: one 25-wafer lot; the logic T1 mask;
    1 `lithmod:resist_t1`; `lithmod:mercury_lamp`; `lithmod:contact_plate`;
    `lithmod:manual_aligner`; `lithmod:basic_air_filter`; `lithmod:optical_microscope`.
15. Aim at the **formed controller**, never the power port, and run `/debug fab energy`.
    **Pass:** the controller buffer reaches 100000 FE. The power port is not a machine block
    entity and cannot be the target.
16. Watch one layer: 600 ticks, 20 FE/tick, 12000 FE, about 30 s. Do not remove or swap the
    FOUP, mask, resist or components while it runs.
    **Pass:** the footer reaches layer `1/1`, the FOUP still shows `25/25` at layer `1`, and
    the T1 mask tooltip drops from 4 uses to 3. A mask loses one use per whole lot, not per
    layer.
17. Remove the finished FOUP and keep the T1 mask for the record; do not reuse it in Batch B.
    **Pass:** the FOUP carries the processed T1 lot and the cell is idle.

## 8. Batch B: memory T2, top-up, persistence and abort

18. Load the second lot with the memory T2 mask, `lithmod:resist_t2` and the T2 components:
    `lithmod:i_line_lamp`, `lithmod:reduction_lens`, `lithmod:stepper_stage`,
    `lithmod:fine_air_filter`, `lithmod:overlay_scope`.
19. Start with only **1** `lithmod:resist_t2` and fill the formed controller with
    `/debug fab energy`. **Pass:** the first layer runs, then the machine pauses for more
    resist; finished layers are retained. Add more of the **same exact** resist item (same item
    and components) while running or paused. **Pass:** the run resumes. Swaps to a different
    resist or removal of the accepted resist are locked; the 12-layer T2 total is 12 resist.
20. Run the full T2 lot: 12 layers, 100 ticks/layer, 40 FE/tick, 48000 FE, about 60 s.
    **Pass:** layer `12/12`, FOUP `25/25`, T2 mask drops from 4 to 3 uses, and resist is
    consumed once per layer.
21. Mid-run persistence: during a layer, save and quit to title, then re-enter the same world.
    **Pass:** the same layer count and stored FE return, the run resumes, and no extra resist or
    energy is refunded. Repeat once while paused for resist if desired.
22. Form/unform during work: remove one casing while a T2 layer runs or is paused, then replace
    it. **Pass:** work pauses while unformed, the controller retains FE, and work resumes after
    it re-forms.
23. Abort check: while a layer is partly paid, use the abort control on the Process Cell screen.
    Read and record its actual label; do not assume an older wording or instant cancellation.
    **Pass:** partial work and any paid resist are cancelled without refund, finished layers
    remain, and the machine stays stopped. Remove the FOUP, then reinsert it. **Pass:**
    unfinished layers resume.

## 9. Test and Package: two chip batches

24. Place the Test and Package machine. Slots: FOUP top-left, package below it, four chip outputs
    on the top row, two scrap outputs below. Load the Batch A FOUP and 64
    `lithmod:chip_package`. Aim at the machine and run `/debug fab energy`.
    **Pass:** the sealed lot is consumed immediately and replaced by an empty FOUP of the same
    item and capacity with a fresh server identity. The visible tooltip reads `0/25`, layer `0`;
    the UUID is hidden and the normal UI cannot prove it.
25. Watch 10 ticks/wafer at 20 FE/tick = 200 FE/wafer. A 25-wafer lot takes about 12.5 s and up
    to 5000 FE. **Pass:** the done/total counter advances and the machine stops cleanly.
26. **Pass:** each PASSING wafer consumes 1 package and writes 4 chips to the four chip slots;
    each FAILING wafer writes 4 `lithmod:chip_scrap` to the two scrap slots. Batch A yields
    `lithmod:logic_chip_t1` if any wafer passes. Passing counts are lot-dependent: do not
    expect an exact 15/20 pass count and do not promise fixed chip/scrap totals. No per-wafer
    rolls are exposed in menus or tooltips.
27. Repeat steps 24-26 with the Batch B FOUP. **Pass:** passing wafers yield
    `lithmod:memory_chip_t2`, failures yield scrap, and the package count falls only by the
    number of passing wafers. If packages, output room or energy run out, the queue pauses; top
    up and it resumes without re-judging the consumed lot.
28. Scan the Creative tabs for production items, blocks and models: all three chips
    (`logic_chip_t1`, `logic_chip_t2`, `memory_chip_t2`), `chip_scrap`, `chip_package`, both
    masks, the ten line components, the four standalone machines, controller, port, casing and
    both FOUPs. **Pass:** all have normal icons/models with no purple/black missing-model
    placeholder. This is a visual spot-check, not an automated render test.

## 10. Datapack recipe override (test only)

29. Select the actual world folder from the repository root:
    ```powershell
    Get-ChildItem run/saves -Directory | Select-Object Name, LastWriteTime
    $world = "M1Walkthrough"
    ```
    Replace `$world` with the folder name of the world under test.
30. Create the pack and override the existing `crystal_puller` recipe. This overrides a
    definition; it does not add a new recipe type.
    ```powershell
    $pack = Join-Path "run/saves/$world" "datapacks/m1_walkthrough"
    New-Item -ItemType Directory -Force -Path (Join-Path $pack "data/lithmod/machine_recipes") | Out-Null
    $meta = '{"pack":{"description":"M1 owner walkthrough","min_format":121,"max_format":[121,0]}}'
    $recipe = '{"machine":"crystal_puller","ingredients":[{"item":"lithmod:silicon","count":4}],"result":{"item":"lithmod:silicon_boule","count":1},"ticks":20}'
    $utf8 = [System.Text.UTF8Encoding]::new($false)
    [System.IO.File]::WriteAllText((Join-Path $pack "pack.mcmeta"), $meta, $utf8)
    [System.IO.File]::WriteAllText((Join-Path $pack "data/lithmod/machine_recipes/crystal_puller.json"), $recipe, $utf8)
    ```
    **Pass:** both files exist as UTF-8 without BOM. The first bytes are `7B`, not
    `EF BB BF`; check with:
    ```powershell
    Format-Hex (Join-Path $pack "pack.mcmeta") | Select-Object -First 1
    ```
31. In game run:
    ```text
    /reload
    /datapack list available
    /datapack enable "file/m1_walkthrough" last
    /reload
    ```
    **Pass:** `file/m1_walkthrough` is listed and enabled last.
32. Empty the Crystal Puller's input and output and make sure no accepted run is in flight. Feed
    a fresh idle puller 4 silicon and fill it with `/debug fab energy`.
    **Pass:** 1 boule appears in about 1 s (20 ticks instead of 400 ticks / 20 s). Costs and
    contracts freeze on ongoing work; the override applies to a new accepted run.
33. Run `/datapack disable "file/m1_walkthrough"` then `/reload`. Feed another fresh idle puller
    4 silicon and full FE. **Pass:** the default timing returns, about 20 s for the boule with
    the same inputs and FE.

## 11. Cleanup and limits

34. Close the world, then remove the test pack:
    ```powershell
    Remove-Item -Recurse -Force (Join-Path "run/saves/$world" "datapacks/m1_walkthrough")
    ```
    **Pass:** the default recipe is active and the pack is gone. Restore
    `features.debugCommands` to `false` (or remove the temporary line), restart, and verify
    `/debug fab energy` is no longer offered. No Arcforge files were edited.

### Known limitations

- Full manual execution is **owner pending**; no client, test or render execution is claimed.
- Single-player only, using the confirmed local early-window workaround. Multiplayer and
  dedicated-server behavior are unverified.
- The normal FOUP UI exposes wafer count, capacity and lowest layer. Hidden lot UUIDs and
  per-wafer rolls cannot be verified in the UI; server-side identity is not visible proof.
- A Creative-inventory FOUP may be a client-view placeholder and is refused by the Slicer. Use
  the `/give` carrier.
- Passing counts and chip/scrap totals are lot-dependent; no exact pass count is promised.
- The abort control's actual label and timing must be read in the client; no older label or
  instant cancellation is asserted.
- Timings are nominal at 20 TPS. Config overrides, lag and accepted frozen runs can change them.
- The step 28 model scan is a spot-check only; no automated rendering test is claimed.
