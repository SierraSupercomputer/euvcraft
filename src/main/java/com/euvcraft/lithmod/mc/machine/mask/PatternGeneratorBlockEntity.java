package com.euvcraft.lithmod.mc.machine.mask;

import com.euvcraft.lithmod.core.MaskData;
import com.euvcraft.lithmod.core.MaskRules;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.data.DieRulesDataLoader;
import com.euvcraft.lithmod.mc.data.SupportLineDataLoader;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.recipe.MachineRecipe;
import com.euvcraft.lithmod.mc.recipe.MachineRecipeRunner;
import com.euvcraft.lithmod.mc.registry.ModDataComponents;
import com.euvcraft.lithmod.mc.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * The Pattern Generator: a blank mask plate plus a design, one patterned photomask out.
 *
 * <p>A plain {@link com.euvcraft.lithmod.mc.recipe.MachineRecipeRunner} machine. The base
 * class owns the energy buffer, the slots, the matching, the FE and the output, and this class
 * adds exactly the two things the base does not know about: which recipes this machine is
 * willing to run at all ({@link #filterRecipes}), and what the finished photomask looks like
 * ({@link #prepareOutput}). There is no second runner and no second controller.</p>
 *
 * <p><b>Generation 1.</b> The only generation this machine has installed is 1, so the mask
 * shop gate is checked as {@code canPattern(die, tier, 1, ...)}. A recipe is accepted only
 * when its resolved product is exactly one {@link ModItems#MASK} carrying a
 * {@link ModDataComponents#MASK_DATA} design, and when that design names a die and node tier
 * the loaded die rules and support-line matrix actually allow. Both loaders are read per
 * tick through {@link DieRulesDataLoader#getRules()} and
 * {@link SupportLineDataLoader#getMatrix()}; before they have run they are {@code null},
 * and {@link MaskRules#canPattern} refuses a null registry rather than throwing, so a
 * machine that has not seen the data pack yet simply refuses everything.</p>
 *
 * <p><b>Why refusal costs the player nothing.</b> Refusal is a filter on the candidate list,
 * not a check inside the run, so a refused recipe is never selected, never adopts restored
 * progress, never draws FE and never consumes an ingredient or writes an output. A die type
 * that does not exist at a tier — memory at tier 1, compute at tier 3 — is refused on the
 * same footing as a product that is not a mask at all.</p>
 *
 * <p><b>Exposure count.</b> The recipe template says how many exposures the mask it builds
 * carries, and {@link #prepareOutput} replaces that with
 * {@link EUVCRAFTMod#settings()}{@code .maskUses()}, read when the recipe completes rather
 * than when it started. A player who edits the config mid-run gets the new number on the
 * mask that finishes after the edit; only the ingredient list and the length are frozen for
 * the length of a run, because those belong to the recipe itself.</p>
 *
 * <p><b>Status for the menu.</b> One extra synced value, owned here and never added to the
 * base class's four: the reason the recipe the player's current inputs would match is being
 * refused. It is recomputed every tick from the unfiltered recipe list, so it appears as
 * soon as a bad design is loaded and goes away as soon as the inputs are corrected. The
 * screen turns it into one short translatable line; no recipe id, die type or file name ever
 * reaches the player.</p>
 *
 * <p>Hand-fed: the base class is a {@link net.minecraft.world.Container} for menus and for
 * the drop path but answers {@code canPlaceItem} and {@code canTakeItem} with
 * {@code false}, so no hopper can reach these slots, and only
 * {@code Capabilities.Energy.BLOCK} is registered for this block entity type.</p>
 */
public class PatternGeneratorBlockEntity extends FabMachineBlockEntity {

    /** The machine id, matching {@code data/lithmod/machines/pattern_generator.json}. */
    public static final String MACHINE_ID = "pattern_generator";

    /** Input slot index 0: the blank plate the mask is cut from. */
    public static final int BLANK_SLOT = 0;
    /** Input slot index 1: the design ingredient, which M1-11 recipes will fill. */
    public static final int DESIGN_SLOT = 1;
    /** How many input slots, and therefore where the output slot starts. */
    public static final int INPUT_SLOTS = 2;
    /** Output slot index 2. */
    public static final int OUTPUT_SLOT = 2;

    /**
     * The only mask shop generation this machine has.
     *
     * <p>Hard-coded on purpose for generation 1: the machine is the generation 1 tool, so the
     * gate is asked as generation 1 rather than read from a block the player could swap to
     * make a recipe legal. Raising the generation is a later milestone's change to this
     * constant and to nothing else.</p>
     */
    public static final int MASK_SHOP_GENERATION = 1;

    /** Nothing refused; the status the menu shows nothing for. */
    public static final int STATUS_NONE = 0;
    /** The matching recipe does not build a photomask at all. */
    public static final int STATUS_NOT_A_MASK = 1;
    /** The recipe builds a mask that carries no design. */
    public static final int STATUS_MISSING_DESIGN = 2;
    /** The design names a die and tier the loaded rules do not allow. */
    public static final int STATUS_DESIGN_REFUSED = 3;

    /** How many values this machine adds to the base four. */
    public static final int STATUS_COUNT = 1;

    private final ContainerData statusData = new SimpleContainerData(STATUS_COUNT);

    /**
     * @param pos and {@code state} only; the {@code BlockEntityType} supplier hands over
     *     just those two.
     */
    public PatternGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(MaskMachines.PATTERN_GENERATOR_BE.get(), pos, state, INPUT_SLOTS, 1);
    }

    @Override
    protected String machineId() {
        return MACHINE_ID;
    }

    @Override
    protected AbstractContainerMenu createMachineMenu(int containerId, Inventory playerInventory,
                                                       FabMachineBlockEntity machine) {
        return new PatternGeneratorMenu(containerId, playerInventory, machine);
    }

    /**
     * The one value this machine syncs for its own menu, after the base class's four.
     *
     * <p>Kept out of {@link FabMachineBlockEntity#DATA_COUNT} on purpose: the base four are
     * every machine's contract, and a machine that needed a fifth value would force it on
     * every other machine's menu and screen. The menu registers this after the base slots on
     * both sides, so it is always the fifth value on the wire.</p>
     */
    public final ContainerData statusData() {
        return this.statusData;
    }

    /** The reason the matching recipe is refused, or {@link #STATUS_NONE}. */
    public final int status() {
        return this.statusData.get(0);
    }

    /**
     * Keeps only the recipes whose product this player's mask shop may actually cut.
     *
     * <p>Order is preserved, so the runner still picks the first matching recipe and not a
     * later one that happens to be cheaper. When nothing is refused the very list that came
     * in is handed back: the copy is only made once a recipe has actually been dropped, so
     * a machine whose recipes are all legal pays nothing for the rule.</p>
     */
    @Override
    protected List<MachineRecipe> filterRecipes(List<MachineRecipe> candidates) {
        if (candidates.isEmpty()) {
            return candidates;
        }
        List<MachineRecipe> allowed = null;
        for (int index = 0; index < candidates.size(); index++) {
            MachineRecipe recipe = candidates.get(index);
            if (judge(recipe).accepted()) {
                // Before the first refusal there is nothing to copy, because every recipe so
                // far is being kept; after it, the running list is the one to append to.
                if (allowed != null) {
                    allowed.add(recipe);
                }
                continue;
            }
            if (allowed == null) {
                allowed = new ArrayList<>(candidates.subList(0, index));
            }
        }
        return allowed == null ? candidates : allowed;
    }

    /**
     * Stamps the configured exposure count onto the finished photomask.
     *
     * <p>The die type and node tier come from the recipe's own design — the generator does
     * not invent a design, it only decides how many exposures the finished mask has left.
     * A product that is not a mask, or a mask with no design, is returned untouched rather
     * than patched: neither can reach here, because both are refused before they run, and a
     * defensive pass-through is the right answer if one ever did.</p>
     *
     * <p>Read at completion, not at start: the number is whatever the config says when the
     * recipe finishes. It is clamped to zero because {@link MaskData} rejects a negative use
     * count and a machine tick must not throw on a bad config value — a clamped zero hands
     * out an already spent mask, which is inert rather than wrong.</p>
     */
    @Override
    protected ItemStack prepareOutput(ItemStack product) {
        if (product.getItem() != ModItems.MASK.get()) {
            return product;
        }
        MaskData design = product.get(ModDataComponents.MASK_DATA.get());
        if (design == null) {
            return product;
        }
        product.set(ModDataComponents.MASK_DATA.get(), new MaskData(design.dieType(),
            design.tierIndex(), Math.max(0, EUVCRAFTMod.settings().maskUses())));
        return product;
    }

    /**
     * The base tick, then this machine's own status value.
     *
     * <p>After, so the status describes the recipes that were on offer this tick rather than
     * the ones that were on offer last tick.</p>
     */
    @Override
    public void serverTick() {
        super.serverTick();
        updateStatus();
    }

    /**
     * Recomputes {@link #status()} from the unfiltered recipe list.
     *
     * <p>A refused recipe only shows a reason when it is what the player's inputs would
     * otherwise have run. If any matching recipe is accepted the machine is going to run that
     * one, so the status is cleared even when an earlier matching recipe was refused; and if
     * nothing matches at all there is nothing to explain, so an empty slot is silent. That is
     * what makes the reason appear on a bad design and disappear as soon as the design is
     * corrected, with no separate reset path.</p>
     */
    private void updateStatus() {
        int next = STATUS_NONE;
        List<ItemStack> inputs = inputSlots();
        for (MachineRecipe recipe : recipes()) {
            if (!MachineRecipeRunner.inputsMatch(recipe, inputs)) {
                continue;
            }
            Verdict verdict = judge(recipe);
            if (verdict.accepted()) {
                // Something the player put in will actually run, so nothing is being refused.
                next = STATUS_NONE;
                break;
            }
            if (next == STATUS_NONE) {
                // The first refusal is the one reported; a later one is never the reason the
                // machine is idle while the earlier recipe was the match.
                next = verdict.status();
            }
        }
        if (this.statusData.get(0) != next) {
            this.statusData.set(0, next);
            setChanged();
        }
    }

    /**
     * Whether {@code recipe}'s product is a photomask this mask shop may cut, and why not when
     * it is not.
     *
     * <p>The product is assembled rather than read off the spec, because the mask item and
     * its component are resolved objects by the time a recipe is a {@link MachineRecipe},
     * and a recipe naming a different item is not a mask however it is spelled.</p>
     */
    private Verdict judge(MachineRecipe recipe) {
        ItemStack product = recipe.assemble();
        if (product.getCount() != 1 || product.getItem() != ModItems.MASK.get()) {
            return Verdict.NOT_A_MASK;
        }
        MaskData design = product.get(ModDataComponents.MASK_DATA.get());
        if (design == null) {
            return Verdict.MISSING_DESIGN;
        }
        // A spent template is not refused: this generator decides the exposure count itself
        // in prepareOutput, so what the template said about uses is never the last word.
        return MaskRules.canPattern(design.dieType(), design.tierIndex(), MASK_SHOP_GENERATION,
                DieRulesDataLoader.getRules(), SupportLineDataLoader.getMatrix())
            ? Verdict.ACCEPTED
            : Verdict.DESIGN_REFUSED;
    }

    /**
     * The answer for one recipe: run it, or refuse it with the status the menu shows.
     *
     * <p>A private enum rather than a bare boolean so that the refusal reason travels with
     * the decision: the filter and the status line must never disagree about why a recipe
     * was refused, and this is the one place that decides.</p>
     */
    private enum Verdict {

        ACCEPTED(STATUS_NONE),
        NOT_A_MASK(STATUS_NOT_A_MASK),
        MISSING_DESIGN(STATUS_MISSING_DESIGN),
        DESIGN_REFUSED(STATUS_DESIGN_REFUSED);

        private final int status;

        Verdict(int status) {
            this.status = status;
        }

        boolean accepted() {
            return this == ACCEPTED;
        }

        int status() {
            return this.status;
        }
    }
}
