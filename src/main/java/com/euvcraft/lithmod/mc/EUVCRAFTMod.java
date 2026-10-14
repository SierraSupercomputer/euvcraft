package com.euvcraft.lithmod.mc;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

import com.euvcraft.lithmod.mc.data.TierDataLoader;
import com.euvcraft.lithmod.mc.data.SupportLineDataLoader;

@Mod(EUVCRAFTMod.MODID)
public class EUVCRAFTMod {
    public static final String MODID = "lithmod";
    public static final Logger LOGGER = LogUtils.getLogger();

    private final FabConfig fabConfig;

    public EUVCRAFTMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        com.euvcraft.lithmod.mc.gametest.EUVCRAFTGameTests.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.LOCAL, FabConfig.getSpec());
        this.fabConfig = new FabConfig();

        NeoForge.EVENT_BUS.register(TierDataLoader.class);
        NeoForge.EVENT_BUS.register(SupportLineDataLoader.class);

        LOGGER.info("EUVCRAFT mod loading...");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("HELLO FROM EUVCRAFT COMMON SETUP");
    }

    public FabConfig getFabConfig() {
        return fabConfig;
    }
}
