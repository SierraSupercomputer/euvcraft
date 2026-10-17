package com.euvcraft.lithmod.mc.machine.silicon;

import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.machine.FabMachineMenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;

/**
 * Menu for the Wafer Slicer: boule in, empty FOUP in, FOUP out.
 *
 * <p>Two of the three slots validate something the framework helpers cannot express — the
 * boule slot takes only boules, the FOUP slot takes only an empty, unstarted lot — so they
 * are added through {@link FabMachineMenu#addCustomSlot}, which counts them as machine
 * slots like the helpers do.</p>
 *
 * <p>All three are added from {@link #addMachineSlots()}, which the base constructor calls
 * before the 36 player slots, and all three are built over
 * {@link FabMachineMenu#machineSlots()} — the block entity on the server and the base's own
 * throwaway container on the client. That is what makes the inherited
 * {@link FabMachineMenu#quickMoveStack} route correctly without any code here: it splits
 * the slot list on the base class's own machine-slot count, which is now the real count.
 * This menu therefore has no {@code quickMoveStack} override and no client container of its
 * own.</p>
 */
public class WaferSlicerMenu extends FabMachineMenu {

    private static final int INPUT_SLOTS = WaferSlicerBlockEntity.FOUP_SLOT + 1;
    private static final int INVENTORY_SIZE = WaferSlicerBlockEntity.OUTPUT_SLOT + 1;

    public WaferSlicerMenu(int containerId, Inventory playerInventory, FabMachineBlockEntity machine) {
        super(SiliconMachines.WAFER_SLICER_MENU.get(), containerId, playerInventory, machine,
            INPUT_SLOTS, INVENTORY_SIZE);
    }

    /**
     * Client-side constructor, reached through the {@code MenuType} factory that
     * {@code IMenuTypeExtension.create} installed.
     *
     * <p>There is no block entity on the client, so the slots are built over the container
     * the base class makes for that case. A client slot only has to exist with the right
     * rules: the contents come from the server's container sync, never from this
     * container.</p>
     */
    public WaferSlicerMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        super(SiliconMachines.WAFER_SLICER_MENU.get(), containerId, playerInventory, null,
            INPUT_SLOTS, INVENTORY_SIZE);
    }

    @Override
    protected void addMachineSlots() {
        Container slots = machineSlots();
        addCustomSlot(new BouleSlot(slots, WaferSlicerBlockEntity.BOULE_SLOT,
            MACHINE_SLOT_X - 18, MACHINE_SLOT_Y));
        addCustomSlot(new EmptyFoupSlot(slots, WaferSlicerBlockEntity.FOUP_SLOT,
            MACHINE_SLOT_X, MACHINE_SLOT_Y));
        addOutputSlot(WaferSlicerBlockEntity.OUTPUT_SLOT, MACHINE_SLOT_X, MACHINE_SLOT_Y + 36);
    }
}
