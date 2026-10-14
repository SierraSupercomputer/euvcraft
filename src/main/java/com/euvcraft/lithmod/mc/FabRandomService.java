package com.euvcraft.lithmod.mc;

import java.util.UUID;

import com.euvcraft.lithmod.core.ContextualRng;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.SavedDataStorage;

/**
 * Server-side entry point for every yield roll.
 *
 * <p>All fab randomness is produced here and nowhere else. The service reads the one
 * world salt out of {@link FabRandomData}, which lives in the overworld's
 * {@code SavedDataStorage}, so every dimension of a save shares it and it is written
 * to disk with the world.</p>
 *
 * <p>Nothing here is ever called on a client: {@link #requireServerSide(Level)} is the
 * guard, it runs on every entry point, and it throws rather than silently returning a
 * roll that the client invented.</p>
 */
public final class FabRandomService {

    private FabRandomService() {
    }

    /**
     * Returns the overworld's saved data storage, after checking that {@code level} is
     * a server level.
     *
     * @param level any level on the server
     * @return the storage holding {@link FabRandomData}
     * @throws IllegalArgumentException if {@code level} is null
     * @throws IllegalStateException    if {@code level} is not a server level
     */
    public static SavedDataStorage storage(Level level) {
        return requireServerSide(level).getServer().overworld().getDataStorage();
    }

    /**
     * Returns the world-scoped salt holder, creating it on first use.
     *
     * @param level any level on the server
     * @return the saved data holding the salt
     * @throws IllegalArgumentException if {@code level} is null
     * @throws IllegalStateException    if {@code level} is not a server level
     */
    public static FabRandomData data(Level level) {
        return storage(level).computeIfAbsent(FabRandomData.TYPE);
    }

    /**
     * Returns the salt of the save this level belongs to. Package private on purpose:
     * the salt is a secret and must not be logged or sent to a client. Code outside
     * this package should draw rolls with {@link #rngFor(Level, UUID, int, int)}.
     *
     * @param level any level on the server
     * @return the 64-bit salt
     */
    static long salt(Level level) {
        return data(level).salt();
    }

    /**
     * Returns a generator for one yield-roll context.
     *
     * @param level      any level on the server
     * @param lotId      sealed lot (FOUP) the wafer belongs to
     * @param waferIndex position of the wafer inside the lot
     * @param stepIndex  index of the process step
     * @return a generator positioned at the start of that context's stream
     * @throws IllegalArgumentException if {@code level} is null
     * @throws IllegalStateException    if {@code level} is not a server level
     */
    public static ContextualRng rngFor(Level level, UUID lotId, int waferIndex, int stepIndex) {
        return data(level).rngFor(lotId, waferIndex, stepIndex);
    }

    /**
     * Returns {@code level} as a server level, or throws.
     *
     * <p>Every public entry point calls this, so a client can never obtain a fab
     * roll: {@link #rngFor(Level, UUID, int, int)} hands the result straight to the
     * yield engine.</p>
     *
     * @param level the level to check
     * @return {@code level} typed as a {@link ServerLevel}
     * @throws IllegalArgumentException if {@code level} is null
     * @throws IllegalStateException    if {@code level} is a client-side or otherwise
     *                                  non-server level
     */
    public static ServerLevel requireServerSide(Level level) {
        if (level == null) {
            throw new IllegalArgumentException(
                "EUVCRAFT yield randomness is server-authoritative but was asked for a null level");
        }
        requireServerSide(level.isClientSide(), describe(level));
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel;
        }
        throw new IllegalStateException(
            "EUVCRAFT yield randomness is server-authoritative but " + describe(level) + " is not a ServerLevel");
    }

    /**
     * The client-side half of {@link #requireServerSide(Level)}, split out so the guard
     * itself can be exercised without constructing a client level.
     *
     * @param clientSide       whether the level lives on a client
     * @param levelDescription human readable level description used in the message
     * @throws IllegalStateException if {@code clientSide} is true
     */
    public static void requireServerSide(boolean clientSide, String levelDescription) {
        if (clientSide) {
            throw new IllegalStateException(
                "EUVCRAFT yield randomness is server-authoritative and must never be rolled on a client, but "
                    + levelDescription + " is a client-side level");
        }
    }

    private static String describe(Level level) {
        return level.dimension().identifier().toString();
    }
}