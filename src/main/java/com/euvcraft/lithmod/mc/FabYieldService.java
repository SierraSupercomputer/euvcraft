package com.euvcraft.lithmod.mc;

import java.util.Objects;

import com.euvcraft.lithmod.core.FoupContents;
import com.euvcraft.lithmod.core.Tier;
import com.euvcraft.lithmod.core.YieldCalibration;
import com.euvcraft.lithmod.core.YieldMath;
import com.euvcraft.lithmod.core.YieldRollEngine;

import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * The Minecraft-facing side of the yield roll engine: the two things a machine needs.
 *
 * <p>{@link #applyLayerStep} puts a lot through one layer of a process and
 * {@link #evaluateLot} judges a finished one. Every roll comes from
 * {@link FabRandomService#rngFor}, which is keyed by the lot id and by the server-only
 * salt, so a roll is reproducible for the server and unknowable for a client, and
 * nothing here ever returns a cumulative roll.</p>
 *
 * <p>{@link #applyLayerStep} takes a {@link ServerLevel} and checks it through
 * {@link FabRandomService#requireServerSide}, so it cannot run on a client.
 * {@link #evaluateLot} has no level to check, but it reads the server config and judges
 * the hidden rolls, and only the server has both: a lot on a client carries the
 * placeholder rolls of the network view.</p>
 *
 * <p>The arithmetic lives in {@link YieldRollEngine}, which has no Minecraft imports and
 * is unit tested on its own.</p>
 */
public final class FabYieldService {

    private FabYieldService() {
    }

    /**
     * Puts every wafer of a lot through one more layer of a process, with no core count
     * penalty.
     *
     * <p>This is the call for any die that is not a compute die, and for a compute die
     * whose core count has not been chosen yet.</p>
     *
     * @param level        the server level the lot is processed on, not null
     * @param lot          the lot to process, not null, not empty
     * @param tier         the tier being run, not null
     * @param layerNumber  the one-based layer to apply; it must be the layer the lot is
     *                     on plus one and no higher than {@code tier.layers()}
     * @param defectFactor how healthy the line is, from
     *                     {@link com.euvcraft.lithmod.core.LineStats#defectFactor()}
     * @return a new lot with the layer applied
     * @throws NullPointerException     if an argument is null
     * @throws IllegalArgumentException if the level is not a server level, the lot is
     *                                  empty, or the layer number is not the next one
     *                                  or is past the tier's layers
     */
    public static FoupContents applyLayerStep(ServerLevel level, FoupContents lot, Tier tier, int layerNumber,
            double defectFactor) {
        return applyLayerStep(level, lot, tier, null, layerNumber, defectFactor);
    }

    /**
     * Puts every wafer of a lot through one more layer of a process at a chosen core
     * count.
     *
     * <p>The rolls are calibrated against the <em>effective</em> yield of that core
     * count, so the lot passes at the rate the core count buys when it reaches test. A
     * 24 core die on T6 is judged at 17%, not at the tier's 85%, and the layer rolls
     * already carry that.</p>
     *
     * @param level        the server level the lot is processed on, not null
     * @param lot          the lot to process, not null, not empty
     * @param tier         the tier being run, not null
     * @param cores        the core count of the die, or {@code null} for none
     * @param layerNumber  the one-based layer to apply; it must be the layer the lot is
     *                     on plus one and no higher than {@code tier.layers()}
     * @param defectFactor how healthy the line is
     * @return a new lot with the layer applied
     * @throws NullPointerException     if an argument but the core count is null
     * @throws IllegalArgumentException if the level is not a server level, the lot is
     *                                  empty, or the layer number is not the next one
     *                                  or is past the tier's layers
     */
    public static FoupContents applyLayerStep(ServerLevel level, FoupContents lot, Tier tier, @Nullable Integer cores,
            int layerNumber, double defectFactor) {
        FabRandomService.requireServerSide(level);
        Objects.requireNonNull(lot, "lot must not be null");
        Objects.requireNonNull(tier, "tier must not be null");

        return YieldRollEngine.applyLayerStep(lot, tier, cores, layerNumber, defectFactor,
            EUVCRAFTMod.settings(), (waferIndex, stepIndex) ->
                FabRandomService.rngFor(level, lot.lotId(), waferIndex, stepIndex));
    }

    /**
     * Judges a finished lot, one answer per wafer in slot order.
     *
     * <p>The effective yield comes from {@link YieldMath#resolveEffectiveYield}: the
     * settings decide the base and the core count penalty, and each wafer passes when
     * its hidden cumulative roll is above {@code 1 - effectiveYield}. The lot is not
     * changed.</p>
     *
     * @param lot  the lot to judge, not null; every wafer must be at the tier's last
     *             layer
     * @param tier the tier it was processed at, not null
     * @param cores the core count of the die, or {@code null} for none
     * @return one pass or fail per wafer, in slot order
     * @throws NullPointerException  if the lot or the tier is null
     * @throws IllegalStateException if any wafer is not at the tier's last layer
     */
    public static boolean[] evaluateLot(FoupContents lot, Tier tier, @Nullable Integer cores) {
        Objects.requireNonNull(lot, "lot must not be null");
        Objects.requireNonNull(tier, "tier must not be null");

        YieldRollEngine.requireCompleteLot(lot, tier);
        double effectiveYield = YieldMath.resolveEffectiveYield(tier, EUVCRAFTMod.settings(), cores);
        return YieldRollEngine.evaluate(lot, effectiveYield);
    }

    /**
     * Returns how many wafers of a finished lot pass.
     *
     * @param lot   the lot to judge, not null
     * @param tier  the tier it was processed at, not null
     * @param cores the core count of the die, or {@code null} for none
     * @return the number of passing wafers
     * @throws NullPointerException  if the lot or the tier is null
     * @throws IllegalStateException if any wafer is not at the tier's last layer
     */
    public static int countPassing(FoupContents lot, Tier tier, @Nullable Integer cores) {
        return YieldRollEngine.countPassing(evaluateLot(lot, tier, cores));
    }

    /**
     * Drops the cached calibration weights.
     *
     * <p>Registered as a settings change listener by {@link EUVCRAFTMod}, so a config
     * reload does not leave the previous weights sitting in the cache. The cached values
     * depend only on the roll count and the yield, so a changed yield misses the cache
     * by itself; this keeps the cache from holding entries for values no longer in use.</p>
     */
    static void onSettingsChanged() {
        YieldCalibration.clearCache();
    }
}