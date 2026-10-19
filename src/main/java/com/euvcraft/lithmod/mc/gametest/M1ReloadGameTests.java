package com.euvcraft.lithmod.mc.gametest;

import com.euvcraft.lithmod.core.MachineDefinition;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.FabConfig;
import com.euvcraft.lithmod.mc.FabSettings;
import com.euvcraft.lithmod.mc.data.MachineDefinitionLoader;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.machine.silicon.CrystalPullerBlockEntity;
import com.euvcraft.lithmod.mc.machine.silicon.SiliconMachines;
import com.euvcraft.lithmod.mc.registry.ModItems;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.SharedConstants;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * M1-12 step 6: real machine-definition datapack reload proof.
 *
 * <p>Reads the bundled {@code data/lithmod/machines/crystal_puller.json} through
 * ResourceManager, establishes baseline energy cost, then installs a unique test
 * datapack that overrides only {@code energy_per_tick} to a distinct positive value
 * (baseline + 7). Uses {@code server.reloadResources(enabled).join()} exactly as
 * {@link PresentationGameTests#datapackOverride} does. Asserts that the live
 * ResourceManager reports the override pack as the source, that
 * {@link MachineDefinitionLoader} reflects the new cost, and that a newly placed
 * CrystalPuller draws the overridden cost on a real paid working tick with actual
 * silicon input. Restores the original datapack selection and config in a nested
 * {@code finally} block before success.</p>
 */
public final class M1ReloadGameTests {

    private static final String ENVIRONMENT_ID = "m1_reload";

    private static final String MACHINE_DEFINITION_RELOAD = "m1_gate_machine_definition_reload";

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(Registries.TEST_FUNCTION, EUVCRAFTMod.MODID);

    private static final BlockPos MACHINE_POS = new BlockPos(1, 1, 1);
    private static final BlockPos MACHINE_POS_2 = new BlockPos(3, 1, 1);

    static {
        FUNCTIONS.register(MACHINE_DEFINITION_RELOAD, () -> M1ReloadGameTests::machineDefinitionReload);
    }

    private M1ReloadGameTests() {
    }

    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener((Consumer<RegisterGameTestsEvent>) M1ReloadGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
            Identifier.fromNamespaceAndPath(EUVCRAFTMod.MODID, ENVIRONMENT_ID),
            new TestEnvironmentDefinition[0]);
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
            environment, Level.OVERWORLD, Identifier.withDefaultNamespace("empty"),
            300, 0, true, Rotation.NONE, false, 1, 1, false, 0);

        register(event, data, MACHINE_DEFINITION_RELOAD);
        EUVCRAFTMod.LOGGER.info("Registered EUVCRAFT M1 reload game tests");
    }

    private static void register(RegisterGameTestsEvent event,
            TestData<Holder<TestEnvironmentDefinition<?>>> data, String name) {
        Identifier id = Identifier.fromNamespaceAndPath(EUVCRAFTMod.MODID, name);
        ResourceKey<Consumer<GameTestHelper>> function = ResourceKey.create(Registries.TEST_FUNCTION, id);
        event.registerTest(id, new FunctionGameTestInstance(function, data));
    }

    /**
     * Real machine-definition datapack reload test.
     *
     * <ol>
     *   <li>Reads the bundled {@code crystal_puller.json} and asserts baseline {@code energy_per_tick} > 0.</li>
     *   <li>Places a fresh CrystalPuller, feeds silicon, runs one paid tick, measures actual FE debit.</li>
     *   <li>Creates a unique test datapack overriding only {@code energy_per_tick} to baseline + 7.</li>
     *   <li>Enables the pack via {@code server.reloadResources(enabled).join()}.</li>
     *   <li>Asserts ResourceManager sourcePackId is the override pack.</li>
     *   <li>Asserts MachineDefinitionLoader registry has the modified cost.</li>
     *   <li>Places a NEW CrystalPuller (AIR first), feeds silicon, runs one paid tick, asserts new cost is used.</li>
     *   <li>Restores original datapack selection and config in nested finally; asserts baseline restored.</li>
     * </ol>
     */
    private static void machineDefinitionReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var server = level.getServer();
        var repo = server.getPackRepository();
        var resourceManager = server.getResourceManager();

        // ---------- Snapshot original datapack selection ----------
        List<String> originalSelected = new ArrayList<>(repo.getSelectedIds());

        // ---------- Snapshot and temporarily set FE multiplier to 1.0 ----------
        double originalFeMultiplier = FabConfig.FE_POWER_MULTIPLIER.get();
        try {
            FabConfig.FE_POWER_MULTIPLIER.set(1.0);
            EUVCRAFTMod.config().onConfigReload();

            // ---------- Step 1: Read bundled crystal_puller.json and get baseline ----------
            Identifier machineResourceId = Identifier.fromNamespaceAndPath("lithmod", "machines/crystal_puller.json");
            Resource baseResource = resourceManager.getResource(machineResourceId).orElseThrow(
                () -> new IllegalStateException("Base machine definition resource not found: " + machineResourceId));
            JsonObject baseJson = parseResource(baseResource);

            int baselineEnergyPerTick = baseJson.get("energy_per_tick").getAsInt();
            helper.assertTrue(baselineEnergyPerTick > 0,
                "Baseline energy_per_tick must be positive, got " + baselineEnergyPerTick);

            // Verify the loaded registry reflects the baseline
            Optional<MachineDefinition> baselineDef = MachineDefinitionLoader.getRegistry().byId("crystal_puller");
            helper.assertTrue(baselineDef.isPresent(), "MachineDefinitionLoader must have crystal_puller definition");
            helper.assertValueEqual(baselineEnergyPerTick, baselineDef.get().energyPerTick(),
                "Loaded definition energy_per_tick must match resource");

            // ---------- Step 2: Baseline run - fresh CrystalPuller spends baseline FE ----------
            int baselineCostPerTick = runAndMeasureCost(helper, level, MACHINE_POS, baselineEnergyPerTick);
            helper.assertValueEqual(baselineEnergyPerTick, baselineCostPerTick,
                "Baseline run must spend exactly baseline energy_per_tick per tick");

            // ---------- Step 3: Create override datapack ----------
            int modifiedEnergyPerTick = baselineEnergyPerTick + 7; // distinct positive value
            String packName = "euvcraft_m1_reload_probe_" + UUID.randomUUID();
            Path worldDataDir = server.getWorldPath(LevelResource.DATAPACK_DIR).toAbsolutePath().normalize();
            Path packRoot = worldDataDir.resolve(packName).normalize();

            if (!packRoot.startsWith(worldDataDir) || packRoot.equals(worldDataDir)) {
                throw new IllegalStateException("Pack root escapes datapack directory");
            }

            try {
                Files.createDirectories(packRoot.resolve("data/lithmod/machines"));

                // Write pack.mcmeta
                int format = SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA);
                PackMetadataSection metadata = new PackMetadataSection(
                    net.minecraft.network.chat.Component.literal("EUVCRAFT M1 machine definition reload test"),
                    new net.minecraft.util.InclusiveRange<>(format));
                JsonObject root = new JsonObject();
                root.add("pack", PackMetadataSection.SERVER_TYPE.codec().encodeStart(JsonOps.INSTANCE, metadata).getOrThrow());
                Files.writeString(packRoot.resolve("pack.mcmeta"), root.toString(), StandardCharsets.UTF_8);

                // Write modified crystal_puller.json (only energy_per_tick changed)
                JsonObject modifiedJson = baseJson.deepCopy();
                modifiedJson.addProperty("energy_per_tick", modifiedEnergyPerTick);
                Files.writeString(
                    packRoot.resolve("data/lithmod/machines/crystal_puller.json"),
                    modifiedJson.toString(),
                    StandardCharsets.UTF_8);

                // ---------- Step 4: Enable pack and reload ----------
                repo.reload();
                String newPackId = "file/" + packName;
                helper.assertTrue(repo.isAvailable(newPackId), "Probe pack not discoverable: " + newPackId);

                List<String> enabled = new ArrayList<>(originalSelected);
                enabled.add(newPackId);
                server.reloadResources(enabled).join();

                // ---------- Step 5: Assert ResourceManager sourcePackId is the override pack ----------
                Resource overriddenResource = resourceManager.getResource(machineResourceId).orElseThrow(
                    () -> new IllegalStateException("Overridden machine definition resource not found after reload"));
                helper.assertTrue(overriddenResource.sourcePackId().equals(newPackId),
                    "Override pack did not win priority for " + machineResourceId + "; got " + overriddenResource.sourcePackId());

                // ---------- Step 6: Assert MachineDefinitionLoader has modified cost ----------
                Optional<MachineDefinition> modifiedDef = MachineDefinitionLoader.getRegistry().byId("crystal_puller");
                helper.assertTrue(modifiedDef.isPresent(), "MachineDefinitionLoader must have crystal_puller definition after reload");
                int loadedModifiedCost = modifiedDef.get().energyPerTick(null, EUVCRAFTMod.settings());
                helper.assertValueEqual(modifiedEnergyPerTick, loadedModifiedCost,
                    "Loaded definition energy_per_tick must match override value");

                // ---------- Step 7: Fresh CrystalPuller with modified cost ----------
                // Must use a NEW block entity (AIR first) to avoid adopting in-flight recipe
                helper.setBlock(MACHINE_POS_2, Blocks.AIR);
                helper.setBlock(MACHINE_POS_2, SiliconMachines.CRYSTAL_PULLER.get());
                CrystalPullerBlockEntity puller = (CrystalPullerBlockEntity) level.getBlockEntity(helper.absolutePos(MACHINE_POS_2));
                helper.assertTrue(puller != null, "CrystalPuller block entity must exist after reload");

                // Refresh definition to pick up new cost
                puller.refreshDefinition();
                int actualCostAfterReload = puller.runner().energyPerTick();
                helper.assertValueEqual(modifiedEnergyPerTick, actualCostAfterReload,
                    "Runner must see modified energy_per_tick after reload");

                // Run one paid tick with silicon input and measure actual debit
                int modifiedCostPerTick = runAndMeasureCost(helper, level, MACHINE_POS_2, modifiedEnergyPerTick);
                helper.assertValueEqual(modifiedEnergyPerTick, modifiedCostPerTick,
                    "Modified run must spend exactly modified energy_per_tick per tick");

                // ---------- Step 8: Missing/invalid override rejection check (bounded) ----------
                // Test that a pack with invalid JSON (negative energy_per_tick) is rejected by loader
                // This is safely bounded: we create a separate pack, enable it, and verify the loader keeps the previous valid definition
                String badPackName = "euvcraft_m1_bad_probe_" + UUID.randomUUID();
                Path badPackRoot = worldDataDir.resolve(badPackName).normalize();
                if (!badPackRoot.startsWith(worldDataDir) || badPackRoot.equals(worldDataDir)) {
                    throw new IllegalStateException("Bad pack root escapes datapack directory");
                }

                Files.createDirectories(badPackRoot.resolve("data/lithmod/machines"));
                JsonObject badMetadataJson = new JsonObject();
                badMetadataJson.add("pack", PackMetadataSection.SERVER_TYPE.codec().encodeStart(JsonOps.INSTANCE, metadata).getOrThrow());
                Files.writeString(badPackRoot.resolve("pack.mcmeta"), badMetadataJson.toString(), StandardCharsets.UTF_8);

                JsonObject badJson = baseJson.deepCopy();
                badJson.addProperty("energy_per_tick", -5); // invalid: negative
                Files.writeString(badPackRoot.resolve("data/lithmod/machines/crystal_puller.json"), badJson.toString(), StandardCharsets.UTF_8);

                repo.reload();
                String badPackId = "file/" + badPackName;
                helper.assertTrue(repo.isAvailable(badPackId), "Bad probe pack not discoverable");

                List<String> badEnabled = new ArrayList<>(enabled);
                badEnabled.add(badPackId);
                server.reloadResources(badEnabled).join();

                // The loader must reject the invalid definition and keep the previous valid one
                Optional<MachineDefinition> afterBadDef = MachineDefinitionLoader.getRegistry().byId("crystal_puller");
                helper.assertTrue(afterBadDef.isPresent(), "Definition must still be present after bad pack");
                int afterBadCost = afterBadDef.get().energyPerTick(null, EUVCRAFTMod.settings());
                helper.assertValueEqual(modifiedEnergyPerTick, afterBadCost,
                    "Invalid override must be rejected; previous valid cost must be retained");

                // Clean up: go back to just the good override pack
                server.reloadResources(enabled).join();

            } catch (java.io.IOException e) {
                throw new IllegalStateException("Datapack probe I/O failed", e);
            } finally {
                // ---------- Step 9: Restore original datapack selection ----------
                try {
                    server.reloadResources(originalSelected).join();
                } catch (Exception restoreEx) {
                    throw new IllegalStateException("Failed to restore original datapack selection", restoreEx);
                }

                // Verify restoration
                List<String> afterSelected = new ArrayList<>(repo.getSelectedIds());
                helper.assertTrue(afterSelected.equals(originalSelected),
                    "Datapack selection not restored. Original: " + originalSelected + " After: " + afterSelected);

                Resource restoredResource = resourceManager.getResource(machineResourceId).orElseThrow(
                    () -> new IllegalStateException("Restored machine definition resource not found"));
                helper.assertTrue(!restoredResource.sourcePackId().equals("file/" + packName),
                    "Probe pack still providing recipe after restore");

                Optional<MachineDefinition> restoredDef = MachineDefinitionLoader.getRegistry().byId("crystal_puller");
                helper.assertTrue(restoredDef.isPresent(), "MachineDefinitionLoader must have crystal_puller definition after restore");
                int restoredCost = restoredDef.get().energyPerTick(null, EUVCRAFTMod.settings());
                helper.assertValueEqual(baselineEnergyPerTick, restoredCost,
                    "Restored definition energy_per_tick must match baseline");

                // Final baseline run with a fresh CrystalPuller
                helper.setBlock(MACHINE_POS, Blocks.AIR);
                helper.setBlock(MACHINE_POS, SiliconMachines.CRYSTAL_PULLER.get());
                int finalBaselineCost = runAndMeasureCost(helper, level, MACHINE_POS, baselineEnergyPerTick);
                helper.assertValueEqual(baselineEnergyPerTick, finalBaselineCost,
                    "Final baseline run must spend exactly baseline energy_per_tick per tick");

            }

        } finally {
            // ---------- Restore FE multiplier ----------
            try {
                FabConfig.FE_POWER_MULTIPLIER.set(originalFeMultiplier);
                EUVCRAFTMod.config().onConfigReload();
            } catch (Exception configEx) {
                throw new IllegalStateException("Failed to restore FE multiplier config", configEx);
            }
        }

        helper.succeed();
    }

    /**
     * Places a fresh CrystalPuller at {@code pos}, feeds 4 silicon, runs until one product
     * is produced, and returns the actual FE spent per tick.
     *
     * <p>The machine is reset to AIR first to ensure a brand new block entity with no
     * in-flight recipe state. Energy is set to full capacity. The recipe is the real
     * loaded crystal_puller recipe (4 silicon -> 1 boule, 400 ticks). We measure the
     * buffer before and after exactly one serverTick that makes progress, accounting for
     * the admission tick (first tick where recipe is picked up).</p>
     *
     * @param helper the game test helper
     * @param level the server level
     * @param pos the position to place the machine (will be set to AIR first)
     * @param expectedCost the expected cost per tick (for validation)
     * @return the actual FE spent per working tick
     */
    private static int runAndMeasureCost(GameTestHelper helper, ServerLevel level, BlockPos pos, int expectedCost) {
        // Reset to AIR first to get a completely fresh block entity
        helper.setBlock(pos, Blocks.AIR);
        helper.setBlock(pos, SiliconMachines.CRYSTAL_PULLER.get());

        CrystalPullerBlockEntity puller = (CrystalPullerBlockEntity) level.getBlockEntity(helper.absolutePos(pos));
        helper.assertTrue(puller != null, "CrystalPuller block entity must exist at " + pos);
        if (puller == null) {
            return -1;
        }

        // Refresh definition to ensure current cost is applied
        puller.refreshDefinition();
        int actualCost = puller.runner().energyPerTick();
        helper.assertValueEqual(expectedCost, actualCost,
            "Runner cost must match expected at " + pos + ": expected " + expectedCost + " got " + actualCost);
        helper.assertTrue(actualCost > 0, "Cost must be positive for a paid tick");

        // Feed silicon (recipe requires 4)
        puller.setSlot(CrystalPullerBlockEntity.INPUT_SLOT, new ItemStack(ModItems.SILICON.get(), 4));

        // Fill buffer to capacity
        long capacity = puller.buffer().getCapacityAsLong();
        puller.setStoredEnergy((int) capacity);

        // First tick: admission (picks up recipe). This tick may or may not spend FE
        // depending on whether the runner implementation draws immediately.
        // We track buffer before/after to measure actual debit.
        long beforeFirst = puller.buffer().getAmountAsLong();
        puller.serverTick();
        long afterFirst = puller.buffer().getAmountAsLong();
        long firstTickDebit = beforeFirst - afterFirst;

        // Continue ticking until we get a tick that actually spends FE (progress tick)
        // The runner uses full-or-zero draw, so a tick with progress will spend exactly cost FE.
        int progressBefore = puller.runner().progress();
        int maxTicks = 10; // bounded - we just need one paid working tick
        long measuredCost = -1;

        for (int tick = 0; tick < maxTicks; tick++) {
            if (puller.runner().progress() > progressBefore) {
                // We got a progress tick - measure the debit for this tick
                long before = puller.buffer().getAmountAsLong();
                puller.serverTick();
                long after = puller.buffer().getAmountAsLong();
                measuredCost = (int) (before - after);
                break;
            }
            // No progress yet - tick and check again
            long before = puller.buffer().getAmountAsLong();
            puller.serverTick();
            long after = puller.buffer().getAmountAsLong();
            long debit = before - after;
            if (debit > 0 && puller.runner().progress() == progressBefore) {
                // This shouldn't happen with full-or-zero, but if it does, it's not a progress tick
            }
        }

        helper.assertTrue(measuredCost > 0, "Must observe at least one paid working tick with positive debit");
        helper.assertValueEqual(expectedCost, measuredCost,
            "Measured cost per working tick must equal expected: expected " + expectedCost + " got " + measuredCost);

        // Verify recipe actually engaged: output produced, input consumed
        helper.assertFalse(puller.outputSlot().isEmpty(), "CrystalPuller must produce output after paid ticks");
        helper.assertTrue(puller.outputSlot().getItem() == ModItems.SILICON_BOULE.get(), "Output must be silicon boule");
        helper.assertValueEqual(1, puller.outputSlot().getCount(), "Output count must be 1");
        helper.assertTrue(puller.slot(CrystalPullerBlockEntity.INPUT_SLOT).isEmpty(), "Input silicon must be consumed");

        return measuredCost;
    }

    private static JsonObject parseResource(Resource resource) {
        try (var reader = new InputStreamReader(resource.openAsReader(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to parse resource: " + resource.sourcePackId(), e);
        }
    }
}