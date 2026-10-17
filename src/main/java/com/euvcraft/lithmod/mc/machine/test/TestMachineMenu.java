package com.euvcraft.lithmod.mc.machine.test;

import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.machine.FabMachineMenu;
import com.euvcraft.lithmod.mc.registry.ModMenus;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

/**
 * Menu for the test-only machine: one FOUP slot and one output slot.
 */
public class TestMachineMenu extends FabMachineMenu {

    /** Server-side constructor, reached through {@code BlockEntity.createMenu}. */
    public TestMachineMenu(int containerId, Inventory playerInventory, FabMachineBlockEntity machine) {
        super(ModMenus.TEST_MACHINE.get(), containerId, playerInventory, machine, TestMachineBlockEntity.STORAGE_SLOT + 1);
    }

    /**
     * Client-side constructor, reached through the {@code MenuType} factory that
     * {@code IMenuTypeExtension.create} installed.
     */
    public TestMachineMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        super(ModMenus.TEST_MACHINE.get(), containerId, playerInventory, null, TestMachineBlockEntity.STORAGE_SLOT + 1);
    }

    @Override
    protected void addMachineSlots() {
        addFoupSlot(TestMachineBlockEntity.INPUT_SLOT, MACHINE_SLOT_X, MACHINE_SLOT_Y);
        addOutputSlot(TestMachineBlockEntity.OUTPUT_SLOT, MACHINE_SLOT_X, MACHINE_SLOT_Y + 36);
        // The storage slot: reachable by the player, but never part of a recipe. Its
        // mayPlace is forced false because the default rule (index < inputSlotCount) would
        // already refuse it, and saying so explicitly keeps the intent readable.
        addMachineSlot(TestMachineBlockEntity.STORAGE_SLOT, MACHINE_SLOT_X + 18, MACHINE_SLOT_Y + 18, false);
    }
}
