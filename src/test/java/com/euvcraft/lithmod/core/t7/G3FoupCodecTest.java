package com.euvcraft.lithmod.core.t7;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.euvcraft.lithmod.core.FoupContents;
import com.euvcraft.lithmod.core.Wafer;
import com.euvcraft.lithmod.core.WaferSubstrate;
import com.euvcraft.lithmod.mc.FoupContentsCodec;
import com.euvcraft.lithmod.mc.network.FoupNetworkContext;
import com.mojang.serialization.DataResult;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Codec-level tests for G3 preparation evidence. They cover the persistent form,
 * the old-save fallback, malformed rejection and the public-only network
 * projection. No server is started.
 */
class G3FoupCodecTest {

    private static final UUID LOT = UUID.fromString("00000000-0000-4000-8000-0000000000f8");

    private static List<Wafer> freshWafers(int count) {
        List<Wafer> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(Wafer.unprocessed());
        }
        return list;
    }

    private static FoupContents g3Lot() {
        return FoupContents.of(LOT, 25, freshWafers(2), WaferSubstrate.ELECTRONIC_GRADE)
            .preparedG3(800, new int[] {1000, 500});
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
    @DisplayName("the persistent codec keeps G3 factors exactly")
    void persistentRoundTripKeepsFactors() {
        FoupContents original = g3Lot();
        FoupContents decoded = encodeDecode(original);

        assertEquals(original, decoded);
        assertEquals(FoupContents.PREPARATION_G3, decoded.preparationGeneration());
        assertEquals(800, decoded.g3Preparation().cleanroomMeanPermille());
        assertEquals(800, decoded.g3Preparation().factorPermilleAt(0));
        assertEquals(400, decoded.g3Preparation().factorPermilleAt(1));
        assertFalse(decoded.g3Preparation().isRedacted());
    }

    @Test
    @DisplayName("an old save without the G3 field still decodes")
    void oldSaveFallbackIsLegacy() {
        CompoundTag saved = (CompoundTag) FoupContentsCodec.CODEC
            .encodeStart(NbtOps.INSTANCE, FoupContents.of(LOT, 25, freshWafers(1), WaferSubstrate.PURE))
            .result().orElseThrow();
        saved.remove(FoupContentsCodec.KEY_G3_PREPARATION);
        saved.remove(FoupContentsCodec.KEY_PREPARATION);

        FoupContents decoded = FoupContentsCodec.CODEC.parse(NbtOps.INSTANCE, saved).result().orElseThrow();
        assertEquals(FoupContents.PREPARATION_LEGACY, decoded.preparationGeneration());
        assertEquals(G3Preparation.none(), decoded.g3Preparation());
    }

    @Test
    @DisplayName("a generation-3 save without factors fails closed")
    void missingFactorsFailClosed() {
        CompoundTag saved = (CompoundTag) FoupContentsCodec.CODEC
            .encodeStart(NbtOps.INSTANCE, FoupContents.of(LOT, 25, freshWafers(1), WaferSubstrate.ELECTRONIC_GRADE))
            .result().orElseThrow();
        saved.putInt(FoupContentsCodec.KEY_PREPARATION, FoupContents.PREPARATION_G3);
        saved.remove(FoupContentsCodec.KEY_G3_PREPARATION);

        assertTrue(FoupContentsCodec.CODEC.parse(NbtOps.INSTANCE, saved).error().isPresent());
    }

    @Test
    @DisplayName("a redacted or mismatched stored value is rejected")
    void malformedEvidenceFailsClosed() {
        CompoundTag saved = (CompoundTag) FoupContentsCodec.CODEC
            .encodeStart(NbtOps.INSTANCE, g3Lot())
            .result().orElseThrow();

        CompoundTag redacted = saved.copy();
        CompoundTag redactedEvidence = new CompoundTag();
        redactedEvidence.putInt("meanPermille", 0);
        redactedEvidence.putBoolean("redacted", true);
        redacted.put(FoupContentsCodec.KEY_G3_PREPARATION, redactedEvidence);
        assertTrue(FoupContentsCodec.CODEC.parse(NbtOps.INSTANCE, redacted).error().isPresent(),
            "a persisted redacted result was accepted as a real prepared lot");

        CompoundTag wrongCount = saved.copy();
        CompoundTag wrongEvidence = new CompoundTag();
        wrongEvidence.putInt("meanPermille", 800);
        wrongEvidence.putIntArray("factors", new int[] {800});
        wrongCount.put(FoupContentsCodec.KEY_G3_PREPARATION, wrongEvidence);
        assertTrue(FoupContentsCodec.CODEC.parse(NbtOps.INSTANCE, wrongCount).error().isPresent(),
            "a factor count that does not match the wafers was accepted");

        CompoundTag badFactor = saved.copy();
        CompoundTag badEvidence = new CompoundTag();
        badEvidence.putInt("meanPermille", 800);
        badEvidence.putIntArray("factors", new int[] {800, 0});
        badFactor.put(FoupContentsCodec.KEY_G3_PREPARATION, badEvidence);
        assertTrue(FoupContentsCodec.CODEC.parse(NbtOps.INSTANCE, badFactor).error().isPresent(),
            "an out-of-range factor was accepted");
    }

    @Test
    @DisplayName("the public network form exposes generation 3 but not the factors")
    void publicFormIsRedacted() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            FoupContentsCodec.STREAM_CODEC.encode(buffer, g3Lot());
            FoupContents received = FoupContentsCodec.STREAM_CODEC.decode(buffer);

            assertTrue(FoupContentsCodec.isClientView(received));
            assertEquals(FoupContents.PREPARATION_G3, received.preparationGeneration());
            assertTrue(received.g3Preparation().isRedacted());
            assertFalse(received.g3Preparation().hasFactors());
            assertEquals(0, received.g3Preparation().factorCount());
            assertThrows(IllegalStateException.class, () -> received.g3Preparation().factorPermilleAt(0));
            assertEquals(received.publicView(), g3Lot().publicView());
        } finally {
            buffer.release();
        }
    }

    @Test
    @DisplayName("FULL Creative transport round-trips the factors losslessly")
    void fullTransportKeepsFactors() {
        FoupContents original = g3Lot();
        FoupNetworkContext.push(FoupNetworkContext.Payload.FULL);
        try {
            ByteBuf buffer = Unpooled.buffer();
            try {
                FoupContentsCodec.STREAM_CODEC.encode(buffer, original);
                assertEquals(original, FoupContentsCodec.STREAM_CODEC.decode(buffer));
            } finally {
                buffer.release();
            }
        } finally {
            FoupNetworkContext.pop(FoupNetworkContext.Payload.FULL);
        }
    }
}
