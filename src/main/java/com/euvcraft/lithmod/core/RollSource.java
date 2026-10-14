package com.euvcraft.lithmod.core;

/**
 * Where a yield step draws its randomness from, given the wafer and the step.
 *
 * <p>This is the one seam between the pure engine and Minecraft: the engine asks for a
 * generator per {@code (waferIndex, stepIndex)} pair and never learns where it came
 * from. The Minecraft side answers with
 * {@link com.euvcraft.lithmod.mc.FabRandomService#rngFor}, which binds the pair to the
 * lot id and the server-only salt; a unit test answers with a seeded
 * {@link ContextualRng} or a fixed sequence.</p>
 */
@FunctionalInterface
public interface RollSource {

    /**
     * Returns a generator positioned at the start of one step's stream.
     *
     * @param waferIndex position of the wafer inside the lot
     * @param stepIndex  index of the process step
     * @return the generator to draw this step's {@code u} from
     */
    Rng rngFor(int waferIndex, int stepIndex);
}