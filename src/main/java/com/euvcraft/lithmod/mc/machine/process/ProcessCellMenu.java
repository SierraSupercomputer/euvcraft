package com.euvcraft.lithmod.mc.machine.process;

import com.euvcraft.lithmod.mc.ResistCatalog;
import com.euvcraft.lithmod.mc.machine.ComponentSlot;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.machine.FabMachineMenu;
import com.euvcraft.lithmod.mc.multiblock.ModMultiblocks;
import com.euvcraft.lithmod.mc.multiblock.ProcessCellControllerBlockEntity;
import com.euvcraft.lithmod.mc.registry.ModItems;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Eight hand-fed input slots. Run-state locking and extra public data follow in M1-09. */
public class ProcessCellMenu extends FabMachineMenu {
    public ProcessCellMenu(int containerId, Inventory playerInventory, FabMachineBlockEntity machine) {
        super(ModMultiblocks.PROCESS_CELL_MENU_TYPE.get(), containerId, playerInventory, machine,
            ProcessCellControllerBlockEntity.INPUT_SLOTS, ProcessCellControllerBlockEntity.SLOT_COUNT);
    }

    public ProcessCellMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, (FabMachineBlockEntity) null);
    }

    @Override
    protected void addMachineSlots() {
        addFoupSlot(ProcessCellControllerBlockEntity.SLOT_FOUP, 26, 35);
        addCustomSlot(new Slot(machineSlots(), ProcessCellControllerBlockEntity.SLOT_MASK, 62, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) { return stack.is(ModItems.MASK.get()); }
            @Override
            public int getMaxStackSize() { return 1; }
        });
        addCustomSlot(new Slot(machineSlots(), ProcessCellControllerBlockEntity.SLOT_RESIST, 98, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) { return !stack.isEmpty() && ResistCatalog.tierOf(stack) > 0; }
            @Override
            public int getMaxStackSize() { return 64; }
        });
        addCustomSlot(new ComponentSlot(machineSlots(), ProcessCellControllerBlockEntity.SLOT_LIGHT_SOURCE, 134, 35, "light_source"));
        addCustomSlot(new ComponentSlot(machineSlots(), ProcessCellControllerBlockEntity.SLOT_OPTICS, 26, 53, "optics"));
        addCustomSlot(new ComponentSlot(machineSlots(), ProcessCellControllerBlockEntity.SLOT_STAGE, 62, 53, "stage"));
        addCustomSlot(new ComponentSlot(machineSlots(), ProcessCellControllerBlockEntity.SLOT_CLEANROOM, 98, 53, "cleanroom"));
        addCustomSlot(new ComponentSlot(machineSlots(), ProcessCellControllerBlockEntity.SLOT_METROLOGY, 134, 53, "metrology"));
    }
}
