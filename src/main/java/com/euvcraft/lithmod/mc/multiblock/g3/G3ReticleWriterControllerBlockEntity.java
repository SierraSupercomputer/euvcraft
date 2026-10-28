package com.euvcraft.lithmod.mc.multiblock.g3;

import com.euvcraft.lithmod.core.FoupContents;
import com.euvcraft.lithmod.core.FoupDisposal;
import com.euvcraft.lithmod.mc.data.SupportRecipeLoader;
import com.euvcraft.lithmod.mc.recipe.SupportRecipe;
import com.euvcraft.lithmod.mc.registry.ModDataComponents;
import com.euvcraft.lithmod.core.MaskData;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * G3 Reticle Writer controller block entity.
 * Selectable tier7 compute/memory/NAND recipes from SupportRecipeLoader/MaskData APIs.
 * Blank mask + redstone + FE, freeze selection and ingredient costs/output/ticks at admission.
 * Generation 3 reflective reticle output.
 * Core selection for compute only consistent T6 system.
 * Cleanroom SOFT: allow outside production with configurable fewer mask uses.
 * Pause with output blocked/power loss, save/reload retains exact job, break returns admitted original carrier/materials.
 */
public class G3ReticleWriterControllerBlockEntity extends G3ControllerBlockEntity {

    // Slot indices: 0 = blank mask, 1 = redstone, 2 = reticle output
    public static final int SLOT_BLANK_MASK = 0;
    public static final int SLOT_REDSTONE = 1;
    public static final int SLOT_RETICLE_OUTPUT = 2;

    public G3ReticleWriterControllerBlockEntity(BlockPos pos, BlockState state) {
        super(G3Machines.RETICLE_WRITER_CONTROLLER_BE.get(), pos, state,
                G3MachineKind.RETICLE_WRITER, 2, 1);
    }

    @Override
    protected String machineId() {
        return "g3_reticle_writer";
    }

    /** Current core count preference for compute designs (1..256, from settings). */
    private int corePreference = 1;

    /**
     * Server-side handler for core count cycle button.
     * Advances through configured coreCountOptions.
     * @return true if the selection moved
     */
    public boolean cycleCores() {
        if (!this.formed || this.jobRunning) return false;
        var options = EUVCRAFTMod.settings().coreCountOptions();
        if (options.isEmpty()) return false;
        int index = options.indexOf(this.corePreference);
        if (index < 0) index = 0;
        this.corePreference = options.get((index + 1) % options.size());
        setChanged();
        return true;
    }

    /** Current core count preference. */
    public int corePreference() {
        return this.corePreference;
    }

    /** Whether core selection is currently allowed (formed, idle). */
    public boolean coresSelectable() {
        return this.formed && !this.jobRunning;
    }

    @Override
    public boolean startWriterJob(String recipeId, int coreCount) {
        // Override to use corePreference if coreCount not specified
        if (coreCount <= 0) coreCount = this.corePreference;
        return super.startWriterJob(recipeId, coreCount);
    }
}