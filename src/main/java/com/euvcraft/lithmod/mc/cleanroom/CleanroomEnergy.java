package com.euvcraft.lithmod.mc.cleanroom;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
/** A cached service capability never charges a replaced/unloaded machine. */
final class CleanroomEnergy implements EnergyHandler{
 private final FabMachineBlockEntity owner;
 private CleanroomEnergy(FabMachineBlockEntity b){owner=b;}
 static EnergyHandler view(FabMachineBlockEntity b){return new CleanroomEnergy(b);}
 private boolean live(){var l=owner.getLevel();return l!=null&&!l.isClientSide()&&!owner.isRemoved()&&l.hasChunkAt(owner.getBlockPos())&&l.getBlockEntity(owner.getBlockPos())==owner;}
 @Override public long getAmountAsLong(){return live()?owner.energy().getAmountAsLong():0;}
 @Override public long getCapacityAsLong(){return live()?owner.energy().getCapacityAsLong():0;}
 @Override public int insert(int amount,TransactionContext tx){return live()?owner.energy().insert(amount,tx):0;}
 @Override public int extract(int amount,TransactionContext tx){return 0;}
}
