package com.euvcraft.lithmod.mc.cleanroom;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.*;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
public final class CleanroomBlocks {
 public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(EUVCRAFTMod.MODID);
 public static final DeferredRegister<BlockEntityType<?>> BES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,EUVCRAFTMod.MODID);
 public static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,EUVCRAFTMod.MODID);
 public static final DeferredBlock<Block> FLOOR=BLOCKS.registerSimpleBlock("cleanroom_floor",p->p.strength(3).requiresCorrectToolForDrops());
 public static final DeferredBlock<Block> WALL=BLOCKS.registerSimpleBlock("cleanroom_wall",p->p.strength(3).requiresCorrectToolForDrops());
 public static final DeferredBlock<TransparentBlock> WINDOW=BLOCKS.registerBlock("cleanroom_window",TransparentBlock::new,p->p.strength(2).noOcclusion());
 public static final DeferredBlock<DoorBlock> DOOR=BLOCKS.registerBlock("cleanroom_door",p->new DoorBlock(BlockSetType.IRON,p),p->p.strength(3).noOcclusion());
 public static final DeferredBlock<CleanroomMachineBlock> CONTROLLER=BLOCKS.registerBlock("cleanroom_controller",CleanroomMachineBlock::new,p->p.strength(4).requiresCorrectToolForDrops());
 public static final DeferredBlock<CleanroomMachineBlock> FILTER=BLOCKS.registerBlock("cleanroom_filter",CleanroomMachineBlock::new,p->p.strength(3).requiresCorrectToolForDrops());
 public static final DeferredBlock<CleanroomPortBlock> POWER_PORT=BLOCKS.registerBlock("cleanroom_power_port",CleanroomPortBlock::new,p->p.strength(3).requiresCorrectToolForDrops());
 public static final DeferredBlock<CleanroomPortBlock> FLUID_PORT=BLOCKS.registerBlock("cleanroom_fluid_port",CleanroomPortBlock::new,p->p.strength(3).requiresCorrectToolForDrops());
 public static final DeferredBlock<CleanroomPortBlock> ITEM_PORT=BLOCKS.registerBlock("cleanroom_item_port",CleanroomPortBlock::new,p->p.strength(3).requiresCorrectToolForDrops());
 public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<CleanroomControllerBlockEntity>> CONTROLLER_BE=BES.register("cleanroom_controller",()->new BlockEntityType<>(CleanroomControllerBlockEntity::new,CONTROLLER.get()));
 public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<CleanroomFilterBlockEntity>> FILTER_BE=BES.register("cleanroom_filter",()->new BlockEntityType<>(CleanroomFilterBlockEntity::new,FILTER.get()));
 public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<CleanroomPortBlockEntity>> PORT_BE=BES.register("cleanroom_port",()->new BlockEntityType<>(CleanroomPortBlockEntity::new,POWER_PORT.get(),FLUID_PORT.get(),ITEM_PORT.get()));
 public static final DeferredHolder<MenuType<?>,MenuType<CleanroomMenu>> MENU=MENUS.register("cleanroom",()->IMenuTypeExtension.create(CleanroomMenu::new));
 public static boolean isShell(Block b){return b==FLOOR.get()||b==WALL.get()||b==WINDOW.get()||b==DOOR.get()||b==CONTROLLER.get()||b==FILTER.get()||b==POWER_PORT.get()||b==FLUID_PORT.get()||b==ITEM_PORT.get();}
 public static void register(IEventBus bus){BLOCKS.register(bus);BES.register(bus);MENUS.register(bus);CleanroomItems.ITEMS.register(bus);bus.addListener(CleanroomBlocks::capabilities);}
 private static void capabilities(RegisterCapabilitiesEvent e){
  e.registerBlockEntity(Capabilities.Energy.BLOCK,CONTROLLER_BE.get(),(be,side)->CleanroomEnergy.view(be));
  e.registerBlockEntity(Capabilities.Energy.BLOCK,FILTER_BE.get(),(be,side)->CleanroomEnergy.view(be));
  e.registerBlockEntity(Capabilities.Energy.BLOCK,PORT_BE.get(),(be,side)->be.energy(side));
  e.registerBlockEntity(Capabilities.Fluid.BLOCK,PORT_BE.get(),(be,side)->be.fluids(side));
  e.registerBlockEntity(Capabilities.Item.BLOCK,PORT_BE.get(),(be,side)->be.items(side));
 }
 private CleanroomBlocks(){}
}
