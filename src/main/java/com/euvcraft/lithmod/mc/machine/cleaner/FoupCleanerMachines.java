package com.euvcraft.lithmod.mc.machine.cleaner;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import net.minecraft.core.registries.Registries;import net.minecraft.world.item.Item;import net.minecraft.world.level.block.entity.BlockEntityType;import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;import net.neoforged.neoforge.registries.*;import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;import net.neoforged.neoforge.capabilities.*;
public final class FoupCleanerMachines {
 public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(EUVCRAFTMod.MODID);
 public static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(EUVCRAFTMod.MODID);
 public static final DeferredRegister<BlockEntityType<?>> BES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,EUVCRAFTMod.MODID);
 public static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,EUVCRAFTMod.MODID);
 public static final DeferredBlock<FoupCleanerBlock> BLOCK=BLOCKS.registerBlock("foup_cleaner",FoupCleanerBlock::new,v->v.strength(3,6).requiresCorrectToolForDrops());
 public static final DeferredItem<net.minecraft.world.item.BlockItem> ITEM=ITEMS.registerSimpleBlockItem("foup_cleaner",BLOCK);
 public static final DeferredItem<Item> WAFER_SCRAP=ITEMS.registerSimpleItem("wafer_scrap");
 public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<FoupCleanerBlockEntity>> BE=BES.register("foup_cleaner",()->new BlockEntityType<>(FoupCleanerBlockEntity::new,BLOCK.get()));
 public static final DeferredHolder<MenuType<?>,MenuType<FoupCleanerMenu>> MENU=MENUS.register("foup_cleaner",()->IMenuTypeExtension.create(FoupCleanerMenu::new));
 public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);BES.register(bus);MENUS.register(bus);bus.addListener(FoupCleanerMachines::caps);}
 private static void caps(RegisterCapabilitiesEvent event){event.registerBlockEntity(Capabilities.Energy.BLOCK,BE.get(),(be,side)->new net.neoforged.neoforge.transfer.energy.EnergyHandler(){public long getAmountAsLong(){return be.live()?be.energy().getAmountAsLong():0;}public long getCapacityAsLong(){return be.live()?be.energy().getCapacityAsLong():0;}public int insert(int n,net.neoforged.neoforge.transfer.transaction.TransactionContext tx){return be.live()?be.energy().insert(n,tx):0;}public int extract(int n,net.neoforged.neoforge.transfer.transaction.TransactionContext tx){return 0;}});event.registerBlockEntity(Capabilities.Fluid.BLOCK,BE.get(),(be,side)->be.waterInput);}
 private FoupCleanerMachines(){}
}
