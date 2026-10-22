package com.euvcraft.lithmod.mc.multiblock.utilities;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.registries.*;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.bus.api.IEventBus;
/** Transport-only purified fluid: no world source conversion or free bucket placement. */
public final class UtilityFluids {
 public static final DeferredRegister<FluidType> TYPES=DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.FLUID_TYPES,EUVCRAFTMod.MODID);
 public static final DeferredHolder<FluidType,FluidType> TYPE=TYPES.register("ultrapure_water",()->new FluidType(FluidType.Properties.create().descriptionId("fluid.lithmod.ultrapure_water")));
 public static final DeferredRegister<Fluid> FLUIDS=DeferredRegister.create(Registries.FLUID,EUVCRAFTMod.MODID);
 public static final DeferredHolder<Fluid,BaseFlowingFluid.Source> ULTRAPURE_WATER=FLUIDS.register("ultrapure_water",()->new BaseFlowingFluid.Source(properties()));
 public static final DeferredHolder<Fluid,BaseFlowingFluid.Flowing> FLOWING=FLUIDS.register("flowing_ultrapure_water",()->new BaseFlowingFluid.Flowing(properties()));
 private static BaseFlowingFluid.Properties properties(){return new BaseFlowingFluid.Properties(TYPE,ULTRAPURE_WATER,FLOWING);}
 public static void register(IEventBus bus){TYPES.register(bus);FLUIDS.register(bus);}
 private UtilityFluids(){}
}
