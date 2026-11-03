package com.euvcraft.lithmod.mc.viewer;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ViewerPayload(String json) implements CustomPacketPayload {
    public static final Type<ViewerPayload> TYPE=new Type<>(Identifier.parse("lithmod:viewer_catalog"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ViewerPayload> CODEC=StreamCodec.of((buffer,payload)->buffer.writeUtf(payload.json(),ViewerCatalogCodec.MAX_CHARS),buffer->new ViewerPayload(buffer.readUtf(ViewerCatalogCodec.MAX_CHARS)));
    public ViewerPayload {if(json==null||json.length()>ViewerCatalogCodec.MAX_CHARS)throw new IllegalArgumentException("Viewer catalog too large");}
    @Override public Type<ViewerPayload> type(){return TYPE;}
}
