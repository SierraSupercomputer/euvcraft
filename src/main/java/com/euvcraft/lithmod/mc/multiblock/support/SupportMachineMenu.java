package com.euvcraft.lithmod.mc.multiblock.support;

import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.machine.FabMachineMenu;
import com.euvcraft.lithmod.mc.registry.ModItems;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The two shell menus of the shared support foundation, one class: the Boule Purifier shell and
 * the E-beam Mask Writer shell.
 *
 * <p><b>Why two menu types.</b> The two kinds have different inventory sizes (4 vs 3 slots), and
 * the client-side menu has no block entity — the base class replaces it with a {@code SimpleContainer}
 * of the given size — so the kind has to be known before any slot is built. The kind is therefore
 * carried by the menu type the server and client both resolve, exactly as the silicon chain's
 * menu types carry the two machine shells, and the base constructor guards
 * {@code size == blockEntity.size} on the server. The screen stays one class.</p>
 *
 * <p><b>Fixed underlying indices</b>, which the tests pin:</p>
 * <ul>
 *   <li>Purifier: 0 ordinary boule in, 1 water bucket in, 2 empty-bucket out, 3 pure boule out.</li>
 *   <li>E-beam:   0 blank plate in, 1 redstone in, 2 mask out.</li>
 * </ul>
 * The pure-boule and cut-mask items are later-job products: the outputs never accept anything
 * until then, they are output-only slots.
 *
 * <p><b>Sync</b> is coarse and public: the base {@code ContainerData} carries stored FE, capacity
 * and progress (zero until the job phase), and the menu's own three values carry formed status and
 * the purifier's water amount/capacity. 26.3 syncs full ints through
 * {@code AdvancedContainerSetDataPayload}, so the e-beam's 4 000 000 FE capacity is preserved
 * as-written; no 16-bit clamping exists in this path.</p>
 */
public class SupportMachineMenu extends FabMachineMenu {

    // ContainerData wire indices (menu-local, after the base data count)
    public static final int WIRE_FORMED = 0;
    public static final int WIRE_WATER = 1;
    public static final int WIRE_WATER_CAPACITY = 2;
    public static final int EXTRA_DATA_COUNT = 3;

    // Slot coordinates per kind (relative to the 176x220 shell)
    private static final int IN_0_X = 62, IN_1_X = 80, OUT_A_X = 104, OUT_B_X = 122;
    private static final int SLOT_Y = 35;

    private final ContainerData extraData;

    /** Server-side menu for a real controller; the kind comes from the actual block state. */
    public static SupportMachineMenu forMachine(int containerId, Inventory playerInventory,
                                                SupportMachineBlockEntity machine) {
        return new SupportMachineMenu(menuTypeFor(machine.kind()), containerId, playerInventory, machine,
            machine.kind());
    }

    /** Client-side purifier shell; registered against the purifier menu type. */
    public static SupportMachineMenu clientPurifier(int containerId, Inventory playerInventory,
                                                    RegistryFriendlyByteBuf buffer) {
        return new SupportMachineMenu(SupportMachines.SUPPORT_PURIFIER_MENU.get(), containerId,
            playerInventory, null, SupportMachineKind.PURIFIER);
    }

    /** Client-side e-beam shell; registered against the e-beam menu type. */
    public static SupportMachineMenu clientEbeam(int containerId, Inventory playerInventory,
                                                 RegistryFriendlyByteBuf buffer) {
        return new SupportMachineMenu(SupportMachines.SUPPORT_EBEAM_MENU.get(), containerId,
            playerInventory, null, SupportMachineKind.EBEAM);
    }

    private static MenuType<?> menuTypeFor(SupportMachineKind kind) {
        return kind == SupportMachineKind.PURIFIER
            ? SupportMachines.SUPPORT_PURIFIER_MENU.get()
            : SupportMachines.SUPPORT_EBEAM_MENU.get();
    }

    private SupportMachineMenu(MenuType<?> type, int containerId, Inventory playerInventory,
                               @Nullable SupportMachineBlockEntity machine, SupportMachineKind kind) {
        super(type, containerId, playerInventory, machine,
            kind.inputSlots(), machine == null ? kind.slotCount() : machine.getContainerSize());
        this.extraData = machine == null ? new SimpleContainerData(EXTRA_DATA_COUNT) : new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case WIRE_FORMED -> machine.isFormed() ? 1 : 0;
                    case WIRE_WATER -> machine.waterAmount();
                    case WIRE_WATER_CAPACITY -> machine.waterCapacity();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) { }

            @Override
            public int getCount() {
                return EXTRA_DATA_COUNT;
            }
        };
        addDataSlots(this.extraData);
    }

    /**
     * The kind this shell serves.
     *
     * <p>Safe to call during the base constructor: the menu type is already assigned when
     * {@link #addMachineSlots()} runs, because {@code AbstractContainerMenu} sets it before the
     * subclass constructor continues.</p>
     */
    public final SupportMachineKind kind() {
        return getType() == SupportMachines.SUPPORT_PURIFIER_MENU.get()
            ? SupportMachineKind.PURIFIER
            : SupportMachineKind.EBEAM;
    }

    /** Whether the machine is formed (1) or not (0). */
    public int formed() {
        return this.extraData.get(WIRE_FORMED);
    }

    /** Water stored in mB; 0 on the e-beam shell. */
    public int water() {
        return this.extraData.get(WIRE_WATER);
    }

    /** Water capacity in mB; 0 on the e-beam shell. */
    public int waterCapacity() {
        return this.extraData.get(WIRE_WATER_CAPACITY);
    }

    @Override
    public boolean stillValid(net.minecraft.world.entity.player.Player player) {
        return blockEntity != null && blockEntity.getLevel() == player.level() && super.stillValid(player);
    }

    @Override
    protected void addMachineSlots() {
        Container slots = machineSlots();
        if (kind() == SupportMachineKind.PURIFIER) {
            // Input validation accepts only the known present items; outputs never accept input.
            addCustomSlot(new ValidatedInputSlot(slots, SLOT_IN_0, IN_0_X, SLOT_Y, ModItems.SILICON_BOULE.get()));
            addCustomSlot(new ValidatedInputSlot(slots, SLOT_IN_1, IN_1_X, SLOT_Y, net.minecraft.world.item.Items.WATER_BUCKET));
            addOutputSlot(2, OUT_A_X, SLOT_Y);
            addOutputSlot(3, OUT_B_X, SLOT_Y);
        } else {
            addCustomSlot(new ValidatedInputSlot(slots, SLOT_IN_0, IN_0_X, SLOT_Y, ModItems.BLANK_MASK.get()));
            addCustomSlot(new ValidatedInputSlot(slots, SLOT_IN_1, IN_1_X, SLOT_Y, net.minecraft.world.item.Items.REDSTONE));
            addOutputSlot(2, OUT_B_X, SLOT_Y);
        }
    }

    /**
     * An input slot that accepts only its one known item.
     *
     * <p>The rule is the same on both sides: the server keeps one here, the client predicts with
     * the same predicate, and the shift-click route consults {@code mayPlace} through
     * {@code moveItemStackTo}.</p>
     */
    private static final class ValidatedInputSlot extends Slot {

        private final net.minecraft.world.item.Item accepted;

        ValidatedInputSlot(Container container, int index, int x, int y,
                           net.minecraft.world.item.Item accepted) {
            super(container, index, x, y);
            this.accepted = accepted;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(this.accepted);
        }
    }
}
