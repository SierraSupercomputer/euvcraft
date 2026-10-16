package com.euvcraft.lithmod.mc.data;

import com.euvcraft.lithmod.core.DieRule;
import com.euvcraft.lithmod.core.DieRules;
import com.euvcraft.lithmod.core.TierRegistry;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads the die type rules from data/lithmod/die_rules/.
 */
public class DieRulesDataLoader extends JsonResourceDataLoader<List<DieRule>, DieRules> {

    private static final Logger LOGGER = EUVCRAFTMod.LOGGER;
    private static final String DIRECTORY = "die_rules";

    public static final Codec<List<DieRule>> RULES_CODEC = DieRuleCodec.LIST_CODEC;

    private static DieRules currentRules;

    public DieRulesDataLoader() {
        super(DIRECTORY, RULES_CODEC, fromList((values, source) -> {
            TierRegistry tiers = TierDataLoader.getRegistry();
            if (tiers == null) {
                tiers = new TierDataLoader().fallback();
            }
            if (values == null || values.isEmpty()) {
                throw new IllegalArgumentException("no die rule data found");
            }
            List<DieRule> merged = new ArrayList<>();
            for (List<DieRule> value : values) {
                if (value != null) {
                    merged.addAll(value);
                }
            }
            return DieRules.build(tiers, merged, source);
        }), LOGGER);
    }

    @SubscribeEvent
    public static void onAddServerReloadListeners(AddServerReloadListenersEvent event) {
        LOGGER.debug("Registering DieRulesDataLoader for server resource reloads");
        event.addListener(Identifier.fromNamespaceAndPath("lithmod", DIRECTORY), new DieRulesDataLoader());
    }

    @Override
    protected void apply(Map<Identifier, List<DieRule>> prepared,
                        ResourceManager resourceManager,
                        ProfilerFiller profiler) {
        super.apply(prepared, resourceManager, profiler);
        DieRules next = getCurrent();
        if (next != null) {
            currentRules = next;
            LOGGER.info("Loaded die rules: {}", currentRules);
            LOGGER.info("Die availability by tier:\n{}", currentRules.availabilityTable());
        }
    }

    @Override
    protected DieRules fallback() {
        TierRegistry tiers = TierDataLoader.getRegistry();
        if (tiers == null) {
            tiers = new TierDataLoader().fallback();
        }
        String resource = "/data/lithmod/" + DIRECTORY + "/rules.json";
        try (InputStream input = DieRulesDataLoader.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing bundled die rule file: " + resource);
            }
            com.google.gson.JsonElement json = com.google.gson.JsonParser.parseReader(
                new InputStreamReader(input, StandardCharsets.UTF_8));
            List<DieRule> parsed = RULES_CODEC.parse(
                com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow();
            return DieRules.build(tiers, parsed, "bundled default die rules");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to read bundled die rule file: " + resource, e);
        }
    }

    public static DieRules getRules() {
        return currentRules;
    }
}
