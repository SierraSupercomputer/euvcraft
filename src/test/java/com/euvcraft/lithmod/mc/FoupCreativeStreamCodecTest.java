package com.euvcraft.lithmod.mc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.euvcraft.lithmod.core.FoupContents;
import com.euvcraft.lithmod.core.RunSpec;
import com.euvcraft.lithmod.core.Wafer;
import com.euvcraft.lithmod.core.WaferSubstrate;
import com.euvcraft.lithmod.core.cleanroom.CleanroomExposure;
import com.euvcraft.lithmod.mc.network.FoupNetworkContext;

import com.mojang.serialization.DataResult;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.ByteBufCodecs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the Creative-mode full FOUP transport of
 * {@link FoupContentsCodec#STREAM_CODEC}.
 *
 * <p>The packet context normally set by the {@code PacketEncoder}/{@code PacketDecoder}
 * mixins is engaged directly here through {@link FoupNetworkContext#section}, so these
 * tests exercise the codec behavior without a mixin runtime and without a server. The
 * real pipeline coverage lives in the game tests.</p>
 */
class FoupCreativeStreamCodecTest {

    private static final UUID LOT = UUID.fromString("00000000-0000-4000-8000-0000000000f8");

    private static List<Wafer> processedWafers(int count, int layer) {
        List<Wafer> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new Wafer(layer, 0.9f - i * 0.01f, Wafer.NO_FLAGS + (i % 3)));
        }
        return list;
    }

    /** A processed lot with run contract, exposure and yield so nothing is left at defaults. */
    private static FoupContents processedMk1() {
        return new FoupContents(LOT, 25, processedWafers(25, 12),
            new RunSpec("logic", 3, 0, 0.9, 24), WaferSubstrate.ORDINARY, FoupContents.PREPARATION_LEGACY);
    }

    private static FoupContents processedMk2() {
        return new FoupContents(UUID.fromString("00000000-0000-4000-8000-0000000000f9"), 64,
            processedWafers(64, 7), new RunSpec("memory", 2, 1, 0.8, 12), WaferSubstrate.PURE,
            FoupContents.PREPARATION_G2, CleanroomExposure.empty(), 500)
            .withRoomExposure(new CleanroomExposure(1200, 411), 376);
    }

    private static FoupContents encodeDecode(FoupContents original, FoupNetworkContext.Payload context) {
        FoupNetworkContext.push(context);
        try {
            ByteBuf buffer = Unpooled.buffer();
            try {
                FoupContentsCodec.STREAM_CODEC.encode(buffer, original);
                buffer.readerIndex(0);
                var decoded=FoupContentsCodec.STREAM_CODEC.decode(buffer);
                assertEquals(1,buffer.refCnt(),"decoder released its borrowed parent buffer");
                assertEquals(0,buffer.readableBytes(),"decoder left trailing component bytes");
                return decoded;
            } finally {
                buffer.release();
            }
        } finally {
            FoupNetworkContext.pop(context);
        }
    }

    @Test
    @DisplayName("a full processed Mk1 survives the network form exactly")
    void processedMk1RoundTrip() {
        FoupContents original = processedMk1();
        FoupContents decoded = encodeDecode(original, FoupNetworkContext.Payload.FULL);

        assertFalse(FoupContentsCodec.isClientView(decoded), "a full decode degraded to a client view");
        assertEquals(original.lotId(), decoded.lotId(), "the lot id was lost");
        assertEquals(original.capacity(), decoded.capacity(), "the capacity changed");
        assertEquals(original.size(), decoded.size(), "the wafer count changed");
        assertEquals(original.run(), decoded.run(), "the run spec changed");
        assertEquals(original.grade(), decoded.grade(), "the grade changed");
        assertEquals(original.preparationGeneration(), decoded.preparationGeneration(), "the preparation changed");
        assertEquals(original, decoded, "the immutable lot changed in some field");
        for (int i = 0; i < original.size(); i++) {
            assertEquals(Float.floatToRawIntBits(original.wafers().get(i).cumulativeRoll()),
                Float.floatToRawIntBits(decoded.wafers().get(i).cumulativeRoll()),
                "the roll raw bits of wafer " + i + " changed");
            assertEquals(original.wafers().get(i).flags(), decoded.wafers().get(i).flags(),
                "the flags of wafer " + i + " changed");
            assertEquals(original.wafers().get(i).layerIndex(), decoded.wafers().get(i).layerIndex(),
                "the layer of wafer " + i + " changed");
        }
    }

    @Test
    @DisplayName("a full processed Mk2 survives G2 preparation, exposure and frozen yield")
    void processedMk2RoundTrip() {
        FoupContents original = processedMk2();
        FoupContents decoded = encodeDecode(original, FoupNetworkContext.Payload.FULL);

        assertEquals(original, decoded, "the Mk2 lot changed");
        assertEquals(1200, decoded.roomExposure().samples(), "the run count of the exposure changed");
        assertEquals(411, decoded.roomExposure().permilleSum(), "the exposure sum changed");
        assertEquals(376, decoded.roomYieldPermille(), "the frozen yield changed");
        assertEquals(FoupContents.PREPARATION_G2, decoded.preparationGeneration(), "G2 preparation was degraded");
    }

    @Test
    @DisplayName("without a context the form stays public-only: private bytes excluded")
    void defaultFormIsPublicOnly() {
        FoupContents first = processedMk1();
        FoupContents second = processedMk2();

        ByteBuf publicFirst = Unpooled.buffer();
        ByteBuf publicSecond = Unpooled.buffer();
        try {
            FoupContentsCodec.STREAM_CODEC.encode(publicFirst, first);
            FoupContentsCodec.STREAM_CODEC.encode(publicSecond, second);
            byte[] a = Arrays.copyOf(publicFirst.array(), publicFirst.readableBytes());
            byte[] b = Arrays.copyOf(publicSecond.array(), publicSecond.readableBytes());
            assertFalse(Arrays.equals(a, b), "different lots still encoded identically");
            // No full payload is ever sent without a grant: the form ends after the public fields.
            assertEquals(FoupContentsCodec.FLAG_NONE, a[a.length - 1], "a default encode carried a payload byte");
            assertEquals(FoupContentsCodec.FLAG_NONE, b[b.length - 1], "a default encode carried a payload byte");

            FoupContents view = FoupContentsCodec.STREAM_CODEC.decode(publicFirst.readerIndex(0));
            assertTrue(FoupContentsCodec.isClientView(view), "a default decode is not recognisable as a client view");
            assertEquals(25, view.size(), "the public wafer count changed");
            assertEquals(12, view.lotLayer(), "the public layer changed");
            assertNull(view.run(), "the public decode was handed the private run spec");
            assertEquals(FoupContentsCodec.CLIENT_VIEW_ROLL, view.wafers().get(3).cumulativeRoll(),
                "a roll reached the default decode");
        } finally {
            publicFirst.release();
            publicSecond.release();
        }
    }

    @Test
    @DisplayName("in FULL context the private bytes differ from the public form")
    void fullFormCarriesPrivateSuffix() {
        FoupContents original = processedMk1();
        FoupNetworkContext.push(FoupNetworkContext.Payload.FULL);
        try {
            ByteBuf full = Unpooled.buffer();
            ByteBuf bare = Unpooled.buffer();
            try {
                FoupContentsCodec.STREAM_CODEC.encode(full, original);
                FoupNetworkContext.section(FoupNetworkContext.Payload.PUBLIC, () -> FoupContentsCodec.STREAM_CODEC.encode(bare, original));
                int delta = full.writerIndex() - bare.writerIndex();
                assertTrue(delta >= 2,
                    "the full form is not the public form plus at least a flag and length prefix (delta " + delta + ")");
                assertTrue(delta < 1024, "the full form overhead grew suspiciously (delta " + delta + ")");
            } finally {
                full.release();
                bare.release();
            }
        } finally {
            FoupNetworkContext.pop(FoupNetworkContext.Payload.FULL);
        }
    }

    @Test
    @DisplayName("a Survival read downgrades a full payload: bytes consumed, value stays public")
    void survivalDowngradesFullPayload() {
        FoupContents original = processedMk1();

        ByteBuf wire = Unpooled.buffer();
        try {
            FoupNetworkContext.push(FoupNetworkContext.Payload.FULL);
            try {
                FoupContentsCodec.STREAM_CODEC.encode(wire, original);
            } finally {
                FoupNetworkContext.pop(FoupNetworkContext.Payload.FULL);
            }

            int encodedLength = wire.readableBytes();
            wire.readerIndex(0);
            FoupContents view = FoupContentsCodec.STREAM_CODEC.decode(wire);
            assertTrue(FoupContentsCodec.isClientView(view), "a Survival decode accepted the private lot");
            assertEquals(original.publicView(), view.publicView(), "the public projection was damaged");
            for (Wafer wafer : view.wafers()) {
                assertEquals(FoupContentsCodec.CLIENT_VIEW_ROLL, wafer.cumulativeRoll(), "a roll leaked in a downgrade");
                assertEquals(Wafer.NO_FLAGS, wafer.flags(), "a flag leaked in a downgrade");
            }
            assertEquals(0, wire.readableBytes(), "the private bytes were not consumed");
            assertTrue(encodedLength > 20, "the wire did not actually carry a payload");
        } finally {
            wire.release();
        }
    }

    @Test
    @DisplayName("a public form decode equals a full-form decode of the projection")
    void survivalOnBareFormStaysEqual() {
        FoupContents original = processedMk1();
        FoupNetworkContext.push(FoupNetworkContext.Payload.FULL);
        try {
            ByteBuf full = Unpooled.buffer();
            ByteBuf bare = Unpooled.buffer();
            try {
                FoupContentsCodec.STREAM_CODEC.encode(full, original);
                FoupContentsCodec.STREAM_CODEC.encode(bare, original);
                FoupContents fullRead = FoupContentsCodec.STREAM_CODEC.decode(full.readerIndex(0));
                FoupContents bareRead = FoupContentsCodec.STREAM_CODEC.decode(bare.readerIndex(0));
                assertEquals(bareRead.publicView(), fullRead.publicView(),
                    "the public projection of the two reads disagreed");
                assertTrue(!fullRead.equals(bareRead), "the two reads lost their difference");
            } finally {
                full.release();
                bare.release();
            }
        } finally {
            FoupNetworkContext.pop(FoupNetworkContext.Payload.FULL);
        }
    }

    @Test
    @DisplayName("hostile length claims are rejected without unbounded allocation")
    void hostileLengthClaimsFailClosed() {
        assertThrows(DecoderException.class, () -> decodeHostile(2_000_000), "an oversized length was accepted");
        assertThrows(DecoderException.class, () -> decodeHostile(-1), "a negative length was accepted");
        assertThrows(DecoderException.class, () -> decodeHostile(30), "a truncated length was accepted");
    }

    private static void decodeHostile(int claimed) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            ByteBufCodecs.VAR_INT.encode(buffer, 25);
            ByteBufCodecs.VAR_INT.encode(buffer, 3);
            ByteBufCodecs.VAR_INT.encode(buffer, 12);
            ByteBufCodecs.STRING_UTF8.encode(buffer, WaferSubstrate.ORDINARY.id());
            ByteBufCodecs.VAR_INT.encode(buffer, FoupContents.PREPARATION_LEGACY);
            buffer.writeByte(FoupContentsCodec.FLAG_FULL);
            ByteBufCodecs.VAR_INT.encode(buffer, claimed);
            buffer.readerIndex(0);
            FoupContentsCodec.STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    @Test
    @DisplayName("an unknown payload flag fails closed")
    void unknownFlagFailsClosed() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            ByteBufCodecs.VAR_INT.encode(buffer, 25);
            ByteBufCodecs.VAR_INT.encode(buffer, 0);
            ByteBufCodecs.VAR_INT.encode(buffer, 0);
            ByteBufCodecs.STRING_UTF8.encode(buffer, WaferSubstrate.ORDINARY.id());
            ByteBufCodecs.VAR_INT.encode(buffer, FoupContents.PREPARATION_LEGACY);
            buffer.writeByte(7);
            buffer.readerIndex(0);
            assertThrows(DecoderException.class, () -> FoupContentsCodec.STREAM_CODEC.decode(buffer),
                "an unknown flag was silently public");
        } finally {
            buffer.release();
        }
    }

    @Test
    @DisplayName("a nil client summary never gains a full payload even in FULL context")
    void clientSummaryStaysNil() {
        FoupContents view = FoupContentsCodec.fromPublicView(25, 3, 12, WaferSubstrate.ORDINARY.id(),
            FoupContents.PREPARATION_LEGACY);
        assertTrue(FoupContentsCodec.isClientView(view), "the placeholder was not recognised");

        FoupNetworkContext.push(FoupNetworkContext.Payload.FULL);
        try {
            ByteBuf buffer = Unpooled.buffer();
            try {
                FoupContentsCodec.STREAM_CODEC.encode(buffer, view);
                buffer.readerIndex(0);
                byte flag = buffer.getByte(findFlagByte(buffer));
                assertEquals(FoupContentsCodec.FLAG_NONE, flag, "a nil summary was sent with a full payload");
            } finally {
                buffer.release();
            }
        } finally {
            FoupNetworkContext.pop(FoupNetworkContext.Payload.FULL);
        }
    }

    /**
     * Locates the trailing payload flag by decoding the header the same way the codec
     * writes it: capacity, count, layer, grade, preparation.
     *
     * @return the position of the payload flag byte
     */
    private static int findFlagByte(ByteBuf buffer) {
        ByteBuf probe = Unpooled.buffer();
        try {
            probe.writeBytes(buffer, 0, buffer.writerIndex());
            int capacity = ByteBufCodecs.VAR_INT.decode(probe);
            int count = ByteBufCodecs.VAR_INT.decode(probe);
            int layer = ByteBufCodecs.VAR_INT.decode(probe);
            String grade = ByteBufCodecs.STRING_UTF8.decode(probe);
            int prep = ByteBufCodecs.VAR_INT.decode(probe);
            assertTrue(count >= 0 && layer >= 0, "the public header was not readable");
            assertTrue(prep == FoupContents.PREPARATION_LEGACY, "the header preparation changed shape");
            return probe.readerIndex();
        } finally {
            probe.release();
        }
    }

    @Test
    @DisplayName("scopes nest and an exception restores the outer scope")
    void nestedScopesAndExceptionsRestore() {
        FoupNetworkContext.push(FoupNetworkContext.Payload.FULL);
        try {
            assertEquals(FoupNetworkContext.Payload.FULL, FoupNetworkContext.current());
            // Inner narrower scope sees PUBLIC and restores when it throws.
            IllegalStateException boom = assertThrows(IllegalStateException.class, () ->
                FoupNetworkContext.section(FoupNetworkContext.Payload.PUBLIC, () -> {
                    assertEquals(FoupNetworkContext.Payload.PUBLIC, FoupNetworkContext.current());
                    throw new IllegalStateException("inside inner scope");
                }), "the inner exception was swallowed");
            assertEquals("inside inner scope", boom.getMessage());
            assertEquals(FoupNetworkContext.Payload.FULL, FoupNetworkContext.current(),
                "the inner scope did not restore the outer grant");
        } finally {
            FoupNetworkContext.pop(FoupNetworkContext.Payload.FULL);
        }

        FoupNetworkContext.section(FoupNetworkContext.Payload.FULL, () -> { });
        assertEquals(FoupNetworkContext.Payload.PUBLIC, FoupNetworkContext.current(),
            "the section left a scope behind");
        IllegalStateException odd = assertThrows(IllegalStateException.class, () ->
            FoupNetworkContext.pop(FoupNetworkContext.Payload.FULL),
            "an unbalanced pop was silent");
        assertTrue(odd.getMessage() != null && odd.getMessage().contains("Unbalanced"),
            "an unbalanced pop should fail loudly: " + odd.getMessage());
    }

    @Test
    @DisplayName("the persistent CODEC is unchanged by the network transport")
    void persistentCodecUnchanged() {
        FoupContents original = processedMk2();
        DataResult<Tag> encoded = FoupContentsCodec.CODEC.encodeStart(NbtOps.INSTANCE, original);
        assertTrue(encoded.result().isPresent(), "encoding failed: " + encoded.error());
        DataResult<FoupContents> decoded = FoupContentsCodec.CODEC.parse(NbtOps.INSTANCE, encoded.result().get());
        assertTrue(decoded.result().isPresent(), "decoding failed: " + decoded.error());
        assertEquals(original, decoded.result().get(), "the persistent codec changed behavior");
        CompoundTag tag = (CompoundTag) encoded.result().get();
        assertTrue(tag.contains(FoupContentsCodec.KEY_ROLLS), "the rolls key vanished");
        assertTrue(tag.contains(FoupContentsCodec.KEY_FLAGS), "the flags key vanished");
        assertTrue(tag.contains(FoupContentsCodec.KEY_RUN_SPEC), "the run spec key vanished");
        assertTrue(tag.contains("roomExposure"), "the room exposure key vanished");
    }
}
