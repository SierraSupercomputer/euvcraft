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
    public static void register(IEventBus bus){BLOCKS.register(bus);BES.register(bus);bus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event)->event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK,ITEM_PORT_BE.get(),(be,side)->be.items(side)));}
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
