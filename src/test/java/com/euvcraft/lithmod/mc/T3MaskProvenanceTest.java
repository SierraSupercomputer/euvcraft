package com.euvcraft.lithmod.mc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.euvcraft.lithmod.core.DieTypes;
import com.euvcraft.lithmod.core.MaskData;
import com.euvcraft.lithmod.mc.machine.mask.MaskDataCodec;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.ByteBufCodecs;

import org.junit.jupiter.api.Test;

/**
 * Provenance tests for {@link MaskData} across the T3 transition: 3-field legacy
 * masks (GEN1) must survive the 4-field codec round-trips, and the new GEN2
 * masks must carry the correct shopGeneration. The consumeUse chain must
 * preserve provenance at every step.
 *
 * <p>These are pure unit tests: no game, no registries, only the codecs and
 * the core record.</p>
 */
class T3MaskProvenanceTest {

    private static final String LOGIC = DieTypes.LOGIC;
    private static final String MEMORY = DieTypes.MEMORY;

    @Test
    void legacyThreeFieldMaskDecodesAsGenOne() {
        // JSON that a GEN1 mask would have saved (no shop_generation field)
        JsonObject json = new JsonObject();
        json.addProperty("die_type", LOGIC);
        json.addProperty("tier_index", 1);
        json.addProperty("uses_remaining", 4);

        MaskData decoded = MaskDataCodec.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
        assertEquals(1, decoded.shopGeneration(), "legacy mask without shop_generation defaults to GEN1");
        assertEquals(LOGIC, decoded.dieType());
        assertEquals(1, decoded.tierIndex());
        assertEquals(4, decoded.usesRemaining());
    }

    @Test
    void legacyThreeFieldNbtDecodesAsGenOne() {
        CompoundTag tag = new CompoundTag();
        tag.putString("die_type", LOGIC);
        tag.putInt("tier_index", 2);
        tag.putInt("uses_remaining", 3);

        MaskData decoded = MaskDataCodec.CODEC.parse(NbtOps.INSTANCE, tag).result().orElseThrow();
        assertEquals(1, decoded.shopGeneration(), "legacy NBT mask without shop_generation defaults to GEN1");
        assertEquals(LOGIC, decoded.dieType());
        assertEquals(2, decoded.tierIndex());
        assertEquals(3, decoded.usesRemaining());
    }

    @Test
    void newFourFieldMaskEncodesShopGeneration() {
        MaskData gen2 = new MaskData(MEMORY, 2, 5, 2);

        JsonElement encoded = MaskDataCodec.CODEC.encodeStart(JsonOps.INSTANCE, gen2).result().orElseThrow();
        JsonObject json = encoded.getAsJsonObject();
        assertEquals(2, json.get("shop_generation").getAsInt(), "GEN2 mask encodes shop_generation=2");

        Tag nbtEncoded = MaskDataCodec.CODEC.encodeStart(NbtOps.INSTANCE, gen2).result().orElseThrow();
        CompoundTag nbt = (CompoundTag) nbtEncoded;
        assertEquals(2, nbt.getInt("shop_generation").orElseThrow(), "GEN2 mask encodes shop_generation=2 in NBT");
    }

    @Test
    void gen2MaskRoundTripsJson() {
        MaskData gen2 = new MaskData(LOGIC, 3, 7, 2);
        JsonElement encoded = MaskDataCodec.CODEC.encodeStart(JsonOps.INSTANCE, gen2).result().orElseThrow();
        MaskData decoded = MaskDataCodec.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElseThrow();
        assertEquals(gen2, decoded, "GEN2 mask JSON round-trip failed");
        assertEquals(2, decoded.shopGeneration());
    }

    @Test
    void gen2MaskRoundTripsNbt() {
        MaskData gen2 = new MaskData(MEMORY, 2, 5, 2);
        Tag encoded = MaskDataCodec.CODEC.encodeStart(NbtOps.INSTANCE, gen2).result().orElseThrow();
        MaskData decoded = MaskDataCodec.CODEC.parse(NbtOps.INSTANCE, encoded).result().orElseThrow();
        assertEquals(gen2, decoded, "GEN2 mask NBT round-trip failed");
        assertEquals(2, decoded.shopGeneration());
    }

    @Test
    void gen2MaskRoundTripsNetwork() {
        MaskData gen2 = new MaskData(LOGIC, 1, 4, 2);
        ByteBuf buffer = Unpooled.buffer();
        try {
            MaskDataCodec.STREAM_CODEC.encode(buffer, gen2);
            MaskData decoded = MaskDataCodec.STREAM_CODEC.decode(buffer);
            assertEquals(gen2, decoded, "GEN2 mask network round-trip failed");
            assertEquals(2, decoded.shopGeneration());
        } finally {
            buffer.release();
        }
    }

