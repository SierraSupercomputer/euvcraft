package com.euvcraft.lithmod.mc.machine.silicon.screen;

import com.euvcraft.lithmod.mc.machine.silicon.SiliconMachineScreen;
import com.euvcraft.lithmod.mc.machine.silicon.WaferSlicerMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the Wafer Slicer: the shared silicon chain layout with this machine's menu.
 *
 * <p>The bars are drawn from the values the server synced; nothing here reads a wafer, a
 * roll or a lot capacity, so a lot never leaks more into the client than the progress the
 * menu already carries.</p>
 */
public class WaferSlicerScreen extends SiliconMachineScreen<WaferSlicerMenu> {

    public WaferSlicerScreen(WaferSlicerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }
}