package com.euvcraft.lithmod.mc.machine.packaging;

import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.machine.FabMachineMenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Menu for the Test and Package machine: a FOUP slot, a package slot, four chip outputs and
 * two scrap outputs.
 *
 * <p>Identical on both sides. The eight machine slots are added in the same order by
 * {@link #addMachineSlots()} on the server instance and on the client instance the
 * {@code MenuType} factory builds, so the slot indices in a click packet mean the same thing
 * on each, and the base class's shift-click routing splits the slot list at the right place:
 * a shift-click out of the machine moves to the player inventory, a shift-click into it
 * consults each slot's {@code mayPlace} — so the FOUP slot still refuses non-FOUPs and the
 * package slot still refuses anything but {@code chip_package} — and the six output slots
 * take nothing.</p>
 *
 * <p>The three queue values are this menu's own and are registered <em>after</em> the base
 * class's four, so they are always the fifth, sixth and seventh values the server syncs and
 * never disturb the energy and progress indices every machine shares. They are registered on
 * the client too, from a local {@link SimpleContainerData}, which is what the sync fills in;
 * without it the client would read values that were never sent.</p>
 */
public class TestPackageMenu extends FabMachineMenu {

    /**
     * Where the coarse state sits on the wire: the fifth, straight after the base class's four.
     *
     * <p>It is that index because the base constructor calls {@code addDataSlots} before this
     * constructor's own body does, on the server instance and on the client instance alike,
     * and the sync counts the values in registration order.</p>
     */
    public static final int STATE_WIRE_INDEX =
        FabMachineBlockEntity.DATA_COUNT + TestPackageBlockEntity.QUEUE_DATA_STATE;
    /** Where the wafers-done count sits on the wire: the sixth. */
    public static final int DONE_WIRE_INDEX =
        FabMachineBlockEntity.DATA_COUNT + TestPackageBlockEntity.QUEUE_DATA_DONE;
    /** Where the wafer total sits on the wire: the seventh. */
    public static final int TOTAL_WIRE_INDEX =
        FabMachineBlockEntity.DATA_COUNT + TestPackageBlockEntity.QUEUE_DATA_TOTAL;

    /** Horizontal centre of the input column, left of the output banks. */
    private static final int INPUT_X = 30;
    /** Vertical offset of the first machine slot row. */
    private static final int FIRST_ROW_Y = 35;
    /** Vertical offset of the second machine slot row. */
    private static final int SECOND_ROW_Y = 53;
    /** Left edge of the output banks. */
    private static final int OUTPUT_X = 70;
    /** Horizontal step between two output slots. */
    private static final int OUTPUT_STEP = 18;

    private final ContainerData queue;

    public TestPackageMenu(int containerId, Inventory playerInventory,
                           FabMachineBlockEntity machine) {
        super(PackagingMachines.TEST_PACKAGE_MENU.get(), containerId, playerInventory, machine,
            TestPackageBlockEntity.INPUT_SLOTS, TestPackageBlockEntity.SLOT_COUNT);
        this.queue = machine instanceof TestPackageBlockEntity packaging
            ? packaging.queueData()
            : new SimpleContainerData(TestPackageBlockEntity.QUEUE_DATA_COUNT);
        addDataSlots(this.queue);
    }

    /**
     * Client-side constructor, reached through the {@code MenuType} factory that
     * {@code IMenuTypeExtension.create} installed.
     *
     * <p>No block entity on this side, so the base class builds a matching eight-slot
     * container and this menu registers its own three values from a local holder that the
     * sync fills in.</p>
     */
    public TestPackageMenu(int containerId, Inventory playerInventory,
                           RegistryFriendlyByteBuf buffer) {
        this(containerId, playerInventory, (FabMachineBlockEntity) null);
    }

    @Override
    protected void addMachineSlots() {
        // The input column sits hard left of the two output banks: the FOUP above the
        // package slot, so the two rows span y = 35..69 and stay clear of the base menu's
        // player inventory at y = 84.
        addFoupSlot(TestPackageBlockEntity.FOUP_SLOT, INPUT_X, FIRST_ROW_Y);
        addCustomSlot(new Slot(machineSlots(), TestPackageBlockEntity.PACKAGE_SLOT,
                INPUT_X, SECOND_ROW_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return TestPackageBlockEntity.isPackage(stack);
            }
        });

        // The four chip outputs, then the two scrap outputs, so the banks are contiguous:
        // slots 2..5 are chips and 6..7 are scrap on both sides.
        for (int slot = 0; slot < TestPackageBlockEntity.CHIP_SLOT_COUNT; slot++) {
            addOutputSlot(TestPackageBlockEntity.CHIP_SLOT_FIRST + slot,
                OUTPUT_X + slot * OUTPUT_STEP, FIRST_ROW_Y);
        }
        for (int slot = 0; slot < TestPackageBlockEntity.SCRAP_SLOT_COUNT; slot++) {
            addOutputSlot(TestPackageBlockEntity.SCRAP_SLOT_FIRST + slot,
                OUTPUT_X + slot * OUTPUT_STEP, SECOND_ROW_Y);
        }
    }

    /**
     * The coarse machine state: idle, running, paused or one of the five admission refusals.
     *
     * <p>The raw number, not a translated line: the menu is server-side code too, and the
     * screen is the only place that turns a state into words for a player.</p>
     */
    public final int state() {
        return this.queue.get(TestPackageBlockEntity.QUEUE_DATA_STATE);
    }

    /** Wafers fully completed of the accepted lot. */
    public final int wafersDone() {
        return this.queue.get(TestPackageBlockEntity.QUEUE_DATA_DONE);
    }

    /** Wafers the accepted lot held, or zero before any lot was accepted. */
    public final int wafersTotal() {
        return this.queue.get(TestPackageBlockEntity.QUEUE_DATA_TOTAL);
    }
}
