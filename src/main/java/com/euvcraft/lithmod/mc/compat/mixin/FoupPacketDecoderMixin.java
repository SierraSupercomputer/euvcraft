package com.euvcraft.lithmod.mc.compat.mixin;

import java.util.List;

import com.euvcraft.lithmod.mc.network.FoupNetworkContext;
import com.llamalad7.mixinextras.injector.WrapMethod;
import com.llamalad7.mixinextras.sugar.Operation;

import org.spongepowered.asm.mixin.Mixin;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

import net.minecraft.network.PacketDecoder;

/**
 * Wraps vanilla {@code PacketDecoder.decode} exactly once per inbound packet so the
 * FOUP stream codec can consult {@link FoupNetworkContext#sideOf(ChannelHandlerContext)}
 * while it parses its payload bytes. The scope is restored in {@code finally}: an
 * exception never leaves a FULL grant behind, and nested scopes unwind correctly.
 *
 * <p>Local integrated-server connections pass through the same {@code PacketDecoder}
 * as remote ones, so a single mixin covers both; no second client-side hook
 * exists, and this class must never import {@code net.minecraft.client} so a
 * dedicated server loads it cleanly.</p>
 */
@Mixin(PacketDecoder.class)
public abstract class FoupPacketDecoderMixin {

    @WrapMethod(method = "decode(Lio/netty/channel/ChannelHandlerContext;Lio/netty/buffer/ByteBuf;Ljava/util/List;)V", require = 1)
    private void lithmod$scopedDecode(ChannelHandlerContext ctx, ByteBuf input, List<Object> out,
        Operation<Void> original) throws Exception {
        FoupNetworkContext.Payload pushed = FoupNetworkContext.sideOf(ctx);
        FoupNetworkContext.push(pushed);
        try {
            original.call(ctx, input, out);
        } finally {
            FoupNetworkContext.pop(pushed);
        }
    }
}
