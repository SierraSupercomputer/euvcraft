package com.euvcraft.lithmod.mc.data;

import com.euvcraft.lithmod.core.MachineDefinition;
import com.euvcraft.lithmod.core.Tier;
import com.euvcraft.lithmod.core.TierRegistry;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.GameTestFixtures;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads {@code data/lithmod/machines/*.json} into a {@link Registry} on every server
 * resource reload.
 *
 * <p>Extends the project's own {@link JsonResourceDataLoader}, which already keeps the last
 * valid registry when a reload fails. On top of that this loader cross-checks every
 * {@code per_tier_energy_per_tick} key against {@link TierRegistry}, because a tier id is
 * data rather than a hard-coded string, and that check needs the tier registry to be loaded.</p>
 */
public final class MachineDefinitionLoader
    extends JsonResourceDataLoader<MachineDefinition, MachineDefinitionLoader.Registry> {

    public MachineDefinitionLoader() {
        super("machines", MachineDefinitionCodec.CODEC,
            MachineDefinitionLoader::runtimeBuild, EUVCRAFTMod.LOGGER);
        CURRENT = this;
    }

    /**
     * Runtime publication path: filters the test-machine fixture definition out before
     * normal validation when fixtures are off, then builds as usual.
     *
     * <p>The filter matches the definition's real payload id ({@code definition.id()},
     * not the file name) and is additive with the same filter in
     * {@link MachineRecipeLoader}, so a datapack reload cannot reintroduce the fixture.
     * The public pure builders {@link #build(List, String)} and {@link #buildFromFiles}
     * stay unfiltered for unit tests.</p>
     */
    private static Registry runtimeBuild(Map<Identifier, MachineDefinition> entries, String source) {
        if (GameTestFixtures.enabled()) {
            return buildFromFiles(entries, source);
        }
        Map<Identifier, MachineDefinition> filtered = new LinkedHashMap<>();
        entries.forEach((fileId, definition) -> {
            if (!isFixtureDefinition(definition)) {
                filtered.put(fileId, definition);
            }
        });
        return buildFromFiles(filtered, source);
    }

    /** The test-machine fixture machine id (payload id, not a file-name pattern). */
    static boolean isFixtureDefinition(MachineDefinition definition) {
        return "test_machine".equals(definition.id());
    }

    @SubscribeEvent
    public static void onAddServerReloadListeners(AddServerReloadListenersEvent event) {
        EUVCRAFTMod.LOGGER.debug("Registering MachineDefinitionLoader for server resource reloads");
        event.addListener(Identifier.fromNamespaceAndPath("lithmod", "machines"), new MachineDefinitionLoader());
    }

    /**
     * The currently loaded registry, or {@code null} before the first successful load.
     *
     * <p>The base loader is what decides whether a failed reload keeps the previous value,
     * so this is a plain read of that field.</p>
     */
    public static Registry getRegistry() {
        MachineDefinitionLoader loader = CURRENT;
        return loader == null ? null : loader.getCurrent();
    }

    /** The single live instance the event bus registered. */
    private static volatile MachineDefinitionLoader CURRENT;

    /** Public entry point for tests: build a registry from already-parsed definitions. */
    public static Registry build(List<MachineDefinition> definitions, String sourceDescription) {
        Map<Identifier, MachineDefinition> entries = new LinkedHashMap<>();
        int index = 0;
        for (MachineDefinition definition : definitions) {
            entries.put(Identifier.fromNamespaceAndPath("lithmod", definition.id()), definition);
            index++;
        }
        return buildFromFiles(entries, sourceDescription);
    }

    /**
     * Validates each definition, naming the file it came from.
     *
     * <p>Throwing out of here is the loud path: the base class catches it, logs the message
     * and keeps the previously loaded registry.</p>
     */
    static Registry buildFromFiles(Map<Identifier, MachineDefinition> entries, String source) {
        Map<String, MachineDefinition> byId = new LinkedHashMap<>();

        entries.forEach((fileId, definition) -> {
            MachineDefinition previous = byId.put(definition.id(), definition);
            if (previous != null) {
                throw new IllegalArgumentException(source + "machine id '" + definition.id()
                    + "' is declared twice (this file: " + fileId + ")");
            }
        });

        TierRegistry tiers = TierDataLoader.getRegistry();
        if (tiers != null) {
            List<String> knownTierIds = tiers.all().stream().map(Tier::id).toList();
            entries.forEach((fileId, definition) -> {
                for (String tierId : definition.perTierEnergyPerTick().keySet()) {
                    if (tiers.byId(tierId).isEmpty()) {
                        throw new IllegalArgumentException(fileId + ": machine '" + definition.id()
                            + "' names unknown tier '" + tierId + "' in per_tier_energy_per_tick"
                            + " (known tiers: " + knownTierIds + ")");
                    }
                }
            });
        }

        return new Registry(byId);
    }

    /** Immutable registry of machine definitions, keyed by machine id. */
    public static final class Registry {

        private final Map<String, MachineDefinition> byId;

        private Registry(Map<String, MachineDefinition> byId) {
            this.byId = Map.copyOf(byId);
        }

        /** The definition for {@code machineId}, or empty when none is loaded. */
        public Optional<MachineDefinition> byId(String machineId) {
            return Optional.ofNullable(byId.get(machineId));
        }

        /** The definition for {@code machineId}, throwing when none is loaded. */
        public MachineDefinition getById(String machineId) {
            return byId(machineId).orElseThrow(
                () -> new IllegalArgumentException("no machine definition with id '" + machineId + "'"));
        }

        public boolean has(String machineId) {
            return byId.containsKey(machineId);
        }

        public List<MachineDefinition> all() {
            return new ArrayList<>(byId.values());
        }

        public int size() {
            return byId.size();
        }

        @Override
        public String toString() {
            return "MachineDefinitionRegistry" + byId.keySet();
        }
    }
}
