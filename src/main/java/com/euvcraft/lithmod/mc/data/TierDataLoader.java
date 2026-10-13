package com.euvcraft.lithmod.mc.data;

import com.euvcraft.lithmod.core.Tier;
import com.euvcraft.lithmod.core.TierRegistry;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.mojang.serialization.Codec;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Loads tier data (data/lithmod/tiers/*.json) through TierCodec and exposes a
 * TierRegistry that is rebuilt on every server resource reload.
 */
public final class TierDataLoader extends SimpleJsonResourceReloadListener<Tier> {

    private static final Logger LOGGER = EUVCRAFTMod.LOGGER;
    private static final FileToIdConverter LISTER = FileToIdConverter.json("tiers");

    private static TierRegistry currentRegistry;

    public TierDataLoader() {
        super(TierCodec.CODEC, LISTER);
    }

    @SubscribeEvent
    public static void onAddServerReloadListeners(AddServerReloadListenersEvent event) {
        LOGGER.debug("Registering TierDataLoader for server resource reloads");
        event.addListener(Identifier.fromNamespaceAndPath("lithmod", "tiers"), new TierDataLoader());
    }

    @Override
    protected CompletableFuture<Void> apply(Map<Identifier, Tier> prepared, ResourceManager resourceManager,
            Executor executor) {
        List<Tier> allTiers = prepared.values().stream().toList();

        if (allTiers.isEmpty()) {
            LOGGER.warn("No tier data loaded from data/lithmod/tiers/, keeping previous registry");
            return CompletableFuture.completedFuture(null);
        }

        try {
            TierRegistry newRegistry = TierRegistry.build(allTiers, "data/lithmod/tiers/");
            currentRegistry = newRegistry;
            LOGGER.info("Loaded {} tiers: {}", newRegistry.all().size(),
                newRegistry.all().stream().map(Tier::id).toList());
        } catch (IllegalArgumentException e) {
            LOGGER.error("Failed to build tier registry: {}", e.getMessage());
        }

        return CompletableFuture.completedFuture(null);
    }

    public static TierRegistry getRegistry() {
        return currentRegistry;
    }
}