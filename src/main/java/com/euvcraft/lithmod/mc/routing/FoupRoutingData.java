package com.euvcraft.lithmod.mc.routing;

import com.euvcraft.lithmod.core.DieTypes;
import com.euvcraft.lithmod.core.routing.FabProgress;

import java.util.Objects;
import java.util.Locale;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;

/**
 * Public routing metadata for a FOUP in transit between process cells.
 *
 * <p>This record carries only the safe, public routing information: the fabrication
 * progress of the lot, the die type being processed, the core count (for compute
 * dies only), and a contamination flag. It deliberately omits the private lot UUID,
 * wafer rolls, effective yield, G3 factors, and the {@link com.euvcraft.lithmod.core.RunSpec}
 * itself. The client and any non-fab system may see this without leaking
 * proprietary lot state.</p>
 *
 * <p>The validation rules enforce the process semantics:
 * <ul>
 *   <li>Before exposure ({@link FabProgress.Stage#UNPROCESSED}, {@link FabProgress.Stage#DEPOSITED},
 *       {@link FabProgress.Stage#COATED}), the die type may be empty (unspecified)
 *       and cores must be 0.</li>
 *   <li>An unbound tier-0 lot ({@link FabProgress.Stage#UNPROCESSED} or
 *       {@link FabProgress.Stage#DEPOSITED}) requires an empty die type and 0 cores.</li>
 *   <li>From {@link FabProgress.Stage#EXPOSED} onward, a non-empty die type is required.</li>
 *   <li>For a non-empty die type: {@code compute} requires {@code cores >= 1};
 *       all other die types require {@code cores == 0}.</li>
 *   <li>The die type, when non-empty, must be a member of {@link DieTypes#ALL}.</li>
 *   <li>Cores must be in the range 0..256.</li>
 * </ul>
 *
 * @param progress     the fabrication progress, never null
 * @param dieType      the die type string, empty when unspecified, otherwise one of {@link DieTypes#ALL}
 * @param cores        the core count, 0..256 (only used for compute dies)
 * @param contaminated whether the lot is contaminated
 */
public record FoupRoutingData(FabProgress progress, String dieType, int cores, boolean contaminated) {

    private record Fields(String stage,int tier,int version,String dieType,int cores,boolean contaminated){}
    public static final Codec<FoupRoutingData> CODEC=RecordCodecBuilder.<Fields>create(i->i.group(
        Codec.STRING.fieldOf("stage").forGetter(Fields::stage),
        Codec.INT.fieldOf("tier").forGetter(Fields::tier),
        Codec.INT.fieldOf("version").forGetter(Fields::version),
        Codec.STRING.fieldOf("die_type").forGetter(Fields::dieType),
        Codec.INT.fieldOf("cores").forGetter(Fields::cores),
        Codec.BOOL.optionalFieldOf("contaminated",false).forGetter(Fields::contaminated)
    ).apply(i,Fields::new)).comapFlatMap(f->{
        try{
            var stage=FabProgress.Stage.valueOf(f.stage().toUpperCase(Locale.ROOT));
            if(!stage.name().toLowerCase(Locale.ROOT).equals(f.stage()))return DataResult.error(()->"Invalid stage spelling");
            return DataResult.success(new FoupRoutingData(new FabProgress(stage,f.tier(),f.version()),f.dieType(),f.cores(),f.contaminated()));
        }catch(IllegalArgumentException|NullPointerException invalid){return DataResult.error(()->"Invalid routing data: "+invalid.getMessage());}
    },d->new Fields(d.progress.stage().name().toLowerCase(Locale.ROOT),d.progress.tier(),d.progress.version(),d.dieType,d.cores,d.contaminated));
    public static final StreamCodec<ByteBuf,FoupRoutingData> STREAM_CODEC=ByteBufCodecs.fromCodec(CODEC);

    /** Current schema version for this record. */
    public static final int CURRENT_VERSION = 1;

    /**
     * Creates a new routing data record with validation.
     *
     * @param progress     the fabrication progress, must not be null
     * @param dieType      the die type, must not be null; empty or one of {@link DieTypes#ALL}
     * @param cores        the core count, must be in range 0..256
     * @param contaminated the contamination flag
     * @throws NullPointerException     if progress or dieType is null
     * @throws IllegalArgumentException if cores is outside 0..256, dieType is not empty
     *                                  and not in {@link DieTypes#ALL}, or the stage/dieType/cores
     *                                  combination violates process rules
     */
    public FoupRoutingData {
        Objects.requireNonNull(progress, "progress must not be null");
        Objects.requireNonNull(dieType, "dieType must not be null");
        if (cores < 0 || cores > 256) {
            throw new IllegalArgumentException("cores must be in range 0..256, got " + cores);
        }
        boolean hasDieType = !dieType.isEmpty();
        if (hasDieType && !DieTypes.ALL.contains(dieType)) {
            throw new IllegalArgumentException("dieType must be empty or one of DieTypes.ALL, got " + dieType);
        }

        FabProgress.Stage stage = progress.stage();
        boolean isPreExposure = stage == FabProgress.Stage.UNPROCESSED
            || stage == FabProgress.Stage.DEPOSITED
            || stage == FabProgress.Stage.COATED;

        if (isPreExposure) {
            // Before exposure: dieType may be empty with cores 0
            if (hasDieType) {
                // If die type is specified before exposure, it must still follow compute cores rule
                if (DieTypes.COMPUTE.equals(dieType) && cores < 1) {
                    throw new IllegalArgumentException("compute die before exposure requires cores >= 1, got " + cores);
                }
                if (!DieTypes.COMPUTE.equals(dieType) && cores != 0) {
                    throw new IllegalArgumentException("non-compute die before exposure requires cores == 0, got " + cores);
                }
            } else {
                // Empty die type: cores must be 0
                if (cores != 0) {
                    throw new IllegalArgumentException("empty die type requires cores == 0, got " + cores);
                }
            }
            // Unbound tier 0 (UNPROCESSED/DEPOSITED) requires empty die and cores 0
            if ((stage == FabProgress.Stage.UNPROCESSED || stage == FabProgress.Stage.DEPOSITED)
                && progress.tier() == 0) {
                if (hasDieType) {
                    throw new IllegalArgumentException(stage + " tier 0 requires empty die type");
                }
                if (cores != 0) {
                    throw new IllegalArgumentException(stage + " tier 0 requires cores == 0");
                }
            }
        } else {
            // EXPOSED, ETCHED, CLEANED require non-empty die type
            if (!hasDieType) {
                throw new IllegalArgumentException(stage + " requires a non-empty die type");
            }
            if (DieTypes.COMPUTE.equals(dieType) && cores < 1) {
                throw new IllegalArgumentException("compute die requires cores >= 1, got " + cores);
            }
            if (!DieTypes.COMPUTE.equals(dieType) && cores != 0) {
                throw new IllegalArgumentException("non-compute die requires cores == 0, got " + cores);
            }
        }
    }

    /**
     * Returns the initial routing data: unprocessed progress, empty die type, 0 cores, not contaminated.
     *
     * @return a new {@code FoupRoutingData} at the starting state
     */
    public static FoupRoutingData initial() {
        return new FoupRoutingData(FabProgress.initial(), "", 0, false);
    }
}
