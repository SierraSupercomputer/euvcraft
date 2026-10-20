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
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Eight hand-fed inputs. The server owns permissions; only coarse public state is synced. */
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
        super(ModMultiblocks.PROCESS_CELL_MENU_TYPE.get(), containerId, playerInventory, machine,
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

    /**
     * Returns whether this is the operating panel (upper) menu. Override in subclass.
     */
    protected boolean isOperatingPanel() {
        return false;
    }

    /**
     * Returns the number of visible machine slots for this menu.
     * Equipment (lower) menu shows 5 slots (underlying 3..7).
     * Operating panel (upper) menu shows 3 slots (underlying 0..2).
     */
    public int visibleMachineSlotCount() {
        return isOperatingPanel() ? 3 : 5;
    }

    /**
     * Returns the starting underlying slot index for this menu's visible slots.
     * Equipment menu starts at 3 (STAGE), operating panel starts at 0 (FOUP).
     */
    protected int visibleSlotStartIndex() {
        return isOperatingPanel() ? 0 : 3;
    }

    @Override
    protected void addMachineSlots() {
        int start = visibleSlotStartIndex();
        int count = visibleMachineSlotCount();
        int x = 26;
        int y = 35;
        int col = 0;
        int row = 0;

        for (int i = 0; i < count; i++) {
            int underlyingIndex = start + i;
            int slotX = x + col * 36;
            int slotY = y + row * 18;

            switch (underlyingIndex) {
                case ProcessCellControllerBlockEntity.SLOT_FOUP -> // 0 - not used in equipment menu
                    throw new IllegalStateException("FOUP slot should not appear in equipment menu");
                case ProcessCellControllerBlockEntity.SLOT_MASK -> // 1 - not used in equipment menu
                    throw new IllegalStateException("MASK slot should not appear in equipment menu");
                case ProcessCellControllerBlockEntity.SLOT_RESIST -> // 2 - not used in equipment menu
                    throw new IllegalStateException("RESIST slot should not appear in equipment menu");
                case ProcessCellControllerBlockEntity.SLOT_LIGHT_SOURCE -> { // 3
                    addCustomSlot(new LockedComponentSlot(machineSlots(), underlyingIndex, slotX, slotY, "light_source"));
                }
                case ProcessCellControllerBlockEntity.SLOT_OPTICS -> { // 4
                    addCustomSlot(new LockedComponentSlot(machineSlots(), underlyingIndex, slotX, slotY, "optics"));
                }
                case ProcessCellControllerBlockEntity.SLOT_STAGE -> { // 5
                    addCustomSlot(new LockedComponentSlot(machineSlots(), underlyingIndex, slotX, slotY, "stage"));
                }
                case ProcessCellControllerBlockEntity.SLOT_CLEANROOM -> { // 6
                    addCustomSlot(new LockedComponentSlot(machineSlots(), underlyingIndex, slotX, slotY, "cleanroom"));
                }
                case ProcessCellControllerBlockEntity.SLOT_METROLOGY -> { // 7
                    addCustomSlot(new LockedComponentSlot(machineSlots(), underlyingIndex, slotX, slotY, "metrology"));
                }
                default -> throw new IllegalStateException("Unexpected slot index: " + underlyingIndex);
            }

            col++;
            if (col >= 4) {
                col = 0;
                row++;
            }
        }
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
        if (id != ABORT_BUTTON) return false;
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
            // Equipment menu has no resist slot visible; resistInsertionInProgress only relevant for operating panel
            resistInsertionInProgress = false;
            try {
                super.clicked(slotIndex, button, action, player);
            } finally {
                resistInsertionInProgress = false;
                if (header >= QUICKCRAFT_HEADER_END) observedQuickcraftType = -1;
            }
            return;
        }
        observedQuickcraftType = -1;
        // Guard hotbar SWAP/THROW/CLONE/QUICK_MOVE on locked machine slots (only our visible slots)
        if (inputsLocked() && slotIndex >= 0 && slotIndex < visibleMachineSlotCount()) {
            // action values in 26.3: PICKUP, QUICK_MOVE, SWAP, CLONE, THROW, QUICK_CRAFT
            switch (action) {
                case SWAP, THROW, CLONE, QUICK_MOVE -> {
                    // Block these actions on locked machine input slots
                    resetQuickCraft();
                    return;
                }
                case PICKUP, QUICK_CRAFT -> {
                    // Allow normal pickup and quick-craft
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
        // Equipment menu has no resist slot visible; no special handling needed
        return super.canDragTo(slot);
    }

    /**
     * Equipment (lower) menu rejects Abort button clicks. Only the operating panel (upper) can abort.
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        // Equipment menu never handles Abort
        return false;
    }

    /**
     * Equipment menu stillValid: checks distance to controller (same as before).
     * Does not check for operating panel presence.
     */
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
