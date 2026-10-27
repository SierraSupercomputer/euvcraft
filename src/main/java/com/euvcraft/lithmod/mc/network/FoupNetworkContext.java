package com.euvcraft.lithmod.mc.network;

import java.util.ArrayDeque;
import java.util.Deque;

import io.netty.channel.ChannelHandlerContext;

import net.minecraft.network.Connection;
import net.minecraft.network.PacketFlow;
import net.minecraft.network.protocol.PacketListener;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * The thread-local switch that decides whether {@code FoupContentsCodec.STREAM_CODEC}
 * may carry the full private FOUP state (rolls, flags, run spec, room exposure) across
 * the network in this packet, and what a decode should do with such a payload.
 *
 * <p>Both the local and the remote {@code Connection} paths serialize packets through
 * vanilla {@code PacketEncoder.encode(ChannelHandlerContext,Packet,ByteBuf)} and
 * {@code PacketDecoder.decode(ChannelHandlerContext,ByteBuf,List)}; the two FOUP
 * mixins wrap those methods and put an {@link Payload} decision on the stack for the
 * duration of the wrapped call. LocalFrameEncoder only wraps already-encoded bytes,
 * so the same hooks cover an integrated single-player server without a second
 * injection point.</p>
 *
 * <p>Side decision, for one pipeline context:</p>
 *
 * <ul>
 *   <li>The connection receives {@code SERVERBOUND} = it is a server connection. Full
 *       payloads are only allowed when the packet listener is the play listener of a
 *       player with {@code hasInfiniteMaterials()} (Creative or operator-infrastructure).
 *       The permission is checked against the {@code Connection}'s own authoritative
 *       player object — never against flags inside the incoming payload.</li>
 *   <li>Otherwise the receiving flow is {@code CLIENTBOUND} = it is a client connection.
 *       Full payloads coming from the server are decoded (the server already decided,
 *       per receiving player, whether to send them). This class must stay free of
 *       {@code net.minecraft.client} imports so a dedicated server can load it.</li>
 *   <li>No {@link Connection} in the pipeline, or an unknown listener, defaults to
 *       {@link Payload#PUBLIC}: fail-closed.</li>
 * </ul>
 *
 * <p>Scopes nest; the earlier scope is restored even when the wrapped call throws
 * (the mixin uses try/finally). Calls outside any scope default to
 * {@link Payload#PUBLIC}, so ordinary persistence, containers and components (which
 * never encode through {@code PacketEncoder}) stay public.</p>
 */
public final class FoupNetworkContext {

    /** May this packet carry the full private FOUP payload? */
    public enum Payload {
        /** Public five-field projection only; a decode consumes and ignores private bytes. */
        PUBLIC,
        /** Full payloads are encoded and decoded exactly. */
        FULL
    }

    private static final ThreadLocal<Deque<Payload>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

    private FoupNetworkContext() {
    }

    /**
     * Returns the payload mode with the most narrow interpretation for the thread.
     * Nothing on the stack counts as {@link Payload#PUBLIC}.
     *
     * @return the current payload mode, never null
     */
    public static Payload current() {
        Deque<Payload> stack = STACK.get();
        return stack.isEmpty() ? Payload.PUBLIC : stack.peek();
    }

    /**
     * Runs the action with the given payload mode on the stack, restoring the
     * previous stack scope even when the action throws.
     *
     * <p>This is the API unit tests and future callers use; the mixins pass in the
     * decision from {@link #sideOf(ChannelHandlerContext)}. Scopes nest: the scope
     * that is current when the action starts is restored afterwards, scoping one
     * wrapped encode/decode call exactly.</p>
     *
     * @param mode   the payload mode to enforce during the action, not null
     * @param action the action to run, not null
     */
    public static void section(Payload mode, Runnable action) {
        push(mode);
        try {
            action.run();
        } finally {
            pop(mode);
        }
    }

    /**
     * Pushes a scope scope marker; pairs with {@link #pop(Payload)}.
     *
     * <p>Only the FOUP mixins and tests should need this; prefer {@link #section(Payload, Runnable)}.</p>
     *
     * @param mode the payload mode to enforce until the matching {@link #pop(Payload)}, not null
     */
    public static void push(Payload mode) {
        STACK.get().push(java.util.Objects.requireNonNull(mode, "mode"));
    }

    /**
     * Pops the scope pushed by {@link #push(Payload)} and fails loudly on an
     * unbalanced scope, which would otherwise leak a FULL grant to later threads'
     * work.
     *
     * @param expected the scope that must currently be on the stack, not null
     */
    public static void pop(Payload expected) {
        Deque<Payload> stack = STACK.get();
        Payload popped = stack.isEmpty() ? null : stack.pop();
        if (popped != expected) {
            throw new IllegalStateException(
                "Unbalanced FOUP network scope: expected " + expected + " on the stack, got " + popped);
        }
        if (stack.isEmpty()) {
            STACK.remove();
        }
    }

    /**
     * Decides the payload mode for one wrapped vanilla packet codec call from its
     * pipeline context; see the class documentation for the rules.
     *
     * <p>Exposed to the mixins so they stay thin, and package-private to tests so
     * the decision can be exercised without a running channel.</p>
     *
     * @param ctx the pipeline context of the wrapped call, not null
     * @return the payload mode, never null, {@link Payload#PUBLIC} outside of known context
     */
    static Payload sideOf(ChannelHandlerContext ctx) {
        Connection connection = ctx.pipeline().get(Connection.class);
        if (connection == null) {
            return Payload.PUBLIC;
        }
        PacketFlow receiving = connection.getReceiving();
        if (receiving == PacketFlow.SERVERBOUND) {
            PacketListener listener = connection.getPacketListener();
            if (listener instanceof ServerGamePacketListenerImpl play
                && play.player.hasInfiniteMaterials()) {
                return Payload.FULL;
            }
            return Payload.PUBLIC;
        }
        return Payload.FULL;
    }
}
