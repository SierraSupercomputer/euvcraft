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
 * <p>Every number comes from {@code data/lithmod/machines/crystal_puller.json}: the base
 * class sizes the energy buffer from the loaded definition, so there is no Java copy of the
 * capacity or the insert limit to drift out of step with the data, and a machine with no
 * definition loaded accepts no energy at all and therefore cannot run.</p>
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
     * @param pos and {@code state} only; 26.3's {@code BlockEntityType} supplier hands over
     *     just those two.
     */
    public CrystalPullerBlockEntity(BlockPos pos, BlockState state) {
        super(SiliconMachines.CRYSTAL_PULLER_BE.get(), pos, state, 1, 1);
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
