package com.euvcraft.lithmod.mc;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

import com.euvcraft.lithmod.core.FabSettings;
import com.euvcraft.lithmod.mc.data.TierDataLoader;
import com.euvcraft.lithmod.mc.data.SupportLineDataLoader;
import com.euvcraft.lithmod.mc.data.DieRulesDataLoader;
import com.euvcraft.lithmod.mc.data.MachineDefinitionLoader;
import com.euvcraft.lithmod.mc.data.MachineRecipeLoader;
import com.euvcraft.lithmod.mc.data.SupportRecipeLoader;
import com.euvcraft.lithmod.mc.registry.ModCreativeTabs;
import com.euvcraft.lithmod.mc.registry.ModDataComponents;
import com.euvcraft.lithmod.mc.registry.ModItems;
import com.euvcraft.lithmod.mc.gametest.M1ShippingGameTests;
import com.euvcraft.lithmod.mc.gametest.MachineDropGameTests;
import com.euvcraft.lithmod.mc.gametest.MachineGameTests;
import com.euvcraft.lithmod.mc.gametest.MaskGameTests;
import com.euvcraft.lithmod.mc.gametest.M1GateGameTests;
import com.euvcraft.lithmod.mc.gametest.M1BoundaryGameTests;
import com.euvcraft.lithmod.mc.gametest.M1ReloadGameTests;
import com.euvcraft.lithmod.mc.gametest.M1DebugGameTests;
import com.euvcraft.lithmod.mc.gametest.MultiblockGameTests;
import com.euvcraft.lithmod.mc.gametest.PackagingGameTests;
import com.euvcraft.lithmod.mc.gametest.ProcessCellGameTests;
import com.euvcraft.lithmod.mc.gametest.PresentationGameTests;
import com.euvcraft.lithmod.mc.gametest.SiliconGameTests;
import com.euvcraft.lithmod.mc.gametest.ClientPolishGameTests;
import com.euvcraft.lithmod.mc.gametest.PlacementAndTitleGameTests;
import com.euvcraft.lithmod.mc.gametest.SupportMultiblockGameTests;
import com.euvcraft.lithmod.mc.gametest.T3ContentGameTests;
import com.euvcraft.lithmod.mc.gametest.T3PurificationGameTests;
import com.euvcraft.lithmod.mc.gametest.T3MaskWriterGameTests;
import com.euvcraft.lithmod.mc.machine.mask.MaskMachines;
import com.euvcraft.lithmod.mc.machine.mask.MaskTooltipAppenders;
import com.euvcraft.lithmod.mc.machine.packaging.PackagingMachines;
import com.euvcraft.lithmod.mc.machine.silicon.SiliconMachines;
import com.euvcraft.lithmod.mc.machine.test.ModMachines;
import com.euvcraft.lithmod.mc.multiblock.ModMultiblocks;
import com.euvcraft.lithmod.mc.multiblock.support.SupportMachines;

@Mod(EUVCRAFTMod.MODID)
public class EUVCRAFTMod {
    public static final String MODID = "lithmod";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static FabConfig config;

