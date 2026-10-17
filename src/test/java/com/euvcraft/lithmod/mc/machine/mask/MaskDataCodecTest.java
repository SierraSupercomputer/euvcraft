package com.euvcraft.lithmod.mc.machine.mask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.euvcraft.lithmod.core.MaskData;

import com.google.gson.JsonObject;
import com.mojang.nbt.CompoundTag;
import com.mojang.nbt.NbtElement;
import com.mojang.nbt.NbtOps;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.minecraft.network.codec.ByteBufCodecs;

import org.junit.jupiter.api.Test;

/**
 * Codec tests for {@link MaskDataCodec}.
 *
 * <p>Only the two codecs and the core {@link MaskData} record are touched, so no
 * item, registry or recipe is involved: these are plain unit tests of the saved
 * and the network form.</p>
 *
 * <p>The rejected cases matter as much as the round trips. If
 * {@link MaskDataCodec#CODEC} let {@link MaskData}'s constructor exception out,
 * the parse below would throw instead of returning an empty
 * {@link DataResult#result()}, and the test would fail loudly.</p>
 */
class MaskDataCodecTest {

    /** One die type {@link MaskData} accepts, used wherever the value is not the point. */
    private static final String LOGIC = "lithmod:logic";

    /** A second one, so a test never proves itself with a single die type. */
    private static final String MEMORY = "lithmod:memory";

    @Test
    void jsonRoundTripKeepsThePublicFields() {
        MaskData data = new MaskData(LOGIC, 2, 7);

        JsonObject json = MaskDataCodec.CODEC.encodeStart(JsonOps.INSTANCE, data).result().orElseThrow();
        assertEquals(LOGIC, json.get("die_type").getAsString());
        assertEquals(2, json.get("tier_index").getAsInt());
        assertEquals(7, json.get("uses_remaining").getAsInt());

        assertEquals(data, MaskDataCodec.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow());
    }

    @Test
    void nbtRoundTripIsStableWhenRepeated() {
        MaskData data = new MaskData(MEMORY, 1, 3);
        MaskData decoded = data;
        for (int cycle = 0; cycle < 3; cycle++) {
            NbtElement encoded = MaskDataCodec.CODEC.encodeStart(NbtOps.INSTANCE, decoded).result().orElseThrow();
            decoded = MaskDataCodec.CODEC.parse(NbtOps.INSTANCE, encoded).result().orElseThrow();
            assertEquals(data, decoded);
        }
    }

    @Test
    void nbtUsesThePublicFieldNames() {
        NbtElement encoded =
            MaskDataCodec.CODEC.encodeStart(NbtOps.INSTANCE, new MaskData(MEMORY, 1, 3)).result().orElseThrow();

        CompoundTag tag = encoded.getAsCompound();
        assertEquals(MEMORY, tag.getString("die_type"));
        assertEquals(1, tag.getInt("tier_index"));
        assertEquals(3, tag.getInt("uses_remaining"));
    }

    @Test
    void aMissingUseCountDefaultsToZero() {
        JsonObject json = new JsonObject();
        json.addProperty("die_type", LOGIC);
        json.addProperty("tier_index", 1);

        MaskData fromJson = MaskDataCodec.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
        assertEquals(new MaskData(LOGIC, 1, 0), fromJson);

        CompoundTag tag = new CompoundTag();
        tag.putString("die_type", LOGIC);
        tag.putInt("tier_index", 2);

        MaskData fromNbt = MaskDataCodec.CODEC.parse(NbtOps.INSTANCE, tag).result().orElseThrow();
        assertEquals(new MaskData(LOGIC, 2, 0), fromNbt);
        assertTrue(fromNbt.isSpent(), "a template without a use count is a spent mask, not an endless one");
    }

    @Test
    void anUnknownDieTypeIsRejected() {
        assertRejected("dieType", fields("lithmod:photons", 1, 4));
    }

    @Test
    void aTierBelowOneIsRejected() {
        assertRejected("tierIndex", fields(LOGIC, 0, 4));
        assertRejected("tierIndex", fields(LOGIC, -1, 4));
    }

    @Test
    void aNegativeUseCountIsRejected() {
        assertRejected("usesRemaining", fields(LOGIC, 1, -1));
    }

    @Test
    void aMissingRequiredFieldIsRejected() {
        JsonObject withoutDieType = new JsonObject();
        withoutDieType.addProperty("tier_index", 1);
        assertRejected("die_type", withoutDieType);
    }

    @Test
    void streamCodecRoundTripsTheSamePublicData() {
        MaskData data = new MaskData(MEMORY, 2, 5);

        ByteBuf buffer = MaskDataCodec.STREAM_CODEC.encodeAlways(data);
        try {
            assertEquals(data, MaskDataCodec.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void streamCodecRejectsInvalidData() {
        assertStreamRejected("lithmod:photons", 1, 4);
        assertStreamRejected(LOGIC, 0, 4);
        assertStreamRejected(LOGIC, 1, -1);
    }

    /**
     * Builds the persisted JSON of one mask.
     *
     * @param dieType       die type to write
     * @param tierIndex     tier to write
     * @param usesRemaining uses to write, or {@code null} to leave the field out
     * @return the JSON the codec is asked to read
     */
    private static JsonObject fields(String dieType, int tierIndex, Integer usesRemaining) {
        JsonObject json = new JsonObject();
        json.addProperty("die_type", dieType);
        json.addProperty("tier_index", tierIndex);
        if (usesRemaining != null) {
            json.addProperty("uses_remaining", usesRemaining);
        }
        return json;
    }

    /**
     * Asserts that one piece of stored data is refused, and named.
     *
     * @param expectedPart substring the refusal has to mention
     * @param json         the stored data to read
     */
    private static void assertRejected(String expectedPart, JsonObject json) {
        DataResult<MaskData> parsed = MaskDataCodec.CODEC.parse(JsonOps.INSTANCE, json);
        assertTrue(parsed.result().isEmpty(), () -> "expected " + json + " to be refused");
        String reason = parsed.error().orElseThrow().message().getString();
        assertTrue(reason.contains(expectedPart),
            () -> "refusal '" + reason + "' should name " + expectedPart);
    }

    /**
     * Asserts that the stream decoder refuses one set of public values.
     *
     * <p>The bytes are written with the same primitives the stream codec uses, so
     * this hands the decoder data it did not have to encode. A stream codec has
     * no {@link DataResult} to fail in, so the refusal is the exception
     * {@link MaskData} throws; a decoder that wrapped it would still be a
     * {@link RuntimeException}.</p>
     *
     * @param dieType       die type to send
     * @param tierIndex     tier to send
     * @param usesRemaining uses to send
     */
    private static void assertStreamRejected(String dieType, int tierIndex, int usesRemaining) {
        ByteBuf buffer = Unpooled.buffer();
        ByteBufCodecs.STRING_UTF8.encode(buffer, dieType, ByteBuf::writeBytes);
        ByteBufCodecs.VAR_INT.encode(buffer, tierIndex, ByteBuf::writeBytes);
        ByteBufCodecs.VAR_INT.encode(buffer, usesRemaining, ByteBuf::writeBytes);
        try {
            assertThrows(RuntimeException.class, () -> MaskDataCodec.STREAM_CODEC.decode(buffer),
                () -> "expected " + dieType + "/" + tierIndex + "/" + usesRemaining + " to be refused");
        } finally {
            buffer.release();
        }
    }
}