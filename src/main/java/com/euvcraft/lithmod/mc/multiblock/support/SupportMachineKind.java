package com.euvcraft.lithmod.mc.multiblock.support;

import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The two support machines the shared native foundation serves.
 *
 * <p>Both controllers are instances of {@link SupportMachineControllerBlock} and share one block
 * entity type ({@code SupportMachines.SUPPORT_CONTROLLER_BE}), so the kind of a controller is
 * <em>derived from its actual block state</em> — which block instance sits at the position — rather
 * than from the block entity type. Same rule for the two menu types: once registered, each block
 * carries exactly its own kind, and nothing else may decide it.</p>
 *
 * <p>These are the T3 support multiblocks only: {@code boule_purifier} (2 inputs, 2 outputs) and
 * {@code ebeam_mask_writer} (2 inputs, 1 output). Job logic, recipes, bucket exchange and mask
 * selection are later work and deliberately absent here.</p>
 */
public enum SupportMachineKind {

    /** The boule purifier: ordinary boule + water bucket in, empty bucket and pure boule out. */
    PURIFIER("boule_purifier", 2, 2),

    /** The e-beam mask writer: blank plate + redstone in, cut mask out. */
    EBEAM("ebeam_mask_writer", 2, 1);

    private final String id;
    private final int inputSlots;
    private final int outputSlots;

    SupportMachineKind(String id, int inputSlots, int outputSlots) {
        this.id = id;
        this.inputSlots = inputSlots;
        this.outputSlots = outputSlots;
    }

    /** The machine id, which is also the machine definition filename under {@code machines/}. */
    public String id() {
        return this.id;
    }

    /** How many input slots stand at the front of the inventory. */
    public int inputSlots() {
        return this.inputSlots;
    }

    /** How many output slots follow the inputs. */
    public int outputSlots() {
        return this.outputSlots;
    }

    /** Inputs plus outputs; there are no storage slots on either machine. */
    public int slotCount() {
        return this.inputSlots + this.outputSlots;
    }

    /**
     * The kind that owns {@code state}'s block, or null when the state is not a support controller.
     *
     * <p>Called from block entity construction ({@code machineId()}), so it must be static-side
     * only and must never fabricate a kind for a block that is not one of the two registered
     * controllers.</p>
     */
    /**
     * The kind whose {@link #id()} matches, or null for an unknown id.
     *
     * <p>Case-sensitive, because only our own data pack writes these ids. Pure lookup:
     * this method touches no registry, so it is safe in unit tests and in the recipe
     * record's constructor.</p>
     *
     * @param id the machine id to look up, may be null
     * @return the matching kind, or {@code null}
     */
    public static @Nullable SupportMachineKind ofMachine(String id) {
        if (id == null) {
            return null;
        }
        if (PURIFIER.id.equals(id)) {
            return PURIFIER;
        }
        if (EBEAM.id.equals(id)) {
            return EBEAM;
        }
        return null;
    }

    public static @Nullable SupportMachineKind of(BlockState state) {
        if (state.getBlock() == SupportMachines.BOULE_PURIFIER.get()) {
            return PURIFIER;
        }
        if (state.getBlock() == SupportMachines.E_BEAM_MASK_WRITER.get()) {
            return EBEAM;
        }
        return null;
    }
}
