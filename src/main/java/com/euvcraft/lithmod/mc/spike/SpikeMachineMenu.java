package com.euvcraft.lithmod.mc.spike;

import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import org.jspecify.annotations.Nullable;

/**
 * Menu for the spike machine.
 *
 * <p>Both constructor shapes exist because {@code IMenuTypeExtension.create} builds the
 * client-side instance from a {@link RegistryFriendlyByteBuf}, while the server builds the
 * instance from the block entity.</p>
 */
public final class SpikeMachineMenu extends AbstractContainerMenu {

    private static final int MACHINE_SLOT_X = 80;
    private static final int MACHINE_SLOT_Y = 35;
    private static final int INVENTORY_Y = 84;
    private static final int HOTBAR_Y = 142;

    private final @Nullable SpikeMachineBlockEntity machine;

    /** Server-side constructor, reached through {@code BlockEntity.createMenu}. */
    public SpikeMachineMenu(int containerId, Inventory playerInventory, SpikeMachineBlockEntity machine) {
        super(SpikeRegistries.MENU.get(), containerId);
        this.machine = machine;
        addSlot(new Slot(new ContainerView(machine), 0, MACHINE_SLOT_X, MACHINE_SLOT_Y));
        addPlayerSlots(playerInventory);
    }

    /** Client-side constructor, reached through {@code IMenuTypeExtension.create}. */
    public SpikeMachineMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        super(SpikeRegistries.MENU.get(), containerId);
        this.machine = null;
        addPlayerSlots(playerInventory);
    }

    public @Nullable SpikeMachineBlockEntity machine() {
        return this.machine;
    }

    /** The machine energy handler, or {@code null} on the client instance. */
    public @Nullable EnergyHandler energy() {
        return this.machine == null ? null : this.machine.energy();
    }

    private void addPlayerSlots(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9,
                    8 + column * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, HOTBAR_Y));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return this.machine != null;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /** A {@link Container} view over the block entity slot list, so {@link Slot} can be built. */
    private static final class ContainerView implements Container {
        private final SpikeMachineBlockEntity machine;

        ContainerView(SpikeMachineBlockEntity machine) {
            this.machine = machine;
        }

        @Override
        public int getContainerSize() {
            return SpikeMachineBlockEntity.SLOT_COUNT;
        }

        @Override
        public boolean isEmpty() {
            return this.machine.items().get(0).isEmpty();
        }

        @Override
        public ItemStack getItem(int index) {
            return this.machine.items().get(index);
        }

        @Override
        public ItemStack removeItem(int index, int count) {
            ItemStack stack = this.machine.items().get(index);
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack removed = stack.split(count);
            if (stack.isEmpty()) {
                this.machine.items().set(index, ItemStack.EMPTY);
            }
            this.machine.setChanged();
            return removed;
        }

        @Override
        public ItemStack removeItemNoUpdate(int index) {
            ItemStack stack = this.machine.items().get(index);
            this.machine.items().set(index, ItemStack.EMPTY);
            return stack;
        }

        @Override
        public void setItem(int index, ItemStack stack) {
            this.machine.items().set(index, stack);
            this.machine.setChanged();
        }

        @Override
        public int getMaxStackSize() {
            return 64;
        }

        @Override
        public void setChanged() {
            this.machine.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            this.machine.items().clear();
        }
    }
}
