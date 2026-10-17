package com.euvcraft.lithmod.mc.recipe;

import com.euvcraft.lithmod.core.EnergySource;
import com.euvcraft.lithmod.core.MachineRecipeSpec;
import com.euvcraft.lithmod.mc.data.MachineRecipeSpecCodec;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
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
 * <p><b>That snapshot is written as JSON text</b>, encoded and decoded through
 * {@link JsonOps} rather than through the save tag's {@code NbtOps}. A recipe's components
 * are compact JSON text already, so a snapshot routed through NBT comes back as structured
 * NBT whose compact text no longer equals what was written; the restored spec would then not
 * equal the recipe the player paid for, and the whole paid progress would be dropped.</p>
 *
 * <p><b>What a completed run consumes.</b> Exactly the recipe's own
 * {@link MachineRecipe#ingredientCounts()}: four silicon for one boule costs four silicon,
 * whatever the stack size in the slot. Counts are re-read from the same recipe that is
 * being run, so a stack can never be shrunk by a number that belongs to another recipe.</p>
 */
public final class MachineRecipeRunner {

    /**
     * Key the running recipe's spec used to be written under, inside the save node.
     *
     * <p>The older compound form. Kept as a reader so a world saved before
     * {@link #SPEC_TEXT_KEY} existed still restores its progress, and never written, so a
     * new save cannot carry both forms of the same recipe.</p>
     */
    private static final String SPEC_KEY = "spec";

    /**
     * Key the running recipe's spec text is written under, inside the save node.
     *
     * <p>A private save format, not part of any datapack or network syntax: it carries the
     * spec as JSON text rather than as an NBT compound so a save/load cycle reproduces the
     * recipe exactly.</p>
     *
     * <p>Only this key is written. {@link #SPEC_KEY} is the older compound form and is read
     * but never written, so a world saved by an earlier build still loads while a new save
     * carries exactly one copy of the spec.</p>
     */
    private static final String SPEC_TEXT_KEY = "spec_text";

    /**
     * The spec text of {@link #SPEC_TEXT_KEY}, as a plain string.
     *
     * <p>The decode half never fails on purpose. A value that is present but is not a string
     * comes back as empty text rather than as a missing key, which is what tells "this save
     * wrote a spec and it is broken" apart from "this save was written before the text key
     * existed": a broken new-format save then fails closed instead of quietly falling back
     * to whatever legacy compound sits beside it.</p>
     */
    private static final Codec<String> SPEC_TEXT_CODEC =
        Codec.PASSTHROUGH.flatComapMap(
            (Dynamic<?> dynamic) -> specTextOf(dynamic),
            (String text) -> DataResult.success(
                new Dynamic<JsonElement>(JsonOps.INSTANCE, new JsonPrimitive(text))));

    /** The string held under a present value, or empty text for any other shape. */
    private static String specTextOf(Dynamic<?> dynamic) {
        JsonElement element = dynamic.convert(JsonOps.INSTANCE).getValue();
        return element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
            ? element.getAsString()
            : "";
    }

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
     * One server tick.
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

        // Progress restored from a save is adopted here, and only here. The first
        // candidate whose inputs match AND whose spec is the exact one the progress was
        // saved for takes the run over and this tick immediately advances it, at the
        // frozen cost loadState restored: another matching recipe that happens to come
        // first must never replace it or restart the count. If no candidate is the saved
        // recipe the progress is dropped; when the saved spec itself is gone nothing is
        // matched at all this tick (fail closed), while a surviving-but-changed recipe
        // lets fresh matching start its own run on this same tick.
        if (hasRestoredProgress()) {
            for (MachineRecipe candidate : recipes) {
                if (inputsMatch(candidate, inputs) && adoptRestored(candidate)) {
                    return advance(inputs, output, outputWriter, energy);
                }
            }
            // reset() clears restoredSpec, so whether it survived must be read first.
            boolean specSurvived = restoredSpec != null;
            reset();
            if (!specSurvived) {
                // Fail closed: the save carried no usable spec, so the paid progress has
                // no identity left. Drop it without handing the tick to whatever recipe
                // now happens to fit; a fresh run may only start on a later tick.
                return false;
            }
            // The spec survived and simply matches no candidate: the recipe changed or
            // vanished, so fresh matching below starts its own run on this same tick.
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
     * <p>The recipe is not written as an id but as its own spec, which is the exact recipe
     * that is running: same ingredients, same product, same length, same cost override. It
     * is written as JSON text under {@link #SPEC_TEXT_KEY}, through
     * {@link MachineRecipeSpecCodec#CODEC} on {@link JsonOps}, because the component payload
     * inside a spec is compact JSON text: encoded through the save tag's {@code NbtOps} it
     * would be rewritten as structured NBT and come back as text whose ordering and numeric
     * shape had moved, so the restored spec would no longer equal the recipe the progress was
     * paid for and {@link #adoptRestored} would drop the run. The frozen per-tick cost is
     * written separately because it can differ from what the definition says today.
     * {@link #adoptRestored} will only give this progress back to that spec.</p>
     *
     * <p>A restored-but-not-yet-adopted run is part of the machine's state too: if a save
     * lands between a load and the next tick, the spec the loaded progress belongs to is
     * written just as if the recipe were already re-attached, so the paid progress
     * survives a save/reload cycle that never got a tick.</p>
     */
    public void saveState(net.minecraft.world.level.storage.ValueOutput output) {
        output.putInt("progress", progress);
        output.putInt("ticks", ticks);
        output.putInt("energy_per_tick", runningEnergyPerTick);
        MachineRecipeSpec savedSpec = current != null ? current.spec()
            : hasRestoredProgress() ? restoredSpec : null;
        output.putBoolean("running", savedSpec != null);
        if (savedSpec != null) {
            output.store(SPEC_TEXT_KEY, SPEC_TEXT_CODEC, encodeSpecText(savedSpec));
        }
    }

    /**
     * Reads back what {@link #saveState} wrote.
     *
     * <p>A saved "running" flag whose progress has reached or passed the saved ticks is
     * discarded rather than trusted: it comes from a hand-edited or truncated tag, not
     * from this class, which never writes a finished run. Zero progress is also refused
     * and the machine restarts fresh, but that is not corruption: a run paused before its
     * first FE payment is a normal running state, this loader just chooses the existing
     * behavior of not restoring an unpaid run.</p>
     *
     * <p>The saved spec is kept as it is, including when it cannot be decoded: a run whose
     * identity did not survive must not be resumed on whatever recipe now fits. With no
     * spec at all, the run also fails closed rather than being re-attached to a guess.
     * {@link #SPEC_TEXT_KEY} is the whole truth about the recipe whenever it is present at
     * all, and a value that is present but will not decode restores no spec rather than
     * falling back to the legacy {@link #SPEC_KEY} compound, so a corrupted new-format save
     * can never be quietly repaired from a stale older one.</p>
     */
    public void loadState(net.minecraft.world.level.storage.ValueInput input) {
        int savedProgress = input.getIntOr("progress", 0);
        int savedTicks = input.getIntOr("ticks", 0);
        boolean savedRunning = input.getBooleanOr("running", false);

        if (!savedRunning || savedTicks <= 0 || savedProgress <= 0 || savedProgress >= savedTicks) {
            reset();
            return;
        }
        // Loading can run on a block entity that is already mid-run, not only a fresh
        // constructor: drop any live run first, so the saved numbers below are the whole
        // of the restored state rather than a patch over it.
        this.current = null;
        this.paused = false;
        this.progress = savedProgress;
        this.ticks = savedTicks;
        this.runningEnergyPerTick = Math.max(0, input.getIntOr("energy_per_tick", this.machineEnergyPerTick));
        Optional<String> savedText = input.read(SPEC_TEXT_KEY, SPEC_TEXT_CODEC);
        this.restoredSpec = savedText.isPresent()
            ? decodeSpecText(savedText.get())
            : input.read(SPEC_KEY, MachineRecipeSpecCodec.CODEC).orElse(null);
    }

    /**
     * The running recipe's spec as JSON text, or empty text when it will not encode.
     *
     * <p>{@link JsonOps} carries the spec exactly as the codec wrote it, so the snapshot and
     * the recipe it was taken from come back equal. Empty text is the fail-closed answer: a
     * save carrying no readable spec restores no progress instead of a guessed one.</p>
     */
    private static String encodeSpecText(MachineRecipeSpec spec) {
        return MachineRecipeSpecCodec.CODEC.encodeStart(JsonOps.INSTANCE, spec)
            .result()
            .map(JsonElement::toString)
            .orElse("");
    }

    /**
     * The spec behind the saved JSON text, or null when that text will not decode.
     *
     * <p>Never throws: malformed saved JSON, a spec the record itself rejects and a
     * component the codec cannot read all come back as null, which is what keeps the load
     * fail-closed rather than throwing out of a world load.</p>
     */
    private static MachineRecipeSpec decodeSpecText(String text) {
        try {
            return MachineRecipeSpecCodec.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString(text))
                .result()
                .orElse(null);
        } catch (RuntimeException malformed) {
            return null;
        }
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
