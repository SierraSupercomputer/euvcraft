package com.euvcraft.lithmod.mc.multiblock.linear;
import com.euvcraft.lithmod.core.FoupContents;
import com.euvcraft.lithmod.core.routing.FabProgress;
import com.euvcraft.lithmod.mc.FoupItems;
import com.euvcraft.lithmod.mc.routing.*;
import com.euvcraft.lithmod.mc.registry.ModDataComponents;
import com.euvcraft.lithmod.mc.registry.ModItems;
import com.euvcraft.lithmod.mc.multiblock.advanced.DepositionEtcherControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** Post-exposure processing preserves wafer rolls, exposure calibration and carrier identity. */
public final class EtchCellBlockEntity extends DepositionEtcherControllerBlockEntity {
    public EtchCellBlockEntity(BlockPos pos,BlockState state){super(LinearMachines.ETCH_CELL_BE.get(),pos,state);}
    @Override protected String machineId(){return "etch_cell";}
    @Override public com.euvcraft.lithmod.core.multiblock.MatchResult matchStructure(com.euvcraft.lithmod.core.multiblock.MultiblockPos anchor,com.euvcraft.lithmod.core.multiblock.MultiblockDirection facing,java.util.function.Function<com.euvcraft.lithmod.core.multiblock.MultiblockPos,com.euvcraft.lithmod.core.multiblock.BlockKind> lookup){
        return com.euvcraft.lithmod.core.multiblock.LinearStructureMatcher.match(anchor,facing,com.euvcraft.lithmod.core.multiblock.LinearStructureMatcher.ETCH_3x3x5,false,lookup);
    }
    @Override public boolean acceptsReagent(ItemStack stack){return stack.is(ModItems.ETCHANT_CHARGE.get());}
    @Override protected boolean canWork(ItemStack carrier){return FoupItems.trustedContentsOf(carrier).run().tierIndex()<5||com.euvcraft.lithmod.mc.cleanroom.CleanroomRooms.sample((net.minecraft.server.level.ServerLevel)level,members()).available();}
    @Override protected void paidTick(ItemStack carrier){
        var lot=FoupItems.trustedContentsOf(carrier);if(lot.run().tierIndex()<5)return;
        var sample=com.euvcraft.lithmod.mc.cleanroom.CleanroomRooms.sample((net.minecraft.server.level.ServerLevel)level,members());carrier.set(ModDataComponents.FOUP_CONTENTS.get(),lot.withRoomExposure(lot.roomExposure().sample(sample.yieldPermille()),0));
    }
    @Override protected int reagentRequired(){return definition().map(d->d.param("etchant_per_lot").orElse(0)).orElse(0);}
    @Override protected @Nullable FoupContents eligible(ItemStack stack){
        if(stack.isEmpty()||stack.getCount()!=1||!FoupItems.isFoup(stack)||!FoupRouting.matchesPrivateLot(stack))return null;
        var lot=FoupItems.trustedContentsOf(stack);var route=stack.get(ModDataComponents.FOUP_ROUTING.get());
        if(lot==null||lot.isEmpty()||lot.isContaminated()||lot.preparationGeneration()<2||route.progress().stage()!=FabProgress.Stage.EXPOSED||(route.progress().tier()<4||route.progress().tier()>6))return null;
        if(route.progress().tier()>=5&&!stack.has(ModDataComponents.COATING_RECEIPT.get()))return null;
        return lot;
    }
    @Override protected ItemStack completeCarrier(ItemStack carrier,FoupContents original){
        var result=carrier.copy();var route=carrier.get(ModDataComponents.FOUP_ROUTING.get());
        result.set(ModDataComponents.FOUP_ROUTING.get(),new FoupRoutingData(route.progress().etched(),route.dieType(),route.cores(),false));
        FoupRouting.refresh(result);return result;
    }
}
