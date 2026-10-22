package com.euvcraft.lithmod.mc.multiblock.assembly;

import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.machine.FabMachineMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.jspecify.annotations.Nullable;

public class AssemblyMenu extends FabMachineMenu {
    private final ContainerData formation;

    public AssemblyMenu(int containerId, Inventory playerInventory, @Nullable FabMachineBlockEntity machine) {
        super(AssemblyMachines.MENU.get(), containerId, playerInventory, machine, 2, 3);
        this.formation = machine instanceof AssemblyControllerBlockEntity owner
                ? owner.formation().data()
                : new SimpleContainerData(1);
        addDataSlots(this.formation);
    }

    public AssemblyMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, (FabMachineBlockEntity) null);
    }

    @Override
    protected void addMachineSlots() {
        // Input slot 1 at x=44, y=35
        addMachineSlot(SLOT_INPUT_1,44,35,true);
        // Input slot 2 at x=80, y=35
        addMachineSlot(SLOT_INPUT_2,80,35,true);
        // Output slot at x=116, y=35
        addMachineSlot(SLOT_OUTPUT,116,35,false);
    }

    public boolean formed() {
        return formation.get(0) == 1;
    }

    // Slot constants matching the block entity
    private static final int SLOT_INPUT_1 = 0;
    private static final int SLOT_INPUT_2 = 1;
    private static final int SLOT_OUTPUT = 2;
}
