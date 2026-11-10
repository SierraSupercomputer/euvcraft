package com.euvcraft.lithmod.mc.multiblock.linear;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedControllerBlock;
import com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedCasingBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;

public final class LinearMachines {
    public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(EUVCRAFTMod.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,EUVCRAFTMod.MODID);
    public static final DeferredBlock<EtchCellBlock> ETCH_CELL=BLOCKS.registerBlock("etch_cell",EtchCellBlock::new,p->p.strength(3,6).requiresCorrectToolForDrops());
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<EtchCellBlockEntity>> ETCH_CELL_BE=BES.register("etch_cell",()->new BlockEntityType<>(EtchCellBlockEntity::new,ETCH_CELL.get()));
    public static final DeferredBlock<FabItemPortBlock> ITEM_PORT=BLOCKS.registerBlock("fab_item_port",FabItemPortBlock::new,p->p.strength(3,6).requiresCorrectToolForDrops());
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<FabItemPortBlockEntity>> ITEM_PORT_BE=BES.register("fab_item_port",()->new BlockEntityType<>(FabItemPortBlockEntity::new,ITEM_PORT.get()));
    public static final DeferredBlock<G3ItemBlock> G3_ITEM_PORT=BLOCKS.registerBlock("g3_fab_item_port",G3ItemBlock::new,p->p.strength(4,8).requiresCorrectToolForDrops());
    public static final DeferredBlock<EuvItemBlock> EUV_ITEM_PORT=BLOCKS.registerBlock("euv_fab_item_port",EuvItemBlock::new,p->p.strength(4,8).requiresCorrectToolForDrops());
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<G3FabItemPortBlockEntity>> G3_ITEM_PORT_BE=BES.register("g3_fab_item_port",()->new BlockEntityType<>(G3FabItemPortBlockEntity::new,G3_ITEM_PORT.get()));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<EuvFabItemPortBlockEntity>> EUV_ITEM_PORT_BE=BES.register("euv_fab_item_port",()->new BlockEntityType<>(EuvFabItemPortBlockEntity::new,EUV_ITEM_PORT.get()));
    public static final class G3ItemBlock extends com.euvcraft.lithmod.mc.multiblock.g3.G3CasingBlock {
        public G3ItemBlock(Properties p){super(p);}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new G3FabItemPortBlockEntity(pos,state);}
    }
    public static final class EuvItemBlock extends com.euvcraft.lithmod.mc.multiblock.euv.EuvScannerMemberBlock {
        public EuvItemBlock(Properties p){super(p,com.euvcraft.lithmod.core.multiblock.BlockKind.CASING);}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new EuvFabItemPortBlockEntity(pos,state);}
    }
    public static final DeferredBlock<G3EtchBlock> G3_ETCH=BLOCKS.registerBlock("g3_etch_cell",G3EtchBlock::new,p->p.strength(4,8).requiresCorrectToolForDrops());
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<G3EtchCellBlockEntity>> G3_ETCH_BE=BES.register("g3_etch_cell",()->new BlockEntityType<>(G3EtchCellBlockEntity::new,G3_ETCH.get()));
    public static final class G3EtchBlock extends com.euvcraft.lithmod.mc.multiblock.g3.G3ControllerBlock {
        public G3EtchBlock(Properties properties){super(properties);}
        @Override protected BlockEntity createBlockEntity(BlockPos pos,BlockState state){return new G3EtchCellBlockEntity(pos,state);}
    }
    public static final DeferredBlock<T8StageBlock> PLANARIZER=BLOCKS.registerBlock("t8_planarizer",p->new T8StageBlock(p,true),p->p.strength(4,8).requiresCorrectToolForDrops());
    public static final DeferredBlock<T8StageBlock> IMPLANTER=BLOCKS.registerBlock("t8_implanter",p->new T8StageBlock(p,false),p->p.strength(4,8).requiresCorrectToolForDrops());
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<T8StageBlockEntity>> PLANARIZER_BE=BES.register("t8_planarizer",()->new BlockEntityType<>((p,s)->new T8StageBlockEntity(p,s,true),PLANARIZER.get()));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<T8StageBlockEntity>> IMPLANTER_BE=BES.register("t8_implanter",()->new BlockEntityType<>((p,s)->new T8StageBlockEntity(p,s,false),IMPLANTER.get()));
    public static final class T8StageBlock extends com.euvcraft.lithmod.mc.multiblock.g3.G3ControllerBlock {
        private final boolean planarizer;
        public T8StageBlock(Properties properties,boolean planarizer){super(properties);this.planarizer=planarizer;}
        @Override protected BlockEntity createBlockEntity(BlockPos pos,BlockState state){return new T8StageBlockEntity(pos,state,planarizer);}
    }
    public static final DeferredBlock<StageControllerBlock> COATING_TRACK=BLOCKS.registerBlock("coating_track",p->new StageControllerBlock(p,true),p->p.strength(3,6).requiresCorrectToolForDrops());
    public static final DeferredBlock<StageControllerBlock> WET_CLEANER=BLOCKS.registerBlock("wet_cleaner",p->new StageControllerBlock(p,false),p->p.strength(3,6).requiresCorrectToolForDrops());
    public static final DeferredBlock<FabUpwInletBlock> UPW_INLET=BLOCKS.registerBlock("fab_upw_inlet",FabUpwInletBlock::new,p->p.strength(3,6).requiresCorrectToolForDrops());
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<CoatingTrackBlockEntity>> COATING_TRACK_BE=BES.register("coating_track",()->new BlockEntityType<>(CoatingTrackBlockEntity::new,COATING_TRACK.get()));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<WetCleanerBlockEntity>> WET_CLEANER_BE=BES.register("wet_cleaner",()->new BlockEntityType<>(WetCleanerBlockEntity::new,WET_CLEANER.get()));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<FabUpwInletBlockEntity>> UPW_INLET_BE=BES.register("fab_upw_inlet",()->new BlockEntityType<>(FabUpwInletBlockEntity::new,UPW_INLET.get()));
    public static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,EUVCRAFTMod.MODID);
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>,net.minecraft.world.inventory.MenuType<LinearStageMenu>> STAGE_PANEL_MENU=MENUS.register("linear_stage_panel",()->net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create((id,inventory,buffer)->new LinearStageMenu(id,inventory,buffer,true)));
    public static void register(IEventBus bus){BLOCKS.register(bus);BES.register(bus);MENUS.register(bus);bus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event)->{event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK,G3_ITEM_PORT_BE.get(),(be,side)->be.items());event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK,EUV_ITEM_PORT_BE.get(),(be,side)->be.items());});bus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event)->event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK,UPW_INLET_BE.get(),(be,side)->be.fluid()));bus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event)->event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK,ITEM_PORT_BE.get(),(be,side)->be.items(side)));}
    public static final class FabItemPortBlock extends AdvancedCasingBlock {
        public FabItemPortBlock(Properties properties){super(properties);}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new FabItemPortBlockEntity(pos,state);}
    }
    public static final class EtchCellBlock extends AdvancedControllerBlock {
        public EtchCellBlock(Properties properties){super(properties);}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new EtchCellBlockEntity(pos,state);}
    }
    private LinearMachines(){}
}
