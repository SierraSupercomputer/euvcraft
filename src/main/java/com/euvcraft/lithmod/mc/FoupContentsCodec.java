package com.euvcraft.lithmod.mc;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import com.euvcraft.lithmod.core.FoupContents;
import com.euvcraft.lithmod.core.FoupPublicView;
import com.euvcraft.lithmod.core.RunSpec;
import com.euvcraft.lithmod.core.Wafer;
import com.euvcraft.lithmod.core.WaferPacking;
import com.euvcraft.lithmod.core.WaferSubstrate;
import com.euvcraft.lithmod.mc.network.FoupNetworkContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The two codecs of the FOUP data component: one that stores a lot and one that is
 * safe to send to a client.
 *
 * <p><strong>{@link #CODEC} (persistent)</strong> writes the whole lot: the lot id,
 * the capacity, the wafer substrate grade, the preparation generation, and the three
 * packed wafer arrays, rolls and flags included. This is what a world save, a chest
 * or a shulker box store. The grade field is optional; when missing it defaults to
 * ORDINARY for backward compatibility. An explicitly present but invalid grade is
 * rejected. The preparationGeneration field is optional; when missing it defaults
 * to 1 (legacy) ONLY when absent. Unknown, malformed, or out-of-range values fail
 * closed.</p>
 *
 * <p><strong>{@link #STREAM_CODEC} (network)</strong> is self describing: it writes
 * the public projection — capacity, wafer count, lot layer, wafer substrate grade
 * and preparation generation — and never carries a {@link Wafer#cumulativeRoll()}
 * or a {@link Wafer#flags()} value except inside the optional bounded full payload
 * documented on {@link #STREAM_CODEC} below, which only exists when the enclosing
 * packet context granted {@link FoupNetworkContext.Payload#FULL}. A client holding a
 * payload without that grant still decodes through
 * {@link #fromPublicView(int, int, int)}, or {@link #fromPublicView(int, int, int, String)}
 * or the overload with preparation generation, which is a display value and must
 * never be treated as authoritative: see the creative-mode spike in the M0-08
 * report. A decode inside a FULL grant rebuilds the immutable lot exactly, so a
 * Creative clone or move never rerolls.</p>
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

    /** Field holding the wafer substrate grade (optional, new in T3 substrate foundation). */
    public static final String KEY_GRADE = "grade";

    /** Field holding the preparation generation (optional, new in T4 preparation). */
    public static final String KEY_PREPARATION = "preparation";

    /**
     * Lot id used by {@link #fromPublicView(int, int, int)} and
     * {@link #fromPublicView(int, int, int, String)}.
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

    /** Unknown explicit grades produce a normal codec error; only absent legacy fields default. */
    private static final Codec<WaferSubstrate> GRADE_CODEC = Codec.STRING.comapFlatMap(id -> {
        WaferSubstrate grade = WaferSubstrate.fromId(id);
        return grade == null ? DataResult.error(() -> "Unknown wafer substrate grade: " + id)
            : DataResult.success(grade);
    }, WaferSubstrate::id);

    /** Preparation generation codec: optional, defaults to 1 ONLY when absent; unknown/malformed/out-of-range fail closed. */
    private static final Codec<Integer> PREPARATION_CODEC = Codec.INT.validate(i -> {
        if (i < FoupContents.PREPARATION_LEGACY || i > FoupContents.PREPARATION_G2) {
            return DataResult.error(() -> "Invalid preparation generation: " + i + " (must be 1 or 2)");
        }
        return DataResult.success(i);
    });

    /**
     * The largest full private payload this stream codec will write or read, sized so a
     * 1 MiB cap covers a Mk2 FOUP of wafers plus room state with room to spare, while a
     * hostile length claim is rejected before any unbounded allocation happens.
     */
    public static final int MAX_FULL_PAYLOAD_BYTES = 1024 * 1024;

    /** Payload byte ending the record with the five public fields alone. */
    public static final byte FLAG_NONE = 0;

    /** Payload byte introducing a bounded length-prefixed full {@link #CODEC} payload. */
    public static final byte FLAG_FULL = 1;

    private record ExposureFields(long ticks,long sum){}
    private static final Codec<com.euvcraft.lithmod.core.cleanroom.CleanroomExposure> EXPOSURE_CODEC = RecordCodecBuilder.<ExposureFields>create(i->i.group(
        Codec.LONG.fieldOf("ticks").forGetter(ExposureFields::ticks),
        Codec.LONG.fieldOf("sum").forGetter(ExposureFields::sum)
    ).apply(i,ExposureFields::new)).comapFlatMap(fields->{
        try{return DataResult.success(new com.euvcraft.lithmod.core.cleanroom.CleanroomExposure(fields.ticks(),fields.sum()));}
        catch(IllegalArgumentException invalid){return DataResult.error(()->"Invalid cleanroom exposure: "+invalid.getMessage());}
    },e->new ExposureFields(e.samples(),e.permilleSum()));

    /** Persistent private state; roomExposure uses stable serialized keys ticks and sum for samples/permilleSum, independent of Java accessor names. */
    public static final Codec<FoupContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        UUIDUtil.CODEC.fieldOf(KEY_LOT).forGetter(FoupContents::lotId),
        Codec.INT.fieldOf(KEY_CAPACITY).forGetter(FoupContents::capacity),
        INT_ARRAY.fieldOf(KEY_LAYERS).forGetter(contents -> WaferPacking.packLayers(contents.wafers())),
        INT_ARRAY.fieldOf(KEY_ROLLS).forGetter(contents -> WaferPacking.packRollBits(contents.wafers())),
        INT_ARRAY.fieldOf(KEY_FLAGS).forGetter(contents -> WaferPacking.packFlags(contents.wafers())),
        RunSpecCodec.CODEC.optionalFieldOf(KEY_RUN_SPEC).forGetter(c -> java.util.Optional.ofNullable(c.run())),
        GRADE_CODEC.optionalFieldOf(KEY_GRADE, WaferSubstrate.ORDINARY).forGetter(FoupContents::grade),
        PREPARATION_CODEC.optionalFieldOf(KEY_PREPARATION, FoupContents.PREPARATION_LEGACY).forGetter(FoupContents::preparationGeneration),
        EXPOSURE_CODEC.optionalFieldOf("roomExposure", com.euvcraft.lithmod.core.cleanroom.CleanroomExposure.empty()).forGetter(FoupContents::roomExposure),
        Codec.intRange(0,1000).optionalFieldOf("roomYieldPermille",0).forGetter(FoupContents::roomYieldPermille)
    ).apply(instance, (lotId, capacity, layers, rollBits, flags, runSpec, grade, preparationGeneration, exposure, roomYield) ->
        new FoupContents(lotId, capacity,
            WaferPacking.unpack(layers, WaferPacking.unpackRollBits(rollBits), flags),
            runSpec.orElse(null), grade, preparationGeneration, exposure, roomYield)));

    /**
     * Network form: the public view only including the wafer substrate grade and preparation generation.
     *
     * <p>Typed on {@link ByteBuf} rather than on the registry aware buffer because it
     * needs no registry lookup at all; a registry aware buffer is still a
     * {@code ByteBuf}, so this is accepted by the data component builder unchanged.</p>
     *
     * <p>Since the Creative-mode full-transport change this form is <em>self
     * describing</em>: after the five public fields it carries one payload byte. A
     * {@value #FLAG_NONE} prefix ends the record after the public fields; a
     * {@value #FLAG_FULL} prefix is followed by a bounded var-int length and the exact
     * {@link #CODEC} state of the lot as an unnamed NBT tag. The full variant is only
     * written when {@link FoupNetworkContext#current()} is {@link Payload#FULL}, granted
     * for one packet by the {@code PacketEncoder}/{@code PacketDecoder} mixins, and
     * only for a real server lot: a nil client summary keeps its public-only form
     * whatever the context, because it holds no private state worth freezing.</p>
     *
     * <p>Decoding enforces the same server authority: with no context, or
     * {@link Payload#PUBLIC}, a full payload is consumed without being deserialized or
     * trusted — the private bytes of a Survival read are skipped and the value stays
     * the public placeholder. The full private payload is read back exactly only when
     * the enclosing packet context was granted {@link Payload#FULL}: a client inbound
     * packet, or a server connection whose play listener belongs to a player with
     * infinite materials. Nothing outside the packet context grants permission: no
     * incoming flag of any kind can widen a decode.</p>
     */
    public static final StreamCodec<ByteBuf, FoupContents> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public FoupContents decode(ByteBuf buffer) {
            int capacity = ByteBufCodecs.VAR_INT.decode(buffer);
            int waferCount = ByteBufCodecs.VAR_INT.decode(buffer);
            int lotLayer = ByteBufCodecs.VAR_INT.decode(buffer);
            String gradeId = ByteBufCodecs.STRING_UTF8.decode(buffer);
            int preparation = ByteBufCodecs.VAR_INT.decode(buffer);
            int flag = buffer.readUnsignedByte();
            if (flag == FLAG_NONE) {
                return fromPublicView(capacity, waferCount, lotLayer, gradeId, preparation);
            }
            if (flag != FLAG_FULL) {
                throw new DecoderException("Unknown FOUP full-payload flag: " + flag);
            }
            int length = ByteBufCodecs.VAR_INT.decode(buffer);
            if (length < 0 || length > MAX_FULL_PAYLOAD_BYTES) {
                throw new DecoderException("FOUP full payload length out of bounds: " + length);
            }
            if (length > buffer.readableBytes()) throw new DecoderException("FOUP full payload truncated");
            if (FoupNetworkContext.current() != FoupNetworkContext.Payload.FULL) {
                // Not authorized for the real contents: consume so the frame stays framed,
                // never allocate for or trust private bytes, and fall back to the public view.
                buffer.skipBytes(length);
                return fromPublicView(capacity, waferCount, lotLayer, gradeId, preparation);
            }
            try {
                FoupContents full = readFullPayload(buffer.readSlice(length));
                return full;
            } catch (IndexOutOfBoundsException truncated) {
                throw new DecoderException("FOUP full payload truncated", truncated);
            } catch (IOException corrupt) {
                throw new DecoderException("Could not read the FOUP full payload", corrupt);
            }
        }

        @Override
        public void encode(ByteBuf buffer, FoupContents contents) {
            ByteBufCodecs.VAR_INT.encode(buffer, contents.capacity());
            ByteBufCodecs.VAR_INT.encode(buffer, contents.size());
            ByteBufCodecs.VAR_INT.encode(buffer, contents.lotLayer());
            ByteBufCodecs.STRING_UTF8.encode(buffer, contents.grade().id());
            ByteBufCodecs.VAR_INT.encode(buffer, contents.preparationGeneration());
            boolean fullAuthorized = FoupNetworkContext.current() == FoupNetworkContext.Payload.FULL
                && !isClientView(contents);
            if (!fullAuthorized) {
                buffer.writeByte(FLAG_NONE);
                return;
            }
            buffer.writeByte(FLAG_FULL);
            writeFullPayload(buffer, contents);
        }
    };

    private FoupContentsCodec() {
    }


    /**
     * Rebuilds the placeholder lot a client holds from the three numbers the network
     * form carries (legacy overload without grade, defaults to ORDINARY, legacy preparation).
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
    public static FoupContents fromPublicView(int capacity, int waferCount, int lotLayer) {
        return fromPublicView(capacity, waferCount, lotLayer, WaferSubstrate.ORDINARY.id(), FoupContents.PREPARATION_LEGACY);
    }

    /**
     * Rebuilds the placeholder lot a client holds from the four numbers the network
     * form carries (including grade, legacy preparation).
     *
     * <p>The result is not a real lot: it has {@link #CLIENT_VIEW_LOT_ID} and every
     * wafer is at the lot layer with {@link #CLIENT_VIEW_ROLL}. Only
     * {@link FoupContents#publicView()} of it is meaningful.</p>
     *
     * <p>Out-of-range values from a malformed or hostile buffer are clamped instead of
     * rejected: this is a display value and must never cost a player their
     * connection. An invalid grade string is clamped to ORDINARY.</p>
     *
     * @param capacity  transmitted capacity
     * @param waferCount transmitted wafer count
     * @param lotLayer  transmitted lot layer
     * @param gradeId   transmitted grade ID
     * @return the placeholder lot
     */
    public static FoupContents fromPublicView(int capacity, int waferCount, int lotLayer, String gradeId) {
        return fromPublicView(capacity, waferCount, lotLayer, gradeId, FoupContents.PREPARATION_LEGACY);
    }

    /**
     * The display clamp of {@link #fromPublicView(int, int, int, String, int)}: a
     * preparation generation of {@link FoupContents#PREPARATION_G2} survives a
     * network round trip only for a non-empty pure-or-better grade, which is the
     * same rule the {@link FoupContents} record itself enforces. Repairing this
     * with any other value would build a lot the record refuses.
     */
    private static int clampedPreparation(int preparationGeneration, int waferCount, WaferSubstrate grade) {
        if (waferCount == 0 || grade.generation() < WaferSubstrate.PURE.generation()) {
            return FoupContents.PREPARATION_LEGACY;
        }
        return (preparationGeneration >= FoupContents.PREPARATION_LEGACY
            && preparationGeneration <= FoupContents.PREPARATION_G2)
            ? preparationGeneration : FoupContents.PREPARATION_LEGACY;
    }

    /**
     * Rebuilds the placeholder lot a client holds from the five numbers the network
     * form carries (including grade and preparation generation).
     *
     * <p>The result is not a real lot: it has {@link #CLIENT_VIEW_LOT_ID} and every
     * wafer is at the lot layer with {@link #CLIENT_VIEW_ROLL}. Only
     * {@link FoupContents#publicView()} of it is meaningful.</p>
     *
     * <p>Out-of-range values from a malformed or hostile buffer are clamped instead of
     * rejected: this is a display value and must never cost a player their
     * connection. An invalid grade string is clamped to ORDINARY. A preparation
     * generation is clamped to 1 (legacy) unless a non-empty lot carries a
     * pure-or-better grade, in which case 2 (G2) survives the round trip —
     * electronic-grade G2 lots used to be degraded to G1 here.</p>
     *
     * @param capacity              transmitted capacity
     * @param waferCount            transmitted wafer count
     * @param lotLayer              transmitted lot layer
     * @param gradeId               transmitted grade ID
     * @param preparationGeneration transmitted preparation generation
     * @return the placeholder lot
     */
    static FoupContents fromPublicView(int capacity, int waferCount, int lotLayer, String gradeId, int preparationGeneration) {
        int safeCapacity = Math.max(1, capacity);
        int safeCount = Math.min(Math.max(0, waferCount), safeCapacity);
        int safeLayer = Math.max(Wafer.UNPROCESSED_LAYER, lotLayer);
        WaferSubstrate safeGrade = WaferSubstrate.fromIdOrDefault(gradeId);
        int safePreparation = clampedPreparation(preparationGeneration, safeCount, safeGrade);

        List<Wafer> placeholders = new ArrayList<>(safeCount);
        for (int i = 0; i < safeCount; i++) {
            placeholders.add(new Wafer(safeLayer, CLIENT_VIEW_ROLL, Wafer.NO_FLAGS));
        }
        return new FoupContents(CLIENT_VIEW_LOT_ID, safeCapacity, placeholders, null, safeGrade, safePreparation);
    }

    /**
     * Rebuilds the server lot an unstarted filled client view stands for, or
     * {@code null} when the view is not safe to rebuild.
     *
     * <p>This is the bounded server-authoritative repair for the Creative inventory
     * round trip: a Creative player who moves a filled FOUP inside their inventory
     * makes the client echo the network form back, and the server slot is
     * overwritten with a {@link #fromPublicView(int, int, int, String, int)}
     * placeholder. {@link FoupItems#trustedContentsOf(net.minecraft.world.item.ItemStack)}
     * then discards it, so the lot loses its wafers completely.</p>
     *
     * <p>For an <em>unstarted</em> view the hidden values the network form does not
     * carry can be recreated exactly, without trusting anything the client
     * invented: a wafer at layer 0 of a lot with no run spec has never been rolled,
     * so its true cumulative roll is {@link Wafer#NO_ROLLS} and its true flags are
     * {@link Wafer#NO_FLAGS}. The rebuilt lot gets a fresh server lot id, the
     * transmitted capacity, the transmitted grade and preparation generation (which
     * the network form is allowed to know and which has already been validated by
     * the {@link FoupContents} record) and exactly {@link Wafer#unprocessed()}
     * wafers.</p>
     *
     * <p>Everything a fabricated client could turn into value is refused with
     * {@code null}: a view with a run spec, an empty view, a view holding wafers at
     * any layer above 0 (processed wafers are never trusted), a view with any other
     * roll or flag value, a capacity smaller than the wafer count, and any lot the
     * {@link FoupContents} record would refuse (for example a hostile G2 generation
     * on a lesser grade). The server lot is left untouched in those cases and keeps
     * the behaviour of
     * {@link FoupItems#trustedContentsOf(net.minecraft.world.item.ItemStack)}.</p>
     *
     * @param clientView the client view to rebuild, not null
     * @return the rebuilt server lot, or {@code null} when the view must not be trusted
     */
    static FoupContents unstartedServerLot(FoupContents clientView) {
        if (!isClientView(clientView) || clientView.run() != null || clientView.isEmpty()
            || clientView.size() > clientView.capacity()) {
            return null;
        }
        List<Wafer> rebuilt = new ArrayList<>(clientView.size());
        for (Wafer wafer : clientView.wafers()) {
            if (wafer.layerIndex() != Wafer.UNPROCESSED_LAYER
                || wafer.cumulativeRoll() != CLIENT_VIEW_ROLL
                || wafer.flags() != Wafer.NO_FLAGS) {
                return null;
            }
            rebuilt.add(Wafer.unprocessed());
        }
        try {
            return new FoupContents(UUID.randomUUID(), clientView.capacity(), rebuilt, null,
                clientView.grade(), clientView.preparationGeneration());
        } catch (RuntimeException invalid) {
            return null;
        }
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

    /**
     * Writes the exact {@link #CODEC} state of a real server lot behind the already
     * written {@link #FLAG_FULL} byte: a bounded var-int length followed by the unnamed
     * NBT form of the component. The encoding is sized first in a scratch buffer so an
     * oversized lot fails loudly before any length is claimed on the wire, and so
     * decoding can reject hostile length claims without allocating.
     *
     * @param buffer   the buffer under the current packet encode context, not null
     * @param contents a real server lot, not a nil client view, not null
     */
    private static void writeFullPayload(ByteBuf buffer, FoupContents contents) {
        DataResult<Tag> encoded = CODEC.encodeStart(NbtOps.INSTANCE, contents);
        if (!encoded.result().isPresent()) {
            throw new IllegalStateException("Full FOUP payload failed to encode: " + encoded.error());
        }
        ByteBuf scratch = Unpooled.buffer(256, MAX_FULL_PAYLOAD_BYTES);
        try {
            DataOutputStream out = new DataOutputStream(new ByteBufOutputStream(scratch));
            try {
                NbtIo.writeUnnamedTag(encoded.result().get(), out);
            } finally {
                out.close();
            }
            int length = scratch.readableBytes();
            if (length > MAX_FULL_PAYLOAD_BYTES) {
                throw new IllegalStateException(
                    "Full FOUP payload is " + length + " bytes, past the " + MAX_FULL_PAYLOAD_BYTES + " byte bound");
            }
            ByteBufCodecs.VAR_INT.encode(buffer, length);
            buffer.writeBytes(scratch);
        } catch (java.io.IOException broken) {
            // A plain in-memory write cannot fail for IO reasons; keep the failure typed anyway.
            throw new IllegalStateException("Full FOUP payload could not be serialized", broken);
        } finally {
            scratch.release();
        }
    }

    /**
     * Reads back the exact {@link #CODEC} state from the bounded slice a full payload
     * claimed on the wire. The slice length was already validated; the NBT accounter is
     * additionally bounded by the same cap.
     *
     * @param slice the payload bytes, exactly as many as the length prefix claimed
     * @return the exact lot, never a client view
     */
    private static FoupContents readFullPayload(ByteBuf slice) throws IOException {
        try {
            Tag tag;
            DataInputStream in = new DataInputStream(new ByteBufInputStream(slice));
            try {
                tag = NbtIo.readUnnamedTag(in, NbtAccounter.create(MAX_FULL_PAYLOAD_BYTES));
            } finally {
                in.close();
            }
            if (!(tag instanceof CompoundTag payload)) {
                throw new DecoderException("The FOUP full payload is not a compound tag");
            }
            DataResult<FoupContents> parsed = CODEC.parse(NbtOps.INSTANCE, payload);
            if (!parsed.result().isPresent()) {
                throw new DecoderException(
                    "The FOUP full payload failed to decode: " + parsed.error().orElse(null));
            }
            if (slice.isReadable()) throw new DecoderException("Trailing FOUP payload bytes");
            return parsed.result().get();
        } finally {
            // readSlice borrows the parent buffer; its owner releases it.
        }
    }
}
