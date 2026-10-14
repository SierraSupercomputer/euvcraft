package com.euvcraft.lithmod.mc;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import com.euvcraft.lithmod.core.ContextualRng;
import com.mojang.serialization.DataResult;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.Level;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the parts of {@link FabRandomService} and {@link FabRandomData} that
 * do not need a running server: the client-side guard and the saved data codec.
 *
 * <p>The storage round trip against a real level lives in the game test
 * {@code lithmod:fab_random_salt_round_trip}.</p>
 */
class FabRandomServiceTest {

    private static final String CLIENT_LEVEL = "minecraft:overworld (client)";

    @Test
    @DisplayName("the client-side guard throws on a client level")
    void clientSideLevelIsRejected() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
            () -> FabRandomService.requireServerSide(true, CLIENT_LEVEL));

        assertTrue(thrown.getMessage().contains("server-authoritative"),
            "message did not explain the rule: " + thrown.getMessage());
        assertTrue(thrown.getMessage().contains(CLIENT_LEVEL),
            "message did not name the level: " + thrown.getMessage());
    }

    @Test
    @DisplayName("the client-side guard accepts a server level")
    void serverSideLevelIsAccepted() {
        assertDoesNotThrow(() -> FabRandomService.requireServerSide(false, "minecraft:overworld"));
    }

    @Test
    @DisplayName("a null level is rejected before anything else")
    void nullLevelIsRejected() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> FabRandomService.requireServerSide((Level) null));

        assertTrue(thrown.getMessage().contains("server-authoritative"),
            "message did not explain the rule: " + thrown.getMessage());
    }

    @Test
    @DisplayName("stored salt survives a codec round trip")
    void codecRoundTripKeepsSalt() {
        FabRandomData original = new FabRandomData(0xBADCAF1234567L);

        DataResult<net.minecraft.nbt.Tag> encoded = FabRandomData.CODEC.encodeStart(NbtOps.INSTANCE, original);
        assertTrue(encoded.result().isPresent(), "encoding failed: " + encoded.error());

        DataResult<FabRandomData> decoded = FabRandomData.CODEC
            .parse(NbtOps.INSTANCE, encoded.result().orElseThrow());

        assertTrue(decoded.result().isPresent(), "decoding failed: " + decoded.error());
        assertEquals(original.salt(), decoded.result().orElseThrow().salt());
    }

    @Test
    @DisplayName("a stored tag without a salt field still decodes")
    void codecToleratesMissingSalt() {
        DataResult<FabRandomData> decoded = FabRandomData.CODEC
            .parse(NbtOps.INSTANCE, new CompoundTag());

        assertTrue(decoded.result().isPresent(), "decoding failed: " + decoded.error());
        assertNotNull(decoded.result().orElseThrow());
    }

    @Test
    @DisplayName("two fresh salts differ")
    void freshSaltsDiffer() {
        // SecureRandom nextLong() repeats with probability 2^-64.
        assertTrue(FabRandomData.fresh().salt() != FabRandomData.fresh().salt(),
            "two SecureRandom draws produced the same salt");
    }

    @Test
    @DisplayName("rounds from a fixed salt are reproducible and context bound")
    void rngForIsContextBound() {
        UUID lot = UUID.fromString("00000000-0000-4000-8000-0000000000a1");
        FabRandomData data = new FabRandomData(42L);

        ContextualRng first = data.rngFor(lot, 0, 0);
        ContextualRng second = data.rngFor(lot, 0, 0);
        ContextualRng other = data.rngFor(lot, 0, 1);

        double a = first.nextDouble();
        double b = second.nextDouble();
        double c = other.nextDouble();

        assertEquals(a, b, 0.0);
        assertNotEquals(a, c, "different step produced the same roll");
    }
}