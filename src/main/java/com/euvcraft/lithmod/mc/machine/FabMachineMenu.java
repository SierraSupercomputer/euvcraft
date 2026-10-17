package com.euvcraft.lithmod.mc.machine;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Base menu for fab machines.
 *
 * <p>Supplies the slot layout helpers, the player inventory rows, the energy and progress
 * values read from the block entity's {@link ContainerData}, and the shift-click routing.
 * Subclasses only decide how many machine slots there are and where they go, through
 * {@link #addMachineSlots()}; a slot with its own validation rule goes in through
 * {@link #addCustomSlot(Slot)}.</p>
 *
 * <p>Shift-click routing is the important part: a click on a machine slot moves to the player
 * inventory, and a click on a player slot moves to the machine. The move goes through
 * {@code moveItemStackTo}, which consults each destination slot's {@code mayPlace}, so a
 * {@link FoupSlot} still refuses non-FOUP stacks on a shift-click.</p>
 */
public abstract class FabMachineMenu extends AbstractContainerMenu {

    /** Horizontal centre of a machine slot column. */
    protected static final int MACHINE_SLOT_X = 80;
    /** Vertical offset of the first machine slot row. */
    protected static final int MACHINE_SLOT_Y = 35;
    /** Vertical offset of the first player-inventory row. */
    protected static final int INVENTORY_Y = 84;
    /** Vertical offset of the player hotbar. */
    protected static final int HOTBAR_Y = 142;

    protected final @Nullable FabMachineBlockEntity blockEntity;
    protected final ContainerData data;
    /**
     * The machine's slots as a {@link Container}.
     *
     * <p>{@link FabMachineBlockEntity} is itself the {@code Container}, so there is no
     * adapter between the menu and the slots. That is deliberate: 26.3 dropped contents via
     * {@code BlockEntity.preRemoveSideEffects}, which only fires for a block entity that is
     * a {@code Container} itself, and having the menu go through the same object keeps one
     * route to the inventory rather than two.</p>
     */
    private final Container machineSlots;
    /** Counted as machine slots are added; see {@link #addMachineSlot(int, int, int, boolean)}. */
    private int machineSlotCount;
    private final int inputSlotCount;

    protected FabMachineMenu(MenuType<?> type, int containerId, Inventory playerInventory,
                             @Nullable FabMachineBlockEntity blockEntity, int inputSlotCount, int inventorySize) {
        super(type, containerId);
        if (inputSlotCount < 0 || inputSlotCount > inventorySize) {
            throw new IllegalArgumentException("Invalid machine slot layout");
        }
        if (blockEntity != null && (blockEntity.getContainerSize() != inventorySize
                || blockEntity.inputSlotCount() != inputSlotCount)) {
            throw new IllegalArgumentException("Menu layout does not match machine inventory");
        }
        this.inputSlotCount = inputSlotCount;
        this.blockEntity = blockEntity;
        // The client needs the same machine slots as the server for correct packet indices.
        this.data = blockEntity == null ? new SimpleContainerData(FabMachineBlockEntity.DATA_COUNT)
            : blockEntity.data();
        this.machineSlots = blockEntity == null ? new SimpleContainer(inventorySize) : blockEntity;
        this.machineSlotCount = 0;

        addDataSlots(this.data);
        addMachineSlots();
        addPlayerSlots(playerInventory);
    }

    /** Whether this menu is the server-side instance bound to a real block entity. */
    public final boolean isServerSide() {
        return this.blockEntity != null;
    }

    /** Adds this machine's own slots. Called during construction. */
    protected abstract void addMachineSlots();

    /**
     * The machine's slots as a {@link Container}.
     *
     * <p>Every machine slot is built over this one object, including the custom slots a
     * subclass adds with {@link #addCustomSlot}, so there is a single route from a slot to
     * the block entity's inventory.</p>
     */
    protected final Container machineSlots() {
        return this.machineSlots;
    }

    /**
     * Adds a slot this menu does not build itself and counts it as a machine slot.
     *
     * <p>The hook exists for a machine whose input slots validate something the plain
     * helpers cannot express — the Wafer Slicer's boule slot and its empty-FOUP slot are
     * the first. Two rules come with it: the slot has to be added from
     * {@link #addMachineSlots()}, which the base constructor runs <em>before</em> the 36
     * player slots, because {@link #quickMoveStack} splits the slot list on the machine
     * count; and it has to be built over {@link #machineSlots()}. Added anywhere else it
     * would be counted as a player slot and shift-clicks would be routed to the wrong half
     * of the menu.</p>
     */
    protected final void addCustomSlot(Slot slot) {
        addSlot(slot);
        this.machineSlotCount++;
    }

    /** Adds a slot that takes anything the machine declares valid. */
    protected final void addMachineSlot(int index, int x, int y) {
        addMachineSlot(index, x, y, true);
    }

    /**
     * Adds a slot, with control over whether the player may put things into it.
     *
     * <p>{@code machineSlotCount} is counted here as slots are added rather than taken from
     * the block entity. A menu is free to expose fewer slots than the block entity has — the
     * framework's storage slots exist for the machine and the drop path, not necessarily for
     * the player — and shift-click routing splits the slot list on that number, so reading
     * it from the block entity would silently treat the first player inventory slots as
     * machine slots.</p>
     */
    protected final void addMachineSlot(int index, int x, int y, boolean mayPlace) {
        addCustomSlot(new Slot(this.machineSlots, index, x, y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return mayPlace && index < inputSlotCount;
            }
        });
    }

    /** Adds a slot that takes only FOUP stacks, one per slot. */
    protected final void addFoupSlot(int index, int x, int y) {
        addCustomSlot(new FoupSlot(this.machineSlots, index, x, y));
    }

    /** Adds an output-only slot: the player can take from it but not put anything in. */
    protected final void addOutputSlot(int index, int x, int y) {
        addCustomSlot(new Slot(this.machineSlots, index, x, y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(Player player) {
                return true;
            }
        });
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

    /** Stored FE. */
    public final int energy() {
        return this.data.get(FabMachineBlockEntity.DATA_ENERGY);
    }

    /** Capacity in FE. */
    public final int energyCapacity() {
        return this.data.get(FabMachineBlockEntity.DATA_CAPACITY);
    }

    /** Ticks completed. */
    public final int progress() {
        return this.data.get(FabMachineBlockEntity.DATA_PROGRESS);
    }

    /** Ticks the running recipe needs. */
    public final int ticks() {
        return this.data.get(FabMachineBlockEntity.DATA_TICKS);
    }

    /** Progress from 0 to 1, for the screen's progress bar. */
    public final float progressFraction() {
        int total = ticks();
        return total <= 0 ? 0.0f : Math.min(1.0f, (float) progress() / total);
    }

    /** Energy fill from 0 to 1, for the screen's energy bar. */
    public final float energyFraction() {
        int capacity = energyCapacity();
        return capacity <= 0 ? 0.0f : Math.min(1.0f, (float) energy() / capacity);
    }

    public final boolean isRunning() {
        return this.blockEntity != null && this.blockEntity.runner().isRunning();
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.blockEntity == null || this.blockEntity.getLevel() == null) {
            return false;
        }
        if (this.blockEntity.getLevel().getBlockEntity(this.blockEntity.getBlockPos()) != this.blockEntity) {
            return false;
        }
        return player.distanceToSqr(
            this.blockEntity.getBlockPos().getX() + 0.5,
            this.blockEntity.getBlockPos().getY() + 0.5,
            this.blockEntity.getBlockPos().getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack original = slot.getItem();
        ItemStack copy = original.copy();

        boolean moved = index < this.machineSlotCount
            ? moveItemStackTo(original, this.machineSlotCount, slots.size(), true)
            : moveItemStackTo(original, 0, this.machineSlotCount, false);

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
