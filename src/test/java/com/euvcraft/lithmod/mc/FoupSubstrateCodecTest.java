package com.euvcraft.lithmod.mc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.euvcraft.lithmod.core.FoupContents;
import com.euvcraft.lithmod.core.FoupPublicView;
import com.euvcraft.lithmod.core.RunSpec;
import com.euvcraft.lithmod.core.Wafer;
import com.euvcraft.lithmod.core.WaferSubstrate;

import com.mojang.serialization.DataResult;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link FoupContentsCodec} with substrate grade support.
 */
class FoupSubstrateCodecTest {

    private static final UUID LOT = UUID.fromString("00000000-0000-4000-8000-0000000000f8");

    private static List<Wafer> wafers(int count, int layer) {
        List<Wafer> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new Wafer(layer, 0.9f - i * 0.01f, Wafer.NO_FLAGS));
        }
        return list;
    }

    private static FoupContents lot(int capacity, int count, int layer, WaferSubstrate grade) {
        return FoupContents.of(LOT, capacity, wafers(count, layer), grade);
    }

    private static FoupContents encodeDecode(FoupContents original) {
        DataResult<Tag> encoded = FoupContentsCodec.CODEC.encodeStart(NbtOps.INSTANCE, original);
        assertTrue(encoded.result().isPresent(), "encoding failed: " + encoded.error());
        DataResult<FoupContents> decoded = FoupContentsCodec.CODEC.parse(NbtOps.INSTANCE,
            encoded.result().orElseThrow());
        assertTrue(decoded.result().isPresent(), "decoding failed: " + decoded.error());
        return decoded.result().orElseThrow();
    }

    @Test
    @DisplayName("persistent codec preserves ORDINARY grade")
    void persistentCodecPreservesOrdinaryGrade() {
        FoupContents original = lot(25, 25, 12, WaferSubstrate.ORDINARY);
        FoupContents decoded = encodeDecode(original);

        assertEquals(original.lotId(), decoded.lotId());
        assertEquals(original.capacity(), decoded.capacity());
        assertEquals(original.wafers(), decoded.wafers());
        assertEquals(WaferSubstrate.ORDINARY, decoded.grade());
    }

    @Test
    @DisplayName("persistent codec preserves PURE grade")
    void persistentCodecPreservesPureGrade() {
        FoupContents original = lot(25, 25, 12, WaferSubstrate.PURE);
        FoupContents decoded = encodeDecode(original);

        assertEquals(WaferSubstrate.PURE, decoded.grade());
    }

    @Test
    @DisplayName("persistent codec defaults missing grade to ORDINARY")
    void persistentCodecDefaultsMissingGrade() {
        // Manually create NBT without grade field
        CompoundTag tag = new CompoundTag();
        tag.put(FoupContentsCodec.KEY_LOT, net.minecraft.core.UUIDUtil.CODEC.encodeStart(NbtOps.INSTANCE, LOT).getOrThrow());
        tag.putInt(FoupContentsCodec.KEY_CAPACITY, 25);
        tag.putIntArray(FoupContentsCodec.KEY_LAYERS, new int[] {1, 2, 3});
        tag.putIntArray(FoupContentsCodec.KEY_ROLLS, new int[] {
            Float.floatToRawIntBits(0.9f),
            Float.floatToRawIntBits(0.89f),
            Float.floatToRawIntBits(0.88f)
        });
        tag.putIntArray(FoupContentsCodec.KEY_FLAGS, new int[] {0, 0, 0});

        DataResult<FoupContents> decoded = FoupContentsCodec.CODEC.parse(NbtOps.INSTANCE, tag);
        assertTrue(decoded.result().isPresent(), "decoding failed: " + decoded.error());
        FoupContents result = decoded.result().orElseThrow();

        assertEquals(WaferSubstrate.ORDINARY, result.grade());
    }

    @Test
    @DisplayName("persistent codec rejects invalid explicit grade")
    void persistentCodecRejectsInvalidGrade() {
        CompoundTag tag = new CompoundTag();
        tag.put(FoupContentsCodec.KEY_LOT, net.minecraft.core.UUIDUtil.CODEC.encodeStart(NbtOps.INSTANCE, LOT).getOrThrow());
        tag.putInt(FoupContentsCodec.KEY_CAPACITY, 25);
        tag.putIntArray(FoupContentsCodec.KEY_LAYERS, new int[] {1});
        tag.putIntArray(FoupContentsCodec.KEY_ROLLS, new int[] {Float.floatToRawIntBits(0.9f)});
        tag.putIntArray(FoupContentsCodec.KEY_FLAGS, new int[] {0});
        tag.putString(FoupContentsCodec.KEY_GRADE, "invalid_grade");

        DataResult<FoupContents> decoded = FoupContentsCodec.CODEC.parse(NbtOps.INSTANCE, tag);
        assertTrue(decoded.result().isEmpty(), "invalid grade was accepted: " + decoded.result().orElse(null));
    }

    @Test
    @DisplayName("network codec includes grade in addition to existing three ints")
    void networkCodecIncludesGrade() {
        FoupContents original = lot(25, 25, 7, WaferSubstrate.PURE);
        FoupPublicView sent = original.publicView();

        ByteBuf buffer = Unpooled.buffer();
        FoupContentsCodec.STREAM_CODEC.encode(buffer, original);
        FoupContents received = FoupContentsCodec.STREAM_CODEC.decode(buffer);

        assertEquals(sent.capacity(), received.capacity());
        assertEquals(sent.waferCount(), received.size());
        assertEquals(sent.lotLayer(), received.lotLayer());
        assertEquals(sent.grade(), received.grade());
        assertEquals(sent, received.publicView());
    }

    @Test
    @DisplayName("network codec clamps invalid grade to ORDINARY")
    void networkCodecClampsInvalidGrade() {
        // Manually encode with invalid grade string
        ByteBuf buffer = Unpooled.buffer();
        net.minecraft.network.codec.ByteBufCodecs.VAR_INT.encode(buffer, 25); // capacity
        net.minecraft.network.codec.ByteBufCodecs.VAR_INT.encode(buffer, 10); // waferCount
        net.minecraft.network.codec.ByteBufCodecs.VAR_INT.encode(buffer, 3);  // lotLayer
        net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8.encode(buffer, "invalid_grade"); // grade

        FoupContents received = FoupContentsCodec.STREAM_CODEC.decode(buffer);
        assertEquals(WaferSubstrate.ORDINARY, received.grade());
    }

    @Test
    @DisplayName("legacy fromPublicView(int,int,int) still works and defaults to ORDINARY")
    void legacyFromPublicViewDefaultsOrdinary() {
        FoupContents received = FoupContentsCodec.fromPublicView(25, 10, 3);
        assertEquals(WaferSubstrate.ORDINARY, received.grade());
        assertEquals(25, received.capacity());
        assertEquals(10, received.size());
        assertEquals(3, received.lotLayer());
    }

    @Test
    @DisplayName("network codec round trips PURE grade")
    void networkCodecRoundTripsPureGrade() {
        FoupContents original = lot(25, 10, 5, WaferSubstrate.PURE);

        ByteBuf first = Unpooled.buffer();
        FoupContentsCodec.STREAM_CODEC.encode(first, original);
        FoupContents once = FoupContentsCodec.STREAM_CODEC.decode(first);

        ByteBuf second = Unpooled.buffer();
        FoupContentsCodec.STREAM_CODEC.encode(second, once);
        FoupContents twice = FoupContentsCodec.STREAM_CODEC.decode(second);

        assertEquals(once.wafers(), twice.wafers());
        assertEquals(first.readableBytes(), second.readableBytes());
        assertEquals(WaferSubstrate.PURE, twice.grade());
    }

    @Test
    @DisplayName("network privacy invariance: different hidden rolls/calibration, identical public fields including grade")
    void networkPrivacyInvariance() {
        // Two lots with same public view but different hidden rolls
        FoupContents lot1 = FoupContents.of(LOT, 25, wafers(10, 3), WaferSubstrate.ORDINARY);
        FoupContents lot2 = FoupContents.of(LOT, 25, wafers(10, 3), WaferSubstrate.ORDINARY);

        // Mutate rolls differently (simulate different calibration)
        List<Wafer> mutated1 = new ArrayList<>();
        for (Wafer w : lot1.wafers()) {
            mutated1.add(new Wafer(w.layerIndex(), w.cumulativeRoll() * 0.5f, w.flags()));
        }
        List<Wafer> mutated2 = new ArrayList<>();
        for (Wafer w : lot2.wafers()) {
            mutated2.add(new Wafer(w.layerIndex(), w.cumulativeRoll() * 0.8f, w.flags()));
        }

        FoupContents different1 = new FoupContents(LOT, 25, mutated1, null, WaferSubstrate.ORDINARY);
        FoupContents different2 = new FoupContents(LOT, 25, mutated2, null, WaferSubstrate.ORDINARY);

        ByteBuf buf1 = Unpooled.buffer();
        ByteBuf buf2 = Unpooled.buffer();
        FoupContentsCodec.STREAM_CODEC.encode(buf1, different1);
        FoupContentsCodec.STREAM_CODEC.encode(buf2, different2);

        FoupContents decoded1 = FoupContentsCodec.STREAM_CODEC.decode(buf1);
        FoupContents decoded2 = FoupContentsCodec.STREAM_CODEC.decode(buf2);

        // Public views must be identical (including grade)
        assertEquals(decoded1.publicView(), decoded2.publicView());
        // But hidden rolls must not leak
        for (Wafer w : decoded1.wafers()) {
            assertEquals(FoupContentsCodec.CLIENT_VIEW_ROLL, w.cumulativeRoll());
        }
        for (Wafer w : decoded2.wafers()) {
            assertEquals(FoupContentsCodec.CLIENT_VIEW_ROLL, w.cumulativeRoll());
        }
    }

    @Test
    @DisplayName("untrusted placeholder: trustedContentsOf still rejects client view")
    void untrustedPlaceholderStillRejected() {
        FoupContents clientView = FoupContentsCodec.fromPublicView(25, 25, 4, "pure");
        assertTrue(FoupContentsCodec.isClientView(clientView));
        assertEquals(WaferSubstrate.PURE, clientView.grade());
    }

    @Test
    @DisplayName("malformed public grade handling: clamped to ORDINARY")
    void malformedPublicGradeClamped() {
        // Empty string
        FoupContents emptyGrade = FoupContentsCodec.fromPublicView(25, 10, 3, "");
        assertEquals(WaferSubstrate.ORDINARY, emptyGrade.grade());

        // Null-like string
        FoupContents nullGrade = FoupContentsCodec.fromPublicView(25, 10, 3, "null");
        assertEquals(WaferSubstrate.ORDINARY, nullGrade.grade());

        // Random string
        FoupContents randomGrade = FoupContentsCodec.fromPublicView(25, 10, 3, "not_a_real_grade");
        assertEquals(WaferSubstrate.ORDINARY, randomGrade.grade());
    }

    @Test
    @DisplayName("encoded packet size: grade field adds small overhead")
    void encodedPacketSize() {
        FoupContents ordinary = lot(25, 25, 3, WaferSubstrate.ORDINARY);
        FoupContents pure = lot(25, 25, 3, WaferSubstrate.PURE);

        ByteBuf bufOrdinary = Unpooled.buffer();
        ByteBuf bufPure = Unpooled.buffer();
        FoupContentsCodec.STREAM_CODEC.encode(bufOrdinary, ordinary);
        FoupContentsCodec.STREAM_CODEC.encode(bufPure, pure);

        // Pure grade string is longer than ordinary
        assertTrue(bufPure.readableBytes() > bufOrdinary.readableBytes());
        // But the difference should be small (just the string length difference)
        assertTrue(bufPure.readableBytes() - bufOrdinary.readableBytes() < 10);
    }
}