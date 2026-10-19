package com.euvcraft.lithmod.mc.gametest;

import com.euvcraft.lithmod.core.MachineDefinition;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.FabConfig;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Rotation;
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
            Resource baseResource = server.getResourceManager().getResource(machineResourceId).orElseThrow(
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
                var format = SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA);
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
                Resource overriddenResource = server.getResourceManager().getResource(machineResourceId).orElseThrow(
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
                clearMachine(helper, level, MACHINE_POS_2);
                helper.setBlock(MACHINE_POS_2, SiliconMachines.CRYSTAL_PULLER.get());
                CrystalPullerBlockEntity puller = (CrystalPullerBlockEntity) level.getBlockEntity(helper.absolutePos(MACHINE_POS_2));
                helper.assertTrue(puller != null, "CrystalPuller block entity must exist after reload");

                // Refresh definition to pick up new cost
                puller.serverTick();
                int actualCostAfterReload = puller.runner().energyPerTick();
                helper.assertValueEqual(modifiedEnergyPerTick, actualCostAfterReload,
                    "Runner must see modified energy_per_tick after reload");

                // Run one paid tick with silicon input and measure actual debit
                int modifiedCostPerTick = runAndMeasureCost(helper, level, MACHINE_POS_2, modifiedEnergyPerTick);
                helper.assertValueEqual(modifiedEnergyPerTick, modifiedCostPerTick,
                    "Modified run must spend exactly modified energy_per_tick per tick");

                // Invalid-entry codec behavior is covered by existing loader tests; this case proves
                // the required valid override and restoration, without adding a new rejection contract.

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

                Resource restoredResource = server.getResourceManager().getResource(machineResourceId).orElseThrow(
                    () -> new IllegalStateException("Restored machine definition resource not found"));
                helper.assertTrue(restoredResource.sourcePackId().equals(baseResource.sourcePackId()),
                    "Probe pack still providing recipe after restore");

                Optional<MachineDefinition> restoredDef = MachineDefinitionLoader.getRegistry().byId("crystal_puller");
                helper.assertTrue(restoredDef.isPresent(), "MachineDefinitionLoader must have crystal_puller definition after restore");
                int restoredCost = restoredDef.get().energyPerTick(null, EUVCRAFTMod.settings());
                helper.assertValueEqual(baselineEnergyPerTick, restoredCost,
                    "Restored definition energy_per_tick must match baseline");

                // Final baseline run with a fresh CrystalPuller
                clearMachine(helper, level, MACHINE_POS);
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
        clearMachine(helper, level, pos);
        helper.setBlock(pos, SiliconMachines.CRYSTAL_PULLER.get());
        CrystalPullerBlockEntity puller = (CrystalPullerBlockEntity) level.getBlockEntity(helper.absolutePos(pos));
        helper.assertTrue(puller != null, "Fresh Crystal Puller block entity exists");
        try {
            puller.serverTick(); // refresh loaded definition while empty, without starting a recipe
            puller.setSlot(CrystalPullerBlockEntity.INPUT_SLOT, new ItemStack(ModItems.SILICON.get(), 4));
            puller.setStoredEnergy(puller.buffer().getCapacityAsInt());
            int paidTicks=0, measuredCost=-1;
            for (int tick=0; tick<2000 && puller.outputSlot().isEmpty(); tick++) {
                long before = puller.buffer().getAmountAsLong();
                int progressBefore = puller.progressTicks();
                puller.serverTick();
                long debit = before-puller.buffer().getAmountAsLong();
                helper.assertTrue(debit >= 0, "Working tick cannot create energy");
                if (debit > 0) {
                    helper.assertValueEqual((long)expectedCost, debit, "Actual paid-tick buffer debit follows the loaded definition");
                    helper.assertTrue(puller.progressTicks()>progressBefore || !puller.outputSlot().isEmpty(),
                            "A paid tick must advance or finish the actual recipe");
                    measuredCost=Math.toIntExact(debit);
                    paidTicks++;
                }
            }
            helper.assertTrue(paidTicks > 0, "At least one actual paid working tick observed");
            helper.assertTrue(puller.outputSlot().is(ModItems.SILICON_BOULE.get()), "Actual recipe completes a silicon boule");
            helper.assertValueEqual(1, puller.outputSlot().getCount(), "Actual recipe produces one boule");
            helper.assertTrue(puller.slot(CrystalPullerBlockEntity.INPUT_SLOT).isEmpty(), "Actual recipe consumed all four silicon");
            EUVCRAFTMod.LOGGER.info("[M1-RELOAD] actual Crystal Puller: {} paid ticks at {} FE",paidTicks,measuredCost);
            return measuredCost;
        } finally {
            clearMachine(helper, level, pos);
        }
    }

    private static void clearMachine(GameTestHelper helper, ServerLevel level, BlockPos pos) {
        var blockEntity=level.getBlockEntity(helper.absolutePos(pos));
        if (blockEntity instanceof net.minecraft.world.Container inventory) inventory.clearContent();
        helper.setBlock(pos, Blocks.AIR);
    }

    private static JsonObject parseResource(Resource resource) {
        try (var reader = resource.openAsReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to parse resource: " + resource.sourcePackId(), e);
        }
    }
}