package com.euvcraft.lithmod.mc.multiblock.hydrogen;

import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.Optional;

/**
 * Transport-only fabricated hydrogen gas fluid: no world source conversion or free bucket placement.
 *
 * <p>This is a {@link BaseFlowingFluid} registered like {@code UtilityFluids.ULTRAPURE_WATER} — it
 * exists only for conduit transport and machine I/O. The coordinator supplies the data-driven
 * Arcforge gas tag and gas properties JSON; this class only declares the fluid and its type.</p>
 *
 * <p>The raw hydrogen fluid comes from Arcforge's electrolyzer with registry id
 * {@code arcforge:hydrogen} (verified at runtime; fail-closed if unavailable). This fluid is the
 * purifier's output: {@code lithmod:fab_hydrogen}.</p>
 */
public final class HydrogenFluids {

    public static final DeferredRegister<net.neoforged.neoforge.fluids.FluidType> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, EUVCRAFTMod.MODID);

    public static final DeferredHolder<net.neoforged.neoforge.fluids.FluidType, net.neoforged.neoforge.fluids.FluidType> TYPE =
            TYPES.register("fab_hydrogen", () -> new net.neoforged.neoforge.fluids.FluidType(
                    net.neoforged.neoforge.fluids.FluidType.Properties.create()
                            .descriptionId("fluid.lithmod.fab_hydrogen")
                            .canConvertToSource(false)
    ));

    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, EUVCRAFTMod.MODID);

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> FAB_HYDROGEN =
            FLUIDS.register("fab_hydrogen", () -> new BaseFlowingFluid.Source(properties()));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING =
            FLUIDS.register("flowing_fab_hydrogen", () -> new BaseFlowingFluid.Flowing(properties()));

    private static BaseFlowingFluid.Properties properties() {
        return new BaseFlowingFluid.Properties(TYPE, FAB_HYDROGEN, FLOWING);
    }

    /**
     * Returns the raw hydrogen fluid from Arcforge ({@code arcforge:hydrogen}) if present,
     * or empty if not loaded. Fail-closed: callers must check presence before use.
     */
    public static Optional<Fluid> rawHydrogenFluid() {
        return Optional.ofNullable(net.minecraftforge.registries.ForgeRegistries.FLUIDS.getValue(new ResourceLocation("arcforge", "hydrogen")));
    }

    /**
     * Returns a raw hydrogen fluid stack for the given amount if the fluid is loaded.
     */
    public static Optional<FluidStack> rawHydrogenStack(int amount) {
        return rawHydrogenFluid().map(fluid -> new FluidStack(fluid, amount));
    }

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        FLUIDS.register(bus);
    }

    private HydrogenFluids() {}
}