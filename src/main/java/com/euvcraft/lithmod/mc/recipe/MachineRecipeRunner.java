package com.euvcraft.lithmod.mc.recipe;

import com.euvcraft.lithmod.core.EnergySource;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Runs one machine's recipes: finds the recipe that fits the input slots, draws energy every
 * tick, pauses when there is none, consumes the ingredients and produces the result when the
 * recipe completes.
 *
 * <p>Owns progress and which recipe is running, and draws FE through an
 * {@link EnergySource} rather than touching a capability, so the rules below are all
 * testable without a level.</p>
 *
 * <p>The rules, in the order they are checked each tick:</p>
 * <ol>
 *   <li>The output must have room. Checked before any energy is drawn, so a full output slot
 *       never eats the player's FE for nothing.</li>
 *   <li>Energy must be there in full. A partial draw counts as no energy and pauses.</li>
 *   <li>A running recipe is never re-matched, so a reload cannot swap a recipe out from
 *       under a half-built product.</li>
 * </ol>
 */
public final class MachineRecipeRunner {

    private final String machineId;

    private MachineRecipe current;
    private int machineEnergyPerTick;
    private int runningEnergyPerTick;
    private int progress;
    private int ticks;
    private boolean paused;

    /**
     * @param machineId the machine whose recipes this runner may use
     * @param machineEnergyPerTick what
     *     {@link com.euvcraft.lithmod.core.MachineDefinition#energyPerTick} resolved to,
     *     used whenever the running recipe has no override of its own
     */
    public MachineRecipeRunner(String machineId, int machineEnergyPerTick) {
        this.machineId = machineId;
        this.machineEnergyPerTick = machineEnergyPerTick;
        this.runningEnergyPerTick = machineEnergyPerTick;
    }

    public String machineId() {
        return machineId;
    }

    /** The FE this runner currently draws per active tick. */
    public int energyPerTick() {
        return runningEnergyPerTick;
    }

    /**
     * Updates the machine-level fallback cost.
     *
     * <p>Ignored while a recipe is running, so a config change cannot retroactively alter the
     * cost of work already paid for.</p>
     */
    public void setMachineEnergyPerTick(int machineEnergyPerTick) {
        this.machineEnergyPerTick = machineEnergyPerTick;
        if (current == null) {
            this.runningEnergyPerTick = machineEnergyPerTick;
        }
    }

    /** The machine-level fallback cost. */
    public int machineEnergyPerTick() {
        return machineEnergyPerTick;
    }

    public MachineRecipe current() {
        return current;
    }

    /** Ticks completed so far for the running recipe. */
    public int progress() {
        return progress;
    }

    /** Ticks the running recipe needs in total. */
    public int ticks() {
        return ticks;
    }

    public boolean isRunning() {
        return current != null;
    }

    /** Running but stopped for want of energy or room. */
    public boolean isPaused() {
        return paused;
    }

    /** Progress as a fraction from 0 to 1, for the menu and the screen. */
    public float progressFraction() {
        return ticks <= 0 ? 0.0f : Math.min(1.0f, (float) progress / ticks);
    }

    /** Whether {@code output} can take {@code recipe}'s product right now. */
    public static boolean hasRoomFor(MachineRecipe recipe, ItemStack output) {
        if (output.isEmpty()) {
            return true;
        }
        if (!ItemStack.isSameItemSameComponents(output, recipe.assemble())) {
            return false;
        }
        return output.getCount() + recipe.resultCount() <= output.getMaxStackSize();
    }

    /** Whether input slot {@code slot} satisfies the recipe's ingredient at that slot. */
    public static boolean matchesSlot(MachineRecipe recipe, int slot, ItemStack stack) {
        if (stack.isEmpty() || stack.getCount() < recipe.ingredientCounts().get(slot)) {
            return false;
        }
        String stackItemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        var ingredient = recipe.spec().ingredients().get(slot);
        return ingredient.isTag()
            ? recipe.tagMembers(ingredient.id()).contains(stackItemId)
            : ingredient.id().equals(stackItemId);
    }

    /** The first recipe in {@code recipes} whose ingredients the inputs satisfy. */
    public static MachineRecipe firstMatch(List<MachineRecipe> recipes, List<ItemStack> inputs) {
        for (MachineRecipe recipe : recipes) {
            if (recipe.inputSlotCount() != inputs.size()) {
                continue;
            }
            boolean matches = true;
            for (int slot = 0; slot < inputs.size(); slot++) {
                if (!matchesSlot(recipe, slot, inputs.get(slot))) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return recipe;
            }
        }
        return null;
    }

