package com.euvcraft.lithmod.mc.compat.mixin;

import com.euvcraft.lithmod.mc.network.FoupNetworkContext;
import com.llamalad7.mixinextras.injector.WrapMethod;
import com.llamalad7.mixinextras.sugar.Operation;

import org.spongepowered.asm.mixin.Mixin;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

import net.minecraft.network.PacketEncoder;
import net.minecraft.network.protocol.Packet;

/**
 * Wraps vanilla {@code PacketEncoder.encode} exactly once per outbound packet so the
 * FOUP stream codec can consult {@link FoupNetworkContext#sideOf(ChannelHandlerContext)}
 * while it writes its payload bytes. The scope is restored in {@code finally}: the
 * vanilla wrap inside {@code encode} (skip classes, protocol swaps) sees the narrowed
 * scope and an exception never leaves a FULL grant behind.
 *
 * <p>The remote and local pipelines both run this encoder, and
 * {@code LocalFrameEncoder} only frames already-encoded bytes, so no separate local
 * hook exists. This class must never import {@code net.minecraft.client} so a
 * dedicated server loads it cleanly.</p>
 */
@Mixin(PacketEncoder.class)
public abstract class FoupPacketEncoderMixin {

    @WrapMethod(method = "encode(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;Lio/netty/buffer/ByteBuf;)V", require = 1)
    private void lithmod$scopedEncode(ChannelHandlerContext ctx, Packet<?> packet, ByteBuf output,
        Operation<Void> original) throws Exception {
        FoupNetworkContext.Payload pushed = FoupNetworkContext.sideOf(ctx);
        FoupNetworkContext.push(pushed);
        try {
            original.call(ctx, packet, output);
        } finally {
            FoupNetworkContext.pop(pushed);
        }
    }
}
