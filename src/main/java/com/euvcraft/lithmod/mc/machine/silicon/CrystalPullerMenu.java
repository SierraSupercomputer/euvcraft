package com.euvcraft.lithmod.mc.machine.silicon;

import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.machine.FabMachineMenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

/**
 * Menu for the Crystal Puller: one input slot and one output slot.
 */
public class CrystalPullerMenu extends FabMachineMenu {

    public CrystalPullerMenu(int containerId, Inventory playerInventory, FabMachineBlockEntity machine) {
        super(SiliconMachines.CRYSTAL_PULLER_MENU.get(), containerId, playerInventory, machine,
            CrystalPullerBlockEntity.INPUT_SLOT + 1, CrystalPullerBlockEntity.OUTPUT_SLOT + 1);
    }

    /**
     * Client-side constructor, reached through the {@code MenuType} factory that
     * {@code IMenuTypeExtension.create} installed.
     */
    public CrystalPullerMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        super(SiliconMachines.CRYSTAL_PULLER_MENU.get(), containerId, playerInventory, null,
            CrystalPullerBlockEntity.INPUT_SLOT + 1, CrystalPullerBlockEntity.OUTPUT_SLOT + 1);
    }

    @Override
    protected void addMachineSlots() {
        addMachineSlot(CrystalPullerBlockEntity.INPUT_SLOT, MACHINE_SLOT_X, MACHINE_SLOT_Y);
        addOutputSlot(CrystalPullerBlockEntity.OUTPUT_SLOT, MACHINE_SLOT_X, MACHINE_SLOT_Y + 36);
    }
}