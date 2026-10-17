package com.euvcraft.lithmod.mc.machine.test;

import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.machine.FabMachineMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The test-only machine, used by the M1-02 game tests and by nothing else.
 *
 * <p>One FOUP input slot and one output slot. Every number here is a literal only because
 * this block exists to exercise the framework; the real machine numbers come from
 * {@code data/lithmod/machines/test_machine.json} once it is loaded.</p>
 */
public class TestMachineBlockEntity extends FabMachineBlockEntity {

    /** The machine id, matching {@code data/lithmod/machines/test_machine.json}. */
    public static final String MACHINE_ID = "test_machine";
    /** Input slot index: a FOUP. */
    public static final int INPUT_SLOT = 0;
    /** Output slot index. */
    public static final int OUTPUT_SLOT = 1;

    /**
     * Capacity used until {@code test_machine.json} is loaded, so the block entity can be
     * constructed during registry population, before any data exists.
     */
    public static final int FALLBACK_CAPACITY = 10_000;
    /** Fallback per-operation insert limit. */
    public static final int FALLBACK_MAX_RECEIVE = 1_000;

    /**
     * @param pos and {@code state} only; 26.3's {@code BlockEntityType} supplier hands over
     *     just those two, so the type comes from the registered holder. Reading the holder is
     *     safe because a block entity is only constructed at runtime, never during registry
     *     population.
     */
    public TestMachineBlockEntity(BlockPos pos, BlockState state) {
        // 1 input (the FOUP), 1 output, 1 storage. The storage slot is what lets a test (or a
        // player) park a third distinct stack in the machine without the recipe matcher
        // seeing it: the runner only ever reads inputSlots().
        super(ModMachines.TEST_MACHINE_BE.get(), pos, state, 1, 1, 1,
            FALLBACK_CAPACITY, FALLBACK_MAX_RECEIVE);
    }

    /** Storage slot index: holds items but is never part of a recipe. */
    public static final int STORAGE_SLOT = 2;

    private SimpleContainer extraInventory = new SimpleContainer(0);

    /** Test fixture for the real subclass extension point, kept out of the base machine. */
    public void setExtraInventoryForTest(ItemStack... stacks) {
        extraInventory = new SimpleContainer(stacks);
    }

    @Override
    protected void dropExtraContents(Level level, BlockPos pos) {
        super.dropExtraContents(level, pos);
        dropContainer(level, pos, extraInventory);
    }

    @Override
    protected String machineId() {
        return MACHINE_ID;
    }

    @Override
    protected AbstractContainerMenu createMachineMenu(int containerId,
                                                       Inventory playerInventory,
                                                       FabMachineBlockEntity machine) {
        return new TestMachineMenu(containerId, playerInventory, machine);
    }
}