    public EUVCRAFTMod(IEventBus modEventBus, ModContainer modContainer) {
        ModDataComponents.DATA_COMPONENTS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        ModMachines.register(modEventBus);
        SiliconMachines.register(modEventBus);
        MaskMachines.register(modEventBus);
        PackagingMachines.register(modEventBus);
        ModMultiblocks.register(modEventBus);
        SupportMachines.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(FoupTooltipAppenders::register);
        modEventBus.addListener(ChipTooltipAppenders::register);
        modEventBus.addListener(MaskTooltipAppenders::register);
modEventBus.addListener(ComponentTooltipAppenders::register);
        // Test-only game tests are registered only when fixtures are enabled. When
        // fixtures are off and the shipping probe is on, ONLY M1ShippingGameTests is
        // registered (required literal id m1_gate_shipping_no_fixtures). Production
        // never registers game tests or the probe: both gates return false there.
        if (GameTestFixtures.enabled()) {
            com.euvcraft.lithmod.mc.gametest.EUVCRAFTGameTests.register(modEventBus);
            com.euvcraft.lithmod.mc.gametest.FabRandomGameTests.register(modEventBus);
            com.euvcraft.lithmod.mc.gametest.FoupGameTests.register(modEventBus);
            com.euvcraft.lithmod.mc.gametest.YieldGameTests.register(modEventBus);
            com.euvcraft.lithmod.mc.gametest.ChipGameTests.register(modEventBus);
            com.euvcraft.lithmod.mc.gametest.ComponentGameTests.register(modEventBus);
            MachineGameTests.register(modEventBus);
            MachineDropGameTests.register(modEventBus);
            MultiblockGameTests.register(modEventBus);
            SiliconGameTests.register(modEventBus);
            MaskGameTests.register(modEventBus);
            PackagingGameTests.register(modEventBus);
            ProcessCellGameTests.register(modEventBus);
            PresentationGameTests.register(modEventBus);
            M1GateGameTests.register(modEventBus);
            M1BoundaryGameTests.register(modEventBus);
            M1ReloadGameTests.register(modEventBus);
            M1DebugGameTests.register(modEventBus);
            ClientPolishGameTests.register(modEventBus);
            PlacementAndTitleGameTests.register(modEventBus);
            SupportMultiblockGameTests.register(modEventBus);
            com.euvcraft.lithmod.mc.gametest.WaterInletGameTests.register(modEventBus);
            T3ContentGameTests.register(modEventBus);
            T3PurificationGameTests.register(modEventBus);
            T3MaskWriterGameTests.register(modEventBus);
        } else if (GameTestFixtures.shippingProbeEnabled()) {
            M1ShippingGameTests.register(modEventBus);
        }

        modContainer.registerConfig(ModConfig.Type.LOCAL, FabConfig.getSpec());
        config = new FabConfig();
        config.addSettingsChangeListener(FabYieldService::onSettingsChanged);

        // Without these the spec values are never read back into FabSettings, so
        // FabConfig.getSettings() would keep returning the defaults forever.
        modEventBus.addListener(EUVCRAFTMod::configLoaded);
        modEventBus.addListener(EUVCRAFTMod::configReloaded);

        NeoForge.EVENT_BUS.addListener(FoupDebugCommand::register);
        NeoForge.EVENT_BUS.addListener(FabDebugCommand::register);

        NeoForge.EVENT_BUS.register(TierDataLoader.class);
        NeoForge.EVENT_BUS.register(SupportLineDataLoader.class);
        NeoForge.EVENT_BUS.register(DieRulesDataLoader.class);
        NeoForge.EVENT_BUS.register(MachineDefinitionLoader.class);
        NeoForge.EVENT_BUS.register(MachineRecipeLoader.class);
        NeoForge.EVENT_BUS.register(SupportRecipeLoader.class);

        LOGGER.info("EUVCRAFT mod loading...");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("HELLO FROM EUVCRAFT COMMON SETUP");
    }

    /**
     * Reads the spec values into {@link FabSettings} when the config file is loaded.
     *
     * @param event the NeoForge config load event
     */
    private static void configLoaded(ModConfigEvent.Loading event) {
        if (isOurConfig(event.getConfig())) {
            config.onConfigLoad(event.getConfig());
        }
    }

    /**
     * Reads the spec values into {@link FabSettings} again when the config is
     * reloaded, for example by {@code /forge config}.
     *
     * @param event the NeoForge config reload event
     */
    private static void configReloaded(ModConfigEvent.Reloading event) {
        if (isOurConfig(event.getConfig())) {
            config.onConfigReload();
        }
    }

    private static boolean isOurConfig(ModConfig candidate) {
        return candidate != null && FabConfig.getSpec() == candidate.getSpec();
    }

    /**
     * Returns the config binding for this mod.
     *
     * <p>Static because item creation, the creative tab and the debug commands all
     * need the current settings and none of them has a mod instance to ask. Before the
     * constructor has run this returns {@code null}, which only happens during
     * registry construction, and nothing reads settings that early.</p>
     *
     * @return the config binding
     */
    public static FabConfig config() {
        return config;
    }

    /**
     * Returns the current fab settings, or the core defaults before the config
     * exists or has been loaded.
     *
     * @return the settings in force right now
     */
    public static FabSettings settings() {
        FabConfig current = config;
        return current == null ? FabSettings.defaults() : current.getSettings();
    }
}