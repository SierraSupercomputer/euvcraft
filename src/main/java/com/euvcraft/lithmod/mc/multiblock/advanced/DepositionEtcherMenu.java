package com.euvcraft.lithmod.mc.multiblock.advanced;
import net.minecraft.world.level.block.state.properties.*;
import com.euvcraft.lithmod.core.multiblock.*;
import com.euvcraft.lithmod.mc.multiblock.EnergyBufferProvider;
import com.euvcraft.lithmod.mc.machine.packaging.TestPackageBlockEntity;
import com.euvcraft.lithmod.mc.registry.ModItems;

import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.machine.FabMachineMenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Menu for the Deposition Etcher: FOUP input, silicon reagent input, prepared FOUP output.
 * Lower cabinet shows equipment/status only if no permanent items; upper panel shows actual operation.
 */
public class DepositionEtcherMenu extends FabMachineMenu {

    private static final int INPUT_X = 30;
    private static final int FIRST_ROW_Y = 35;
    private static final int SECOND_ROW_Y = 53;
    private static final int OUTPUT_X = 122;

    public DepositionEtcherMenu(int containerId, Inventory playerInventory,
                                 AdvancedControllerBlockEntity machine) {
        super(AdvancedMachines.DEPOSITION_ETCHER_MENU.get(), containerId, playerInventory, machine,
              DepositionEtcherControllerBlockEntity.INPUT_SLOTS,
              DepositionEtcherControllerBlockEntity.SLOT_COUNT);
    }

    public DepositionEtcherMenu(int containerId, Inventory playerInventory,
                                 RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, (AdvancedControllerBlockEntity) null);
    }

    @Override
    protected void addMachineSlots() {
        // Lower cabinet (controller) - equipment/status only if no permanent items
        // Upper panel (operating panel) - FOUP input, silicon reagent, prepared FOUP output
        
        // The operating panel shows the three slots:
        // Slot 0: FOUP input (upper left)
        addFoupSlot(DepositionEtcherControllerBlockEntity.SLOT_FOUP_INPUT, INPUT_X, FIRST_ROW_Y);
        // Slot 1: Silicon reagent (upper right)
        addCustomSlot(new Slot(machineSlots(), DepositionEtcherControllerBlockEntity.SLOT_SILICON_REAGENT,
                INPUT_X + 36, FIRST_ROW_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.SILICON.get());
            }
        });
        // Slot 2: Prepared FOUP output (below)
        addOutputSlot(DepositionEtcherControllerBlockEntity.SLOT_PREPARED_FOUP_OUTPUT, OUTPUT_X, SECOND_ROW_Y);
    }
}