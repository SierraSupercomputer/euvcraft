package com.euvcraft.lithmod.mc.machine.process;

import com.euvcraft.lithmod.core.DieTypes;
import com.euvcraft.lithmod.core.MaskData;
import com.euvcraft.lithmod.mc.registry.ModDataComponents;
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
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Two views of one eight-slot inventory. Permissions are server-owned; sync is coarse and public. */
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
    private boolean resistInsertionInProgress;
    private int observedQuickcraftType = -1;

    public ProcessCellMenu(int containerId, Inventory playerInventory, FabMachineBlockEntity machine) {
        this(ModMultiblocks.PROCESS_CELL_MENU_TYPE.get(), containerId, playerInventory, machine);
    }

    protected ProcessCellMenu(MenuType<?> type, int containerId, Inventory playerInventory, @Nullable FabMachineBlockEntity machine) {
        super(type, containerId, playerInventory, machine,
                ProcessCellControllerBlockEntity.INPUT_SLOTS, ProcessCellControllerBlockEntity.SLOT_COUNT);

        // Both sides get their own ContainerData; server updates it from controller getters.
        this.cellData = machine instanceof ProcessCellControllerBlockEntity controller ? new ContainerData() {
            @Override
            public int get(int index) {
                MaskData mask = controller.slot(ProcessCellControllerBlockEntity.SLOT_MASK)
                    .get(ModDataComponents.MASK_DATA.get());
                return switch (index) {
                    case 0 -> controller.aborted() ? STATUS_ABORTED : controller.definition().isEmpty()
                        ? STATUS_DEFINITION_MISSING : controller.processState().ordinal();
                    case 1 -> controller.lastRefusal() == null ? REFUSAL_NONE : controller.lastRefusal().code().ordinal() + 1;
                    case 2 -> controller.currentLayer();
                    case 3 -> controller.totalLayers();
                    case 4 -> controller.isFormed() ? 1 : 0;
                    case 5 -> mask == null ? controller.tierIndex() : mask.tierIndex();
                    case 6 -> {
                        String die = mask == null ? controller.dieType() : mask.dieType();
                        yield die == null ? 0 : DieTypes.ALL.indexOf(die) + 1;
                    }
                    case 7 -> controller.inputsLocked() ? 1 : 0;
                    default -> 0;
                };
            }
            @Override public void set(int index, int value) { }
            @Override public int getCount() { return 8; }
        } : new SimpleContainerData(8);
        this.serverSide = machine instanceof ProcessCellControllerBlockEntity;
        addDataSlots(this.cellData);
    }

    public ProcessCellMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, (FabMachineBlockEntity) null);
    }

    /** Fixed role, safe even during the superclass constructor. */
    public boolean isOperatingPanel() { return false; }

    public final int visibleMachineSlotCount() { return isOperatingPanel() ? 3 : 5; }

    @Override
    public boolean stillValid(Player player) {
        return blockEntity != null && blockEntity.getLevel() == player.level() && super.stillValid(player);
    }

    @Override
    protected void addMachineSlots() {
        if (isOperatingPanel()) {
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
            public boolean mayPickup(Player player) {
                // Vanilla's single-target drag delegates privately to PICKUP. This permission
                // exists only inside an insertion operation with a matching nonempty cursor.
                return resistInsertionInProgress || !inputsLocked();
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                if (inputsLocked()) {
                    ProcessCellControllerBlockEntity controller = getController();
                    // The client predicts catalog placement; the server enforces the frozen item/components.
                    return controller == null ? !stack.isEmpty() && ResistCatalog.tierOf(stack) > 0
                        : controller.canTopUpResist(stack);
                }
                return !stack.isEmpty() && ResistCatalog.tierOf(stack) > 0;
            }

            @Override
            public int getMaxStackSize() {
                return 64;
            }
        });

            return;
        }
        // Slot 3: LIGHT_SOURCE
        addCustomSlot(new LockedComponentSlot(machineSlots(), ProcessCellControllerBlockEntity.SLOT_LIGHT_SOURCE, 26, 35, "light_source"));

        // Slot 4: OPTICS
        addCustomSlot(new LockedComponentSlot(machineSlots(), ProcessCellControllerBlockEntity.SLOT_OPTICS, 53, 35, "optics"));

        // Slot 5: STAGE
        addCustomSlot(new LockedComponentSlot(machineSlots(), ProcessCellControllerBlockEntity.SLOT_STAGE, 80, 35, "stage"));

        // Slot 6: CLEANROOM
        addCustomSlot(new LockedComponentSlot(machineSlots(), ProcessCellControllerBlockEntity.SLOT_CLEANROOM, 107, 35, "cleanroom"));

        // Slot 7: METROLOGY
        addCustomSlot(new LockedComponentSlot(machineSlots(), ProcessCellControllerBlockEntity.SLOT_METROLOGY, 134, 35, "metrology"));
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
        return controller != null ? controller.inputsLocked() : cellData != null && locked() != 0;
    }

    /**
     * Whether the controller is in the aborted-latched state (Abort was pressed, waiting for FOUP removal).
     */
    public boolean aborted() {
        ProcessCellControllerBlockEntity controller = getController();
        return controller != null ? controller.aborted() : status() == STATUS_ABORTED;
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

    /** Tier index, or zero when no mask or accepted contract. */
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
     * Server-validated Abort button handler.
     * Returns false for: other id, client side, not a ServerPlayer, wrong player.containerMenu,
     * stale or wrong world, distance > 8, no active input lock.
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!isOperatingPanel() || id != ABORT_BUTTON) return false;
        if (!serverSide) return false;
        if (!(player instanceof net.minecraft.server.level.ServerPlayer)) return false;
        if (player.containerMenu != this) return false;
        if (!stillValid(player)) return false;

        ProcessCellControllerBlockEntity controller = getController();
        if (controller == null || !(controller.getLevel() instanceof net.minecraft.server.level.ServerLevel)) return false;
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
        if (inputsLocked() && index >= 0 && index < visibleMachineSlotCount())
            return ItemStack.EMPTY;
        return super.quickMoveStack(player, index);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        int index = slots.indexOf(slot);
        return !(inputsLocked() && index >= 0 && index < visibleMachineSlotCount())
            && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public void clicked(int slotIndex, int button, net.minecraft.world.inventory.ContainerInput action, Player player) {
        if (action == net.minecraft.world.inventory.ContainerInput.QUICK_CRAFT) {
            int header = getQuickcraftHeader(button);
            if (header == QUICKCRAFT_HEADER_START) observedQuickcraftType = getQuickcraftType(button);
            ItemStack carried = getCarried();
            resistInsertionInProgress = isOperatingPanel() && inputsLocked() && !carried.isEmpty()
                && (observedQuickcraftType == QUICKCRAFT_TYPE_CHARITABLE
                    || observedQuickcraftType == QUICKCRAFT_TYPE_GREEDY)
                && slots.get(ProcessCellControllerBlockEntity.SLOT_RESIST).mayPlace(carried);
            try {
                super.clicked(slotIndex, button, action, player);
            } finally {
                resistInsertionInProgress = false;
                if (header >= QUICKCRAFT_HEADER_END) observedQuickcraftType = -1;
            }
            return;
        }
        observedQuickcraftType = -1;
        // Vanilla checks mayPickup before merging into an occupied slot. Permit insertion
        // explicitly while keeping every extraction and swap path locked.
        if (isOperatingPanel() && inputsLocked() && slotIndex == ProcessCellControllerBlockEntity.SLOT_RESIST
                && action == net.minecraft.world.inventory.ContainerInput.PICKUP
                && (button == 0 || button == 1)) {
            resetQuickCraft();
            ItemStack carried = getCarried();
            Slot resist = slots.get(ProcessCellControllerBlockEntity.SLOT_RESIST);
            if (!carried.isEmpty() && resist.mayPlace(carried)) {
                setCarried(resist.safeInsert(carried, button == 0 ? carried.getCount() : 1));
            }
            return;
        }
        // Guard hotbar SWAP/THROW/CLONE/QUICK_MOVE on locked machine slots
        if (inputsLocked() && slotIndex >= 0 && slotIndex < visibleMachineSlotCount()) {
            // action values in 26.3: PICKUP, QUICK_MOVE, SWAP, CLONE, THROW, QUICK_CRAFT
            switch (action) {
                case SWAP, THROW, CLONE, QUICK_MOVE -> {
                    // Block these actions on locked machine input slots
                    resetQuickCraft();
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

    @Override
    public boolean canDragTo(Slot slot) {
        // Creative drag-fill may still operate on the player's own inventory, but must
        // never create additional resist in a locked machine input.
        return !(isOperatingPanel() && inputsLocked() && observedQuickcraftType == QUICKCRAFT_TYPE_CLONE
                && slot == slots.get(ProcessCellControllerBlockEntity.SLOT_RESIST))
            && super.canDragTo(slot);
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

    }
}
