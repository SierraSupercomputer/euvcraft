package com.euvcraft.lithmod.mc.machine.silicon;

import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.machine.FabMachineMenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Menu for the Wafer Slicer: boule in, empty FOUP in, FOUP out.
 *
 * <p>Two of the three slots validate something the framework helpers cannot express — the
 * boule slot takes only boules, the FOUP slot takes only an FOUP whose lot is empty — so
 * the slots are added by the constructor rather than by
 * {@link FabMachineMenu#addMachineSlot}. The framework's own slot counter does not see
 * them, so {@link #quickMoveStack} is overridden here to route on this menu's real machine
 * slot count; without that, shift-clicking a FOUP would treat it as a player-inventory
 * stack and try to drop it into the wrong slots.</p>
 */
public class WaferSlicerMenu extends FabMachineMenu {

    /** Machine slots, in slot-index order: boule, FOUP, output. */
    private static final int MACHINE_SLOTS = WaferSlicerBlockEntity.OUTPUT_SLOT + 1;
    private static final int INPUT_SLOTS = WaferSlicerBlockEntity.FOUP_SLOT + 1;
    private static final int INVENTORY_SIZE = MACHINE_SLOTS;

    public WaferSlicerMenu(int containerId, Inventory playerInventory, FabMachineBlockEntity machine) {
        super(SiliconMachines.WAFER_SLICER_MENU.get(), containerId, playerInventory, machine,
            INPUT_SLOTS, INVENTORY_SIZE);

        addSlot(new BouleSlot(machine, WaferSlicerBlockEntity.BOULE_SLOT,
            MACHINE_SLOT_X - 18, MACHINE_SLOT_Y));
        addSlot(new EmptyFoupSlot(machine, WaferSlicerBlockEntity.FOUP_SLOT,
            MACHINE_SLOT_X, MACHINE_SLOT_Y));
        addOutputSlot(WaferSlicerBlockEntity.OUTPUT_SLOT, MACHINE_SLOT_X, MACHINE_SLOT_Y + 36);
    }

    /**
     * Client-side constructor, reached through the {@code MenuType} factory that
     * {@code IMenuTypeExtension.create} installed.
     *
     * <p>There is no block entity on the client, so the two validating slots are built over
     * a throwaway container. A client slot only has to exist with the right rules: the
     * contents come from the server's container sync, never from this container.</p>
     */
    public WaferSlicerMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        super(SiliconMachines.WAFER_SLICER_MENU.get(), containerId, playerInventory, null,
            INPUT_SLOTS, INVENTORY_SIZE);

        SimpleContainer clientSlots = new SimpleContainer(INVENTORY_SIZE);
        addSlot(new BouleSlot(clientSlots, WaferSlicerBlockEntity.BOULE_SLOT,
            MACHINE_SLOT_X - 18, MACHINE_SLOT_Y));
        addSlot(new EmptyFoupSlot(clientSlots, WaferSlicerBlockEntity.FOUP_SLOT,
            MACHINE_SLOT_X, MACHINE_SLOT_Y));
        addOutputSlot(WaferSlicerBlockEntity.OUTPUT_SLOT, MACHINE_SLOT_X, MACHINE_SLOT_Y + 36);
    }

    @Override
    protected void addMachineSlots() {
        // The three slots are added by the constructors above; see the class comment.
    }

    /**
     * Shift-click routing on this menu's real machine slot count.
     *
     * <p>Same rules as the base implementation — a click on a machine slot moves to the
     * player, a click on a player slot moves to the machine — with
     * {@link #MACHINE_SLOTS} in place of the framework's count, which does not include the
     * two validating slots. {@code moveItemStackTo} consults each destination's
     * {@code mayPlace}, so the validating slots still refuse their own bad stacks here.</p>
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= this.slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack original = slot.getItem();
        ItemStack copy = original.copy();

        boolean moved = index < MACHINE_SLOTS
            ? moveItemStackTo(original, MACHINE_SLOTS, this.slots.size(), true)
            : moveItemStackTo(original, 0, MACHINE_SLOTS, false);

        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (original.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }
}