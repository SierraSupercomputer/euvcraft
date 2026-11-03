package com.euvcraft.lithmod.mc.machine.recycler;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.*;
public final class RecyclerMachines {
    public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(EUVCRAFTMod.MODID);
    public static final DeferredBlock<ScrapRecyclerBlock> BLOCK=BLOCKS.registerBlock("scrap_recycler",ScrapRecyclerBlock::new,p->p.strength(3,6).requiresCorrectToolForDrops());
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,EUVCRAFTMod.MODID);
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<ScrapRecyclerBlockEntity>> BLOCK_ENTITY=BLOCK_ENTITIES.register("scrap_recycler",()->new BlockEntityType<>(ScrapRecyclerBlockEntity::new,BLOCK.get()));
    public static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,EUVCRAFTMod.MODID);
    public static final DeferredHolder<MenuType<?>,MenuType<ScrapRecyclerMenu>> MENU=MENUS.register("scrap_recycler",()->IMenuTypeExtension.create(ScrapRecyclerMenu::new));
    public static void register(IEventBus bus){BLOCKS.register(bus);BLOCK_ENTITIES.register(bus);MENUS.register(bus);bus.addListener(RecyclerMachines::capabilities);}
    private static void capabilities(RegisterCapabilitiesEvent event){event.registerBlockEntity(Capabilities.Energy.BLOCK,BLOCK_ENTITY.get(),(be,side)->be.energy());event.registerBlockEntity(Capabilities.Item.BLOCK,BLOCK_ENTITY.get(),(be,side)->be.items());}
    private RecyclerMachines(){}
}
