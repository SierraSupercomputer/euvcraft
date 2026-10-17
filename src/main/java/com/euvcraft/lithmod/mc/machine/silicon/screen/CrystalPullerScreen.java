package com.euvcraft.lithmod.mc.machine.silicon.screen;

import com.euvcraft.lithmod.mc.machine.silicon.CrystalPullerMenu;
import com.euvcraft.lithmod.mc.machine.silicon.SiliconMachineScreen;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the Crystal Puller: the shared silicon chain layout with this machine's menu.
 */
public class CrystalPullerScreen extends SiliconMachineScreen<CrystalPullerMenu> {

    public CrystalPullerScreen(CrystalPullerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }
}