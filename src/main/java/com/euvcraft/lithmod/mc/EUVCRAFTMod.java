package com.euvcraft.lithmod.mc;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.bus.api.IEventBus;

import com.euvcraft.lithmod.mc.data.TierDataLoader;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(EUVCRAFTMod.MODID)
public class EUVCRAFTMod {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "lithmod";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    private final FabConfig fabConfig;

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public EUVCRAFTMod(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register server config (NeoForge 26.3 renamed COMMON to LOCAL)
        modContainer.registerConfig(localConfigType(), FabConfig.getSpec());
        this.fabConfig = new FabConfig();

        // Register tier data reload listener
        TierDataLoader.register();

        LOGGER.info("EUVCRAFT mod loading...");
    }

    // FML 12.0.8 (NeoForge 26.3.0.37-beta) renamed COMMON to LOCAL.
    // Looked up by name so the mod runs on both sides of the rename while 26.3 is in beta.
    private static ModConfig.Type localConfigType() {
        for (ModConfig.Type type : ModConfig.Type.values()) {
            if (type.name().equals("LOCAL")) {
                return type;
            }
        }
        return ModConfig.Type.valueOf("COMMON");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM EUVCRAFT COMMON SETUP");
    }

    /**
     * Returns the fab config instance for accessing settings.
     */
    public FabConfig getFabConfig() {
        return fabConfig;
    }
}