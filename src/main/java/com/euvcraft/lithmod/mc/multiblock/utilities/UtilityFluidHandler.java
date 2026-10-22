package com.euvcraft.lithmod.mc.multiblock.utilities;
import com.euvcraft.lithmod.core.WaterTank;import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.ResourceHandler;import net.neoforged.neoforge.transfer.fluid.FluidResource;import net.neoforged.neoforge.transfer.transaction.TransactionContext;import java.util.Objects;
/** A stale port lens never follows a replacement or newly linked controller. */
final class UtilityFluidHandler implements ResourceHandler<FluidResource>{
 final UtilityPortBlockEntity port;final UtilityControllerBlockEntity owner;final boolean inlet;final FluidResource fluid;
 UtilityFluidHandler(UtilityPortBlockEntity port,UtilityControllerBlockEntity owner,boolean inlet){this.port=port;this.owner=owner;this.inlet=inlet;fluid=FluidResource.of(inlet?Fluids.WATER:UtilityFluids.ULTRAPURE_WATER.get());}
 private WaterTank live(){var level=owner.getLevel();if(level==null||level.isClientSide()||port.isRemoved()||owner.isRemoved()||port.getLevel()!=level||level.getBlockEntity(owner.getBlockPos())!=owner||level.getBlockEntity(port.getBlockPos())!=port||port.controller()!=owner)return null;owner.revalidate();return owner.isFormed()&&port.controller()==owner&&owner.members().contains(port.getBlockPos())&&(inlet?port.structureKind()==com.euvcraft.lithmod.core.multiblock.BlockKind.WATER_INLET:port.structureKind()==com.euvcraft.lithmod.core.multiblock.BlockKind.FLUID_OUTLET)?(inlet?owner.raw:owner.pure):null;}
 private static void index(int i){Objects.checkIndex(i,1);}private static void transfer(FluidResource r,int n,TransactionContext tx){Objects.requireNonNull(r);Objects.requireNonNull(tx);if(r.isEmpty()||n<0)throw new IllegalArgumentException();}
 @Override public int size(){return 1;}@Override public FluidResource getResource(int i){index(i);var tank=live();return tank!=null&&tank.amount()>0?fluid:FluidResource.EMPTY;}
 @Override public long getAmountAsLong(int i){index(i);var tank=live();return tank==null?0:tank.amount();}@Override public long getCapacityAsLong(int i,FluidResource r){index(i);var tank=live();return tank!=null&&(r.isEmpty()||r.equals(fluid))?tank.capacity():0;}
 @Override public boolean isValid(int i,FluidResource r){index(i);return fluid.equals(r)&&live()!=null;}
 @Override public int insert(int i,FluidResource r,int n,TransactionContext tx){index(i);transfer(r,n,tx);var tank=live();if(!inlet||tank==null||!r.equals(fluid))return 0;int amount=tank.fill(n,true);if(amount>0){owner.rawJournal.updateSnapshots(tx);tank.fill(amount,false);}return amount;}
 @Override public int extract(int i,FluidResource r,int n,TransactionContext tx){index(i);transfer(r,n,tx);var tank=live();if(inlet||tank==null||!r.equals(fluid))return 0;int amount=Math.min(n,tank.amount());if(amount>0){owner.pureJournal.updateSnapshots(tx);tank.drainExact(amount,false);}return amount;}
}


