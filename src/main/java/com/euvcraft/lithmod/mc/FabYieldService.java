package com.euvcraft.lithmod.mc;

import java.util.Objects;

import com.euvcraft.lithmod.core.FoupContents;
import com.euvcraft.lithmod.core.Tier;
import com.euvcraft.lithmod.core.YieldCalibration;
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
 * <p>Both entry points take a {@link ServerLevel} and check it through
 * {@link FabRandomService#requireServerSide}, so they cannot run on a client.
 * They also refuse lots that are client views (carrying the placeholder rolls from the
 * network codec), because a client view has no real rolls and would pass every test.</p>
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
     * @param lot          the lot to process, not null, not empty, not a client view
     * @param tier         the tier being run, not null
     * @param layerNumber  the one-based layer to apply; it must be the layer the lot is
     *                     on plus one and no higher than {@code tier.layers()}
     * @param defectFactor how healthy the line is, from
     *                     {@link com.euvcraft.lithmod.core.LineStats#defectFactor()}
     * @return a new lot with the layer applied
     * @throws NullPointerException     if an argument is null
     * @throws IllegalArgumentException if the level is not a server level, the lot is
     *                                  empty, a client view, or the layer number is not
     *                                  the next one or is past the tier's layers
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
     * @param lot          the lot to process, not null, not empty, not a client view
     * @param tier         the tier being run, not null
     * @param cores        the core count of the die, or {@code null} for none
     * @param layerNumber  the one-based layer to apply; it must be the layer the lot is
     *                     on plus one and no higher than {@code tier.layers()}
     * @param defectFactor how healthy the line is
     * @return a new lot with the layer applied
     * @throws NullPointerException     if an argument but the core count is null
     * @throws IllegalArgumentException if the level is not a server level, the lot is
     *                                  empty, a client view, or the layer number is not
     *                                  the next one or is past the tier's layers
     */
    public static FoupContents applyLayerStep(ServerLevel level, FoupContents lot, Tier tier, @Nullable Integer cores,
            int layerNumber, double defectFactor) {
        FabRandomService.requireServerSide(level);
        Objects.requireNonNull(lot, "lot must not be null");
        Objects.requireNonNull(tier, "tier must not be null");

        if (FoupContentsCodec.isClientView(lot)) {
            throw new IllegalArgumentException("a client-view lot cannot take a layer step");
        }

        return YieldRollEngine.applyLayerStep(lot, tier, cores, layerNumber, defectFactor,
            EUVCRAFTMod.settings(), (waferIndex, stepIndex) ->
                FabRandomService.rngFor(level, lot.lotId(), waferIndex, stepIndex));
    }

    /**
     * Judges a finished lot, one answer per wafer in slot order.
     *
     * <p>The effective yield comes from the lot's stored run spec, not from live
     * settings. The tier and cores arguments are only checked for consistency with the
     * spec. The lot is not changed.</p>
     *
     * @param lot  the lot to judge, not null; every wafer must be at the tier's last
     *             layer and the lot must carry a run spec
     * @param tier the tier it was processed at, not null, must match the spec
     * @param cores the core count of the die, or {@code null} for none, must match the spec
     * @return one pass or fail per wafer, in slot order
     * @throws NullPointerException  if the lot or the tier is null
     * @throws IllegalStateException if the lot has no run spec, the tier or cores do not
     *                               match the spec, or any wafer is not at the tier's
     *                               last layer
     */
    public static boolean[] evaluateLot(FoupContents lot, Tier tier, @Nullable Integer cores) {
        Objects.requireNonNull(lot, "lot must not be null");
        Objects.requireNonNull(tier, "tier must not be null");

        if (FoupContentsCodec.isClientView(lot)) {
            throw new IllegalArgumentException("a client-view lot cannot be judged");
        }

        return YieldRollEngine.evaluate(lot, tier, cores);
    }

    /**
     * Judges a finished lot on a server level, with an explicit server-side check.
     *
     * <p>This overload exists so that callers who already have a level can make the
     * server-side guarantee explicit; the no-level overload is kept for callers that
     * do not have a level but know they are on the server.</p>
     *
     * @param level the server level, not null
     * @param lot   the lot to judge, not null; must not be a client view
     * @param tier  the tier it was processed at, not null
     * @param cores the core count of the die, or {@code null} for none
     * @return one pass or fail per wafer, in slot order
     * @throws NullPointerException  if any argument is null
     * @throws IllegalStateException if the lot has no run spec, the tier or cores do not
     *                               match the spec, or any wafer is not at the tier's
     *                               last layer
     * @throws IllegalArgumentException if the level is not a server level or the lot is
     *                                  a client view
     */
    public static boolean[] evaluateLot(ServerLevel level, FoupContents lot, Tier tier, @Nullable Integer cores) {
        FabRandomService.requireServerSide(level);
        return evaluateLot(lot, tier, cores);
    }

    /**
     * Returns how many wafers of a finished lot pass.
     *
     * @param lot   the lot to judge, not null
     * @param tier  the tier it was processed at, not null
     * @param cores the core count of the die, or {@code null} for none
     * @return the number of passing wafers
     * @throws NullPointerException  if the lot or the tier is null
     * @throws IllegalStateException if the lot has no run spec, the tier or cores do not
     *                               match the spec, or any wafer is not at the tier's
     *                               last layer
     */
    public static int countPassing(FoupContents lot, Tier tier, @Nullable Integer cores) {
        return YieldRollEngine.countPassing(evaluateLot(lot, tier, cores));
    }

    /**
     * Returns how many wafers of a finished lot pass, with an explicit server-level check.
     *
     * @param level the server level, not null
     * @param lot   the lot to judge, not null; must not be a client view
     * @param tier  the tier it was processed at, not null
     * @param cores the core count of the die, or {@code null} for none
     * @return the number of passing wafers
     * @throws NullPointerException  if any argument is null
     * @throws IllegalStateException if the lot has no run spec, the tier or cores do not
     *                               match the spec, or any wafer is not at the tier's
     *                               last layer
     * @throws IllegalArgumentException if the level is not a server level or the lot is
     *                                  a client view
     */
    public static int countPassing(ServerLevel level, FoupContents lot, Tier tier, @Nullable Integer cores) {
        return YieldRollEngine.countPassing(evaluateLot(level, lot, tier, cores));
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