package com.euvcraft.lithmod.mc.multiblock.advanced;
import net.minecraft.world.level.block.state.properties.*;
import com.euvcraft.lithmod.core.multiblock.*;
import com.euvcraft.lithmod.mc.multiblock.EnergyBufferProvider;
import com.euvcraft.lithmod.mc.machine.packaging.TestPackageBlockEntity;
import com.euvcraft.lithmod.mc.registry.ModItems;

import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.machine.FabMachineMenu;
import com.euvcraft.lithmod.mc.machine.packaging.TestPackageBlockEntity;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Menu for the Advanced Test Package (lower cabinet).
 * Shows equipment/status only if no permanent items.
 * The upper panel (AdvancedOperatingPanelMenu) shows the actual operation slots.
 */
public class AdvancedTestPackageMenu extends FabMachineMenu {

    private final ContainerData queue;

    public AdvancedTestPackageMenu(int containerId, Inventory playerInventory,
                                    AdvancedTestPackageControllerBlockEntity machine) {
        super(AdvancedMachines.ADVANCED_TEST_PACKAGE_MENU.get(), containerId, playerInventory, machine,
              TestPackageBlockEntity.INPUT_SLOTS, TestPackageBlockEntity.SLOT_COUNT);
        this.queue = machine.queueData();
        addDataSlots(this.queue);
    }

    public AdvancedTestPackageMenu(int containerId, Inventory playerInventory,
                                    RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, (AdvancedTestPackageControllerBlockEntity) null);
    }

    @Override
    protected void addMachineSlots() {
        // Lower cabinet: equipment/status only if no permanent items
        // The actual operational slots are in the upper panel menu
        // This menu just shows status - no interactive slots for the lower cabinet
        // But we still need to add the slots to the container for shift-click handling
        // They are just not rendered in the lower cabinet screen
        
        // Add all slots but they won't be visible in lower cabinet screen
        // FOUP slot (0)
        addFoupSlot(TestPackageBlockEntity.FOUP_SLOT, -100, -100); // Off-screen
        // Package slot (1)
        addCustomSlot(new Slot(machineSlots(), TestPackageBlockEntity.PACKAGE_SLOT, -100, -100) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return TestPackageBlockEntity.isPackage(stack);
            }
        });
        // Chip outputs (2-5)
        for (int slot = 0; slot < TestPackageBlockEntity.CHIP_SLOT_COUNT; slot++) {
            addOutputSlot(TestPackageBlockEntity.CHIP_SLOT_FIRST + slot, -100, -100);
        }
        // Scrap outputs (6-7)
        for (int slot = 0; slot < TestPackageBlockEntity.SCRAP_SLOT_COUNT; slot++) {
            addOutputSlot(TestPackageBlockEntity.SCRAP_SLOT_FIRST + slot, -100, -100);
        }
    }

    /** The coarse machine state: idle, running, paused or one of the five admission refusals. */
    public final int state() {
        return this.queue.get(TestPackageBlockEntity.QUEUE_DATA_STATE);
    }

    /** Wafers fully completed of the accepted lot. */
    public final int wafersDone() {
        return this.queue.get(TestPackageBlockEntity.QUEUE_DATA_DONE);
    }

    /** Wafers the accepted lot held, or zero before any lot was accepted. */
    public final int wafersTotal() {
        return this.queue.get(TestPackageBlockEntity.QUEUE_DATA_TOTAL);
    }
}