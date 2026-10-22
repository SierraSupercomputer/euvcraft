package com.euvcraft.lithmod.mc.multiblock.utilities;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;import com.euvcraft.lithmod.core.multiblock.BlockKind;
import net.minecraft.core.registries.Registries;import net.minecraft.world.inventory.MenuType;import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;import net.neoforged.neoforge.registries.*;import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.capabilities.*;
public final class UtilityMachines{
 public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(EUVCRAFTMod.MODID);
 public static final DeferredRegister<BlockEntityType<?>> BES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,EUVCRAFTMod.MODID);
 public static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,EUVCRAFTMod.MODID);
 public static final DeferredBlock<UtilityControllerBlock> CONTROLLER=BLOCKS.registerBlock("utility_plant",UtilityControllerBlock::new,p->p.strength(4,8).requiresCorrectToolForDrops());
 public static final DeferredBlock<UtilityPortBlock> WATER_INLET=BLOCKS.registerBlock("utility_water_inlet",p->new UtilityPortBlock(p,BlockKind.WATER_INLET),p->p.strength(4,8).requiresCorrectToolForDrops());
 public static final DeferredBlock<UtilityPortBlock> PURE_OUTLET=BLOCKS.registerBlock("utility_pure_outlet",p->new UtilityPortBlock(p,BlockKind.FLUID_OUTLET),p->p.strength(4,8).requiresCorrectToolForDrops());
 public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<UtilityControllerBlockEntity>> CONTROLLER_BE=BES.register("utility_plant",()->new BlockEntityType<>(UtilityControllerBlockEntity::new,CONTROLLER.get()));
 public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<UtilityPortBlockEntity>> PORT_BE=BES.register("utility_port",()->new BlockEntityType<>(UtilityPortBlockEntity::new,WATER_INLET.get(),PURE_OUTLET.get()));
 public static final DeferredHolder<MenuType<?>,MenuType<UtilityMenu>> MENU=MENUS.register("utility_plant",()->IMenuTypeExtension.create(UtilityMenu::new));
 public static void register(IEventBus bus){BLOCKS.register(bus);BES.register(bus);MENUS.register(bus);bus.addListener(UtilityMachines::capabilities);}
 private static void capabilities(RegisterCapabilitiesEvent e){e.registerBlockEntity(Capabilities.Fluid.BLOCK,PORT_BE.get(),(be,side)->be.handler());}
 private UtilityMachines(){}
}
