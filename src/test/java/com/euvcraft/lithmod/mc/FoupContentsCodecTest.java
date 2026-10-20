package com.euvcraft.lithmod.mc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import com.euvcraft.lithmod.core.FabSettings;
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
 * Unit tests for the two {@link FoupContentsCodec} codecs.
 *
 * <p>Both codecs are exercised without a running server: the persistent one through
 * {@link NbtOps}, the network one through a plain {@link ByteBuf}. The
 * {@link net.minecraft.world.item.ItemStack} level checks need real registries and
 * live in the game test {@code lithmod:foup_stack_round_trip}.</p>
 */
class FoupContentsCodecTest {

    private static final UUID LOT = UUID.fromString("00000000-0000-4000-8000-0000000000f8");

    private static List<Wafer> wafers(int count, int layer) {
        List<Wafer> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new Wafer(layer, 0.9f - i * 0.01f, Wafer.NO_FLAGS));
        }
        return list;
    }

    private static FoupContents lot(int capacity, int count, int layer) {
        return new FoupContents(LOT, capacity, wafers(count, layer));
    }

    private static FoupContents lot(int capacity, int count, int layer, WaferSubstrate grade) {
        return new FoupContents(LOT, capacity, wafers(count, layer), grade);
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
    @DisplayName("the persistent codec preserves every roll exactly")
    void persistentCodecKeepsRolls() {
        FoupContents original = lot(25, 25, 12);
        FoupContents decoded = encodeDecode(original);

        assertEquals(original.lotId(), decoded.lotId());
        assertEquals(original.capacity(), decoded.capacity());
        assertEquals(original.wafers(), decoded.wafers());
        assertEquals(WaferSubstrate.ORDINARY, decoded.grade());
        for (int i = 0; i < original.size(); i++) {
            assertTrue(original.wafers().get(i).cumulativeRoll() == decoded.wafers().get(i).cumulativeRoll(),
                "roll " + i + " changed: " + original.wafers().get(i).cumulativeRoll()
                    + " -> " + decoded.wafers().get(i).cumulativeRoll());
        }
    }

    @Test
    @DisplayName("the persistent codec survives the extreme roll values")
    void persistentCodecKeepsExtremeRolls() {
        FoupContents original = new FoupContents(LOT, 4, List.of(
            new Wafer(1, Float.MIN_VALUE, 0),
            new Wafer(1, 1.0f, 0),
            new Wafer(0, 0.5f, 0),
            new Wafer(90, 0.9999999f, 0)));

        assertEquals(original.wafers(), encodeDecode(original).wafers());
        assertEquals(WaferSubstrate.ORDINARY, encodeDecode(original).grade());
    }

    @Test
    @DisplayName("the persistent form is three NBT int arrays plus the lot id, capacity and optional grade")
    void persistentFormShape() {
        CompoundTag tag = (CompoundTag) FoupContentsCodec.CODEC
            .encodeStart(NbtOps.INSTANCE, lot(25, 25, 12))
            .result().orElseThrow();

        assertEquals(25, ((IntArrayTag) tag.get(FoupContentsCodec.KEY_LAYERS)).getAsIntArray().length,
            "layers is not a 25 entry array");
        assertEquals(25, ((IntArrayTag) tag.get(FoupContentsCodec.KEY_ROLLS)).getAsIntArray().length,
            "rolls is not a 25 entry array");
        assertEquals(25, ((IntArrayTag) tag.get(FoupContentsCodec.KEY_FLAGS)).getAsIntArray().length,
            "flags is not a 25 entry array");
        assertEquals(25, ((IntTag) tag.get(FoupContentsCodec.KEY_CAPACITY)).value());
        // Grade field is optional and omitted when ORDINARY (default)
        assertTrue(!tag.contains(FoupContentsCodec.KEY_GRADE) || tag.getString(FoupContentsCodec.KEY_GRADE).equals("ordinary"),
            "grade field should be absent or ordinary for default lot");
    }

    @Test
    @DisplayName("the network codec keeps count, capacity, lot layer and grade")
    void networkCodecKeepsThePublicView() {
        FoupContents original = lot(25, 25, 7);
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
    @DisplayName("the network codec carries no roll and no flag")
    void networkCodecHidesRollsAndFlags() {
        FoupContents original = lot(25, 25, 7);

        ByteBuf buffer = Unpooled.buffer();
        FoupContentsCodec.STREAM_CODEC.encode(buffer, original);
        int bytes = buffer.readableBytes();
        FoupContents received = FoupContentsCodec.STREAM_CODEC.decode(buffer);

        for (Wafer wafer : received.wafers()) {
            assertEquals(FoupContentsCodec.CLIENT_VIEW_ROLL, wafer.cumulativeRoll(),
                "a roll reached the client");
            assertEquals(Wafer.NO_FLAGS, wafer.flags(), "a flag reached the client");
        }
        assertTrue(bytes < original.size(),
            "the network form takes " + bytes + " bytes for " + original.size() + " wafers");
        assertTrue(FoupContentsCodec.isClientView(received),
            "a decoded lot is not recognisable as a client view");
        assertEquals(WaferSubstrate.ORDINARY, received.grade());
    }

    @Test
    @DisplayName("the network form does not leak the lot id either")
    void networkCodecHidesTheLotId() {
        FoupContents original = lot(25, 3, 2);

        ByteBuf buffer = Unpooled.buffer();
        FoupContentsCodec.STREAM_CODEC.encode(buffer, original);
        FoupContents received = FoupContentsCodec.STREAM_CODEC.decode(buffer);

        assertNotEquals(original.lotId(), received.lotId());
        assertEquals(FoupContentsCodec.CLIENT_VIEW_LOT_ID, received.lotId());
    }

    @Test
    @DisplayName("a server-side lot is not a client view")
    void serverLotIsNotAClientView() {
        assertTrue(!FoupContentsCodec.isClientView(lot(25, 1, 1)));
    }

    @Test
    @DisplayName("the decoded public view is stable when sent again")
    void networkCodecIsStable() {
        FoupContents original = lot(25, 9, 4);

        ByteBuf first = Unpooled.buffer();
        FoupContentsCodec.STREAM_CODEC.encode(first, original);
        FoupContents once = FoupContentsCodec.STREAM_CODEC.decode(first);

        ByteBuf second = Unpooled.buffer();
        FoupContentsCodec.STREAM_CODEC.encode(second, once);
        FoupContents twice = FoupContentsCodec.STREAM_CODEC.decode(second);

        assertEquals(once.wafers(), twice.wafers());
        assertEquals(first.readableBytes(), second.readableBytes());
        assertEquals(WaferSubstrate.ORDINARY, twice.grade());
    }

    @Test
    @DisplayName("an empty lot round trips through both codecs")
    void emptyLotRoundTrips() {
        FoupContents empty = FoupContents.empty(LOT, 25);
        assertEquals(empty, encodeDecode(empty));

        ByteBuf buffer = Unpooled.buffer();
        FoupContentsCodec.STREAM_CODEC.encode(buffer, empty);
        FoupContents received = FoupContentsCodec.STREAM_CODEC.decode(buffer);

        assertEquals(new FoupPublicView(25, 0, 0, WaferSubstrate.ORDINARY), received.publicView());
    }

    @Test
    @DisplayName("a malformed network form is clamped instead of dropped")
    void networkCodecClampsHostileInput() {
        FoupContents clamped = FoupContentsCodec.fromPublicView(0, 999, -5);

        assertEquals(1, clamped.capacity());
        assertEquals(1, clamped.size());
        assertEquals(Wafer.UNPROCESSED_LAYER, clamped.lotLayer());
        assertEquals(WaferSubstrate.ORDINARY, clamped.grade());

        FoupContents negative = FoupContentsCodec.fromPublicView(25, -3, 4);
        assertEquals(0, negative.size());
        assertEquals(WaferSubstrate.ORDINARY, negative.grade());
    }

    @Test
    @DisplayName("stored invalid data is rejected instead of silently repaired")
    void persistentCodecRejectsInvalidData() {
        CompoundTag shortArrays = new CompoundTag();
        shortArrays.putInt(FoupContentsCodec.KEY_CAPACITY, 4);
        shortArrays.putIntArray(FoupContentsCodec.KEY_LAYERS, new int[] {1, 2});
        shortArrays.putIntArray(FoupContentsCodec.KEY_ROLLS, new int[] {Float.floatToRawIntBits(0.5f)});
        shortArrays.putIntArray(FoupContentsCodec.KEY_FLAGS, new int[] {0, 0});

        DataResult<FoupContents> unequal = FoupContentsCodec.CODEC.parse(NbtOps.INSTANCE, shortArrays);
        assertTrue(unequal.result().isEmpty(),
            "arrays of different length were accepted: " + unequal.result().orElse(null));

        CompoundTag badRoll = new CompoundTag();
        badRoll.putInt(FoupContentsCodec.KEY_CAPACITY, 1);
        badRoll.putIntArray(FoupContentsCodec.KEY_LAYERS, new int[] {1});
        badRoll.putIntArray(FoupContentsCodec.KEY_ROLLS, new int[] {Float.floatToRawIntBits(0.0f)});
        badRoll.putIntArray(FoupContentsCodec.KEY_FLAGS, new int[] {0});

        DataResult<FoupContents> zeroRoll = FoupContentsCodec.CODEC.parse(NbtOps.INSTANCE, badRoll);
        assertTrue(zeroRoll.result().isEmpty(),
            "a roll of 0 was accepted: " + zeroRoll.result().orElse(null));

        CompoundTag overCapacity = new CompoundTag();
        overCapacity.putInt(FoupContentsCodec.KEY_CAPACITY, 1);
        overCapacity.putIntArray(FoupContentsCodec.KEY_LAYERS, new int[] {1, 2});
        overCapacity.putIntArray(FoupContentsCodec.KEY_ROLLS,
            new int[] {Float.floatToRawIntBits(0.5f), Float.floatToRawIntBits(0.25f)});
        overCapacity.putIntArray(FoupContentsCodec.KEY_FLAGS, new int[] {0, 0});

        DataResult<FoupContents> tooMany = FoupContentsCodec.CODEC.parse(NbtOps.INSTANCE, overCapacity);
        assertTrue(tooMany.result().isEmpty(),
            "more wafers than the capacity were accepted: " + tooMany.result().orElse(null));
    }

    @Test
    @DisplayName("an existing lot keeps the capacity stored in it, not the configured one")
    void storedCapacityIsIndependentOfConfig() {
        int configured = FabSettings.defaults().foupCapacity();
        int otherCapacity = configured == 64 ? 25 : 64;
        FoupContents lot = new FoupContents(LOT, otherCapacity, wafers(3, 2));

        assertEquals(otherCapacity, encodeDecode(lot).capacity(),
            "a stored capacity was replaced by the configured one");

        FoupPublicView view = lot.publicView();
        assertEquals(otherCapacity, view.capacity(), "the public view lost the stored capacity");
    }

    @Test
    @DisplayName("persistent codec preserves explicit PURE grade")
    void persistentCodecPreservesPureGrade() {
        FoupContents original = lot(25, 10, 3, WaferSubstrate.PURE);
        FoupContents decoded = encodeDecode(original);

        assertEquals(WaferSubstrate.PURE, decoded.grade());
        assertEquals(original.wafers(), decoded.wafers());
    }

    @Test
    @DisplayName("persistent form includes grade field when PURE")
    void persistentFormIncludesGradeWhenPure() {
        CompoundTag tag = (CompoundTag) FoupContentsCodec.CODEC
            .encodeStart(NbtOps.INSTANCE, lot(25, 10, 3, WaferSubstrate.PURE))
            .result().orElseThrow();

        assertTrue(tag.contains(FoupContentsCodec.KEY_GRADE), "grade field missing for PURE lot");
        assertEquals("pure", tag.getString(FoupContentsCodec.KEY_GRADE));
    }

    @Test
    @DisplayName("legacy fromPublicView(int,int,int) defaults grade to ORDINARY")
    void legacyFromPublicViewDefaultsGrade() {
        FoupContents decoded = FoupContentsCodec.fromPublicView(25, 10, 3);
        assertEquals(WaferSubstrate.ORDINARY, decoded.grade());
    }

    @Test
    @DisplayName("encoded sizes: 25 wafers and 64 wafers, stored and on the wire with grade")
    void encodedSizes() {
        for (int wafers : new int[] {25, 64}) {
            FoupContents contents = new FoupContents(LOT, wafers, FoupContentsCodecTest.wafers(wafers, 3));

            DataResult<Tag> encoded = FoupContentsCodec.CODEC.encodeStart(NbtOps.INSTANCE, contents);
            assertTrue(encoded.result().isPresent(), "encoding failed: " + encoded.error());

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                NbtIo.writeUnnamedTag(encoded.result().get(), out);
            } catch (IOException e) {
                throw new AssertionError("could not measure the NBT form", e);
            }

            ByteBuf buffer = Unpooled.buffer();
            FoupContentsCodec.STREAM_CODEC.encode(buffer, contents);

            System.out.printf("FOUP component: %d wafers -> %d bytes NBT, %d bytes on the wire%n",
                wafers, bytes.size(), buffer.readableBytes());

            assertTrue(bytes.size() > 0, "the persistent form wrote nothing");
            assertTrue(bytes.size() < 10 + wafers * 16L,
                "the persistent form is not compact: " + bytes.size() + " bytes for " + wafers + " wafers");
        }
    }
}