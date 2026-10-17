package com.euvcraft.lithmod.mc.recipe;

import com.euvcraft.lithmod.core.EnergySource;
import com.euvcraft.lithmod.core.MachineRecipeSpec;
import com.euvcraft.lithmod.mc.data.MachineRecipeSpecCodec;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Consumer;

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
 *   <li>The inputs must still satisfy the running recipe, item <em>and</em> count. A
 *       player can pull an ingredient back out or swap it mid-run, and a recipe that
 *       finished against inputs that are no longer there would be free output, so a run
 *       whose inputs went invalid is dropped whole: progress is lost and nothing is
 *       consumed and nothing is written.</li>
 *   <li>The output must have room. Checked before any energy is drawn, so a full output
 *       slot never eats the player's FE for nothing.</li>
 *   <li>Energy must be there in full. A partial draw counts as no energy and pauses.</li>
 *   <li>A running recipe is never re-matched, so a reload cannot swap a recipe out from
 *       under a half-built product.</li>
 * </ol>
 *
 * <p><b>Recipe identity across a datapack reload.</b> {@link #current} is the resolved
 * recipe object itself, and it is kept for the whole run: {@link #tick} only ever looks at
 * the candidate list while nothing is running. A reload replaces the loaded registry, but
 * a half-finished run keeps the recipe it started with rather than silently becoming a
 * different recipe with a different cost and a different product.</p>
 *
 * <p><b>Recipe identity across a save and load.</b> A run has to survive a world reload
 * too, so {@link #saveState} writes the running recipe's own
 * {@link MachineRecipeSpec} next to its numbers and {@link #adoptRestored} re-attaches
 * progress only to that exact spec. Re-matching by input alone would let a datapack edit
 * hand old progress to a different product or a different length for free; a spec that is
 * missing or has changed drops the run instead, and the machine starts again from tick one.
 * The frozen per-tick cost is kept with it, so a resumed run is charged exactly what an
 * uninterrupted one would have been charged.</p>
 *
 * <p><b>What a completed run consumes.</b> Exactly the recipe's own
 * {@link MachineRecipe#ingredientCounts()}: four silicon for one boule costs four silicon,
 * whatever the stack size in the slot. Counts are re-read from the same recipe that is
 * being run, so a stack can never be shrunk by a number that belongs to another recipe.</p>
 */
public final class MachineRecipeRunner {

    /** Key the running recipe's spec is written under, inside the save node. */
    private static final String SPEC_KEY = "spec";

    private final String machineId;

    private MachineRecipe current;
    private int machineEnergyPerTick;
    private int runningEnergyPerTick;
    private int progress;
    private int ticks;
    private boolean paused;
    /** The spec restored progress belongs to, until it is adopted or thrown away. */
    private MachineRecipeSpec restoredSpec;

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

    /**
     * Whether every input slot currently satisfies {@code recipe}, item and count.
     *
     * <p>This is the revalidation the tick performs on the recipe that is already running:
     * the same rules {@link #firstMatch} uses to choose one, applied again so a mid-run
     * removal or swap is caught before any energy is spent or any product is written.</p>
     */
    public static boolean inputsMatch(MachineRecipe recipe, List<ItemStack> inputs) {
        if (recipe.inputSlotCount() != inputs.size()) {
            return false;
        }
        for (int slot = 0; slot < inputs.size(); slot++) {
            if (!matchesSlot(recipe, slot, inputs.get(slot))) {
                return false;
            }
        }
        return true;
    }

    /** The first recipe in {@code recipes} whose ingredients the inputs satisfy. */
    public static MachineRecipe firstMatch(List<MachineRecipe> recipes, List<ItemStack> inputs) {
        for (MachineRecipe recipe : recipes) {
            if (inputsMatch(recipe, inputs)) {
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
                        Consumer<ItemStack> outputWriter,
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

    /**
     * Runs one tick of the recipe that is already selected.
     *
     * <p>The inputs are checked twice on the tick a recipe completes: once at the top, so
     * a run that has become invalid is dropped before it costs the player any FE, and once
     * again immediately before the product is written, so nothing is consumed for a product
     * that came out of inputs that had just been taken away.</p>
     */
    private boolean advance(List<ItemStack> inputs,
                            ItemStack output,
                            Consumer<ItemStack> outputWriter,
                            EnergySource energy) {
        if (!inputsMatch(current, inputs)) {
            reset();
            return false;
        }
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

        if (!inputsMatch(current, inputs)) {
            reset();
            return false;
        }

        ItemStack product = current.assemble();
        consume(current, inputs);
        outputWriter.accept(product);
        reset();
        return true;
    }

    /** Shrinks each input slot by exactly the count its own ingredient declares. */
    private static void consume(MachineRecipe recipe, List<ItemStack> inputs) {
        for (int slot = 0; slot < inputs.size(); slot++) {
            int count = recipe.ingredientCounts().get(slot);
            ItemStack stack = inputs.get(slot);
            if (stack.isEmpty() || count <= 0) {
                continue;
            }
            stack.shrink(count);
        }
    }

    /** Drops the running recipe and its progress, keeping the configured machine cost. */
    public void reset() {
        current = null;
        progress = 0;
        ticks = 0;
        runningEnergyPerTick = machineEnergyPerTick;
        paused = false;
        restoredSpec = null;
    }

    /**
     * Writes the numeric state of a half-finished recipe, so a world reload resumes it.
     *
     * <p>The recipe is not written as an id but as its own
     * {@link MachineRecipeSpecCodec#CODEC}-encoded spec, which is the exact recipe that is
     * running: same ingredients, same product, same length, same cost override. The frozen
     * per-tick cost is written separately because it can differ from what the definition
     * says today. {@link #adoptRestored} will only give this progress back to that spec.</p>
     */
    public void saveState(net.minecraft.world.level.storage.ValueOutput output) {
        output.putInt("progress", progress);
        output.putInt("ticks", ticks);
        output.putInt("energy_per_tick", runningEnergyPerTick);
        output.putBoolean("running", current != null);
        if (current != null) {
            output.store(SPEC_KEY, MachineRecipeSpecCodec.CODEC, current.spec());
        }
    }

    /**
     * Reads back what {@link #saveState} wrote.
     *
     * <p>A saved "running" flag with no progress, or progress at or past the saved ticks, is
     * discarded rather than trusted: it can only come from a hand-edited or truncated tag.
     * The saved spec is kept as it is, including when it cannot be decoded: a run whose
     * identity did not survive must not be resumed on whatever recipe now fits.</p>
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
        this.restoredSpec = input.read(SPEC_KEY, MachineRecipeSpecCodec.CODEC).orElse(null);
    }

    /** Whether progress was restored but no recipe has been matched to it yet. */
    public boolean hasRestoredProgress() {
        return this.current == null && this.ticks > 0 && this.progress > 0 && this.progress < this.ticks;
    }

    /**
     * Gives restored progress back to {@code candidate}, but only to the same recipe.
     *
     * <p>Returns false when the save carried no spec or a spec that is not
     * {@code candidate}'s, which is the caller's signal to {@link #reset()}: progress paid
     * for one recipe must never be handed to another. The frozen per-tick cost stays as
     * {@link #loadState} restored it, so a resumed run costs what it was already costing.</p>
     *
     * @return true when {@code candidate} is the recipe the saved progress belongs to
     */
    public boolean adoptRestored(MachineRecipe candidate) {
        if (restoredSpec == null || !restoredSpec.equals(candidate.spec())) {
            return false;
        }
        this.current = candidate;
        this.restoredSpec = null;
        return true;
    }

    @Override
    public String toString() {
        return "MachineRecipeRunner[" + machineId + " "
            + (current == null ? "idle" : progress + "/" + ticks + (paused ? " paused" : "")) + "]";
    }
}
