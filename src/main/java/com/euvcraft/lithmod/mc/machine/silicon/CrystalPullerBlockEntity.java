package com.euvcraft.lithmod.mc.machine.silicon;

import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Crystal Puller: silicon in, one boule out.
 *
 * <p>A plain {@link com.euvcraft.lithmod.mc.recipe.MachineRecipeRunner} machine. The base
 * class does everything — matching a recipe from
 * {@code data/lithmod/machine_recipes}, drawing the energy the definition asks for,
 * consuming the ingredients and writing the product — so this class only names the machine
 * and counts slots.</p>
 *
 * <p>Hand-fed only: the base class implements {@code Container} for menus and for the drop
 * path but answers {@code canPlaceItem} and {@code canTakeItem} with {@code false}, so no
 * hopper can move items in or out.</p>
 */
public class CrystalPullerBlockEntity extends FabMachineBlockEntity {

    /** The machine id, matching {@code data/lithmod/machines/crystal_puller.json}. */
    public static final String MACHINE_ID = "crystal_puller";

    /** Input slot index: silicon. */
    public static final int INPUT_SLOT = 0;
    /** Output slot index: the boule. */
    public static final int OUTPUT_SLOT = 1;

    /**
     * Buffer capacity used until {@code crystal_puller.json} is loaded.
     *
     * <p>{@link FabMachineBlockEntity} builds its energy buffer once, in the constructor,
     * and never resizes it; the value in the definition only reaches the synced display
     * value. Keeping this equal to the {@code energy_capacity} in the data file is what
     * makes the two agree without a framework change.</p>
     */
    public static final int FALLBACK_CAPACITY = 20_000;
    /** Fallback per-operation insert limit, equal to {@code max_receive} in the data file. */
    public static final int FALLBACK_MAX_RECEIVE = 2_000;

    /**
     * @param pos and {@code state} only; 26.3's {@code BlockEntityType} supplier hands over
     *     just those two.
     */
    public CrystalPullerBlockEntity(BlockPos pos, BlockState state) {
        super(SiliconMachines.CRYSTAL_PULLER_BE.get(), pos, state, 1, 1,
            FALLBACK_CAPACITY, FALLBACK_MAX_RECEIVE);
    }

    @Override
    protected String machineId() {
        return MACHINE_ID;
    }

    @Override
    protected AbstractContainerMenu createMachineMenu(int containerId, Inventory playerInventory,
                                                       FabMachineBlockEntity machine) {
        return new CrystalPullerMenu(containerId, playerInventory, machine);
    }
}