    @Test
    void networkCodecRejectsInvalidGeneration() {
        // GEN0
        ByteBuf buf0 = Unpooled.buffer();
        ByteBufCodecs.STRING_UTF8.encode(buf0, LOGIC);
        ByteBufCodecs.VAR_INT.encode(buf0, 1);
        ByteBufCodecs.VAR_INT.encode(buf0, 4);
        ByteBufCodecs.VAR_INT.encode(buf0, 0);
        try {
            assertThrows(RuntimeException.class, () -> MaskDataCodec.STREAM_CODEC.decode(buf0));
        } finally { buf0.release(); }

        // GEN3
        ByteBuf buf3 = Unpooled.buffer();
        ByteBufCodecs.STRING_UTF8.encode(buf3, LOGIC);
        ByteBufCodecs.VAR_INT.encode(buf3, 1);
        ByteBufCodecs.VAR_INT.encode(buf3, 4);
        ByteBufCodecs.VAR_INT.encode(buf3, 3);
        try {
            assertThrows(RuntimeException.class, () -> MaskDataCodec.STREAM_CODEC.decode(buf3));
        } finally { buf3.release(); }

        // Negative
        ByteBuf bufNeg = Unpooled.buffer();
        ByteBufCodecs.STRING_UTF8.encode(bufNeg, LOGIC);
        ByteBufCodecs.VAR_INT.encode(bufNeg, 1);
        ByteBufCodecs.VAR_INT.encode(bufNeg, 4);
        ByteBufCodecs.VAR_INT.encode(bufNeg, -1);
        try {
            assertThrows(RuntimeException.class, () -> MaskDataCodec.STREAM_CODEC.decode(bufNeg));
        } finally { bufNeg.release(); }
    }

    @Test
    void consumeUseChainPreservesGeneration() {
        // GEN1 chain
        MaskData gen1 = new MaskData(LOGIC, 1, 4, 1);
        assertEquals(1, gen1.shopGeneration());
        MaskData gen1a = gen1.consumeUse();
        assertEquals(1, gen1a.shopGeneration());
        MaskData gen1b = gen1a.consumeUse();
        assertEquals(1, gen1b.shopGeneration());
        MaskData gen1c = gen1b.consumeUse();
        assertEquals(1, gen1c.shopGeneration());
        MaskData gen1d = gen1c.consumeUse();
        assertEquals(1, gen1d.shopGeneration());
        assertTrue(gen1d.isSpent());
        assertThrows(IllegalStateException.class, gen1d::consumeUse);

        // GEN2 chain
        MaskData gen2 = new MaskData(MEMORY, 2, 3, 2);
        assertEquals(2, gen2.shopGeneration());
        MaskData gen2a = gen2.consumeUse();
        assertEquals(2, gen2a.shopGeneration());
        MaskData gen2b = gen2a.consumeUse();
        assertEquals(2, gen2b.shopGeneration());
        MaskData gen2c = gen2b.consumeUse();
        assertEquals(2, gen2c.shopGeneration());
        assertTrue(gen2c.isSpent());
        assertThrows(IllegalStateException.class, gen2c::consumeUse);
    }

    @Test
    void mixedGenerationMasksDoNotInteract() {
        MaskData gen1 = new MaskData(LOGIC, 1, 2, 1);
        MaskData gen2 = new MaskData(LOGIC, 1, 2, 2);

        // They are different records
        assertNotEquals(gen1, gen2);
        assertNotEquals(gen1.consumeUse(), gen2.consumeUse());

        // After one use, still different generations
        assertEquals(1, gen1.consumeUse().shopGeneration());
        assertEquals(2, gen2.consumeUse().shopGeneration());
    }

    @Test
    void explicitInvalidGenerationRejected() {
        // JSON with explicit invalid shop_generation
        JsonObject json = new JsonObject();
        json.addProperty("die_type", LOGIC);
        json.addProperty("tier_index", 1);
        json.addProperty("uses_remaining", 4);
        json.addProperty("shop_generation", 0);
        DataResult<MaskData> result = MaskDataCodec.CODEC.parse(JsonOps.INSTANCE, json);
        assertTrue(result.result().isEmpty(), "shop_generation=0 should be rejected");
        String reason = result.error().orElseThrow().message();
        assertTrue(reason.contains("shopGeneration") || reason.contains("generation"));

        json.addProperty("shop_generation", 3);
        result = MaskDataCodec.CODEC.parse(JsonOps.INSTANCE, json);
        assertTrue(result.result().isEmpty(), "shop_generation=3 should be rejected");

        json.addProperty("shop_generation", -1);
        result = MaskDataCodec.CODEC.parse(JsonOps.INSTANCE, json);
        assertTrue(result.result().isEmpty(), "shop_generation=-1 should be rejected");
    }

    @Test
    void gen1PatternGeneratorConstantIsOne() {
        // This is a design contract test: the PatternGenerator constant must remain 1
        // so that it only ever produces GEN1 masks. The e-beam produces GEN2.
        assertEquals(1, com.euvcraft.lithmod.mc.machine.mask.PatternGeneratorBlockEntity.MASK_SHOP_GENERATION);
    }

    private static void assertNotEquals(Object a, Object b) {
        if (a.equals(b)) {
            throw new AssertionError("expected not equal: " + a + " == " + b);
        }
    }
}