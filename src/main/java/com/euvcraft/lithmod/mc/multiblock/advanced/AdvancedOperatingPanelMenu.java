package com.euvcraft.lithmod.mc.multiblock.advanced;

import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.machine.FabMachineMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Menu for the Advanced Operating Panel (upper panel of two-panel machine).
 * Shows the operational slots for both deposition_etcher and advanced_test_package.
 * The actual slot layout is determined by the controller's machine type.
 */
public class AdvancedOperatingPanelMenu extends FabMachineMenu {

    private final BlockPos controllerPos;

    public AdvancedOperatingPanelMenu(int containerId, Inventory playerInventory,
                                       AdvancedControllerBlockEntity machine, BlockPos controllerPos) {
        super(AdvancedMachines.ADVANCED_OPERATING_PANEL_MENU.get(), containerId, playerInventory, machine,
              machine.inputSlotCount(), machine.slotCount());
        this.controllerPos = controllerPos;
    }

    public AdvancedOperatingPanelMenu(int containerId, Inventory playerInventory,
                                       RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, null, BlockPos.ZERO);
    }

    @Override
    protected void addMachineSlots() {
        AdvancedControllerBlockEntity controller = (AdvancedControllerBlockEntity) machine;
        if (controller == null) return;

        // Layout depends on machine type
        String machineId = controller.machineId();
        
        if ("deposition_etcher".equals(machineId)) {
            // Deposition Etcher: FOUP input, silicon reagent, prepared FOUP output
            addFoupSlot(DepositionEtcherControllerBlockEntity.SLOT_FOUP_INPUT, 30, 35);
            addCustomSlot(new Slot(machineSlots(), DepositionEtcherControllerBlockEntity.SLOT_SILICON_REAGENT,
                    66, 35) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.is(ModItems.SILICON.get());
                }
            });
            addOutputSlot(DepositionEtcherControllerBlockEntity.SLOT_PREPARED_FOUP_OUTPUT, 122, 53);
        } else if ("advanced_test_package".equals(machineId)) {
            // Advanced Test Package: same layout as TestPackageMenu but from multiblock
            // FOUP slot (0), Package slot (1), 4 chip outputs (2-5), 2 scrap outputs (6-7)
            addFoupSlot(TestPackageBlockEntity.FOUP_SLOT, 30, 35);
            addCustomSlot(new Slot(machineSlots(), TestPackageBlockEntity.PACKAGE_SLOT, 30, 53) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return TestPackageBlockEntity.isPackage(stack);
                }
            });
            for (int slot = 0; slot < TestPackageBlockEntity.CHIP_SLOT_COUNT; slot++) {
                addOutputSlot(TestPackageBlockEntity.CHIP_SLOT_FIRST + slot, 70 + slot * 18, 35);
            }
            for (int slot = 0; slot < TestPackageBlockEntity.SCRAP_SLOT_COUNT; slot++) {
                addOutputSlot(TestPackageBlockEntity.SCRAP_SLOT_FIRST + slot, 70 + slot * 18, 53);
            }
        }
    }
}