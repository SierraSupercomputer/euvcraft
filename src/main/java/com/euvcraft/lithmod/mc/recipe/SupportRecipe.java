package com.euvcraft.lithmod.mc.recipe;

import com.euvcraft.lithmod.core.MachineIngredient;
import com.euvcraft.lithmod.core.WaterTank;
import com.euvcraft.lithmod.mc.multiblock.support.SupportMachineKind;

import java.util.Objects;

/**
 * One support-machine job recipe as it appears in {@code data/lithmod/support_recipes/*.json},
 * kept as pure string data so validation can be unit tested with no game running.
 *
 * <p>This is the support machines' own recipe model, deliberately separate from
 * {@link MachineRecipeSpec}: a support job has a duration and a water cost of its own and
 * must never be retuned by editing the generic machine-recipe files, which are tuned around
 * the runner's machine-side FE multiplier. The Boule Purifier and the later E-beam Mask
 * Writer share this loader; nothing here knows about the E-beam beyond that its recipes
 * carry no water.</p>
 *
 * <p><b>Semantic validation is the constructor's job</b>, and it is strict:</p>
 * <ul>
 *   <li>{@code machine} must be one of the registered {@link SupportMachineKind} ids;</li>
 *   <li>a purifier recipe consumes exactly one {@code lithmod:silicon_boule} (ordinary) and
 *       produces exactly {@code lithmod:pure_silicon_boule}, carries a positive water cost
 *       within the tank's hard bound, and has no secondary ingredient;</li>
 *   <li>an e-beam recipe carries no water.</li>
 * </ul>
 *
 * <p>Anything else throws, so a malformed datapack entry fails closed at load time instead
 * of shipping a guessed cost into a running job. What the constructor cannot check — that
 * the item ids name registered items — the loader resolves against the live registries.</p>
 *
 * @param machine       the {@link SupportMachineKind#id()} this job runs in
 * @param input         the primary ingredient, in slot 0
 * @param secondary     the optional secondary ingredient, in slot 1; {@code null} when the
 *                      machine has none (the purifier, today)
 * @param outputItem    namespaced item id of the product
 * @param outputCount   how many the job produces
 * @param duration      ticks the job takes, {@code >= 1}, bounded
 * @param energyPerTick FE this job draws per tick, {@code >= 1}, bounded
 * @param waterMb       water this job consumes once at completion, {@code >= 0}, bounded
 */
public record SupportRecipe(
    String machine,
    MachineIngredient input,
    MachineIngredient secondary,
    String outputItem,
    int outputCount,
    int duration,
    int energyPerTick,
    int waterMb
) {

    /** Upper bound for a job's duration; no real job runs longer than this. */
    public static final int MAX_DURATION_TICKS = 1_000_000;

    /** Upper bound for one tick's job cost; matches the energy hard bound scale. */
    public static final int MAX_ENERGY_PER_TICK = 1_000_000;

    /** The purifier's only input: an ordinary boule. */
    public static final String ORDINARY_BOULE_ID = "lithmod:silicon_boule";

    /** The purifier's only product: a pure boule. */
    public static final String PURE_BOULE_ID = "lithmod:pure_silicon_boule";

    public SupportRecipe {
        Objects.requireNonNull(machine, "machine must not be null");
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(outputItem, "outputItem must not be null");

        if (machine.isBlank()) {
            throw new IllegalArgumentException("machine must not be blank");
        }
        if (SupportMachineKind.ofMachine(machine) == null) {
            throw new IllegalArgumentException(
                "machine '" + machine + "' is not a registered support machine");
        }
        if (outputItem.isBlank() || !outputItem.contains(":")) {
            throw new IllegalArgumentException(
                "output must be namespaced (namespace:path), got '" + outputItem + "'");
        }
        if (outputCount <= 0) {
            throw new IllegalArgumentException("output count must be positive, got " + outputCount);
        }
        if (duration <= 0 || duration > MAX_DURATION_TICKS) {
            throw new IllegalArgumentException("duration must be in 1.." + MAX_DURATION_TICKS + ", got " + duration);
        }
        if (energyPerTick <= 0 || energyPerTick > MAX_ENERGY_PER_TICK) {
            throw new IllegalArgumentException(
                "energy_per_tick must be in 1.." + MAX_ENERGY_PER_TICK + ", got " + energyPerTick);
        }
        if (waterMb < 0 || waterMb > WaterTank.MAX_CAPACITY) {
            throw new IllegalArgumentException(
                "water_mb must be in 0.." + WaterTank.MAX_CAPACITY + ", got " + waterMb);
        }

        if (SupportMachineKind.PURIFIER.id().equals(machine)) {
            if (!ORDINARY_BOULE_ID.equals(input.id())) {
                throw new IllegalArgumentException(
                    "boule_purifier input must be " + ORDINARY_BOULE_ID + ", got " + input.id());
            }
            if (!PURE_BOULE_ID.equals(outputItem)) {
                throw new IllegalArgumentException(
                    "boule_purifier output must be " + PURE_BOULE_ID + ", got " + outputItem);
            }
            if (waterMb <= 0) {
                throw new IllegalArgumentException(
                    "boule_purifier water_mb must be positive, got " + waterMb);
            }
            if (secondary != null) {
                throw new IllegalArgumentException("boule_purifier recipes take no secondary ingredient");
            }
        } else {
            if (waterMb != 0) {
                throw new IllegalArgumentException(
                    "ebeam_mask_writer recipes take no water, got water_mb=" + waterMb);
            }
        }
    }

    /** The recipe's output count; a small alias that reads better in log lines. */
    public int resultCount() {
        return this.outputCount;
    }

    @Override
    public String toString() {
        return "SupportRecipe[" + machine + ": " + input + (secondary == null ? "" : " + " + secondary)
            + " -> " + resultCount() + "x " + outputItem + " in " + duration + " ticks, "
            + energyPerTick + " FE/tick, " + waterMb + " mB]";
    }
}
