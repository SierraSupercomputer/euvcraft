package com.euvcraft.lithmod.mc;

import java.security.SecureRandom;
import java.util.Optional;
import java.util.UUID;

import com.euvcraft.lithmod.core.ContextualRng;
import com.mojang.serialization.Codec;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * World-scoped saved data holding the single random salt that seeds every yield roll.
 *
 * <p>Storage: the overworld's {@code SavedDataStorage}, file
 * {@code <world>/dimensions/minecraft/overworld/data/lithmod/fab_random.dat}, gzip
 * compressed NBT written as {@code {"DataVersion": n, "data": {"salt": <long>}}}.
 * Because the data is attached to the overworld, every dimension of a save shares the
 * one salt, and the salt lives with the world instead of in the global config.</p>
 *
 * <p>The salt is generated from {@link SecureRandom} the first time it is needed and
 * is then persisted. It is never derived from the world seed, from the session UUID,
 * from system time, or from anything a client ever receives; a client only ever sees
 * the resulting roll values, which is what makes rolls unpredictable but replayable.</p>
 */
public final class FabRandomData extends SavedData {

    /** NBT field holding the 64-bit salt. */
    public static final String KEY_SALT = "salt";

    /** Identifier of the stored data; also decides the file name. */
    public static final Identifier ID = Identifier.fromNamespaceAndPath("lithmod", "fab_random");

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Serialization format: a single {@code salt} field. A stored tag without the
     * field is read as "no salt yet" and produces a freshly generated one.
     *
     * <p>Built from {@code MapCodec#codec()} rather than {@code RecordCodecBuilder}
     * because in DataFixerUpper 10 (the version Minecraft 26.3 ships) a
     * {@code MapCodec} is no longer a {@code Codec}.</p>
     */
    public static final Codec<FabRandomData> CODEC = Codec.LONG.optionalFieldOf(KEY_SALT)
        .codec()
        .xmap(FabRandomData::fromOptionalSalt, FabRandomData::toOptionalSalt);

    /** The saved data type registered with the level's storage. */
    public static final SavedDataType<FabRandomData> TYPE = new SavedDataType<>(ID, FabRandomData::fresh, CODEC);

    private final long salt;

    /** Creates a data holder around an existing salt. */
    public FabRandomData(long salt) {
        this.salt = salt;
    }

    /**
     * Creates a brand new holder with a fresh secure salt.
     *
     * @return a holder with a newly generated salt
     */
    public static FabRandomData fresh() {
        return new FabRandomData(randomSalt());
    }

    /**
     * Draws a salt from the platform's secure random source. This is the only source
     * of fab randomness entropy; it never touches world or session state.
     *
     * @return a random 64-bit value
     */
    public static long randomSalt() {
        return SECURE_RANDOM.nextLong();
    }

    private static FabRandomData fromOptionalSalt(Optional<Long> stored) {
        return new FabRandomData(stored.orElseGet(FabRandomData::randomSalt));
    }

    private static Optional<Long> toOptionalSalt(FabRandomData data) {
        return Optional.of(data.salt());
    }

    /**
     * Returns the world salt.
     *
     * @return the 64-bit salt
     */
    public long salt() {
        return this.salt;
    }

    /**
     * Creates a generator for one yield-roll context, using this world salt.
     *
     * @param lotId      sealed lot the wafer belongs to
     * @param waferIndex position of the wafer inside the lot
     * @param stepIndex  index of the process step
     * @return a generator positioned at the start of that context's stream
     */
    public ContextualRng rngFor(UUID lotId, int waferIndex, int stepIndex) {
        return new ContextualRng(this.salt, lotId, waferIndex, stepIndex);
    }
}