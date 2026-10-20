package com.euvcraft.lithmod.mc.machine.mask;

import com.euvcraft.lithmod.core.MaskData;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The persistent and the network codec of one {@link MaskData}.
 *
 * <p>Everything a mask carries is public: the die type it patterns, the node tier
 * it was cut for, how many exposures are left and the mask shop generation it
 * was cut at (1 or 2). So unlike
 * {@link com.euvcraft.lithmod.mc.FoupContentsCodec}, which needs a persistent
 * form that keeps hidden values and a network form that drops them, one codec
 * here serves both and the client is told exactly what the server keeps.</p>
 *
 * <p>The persistent field names are part of the saved data and of the recipe
 * result templates: {@code die_type}, {@code tier_index}, {@code uses_remaining}
 * and {@code shop_generation}. {@code uses_remaining} and {@code shop_generation}
 * are optional and default to 0 and 1 respectively, so a recipe that writes only a
 * design hands out a spent gen-1 mask instead of an endless one.</p>
 */
public final class MaskDataCodec {

    /**
     * The persistent form, used by the data component and by recipe results.
     *
     * <p>This never throws. An unknown die type, a tier below 1, a negative
     * use count or an invalid shop_generation comes back as a {@link DataResult}
     * error carrying {@link MaskData}'s own message, because every layer that
     * reads a component already knows how to report a failed {@code DataResult}
     * and none of them knows what to do with a constructor exception.</p>
     */
    public static final Codec<MaskData> CODEC = persistentCodec();

    /**
     * The network form: the same four public values, in the same order.
     *
     * <p>A stream codec has no {@link DataResult} to fail in, so invalid data is
     * rejected by throwing out of {@link StreamCodec#decode} instead; that is how
     * the connection layer already hears about a bad packet. Unknown or out-of-range
     * shop_generation values are rejected.</p>
     */
    public static final StreamCodec<ByteBuf, MaskData> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, MaskData::dieType,
        ByteBufCodecs.VAR_INT, MaskData::tierIndex,
        ByteBufCodecs.VAR_INT, MaskData::usesRemaining,
        ByteBufCodecs.VAR_INT, MaskData::shopGeneration,
        MaskData::new);

    private MaskDataCodec() {
    }

    /**
     * Builds the persistent codec.
     *
     * <p>Written as a method rather than as one field initialiser so that the
     * field holder below is a local: a record codec has to hand its fields to a
     * constructor before anything has looked at them, and that constructor must
     * not throw.</p>
     *
     * <p>The reverse mapping takes a validated {@link MaskData} back to the same
     * four public fields, so encoding never has to invent values that decoding
     * would not accept.</p>
     *
     * @return the persistent {@code MaskData} codec
     */
    private static Codec<MaskData> persistentCodec() {
        Codec<Fields> fields = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("die_type").forGetter(Fields::dieType),
            Codec.INT.fieldOf("tier_index").forGetter(Fields::tierIndex),
            Codec.INT.optionalFieldOf("uses_remaining", 0).forGetter(Fields::usesRemaining),
            Codec.INT.optionalFieldOf("shop_generation", 1).forGetter(Fields::shopGeneration))
            .apply(instance, Fields::new));
        return fields.flatXmap(MaskDataCodec::maskData,
            data -> DataResult.success(new Fields(data.dieType(), data.tierIndex(), data.usesRemaining(), data.shopGeneration())));
    }

    /**
     * Turns the decoded fields into a mask, or into the reason there is none.
     *
     * @param fields the persisted values, still unvalidated
     * @return the mask, or the message {@link MaskData} refused these values with
     */
    private static DataResult<MaskData> maskData(Fields fields) {
        try {
            return DataResult.success(new MaskData(fields.dieType(), fields.tierIndex(), fields.usesRemaining(), fields.shopGeneration()));
        } catch (IllegalArgumentException rejected) {
            String reason = rejected.getMessage() == null ? rejected.toString() : rejected.getMessage();
            return DataResult.error(() -> reason);
        }
    }

    /**
     * The unvalidated persisted shape of one mask.
     *
     * <p>A separate type from {@link MaskData} only so that the throw happens
     * inside {@link #maskData(Fields)}, where it can become a
     * {@link DataResult} error instead of escaping the codec.</p>
     *
     * @param dieType         persisted die type, not yet checked
     * @param tierIndex       persisted tier, not yet checked
     * @param usesRemaining   persisted uses, defaulted to 0 when absent
     * @param shopGeneration  persisted shop generation, defaulted to 1 when absent
     */
    private record Fields(String dieType, int tierIndex, int usesRemaining, int shopGeneration) {
    }
}