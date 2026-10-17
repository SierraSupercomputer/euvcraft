package com.euvcraft.lithmod.mc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import com.euvcraft.lithmod.core.FoupContents;
import com.euvcraft.lithmod.core.RunSpec;
import com.euvcraft.lithmod.core.Wafer;
import com.euvcraft.lithmod.core.WaferPacking;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The two codecs of the FOUP data component: one that stores a lot and one that is
 * safe to send to a client.
 *
 * <p><strong>{@link #CODEC} (persistent)</strong> writes the whole lot: the lot id,
 * the capacity and the three packed wafer arrays, rolls and flags included. This is
 * what a world save, a chest or a shulker box store.</p>
 *
 * <p><strong>{@link #STREAM_CODEC} (network)</strong> writes
 * {@link FoupContents#publicView()} and nothing else: capacity, wafer count and lot
 * layer. A client therefore never receives a {@link Wafer#cumulativeRoll()} or a
 * {@link Wafer#flags()} value. The other direction rebuilds a placeholder lot through
 * {@link #fromPublicView(int, int, int)}, which is a display value and must never be
 * treated as authoritative: see the creative-mode spike in the M0-08 report.</p>
 *
 * <p>The persistent form stores the rolls as raw IEEE-754 bits in an integer array,
 * next to two more integer arrays, so a lot costs three NBT int arrays and no
 * per-wafer field names. See {@link WaferPacking}.</p>
 */
public final class FoupContentsCodec {

    /** Field holding the lot id. */
    public static final String KEY_LOT = "lot";

    /** Field holding the capacity. */
    public static final String KEY_CAPACITY = "capacity";

    /** Field holding the packed layer indexes. */
    public static final String KEY_LAYERS = "layers";

    /** Field holding the packed cumulative rolls, as raw IEEE-754 bits. */
    public static final String KEY_ROLLS = "rolls";

    /** Field holding the packed flags. */
    public static final String KEY_FLAGS = "flags";

    /** Field holding the run spec (optional, new in M0-09b). */
    public static final String KEY_RUN_SPEC = "runSpec";

    /**
     * Lot id used by {@link #fromPublicView(int, int, int)}.
     *
     * <p>The real lot id is not part of the public view, so the network form has to
     * put something there. It uses the nil UUID, which a server-side
     * {@link UUID#randomUUID()} is practically never going to draw, so the value is
     * recognisable. That is a convenience, not a guarantee: a lot that has this id is
     * treated as a client view because of this marker, and the marker is checked
     * explicitly by {@link #isClientView(FoupContents)} and by
     * {@link FoupItems#trustedContentsOf(net.minecraft.world.item.ItemStack)}.</p>
     */
    public static final UUID CLIENT_VIEW_LOT_ID = new UUID(0L, 0L);

    /**
     * Roll value used for the placeholder wafers of a client-side view: a wafer no
     * roll has been applied to yet.
     */
    public static final float CLIENT_VIEW_ROLL = Wafer.NO_ROLLS;

    /**
     * An int array as a codec. {@link Codec#INT_STREAM} is the vanilla representation
     * (vanilla stores a UUID the same way) and it maps to an NBT int array, which is
     * the compact form this component wants.
     */
    private static final Codec<int[]> INT_ARRAY = Codec.INT_STREAM.xmap(IntStream::toArray, IntStream::of);

    /** Persistent form: everything, rolls, flags and the optional run spec included. */
    public static final Codec<FoupContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.CODEC.fieldOf(KEY_LOT).forGetter(FoupContents::lotId),
        Codec.INT.fieldOf(KEY_CAPACITY).forGetter(FoupContents::capacity),
        INT_ARRAY.fieldOf(KEY_LAYERS).forGetter(contents -> WaferPacking.packLayers(contents.wafers())),
        INT_ARRAY.fieldOf(KEY_ROLLS).forGetter(contents -> WaferPacking.packRollBits(contents.wafers())),
        INT_ARRAY.fieldOf(KEY_FLAGS).forGetter(contents -> WaferPacking.packFlags(contents.wafers())),
        RunSpecCodec.CODEC.optionalFieldOf(KEY_RUN_SPEC).forGetter(c -> java.util.Optional.ofNullable(c.run()))
    ).apply(instance, (lotId, capacity, layers, rollBits, flags, runSpec) ->
        new FoupContents(lotId, capacity,
            WaferPacking.unpack(layers, WaferPacking.unpackRollBits(rollBits), flags),
            runSpec.orElse(null))));

    /**
     * Network form: the public view only.
     *
     * <p>Typed on {@link ByteBuf} rather than on the registry aware buffer because it
     * needs no registry lookup at all; a registry aware buffer is still a
     * {@code ByteBuf}, so this is accepted by the data component builder unchanged.</p>
     */
    public static final StreamCodec<ByteBuf, FoupContents> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, contents -> contents.capacity(),
        ByteBufCodecs.VAR_INT, contents -> contents.size(),
        ByteBufCodecs.VAR_INT, contents -> contents.lotLayer(),
        FoupContentsCodec::fromPublicView
    );

    private FoupContentsCodec() {
    }

    /**
     * Rebuilds a lot from the stored form.
     *
     * @param lotId     stored lot id
     * @param capacity  stored capacity
     * @param layers    packed layer indexes
     * @param rollBits  packed cumulative rolls as raw IEEE-754 bits
     * @param flags     packed flags
     * @return the decoded lot
     * @throws IllegalArgumentException if the arrays disagree in length or a roll is
     *                                  outside {@code (0, 1]}
     */
    static FoupContents decodePersistent(UUID lotId, int capacity, int[] layers, int[] rollBits, int[] flags) {
        return new FoupContents(lotId, capacity,
            WaferPacking.unpack(layers, WaferPacking.unpackRollBits(rollBits), flags));
    }

    /**
     * Rebuilds the placeholder lot a client holds from the three numbers the network
     * form carries.
     *
     * <p>The result is not a real lot: it has {@link #CLIENT_VIEW_LOT_ID} and every
     * wafer is at the lot layer with {@link #CLIENT_VIEW_ROLL}. Only
     * {@link FoupContents#publicView()} of it is meaningful.</p>
     *
     * <p>Out-of-range values from a malformed or hostile buffer are clamped instead of
     * rejected: this is a display value and must never cost a player their
     * connection.</p>
     *
     * @param capacity  transmitted capacity
     * @param waferCount transmitted wafer count
     * @param lotLayer  transmitted lot layer
     * @return the placeholder lot
     */
    static FoupContents fromPublicView(int capacity, int waferCount, int lotLayer) {
        int safeCapacity = Math.max(1, capacity);
        int safeCount = Math.min(Math.max(0, waferCount), safeCapacity);
        int safeLayer = Math.max(Wafer.UNPROCESSED_LAYER, lotLayer);

        List<Wafer> placeholders = new ArrayList<>(safeCount);
        for (int i = 0; i < safeCount; i++) {
            placeholders.add(new Wafer(safeLayer, CLIENT_VIEW_ROLL, Wafer.NO_FLAGS));
        }
        return new FoupContents(CLIENT_VIEW_LOT_ID, safeCapacity, placeholders);
    }

    /**
     * Returns whether a lot is a client-side view built by
     * {@link #fromPublicView(int, int, int)} rather than a server-side lot.
     *
     * <p>This is the marker that {@link FoupItems#trustedContentsOf(net.minecraft.world.item.ItemStack)}
     * checks. It is a marker, not an authority check: it says "this value came from
     * the network form", nothing more. Nothing outside this package decides whether to
     * trust a lot; see the creative-mode spike in the M0-08 report.</p>
     *
     * @param contents the lot to inspect, not null
     * @return {@code true} if the lot carries the client view lot id
     */
    public static boolean isClientView(FoupContents contents) {
        return CLIENT_VIEW_LOT_ID.equals(contents.lotId());
    }
}