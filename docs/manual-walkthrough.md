# Manual Walkthrough Checklist

This checklist is for the project owner to run in a real Minecraft client with display. The automated test session has no display.

## Prerequisites

1. Build the mod:
   ```powershell
   .\gradlew.bat build --offline
   ```

2. Install the mod and its dependencies (Encoded Logistics, Arcforge) in a Minecraft 26.3 / NeoForge 26.3 instance.

3. Enable debug commands in config:
   - Edit `run/config/lithmod-local.toml` (or server config)
   - Set `features.debugCommands = true`
   - Restart the game/server

## Test Steps

### 1. Fill a FOUP with the debug command
- Open a world (creative or survival)
- Run: `/debug foup fill 25 5`
  - This creates a FOUP Mk1 with 25 wafers at layer 5
  - Note the lot ID and roll values shown in chat/log

### 2. Move the FOUP between containers
- **Inventory → Chest**: Place FOUP in chest, close, reopen, verify tooltip shows "25 wafers, layer 5"
- **Chest → Shulker Box**: Move FOUP to shulker box, close, reopen, verify
- **Shulker Box → Inventory**: Move back to player inventory
- **Inventory → Ground**: Drop FOUP (Q key), pick it up
- **Ground → Ender Chest**: Place in ender chest, close, reopen from another ender chest
- **Ender Chest → Inventory**: Move back

After each move, run: `/debug foup inspect` while holding the FOUP and verify:
- Lot ID unchanged
- Wafer count unchanged (25)
- Layer unchanged (5)
- All 25 hidden roll values identical to step 1

### 3. Relog test
- Save and quit to title screen
- Re-enter the same world
- Run: `/debug foup inspect`
- Verify all rolls, lot ID, wafer count, and layer match exactly what was recorded in step 1

### 4. Tooltip verification
- Hover over the FOUP in any slot (inventory, chest, shulker box, ender chest)
- Tooltip should show only: "25 wafers, layer 5"
- **No roll values, no flags, no lot ID** in tooltip

### 5. Model rendering
- Hold FOUP Mk1 in hand → should render as hopper texture
- Hold FOUP Mk2 in hand → should render as brewing stand texture
- Drop on ground → should render as 3D item with correct model
- **No purple/black missing-model placeholder anywhere**

### 6. Creative mode caveat
- In creative inventory, search for "FOUP"
- Both Mk1 and Mk2 appear
- Taking one gives an empty FOUP (fresh lot ID, configured capacity, 0 wafers)
- Middle-click duplication creates a server-side duplicate with same hidden rolls
- Replacing a FOUP stack in creative creates a client-view lot that `trustedContentsOf` rejects

## Expected Results (PASS Criteria)

| Step | PASS Condition |
|------|----------------|
| 1 | `/debug foup fill 25 5` succeeds; chat shows lot created |
| 2 | After every container move, `/debug foup inspect` shows identical lot ID, 25 wafers, layer 5, and all 25 roll values identical |
| 3 | After relog, `/debug foup inspect` shows identical data |
| 4 | Tooltip shows only "25 wafers, layer 5" — no hidden data |
| 5 | Both FOUP items render with hopper/brewing stand textures, not missing model |
| 6 | Creative mode behaviors match documented limitations |

## Debug Command Reference

| Command | Description |
|---------|-------------|
| `/debug foup fill <count> <layer>` | Give player a FOUP Mk1 with `<count>` wafers at `<layer>` |
| `/debug foup inspect` | Print held FOUP's lot ID, capacity, wafer count, layer, and all roll values to chat |

## Notes

- The debug commands require `features.debugCommands = true` in config AND operator permission (level 3, because vanilla owns `/debug`)
- If the debug command branch doesn't appear, verify the config is loaded on the server side
- The FOUP Mk2 is only obtainable via creative tab or `/give`
- All game tests (688) pass in headless mode; this walkthrough covers the client-side rendering and real-world persistence that headless tests cannot verify