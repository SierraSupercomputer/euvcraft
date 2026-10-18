package com.euvcraft.lithmod.mc.machine.process;

import com.euvcraft.lithmod.mc.ComponentCatalog;
import com.euvcraft.lithmod.mc.FoupItems;
import com.euvcraft.lithmod.mc.ModItems;
import com.euvcraft.lithmod.mc.ModMultiblocks;
import com.euvcraft.lithmod.mc.ResistCatalog;
import com.euvcraft.lithmod.mc.machine.FabMachineMenu;
import com.euvcraft.lithmod.mc.machine.FoupSlot;
import com.euvcraft.lithmod.mc.machine.ComponentSlot;
import com.euvcraft.lithmod.mc.multiblock.ProcessCellControllerBlockEntity;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Menu for the Process Cell controller: 8 machine slots + 36 player slots = 44 total.
 * <ul>
 *   <li>Slot 0: FOUP (FoupSlot, 1 stack)</li>
 *   <li>Slot 1: Mask (accepts only ModItems.MASK)</li>
 *   <li>Slot 2: Resist (accepts positive ResistCatalog.tierOf, max 64)</li>
 *   <li>Slot 3: Light Source Component (ComponentSlot, kind "light_source")</li>
 *   <li>Slot 4: Optics Component (ComponentSlot, kind "optics")</li>
 *   <li>Slot 5: Stage Component (ComponentSlot, kind "stage")</li>
 *   <li>Slot 6: Cleanroom Component (ComponentSlot, kind "cleanroom")</li>
 *   <li>Slot 7: Metrology Component (ComponentSlot, kind "metrology")</li>
 * </ul>
 * No active-lock placeholder methods/public aliases: active-state restrictions/state wire/Abort are next phase.
 */
public class ProcessCellMenu extends FabMachineMenu {

    public ProcessCellMenu(MenuType<?> type, int containerId, Inventory playerInventory,
                           @Nullable ProcessCellControllerBlockEntity blockEntity) {
        super(type, containerId, playerInventory, blockEntity,
                ProcessCellControllerBlockEntity.INPUT_SLOTS,
                ProcessCellControllerBlockEntity.SLOT_COUNT);
    }

    // Client constructor (blockEntity is null)
    public ProcessCellMenu(int containerId, Inventory playerInventory) {
        this(ModMultiblocks.PROCESS_CELL_MENU_TYPE.get(), containerId, playerInventory, null);
    }

    @Override
    protected void addMachineSlots() {
        // Layout: single column at x=80, starting y=35, spacing 18
        final int x = MACHINE_SLOT_X;
        int y = MACHINE_SLOT_Y;

        // Slot 0: FOUP
        addFoupSlot(ProcessCellControllerBlockEntity.SLOT_FOUP, x, y);
        y += 18;

        // Slot 1: Mask - accepts only ModItems.MASK
        addCustomSlot(new MaskSlot(machineSlots(), ProcessCellControllerBlockEntity.SLOT_MASK, x, y));
        y += 18;

        // Slot 2: Resist - accepts only resist items with positive tier, max 64
        addCustomSlot(new ResistSlot(machineSlots(), ProcessCellControllerBlockEntity.SLOT_RESIST, x, y));
        y += 18;

        // Slot 3: Light Source Component
        addCustomSlot(new ComponentSlot(machineSlots(), ProcessCellControllerBlockEntity.SLOT_LIGHT_SOURCE, x, y, "light_source"));
        y += 18;

        // Slot 4: Optics Component
        addCustomSlot(new ComponentSlot(machineSlots(), ProcessCellControllerBlockEntity.SLOT_OPTICS, x, y, "optics"));
        y += 18;

        // Slot 5: Stage Component
        addCustomSlot(new ComponentSlot(machineSlots(), ProcessCellControllerBlockEntity.SLOT_STAGE, x, y, "stage"));
        y += 18;

        // Slot 6: Cleanroom Component
        addCustomSlot(new ComponentSlot(machineSlots(), ProcessCellControllerBlockEntity.SLOT_CLEANROOM, x, y, "cleanroom"));
        y += 18;

        // Slot 7: Metrology Component
        addCustomSlot(new ComponentSlot(machineSlots(), ProcessCellControllerBlockEntity.SLOT_METROLOGY, x, y, "metrology"));
    }

    /**
     * Slot that accepts only the Mask item (ModItems.MASK).
     */
    private static class MaskSlot extends Slot {
        public MaskSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(ModItems.MASK.get());
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    /**
     * Slot that accepts only resist items with a positive tier, stackable to 64.
     */
    private static class ResistSlot extends Slot {
        public ResistSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !stack.isEmpty() && ResistCatalog.tierOf(stack).map(t -> t > 0).orElse(false);
        }

        @Override
        public int getMaxStackSize() {
            return 64;
        }
    }
}