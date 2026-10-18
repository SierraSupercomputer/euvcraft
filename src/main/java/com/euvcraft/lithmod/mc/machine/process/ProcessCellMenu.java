package com.euvcraft.lithmod.mc.machine.process;

import com.euvcraft.lithmod.core.DieTypes;
import com.euvcraft.lithmod.core.ProcessState;
import com.euvcraft.lithmod.core.RefusalReason;
import com.euvcraft.lithmod.mc.ResistCatalog;
import com.euvcraft.lithmod.mc.machine.ComponentSlot;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.machine.FabMachineMenu;
import com.euvcraft.lithmod.mc.machine.FoupSlot;
import com.euvcraft.lithmod.mc.multiblock.ModMultiblocks;
import com.euvcraft.lithmod.mc.multiblock.ProcessCellControllerBlockEntity;
import com.euvcraft.lithmod.mc.registry.ModItems;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Menu for the Process Cell: eight hand-fed input slots with run-state locking.
 * <p>
 * Slot layout (indices 0..7):
 * 0 FOUP, 1 MASK, 2 RESIST, 3 LIGHT_SOURCE, 4 OPTICS, 5 STAGE, 6 CLEANROOM, 7 METROLOGY
 * </p>
 * <p>
 * While the controller reports {@link ProcessCellControllerBlockEntity#inputsLocked()} (running,
 * paused, or pending), the FOUP, mask and component slots are locked for placement and pickup.
 * The resist slot permits placement <em>only</em> when {@link ProcessCellControllerBlockEntity#canTopUpResist(ItemStack)}
 * returns true — same item and same components as the accepted contract. This works even when the
 * resist slot is currently empty.
 * </p>
 * <p>
 * In idle, aborted or finished states, normal hand-feeding with the same predicates and max
 * counts is permitted.
 * </p>
 * <p>
 * Eight coarse {@link ContainerData} values are synced after the base four:
 * <ol>
 *   <li>{@code STATUS} (index 4): {@link ProcessState} ordinal 0..5 plus {@code ABORTED=6}, {@code DEFINITION_MISSING=7}</li>
 *   <li>{@code REFUSAL} (index 5): {@link RefusalReason.Code} ordinal + 1, 0 = none</li>
 *   <li>{@code LAYER} (index 6): current layer (0 when no run)</li>
 *   <li>{@code TOTAL} (index 7): total layers (0 when no contract)</li>
 *   <li>{@code FORMED} (index 8): 1 if cell is formed, 0 otherwise</li>
 *   <li>{@code TIER} (index 9): tier index + 1 (0 when no contract)</li>
 *   <li>{@code DIE} (index 10): {@link DieTypes#ALL} index + 1 (0 when no contract)</li>
 *   <li>{@code LOCKED} (index 11): 1 if inputs are locked, 0 otherwise</li>
 * </ol>
 * </p>
 * <p>
 * Server reads the controller's current getters and definition; mask tier/die display prefer
 * {@link com.euvcraft.lithmod.mc.registry.ModDataComponents#MASK_DATA} if present, falling back to
 * the accepted contract's public tier/die via the controller's existing getters. The client uses a
 * {@link SimpleContainerData} of size 8. No whole FOUP lot, {@link com.euvcraft.lithmod.mc.machine.process.ProcessRunRequest},
 * per-wafer rolls/flags/pass counts or defect factor are serialized into the menu. No custom sync
 * packet or full NBT is added.
 * </p>
 * <p>
 * {@link #quickMoveStack} is overridden to reject extraction from locked machine indices 0..7
 * because the base implementation ignores {@code mayPickup}. Player-to-machine quick-move may
 * route only valid same-type resist while locked via the actual slot predicates.
 * </p>
 * <p>
 * Hotbar SWAP, THROW, CLONE and QUICK_MOVE on locked machine slots are guarded in
 * {@link #clicked(int, int, net.minecraft.world.inventory.ClickAction, Player)} using the actual
 * {@link net.minecraft.world.inventory.ClickAction} API. Normal PICKUP and QUICK_CRAFT same-type
 * resist top-up is preserved. Double-click/PICKUP_ALL must not extract locked machine inputs;
 * {@link Slot#canTakeItemForPickAll(ItemStack)} is overridden on the locked slots.
 * </p>
 */
public class ProcessCellMenu extends FabMachineMenu {

    // ContainerData wire indices (base DATA_COUNT = 4)
    public static final int DATA_STATUS = FabMachineBlockEntity.DATA_COUNT + 0; // 4
    public static final int DATA_REFUSAL = FabMachineBlockEntity.DATA_COUNT + 1; // 5
    public static final int DATA_LAYER = FabMachineBlockEntity.DATA_COUNT + 2; // 6
    public static final int DATA_TOTAL = FabMachineBlockEntity.DATA_COUNT + 3; // 7
    public static final int DATA_FORMED = FabMachineBlockEntity.DATA_COUNT + 4; // 8
    public static final int DATA_TIER = FabMachineBlockEntity.DATA_COUNT + 5; // 9
    public static final int DATA_DIE = FabMachineBlockEntity.DATA_COUNT + 6; // 10
    public static final int DATA_LOCKED = FabMachineBlockEntity.DATA_COUNT + 7; // 11

    // Status values
    public static final int STATUS_ABORTED = 6;
    public static final int STATUS_DEFINITION_MISSING = 7;

    // Refusal values: Code.ordinal() + 1, 0 = none
    public static final int REFUSAL_NONE = 0;

    // Abort button ID
    public static final int ABORT_BUTTON = 0;

    private final ContainerData cellData;
    private final boolean serverSide;

    public ProcessCellMenu(int containerId, Inventory playerInventory, FabMachineBlockEntity machine) {
        super(ModMultiblocks.PROCESS_CELL_MENU_TYPE.get(), containerId, playerInventory, machine,
                ProcessCellControllerBlockEntity.INPUT_SLOTS, ProcessCellControllerBlockEntity.SLOT_COUNT);

        // Both sides get their own ContainerData; server updates it from controller getters.
        this.cellData = new SimpleContainerData(8);
        this.serverSide = machine instanceof ProcessCellControllerBlockEntity;
        addDataSlots(this.cellData);
    }

    public ProcessCellMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, (FabMachineBlockEntity) null);
    }

    @Override
    protected void addMachineSlots() {
        // Slot 0: FOUP
        addCustomSlot(new LockedFoupSlot(machineSlots(), ProcessCellControllerBlockEntity.SLOT_FOUP, 26, 35));

        // Slot 1: MASK
        addCustomSlot(new LockedSlot(machineSlots(), ProcessCellControllerBlockEntity.SLOT_MASK, 62, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return !inputsLocked() && stack.is(ModItems.MASK.get());
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        // Slot 2: RESIST - allows top-up only when same item+components as accepted contract
        addCustomSlot(new LockedSlot(machineSlots(), ProcessCellControllerBlockEntity.SLOT_RESIST, 98, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                if (inputsLocked()) {
                    ProcessCellControllerBlockEntity controller = getController();
                    return controller != null && controller.canTopUpResist(stack);
                }
                return !stack.isEmpty() && ResistCatalog.tierOf(stack) > 0;
            }

            @Override
            public int getMaxStackSize() {
                return 64;
            }
        });

        // Slot 3: LIGHT_SOURCE
        addCustomSlot(new LockedComponentSlot(machineSlots(), ProcessCellControllerBlockEntity.SLOT_LIGHT_SOURCE, 134, 35, "light_source"));

        // Slot 4: OPTICS
        addCustomSlot(new LockedComponentSlot(machineSlots(), ProcessCellControllerBlockEntity.SLOT_OPTICS, 26, 53, "optics"));

        // Slot 5: STAGE
        addCustomSlot(new LockedComponentSlot(machineSlots(), ProcessCellControllerBlockEntity.SLOT_STAGE, 62, 53, "stage"));

        // Slot 6: CLEANROOM
        addCustomSlot(new LockedComponentSlot(machineSlots(), ProcessCellControllerBlockEntity.SLOT_CLEANROOM, 98, 53, "cleanroom"));

        // Slot 7: METROLOGY
        addCustomSlot(new LockedComponentSlot(machineSlots(), ProcessCellControllerBlockEntity.SLOT_METROLOGY, 134, 53, "metrology"));
    }

    /**
     * Returns the controller block entity if this is the server-side menu, null otherwise.
     */
    private @Nullable ProcessCellControllerBlockEntity getController() {
        return serverSide && blockEntity instanceof ProcessCellControllerBlockEntity controller ? controller : null;
    }

    /**
     * Whether the controller currently has inputs locked (running, paused, or pending).
     */
    public boolean inputsLocked() {
        ProcessCellControllerBlockEntity controller = getController();
        return controller != null && controller.inputsLocked();
    }

    /**
     * Whether the controller is in the aborted-latched state (Abort was pressed, waiting for FOUP removal).
     */
    public boolean aborted() {
        ProcessCellControllerBlockEntity controller = getController();
        return controller != null && controller.aborted();
    }

    /**
     * Current process state as a coarse status value for the client.
     * Ordinals 0..5 match ProcessState; 6 = ABORTED, 7 = DEFINITION_MISSING.
     */
    public int status() {
        return cellData.get(DATA_STATUS - FabMachineBlockEntity.DATA_COUNT);
    }

    /** Refusal reason as Code.ordinal() + 1, or 0 for none. */
    public int refusal() {
        return cellData.get(DATA_REFUSAL - FabMachineBlockEntity.DATA_COUNT);
    }

    /** Current layer (0 when no run). */
    public int currentLayer() {
        return cellData.get(DATA_LAYER - FabMachineBlockEntity.DATA_COUNT);
    }

    /** Total layers in the contract (0 when no contract). */
    public int totalLayers() {
        return cellData.get(DATA_TOTAL - FabMachineBlockEntity.DATA_COUNT);
    }

    /** Whether the cell is formed (1) or not (0). */
    public int formed() {
        return cellData.get(DATA_FORMED - FabMachineBlockEntity.DATA_COUNT);
    }

    /** Tier index + 1 (0 when no contract). */
    public int tierIndex() {
        return cellData.get(DATA_TIER - FabMachineBlockEntity.DATA_COUNT);
    }

    /** DieTypes.ALL index + 1 (0 when no contract). */
    public int dieIndex() {
        return cellData.get(DATA_DIE - FabMachineBlockEntity.DATA_COUNT);
    }

    /** Whether inputs are locked (1) or not (0). */
    public int locked() {
        return cellData.get(DATA_LOCKED - FabMachineBlockEntity.DATA_COUNT);
    }

    /**
     * Server-side: called each tick to push the controller's current coarse state into the
     * synced ContainerData. No-op on the client.
     */
    public void updateContainerData() {
        if (!serverSide) return;
        ProcessCellControllerBlockEntity controller = getController();
        if (controller == null) return;

        ProcessState state = controller.processState();
        int status;
        if (controller.aborted()) {
            status = STATUS_ABORTED;
        } else if (!controller.definition().isPresent()) {
            status = STATUS_DEFINITION_MISSING;
        } else {
            status = state.ordinal();
        }
        cellData.set(DATA_STATUS - FabMachineBlockEntity.DATA_COUNT, status);

        RefusalReason refusal = controller.lastRefusal();
        int refusalVal = (refusal == null) ? REFUSAL_NONE : refusal.code().ordinal() + 1;
        cellData.set(DATA_REFUSAL - FabMachineBlockEntity.DATA_COUNT, refusalVal);

        cellData.set(DATA_LAYER - FabMachineBlockEntity.DATA_COUNT, controller.currentLayer());
        cellData.set(DATA_TOTAL - FabMachineBlockEntity.DATA_COUNT, controller.totalLayers());
        cellData.set(DATA_FORMED - FabMachineBlockEntity.DATA_COUNT, controller.isFormed() ? 1 : 0);
        cellData.set(DATA_TIER - FabMachineBlockEntity.DATA_COUNT, controller.tierIndex() + 1);
        String die = controller.dieType();
        int dieIdx = (die == null) ? 0 : (DieTypes.ALL.indexOf(die) + 1);
        cellData.set(DATA_DIE - FabMachineBlockEntity.DATA_COUNT, dieIdx);
        cellData.set(DATA_LOCKED - FabMachineBlockEntity.DATA_COUNT, controller.inputsLocked() ? 1 : 0);
    }

    /**
     * Server-validated Abort button handler.
     * Returns false for: other id, client side, not a ServerPlayer, wrong player.containerMenu,
     * stale or wrong world, distance > 8, no active input lock.
     */
    public boolean clickMenuButton(Player player, int id) {
        if (id != ABORT_BUTTON) return false;
        if (!serverSide) return false;
        if (!(player instanceof net.minecraft.server.level.ServerPlayer)) return false;
        if (player.containerMenu != this) return false;
        if (!stillValid(player)) return false;

        ProcessCellControllerBlockEntity controller = getController();
        if (controller == null) return false;
        if (controller.getLevel() != player.level()) return false;

        double dx = player.getX() - (controller.getBlockPos().getX() + 0.5);
        double dy = player.getY() - (controller.getBlockPos().getY() + 0.5);
        double dz = player.getZ() - (controller.getBlockPos().getZ() + 0.5);
        if (dx * dx + dy * dy + dz * dz > 64.0) return false; // 8^2 = 64

        if (!controller.inputsLocked()) return false;

        controller.abortRun();
        return true;
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

        // While inputs are locked, reject extraction FROM locked machine slots (indices 0..7)
        // because base quickMoveStack ignores mayPickup.
        if (inputsLocked() && index < machineSlotCount && index < ProcessCellControllerBlockEntity.INPUT_SLOTS) {
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

    @Override
    public void clicked(int slotIndex, int button, net.minecraft.world.inventory.ClickAction action, Player player) {
        // Guard hotbar SWAP/THROW/CLONE/QUICK_MOVE on locked machine slots
        if (inputsLocked() && slotIndex >= 0 && slotIndex < machineSlotCount && slotIndex < ProcessCellControllerBlockEntity.INPUT_SLOTS) {
            // action values in 26.3: PICKUP, QUICK_MOVE, SWAP, CLONE, THROW, QUICK_CRAFT
            switch (action) {
                case SWAP, THROW, CLONE, QUICK_MOVE -> {
                    // Block these actions on locked machine input slots
                    return;
                }
                case PICKUP, QUICK_CRAFT -> {
                    // Allow normal pickup and quick-craft (for resist top-up)
                }
                default -> {
                    // Allow any other action
                }
            }
        }
        super.clicked(slotIndex, button, action, player);
    }

    // ------------------------------------------------------------------------
    // Locked slot implementations
    // ------------------------------------------------------------------------

    /**
     * FOUP slot that locks pickup and placement when inputs are locked.
     */
    private class LockedFoupSlot extends FoupSlot {
        LockedFoupSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (inputsLocked()) return false;
            return super.mayPlace(stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            if (inputsLocked()) return false;
            return super.mayPickup(player);
        }

        @Override
        public boolean canTakeItemForPickAll(ItemStack stack) {
            if (inputsLocked()) return false;
            return super.canTakeItemForPickAll(stack);
        }
    }

    /**
     * Base locked slot: locks pickup when inputs are locked; placement is controlled by subclass.
     * Also blocks PICKUP_ALL extraction when locked.
     */
    private abstract class LockedSlot extends Slot {
        LockedSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            if (inputsLocked()) return false;
            return true;
        }

        @Override
        public boolean canTakeItemForPickAll(ItemStack stack) {
            if (inputsLocked()) return false;
            return super.canTakeItemForPickAll(stack);
        }
    }

    /**
     * Component slot that locks pickup and placement when inputs are locked.
     */
    private class LockedComponentSlot extends ComponentSlot {
        LockedComponentSlot(Container container, int index, int x, int y, String kind) {
            super(container, index, x, y, kind);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (inputsLocked()) return false;
            return super.mayPlace(stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            if (inputsLocked()) return false;
            return super.mayPickup(player);
        }

        @Override
        public boolean canTakeItemForPickAll(ItemStack stack) {
            if (inputsLocked()) return false;
            return super.canTakeItemForPickAll(stack);
        }
    }
}