    /**
     * Advances the runner by one tick.
     *
     * @param recipes candidate recipes for this machine
     * @param inputs the input slots, in slot order; consumed when a recipe completes
     * @param output the output slot contents, used to check there is room
     * @param outputWriter writes the product once a recipe completes
     * @param energy where to draw FE from
     * @return true when a recipe completed on this tick
     */
    public boolean tick(List<MachineRecipe> recipes,
                        List<ItemStack> inputs,
                        ItemStack output,
                        java.util.function.Consumer<ItemStack> outputWriter,
                        EnergySource energy) {
        if (current != null) {
            return advance(inputs, output, outputWriter, energy);
        }

        MachineRecipe chosen = firstMatch(recipes, inputs);
        if (chosen == null || !hasRoomFor(chosen, output)) {
            paused = false;
            return false;
        }

        current = chosen;
        ticks = chosen.ticks();
        progress = 0;
        runningEnergyPerTick = chosen.energyPerTick(machineEnergyPerTick);

        return advance(inputs, output, outputWriter, energy);
    }

    /** Runs one tick of the recipe that is already selected. */
    private boolean advance(List<ItemStack> inputs,
                            ItemStack output,
                            java.util.function.Consumer<ItemStack> outputWriter,
                            EnergySource energy) {
        if (!hasRoomFor(current, output)) {
            paused = true;
            return false;
        }
        if (energy.draw(runningEnergyPerTick) < runningEnergyPerTick) {
            paused = true;
            return false;
        }

        paused = false;
        progress++;
        if (progress < ticks) {
            return false;
        }

        ItemStack product = current.assemble();
        consume(inputs);
        outputWriter.accept(product);
        reset();
        return true;
    }

    private static void consume(List<ItemStack> inputs) {
        for (ItemStack stack : inputs) {
            if (!stack.isEmpty()) {
                stack.shrink(1);
            }
        }
    }

    /** Drops the running recipe and its progress, keeping the configured machine cost. */
    public void reset() {
        current = null;
        progress = 0;
        ticks = 0;
        runningEnergyPerTick = machineEnergyPerTick;
        paused = false;
    }

    /**
     * Writes the numeric state of a half-finished recipe, so a world reload resumes it.
     *
     * <p>Only numbers are written; the recipe itself is re-matched from the loaded data on
     * the next tick. That is what lets a datapack edit a recipe without stranding a machine
     * on a definition that no longer exists.</p>
     */
    public void saveState(net.minecraft.world.level.storage.ValueOutput output) {
        output.putInt("progress", progress);
        output.putInt("ticks", ticks);
        output.putInt("energy_per_tick", runningEnergyPerTick);
        output.putBoolean("running", current != null);
    }

    /**
     * Reads back what {@link #saveState} wrote.
     *
     * <p>A saved "running" flag with no progress, or progress at or past the saved ticks, is
     * discarded rather than trusted: it can only come from a hand-edited or truncated tag.</p>
     */
    public void loadState(net.minecraft.world.level.storage.ValueInput input) {
        int savedProgress = input.getIntOr("progress", 0);
        int savedTicks = input.getIntOr("ticks", 0);
        boolean savedRunning = input.getBooleanOr("running", false);

        if (!savedRunning || savedTicks <= 0 || savedProgress <= 0 || savedProgress >= savedTicks) {
            reset();
            return;
        }
        this.progress = savedProgress;
        this.ticks = savedTicks;
        this.runningEnergyPerTick = Math.max(0, input.getIntOr("energy_per_tick", this.machineEnergyPerTick));
    }

    /** Whether progress was restored but no recipe has been matched to it yet. */
    public boolean hasRestoredProgress() {
        return this.current == null && this.ticks > 0 && this.progress > 0 && this.progress < this.ticks;
    }

    /** Adopts {@code recipe} as the one whose progress was restored. */
    public void adopt(MachineRecipe recipe) {
        this.current = recipe;
        this.runningEnergyPerTick = recipe.energyPerTick(this.runningEnergyPerTick);
    }

    @Override
    public String toString() {
        return "MachineRecipeRunner[" + machineId + " "
            + (current == null ? "idle" : progress + "/" + ticks + (paused ? " paused" : "")) + "]";
    }
}
