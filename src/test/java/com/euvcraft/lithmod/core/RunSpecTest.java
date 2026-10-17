package com.euvcraft.lithmod.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import com.euvcraft.lithmod.mc.RunSpecCodec;
import com.mojang.serialization.DataResult;

import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for {@link RunSpec} validation and codec. */
class RunSpecTest {

    private static final UUID LOT_ID = UUID.fromString("00000000-0000-4000-8000-0000000009b1");

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("unknown die type string is refused")
        void unknownDieTypeRefused() {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new RunSpec("unknown", 1, 0, 0.6, 1));
            assertTrue(thrown.getMessage().contains("dieType must be one of"));
        }

        @Test
        @DisplayName("null die type is refused")
        void nullDieTypeRefused() {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new RunSpec(null, 1, 0, 0.6, 1));
            assertTrue(thrown.getMessage().contains("dieType must not be null"));
        }

        @Test
        @DisplayName("valid die types are accepted")
        void validDieTypesAccepted() {
            for (String type : DieTypes.ALL) {
                RunSpec spec = new RunSpec(type, 1, 0, 0.6, 1);
                assertEquals(type, spec.dieType());
            }
        }
    }

    @Nested
    @DisplayName("codec round trip")
    class CodecRoundTrip {

        @Test
        @DisplayName("die type is preserved through codec round trip")
        void dieTypePreserved() {
            for (String type : DieTypes.ALL) {
                RunSpec original = new RunSpec(type, 3, 8, 0.75, 24);
                DataResult<Tag> encoded = RunSpecCodec.CODEC.encodeStart(NbtOps.INSTANCE, original);
                assertTrue(encoded.result().isPresent(), "encoding failed for " + type);

                DataResult<RunSpec> decoded = RunSpecCodec.CODEC.parse(NbtOps.INSTANCE, encoded.result().get());
                assertTrue(decoded.result().isPresent(), "decoding failed for " + type);

                RunSpec roundTrip = decoded.result().get();
                assertEquals(type, roundTrip.dieType(), "die type mismatch for " + type);
                assertEquals(original.tierIndex(), roundTrip.tierIndex());
                assertEquals(original.cores(), roundTrip.cores());
                assertEquals(original.effectiveYield(), roundTrip.effectiveYield());
                assertEquals(original.rollCount(), roundTrip.rollCount());
            }
        }

        @Test
        @DisplayName("decoding an unknown die type fails")
        void unknownDieTypeDecodingFails() {
            // Create NBT with unknown die type manually
            var nbt = new net.minecraft.nbt.CompoundTag();
            nbt.putString("die_type", "unknown");
            nbt.putInt("tierIndex", 1);
            nbt.putInt("cores", 0);
            nbt.putDouble("effectiveYield", 0.6);
            nbt.putInt("rollCount", 1);

            // The codec validation happens in the RunSpec constructor, which throws during parsing
            assertThrows(IllegalArgumentException.class,
                () -> RunSpecCodec.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow());
        }
    }
}