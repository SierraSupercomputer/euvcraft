package com.euvcraft.lithmod.mc.multiblock;

import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** Retained T7 port handles close when either endpoint, role or ownership changes. */
public final class T7EnergyPort {
    private T7EnergyPort() {}
    public static EnergyHandler view(BlockEntity port,FabMachineBlockEntity owner,BooleanSupplier linked) {
        Objects.requireNonNull(port);Objects.requireNonNull(owner);Objects.requireNonNull(linked);
        return new EnergyHandler() {
            private boolean valid() {
                var level=port.getLevel();
                return level!=null&&!level.isClientSide()&&!port.isRemoved()&&!owner.isRemoved()
                    &&owner.getLevel()==level&&level.hasChunkAt(port.getBlockPos())&&level.hasChunkAt(owner.getBlockPos())
                    &&level.getBlockEntity(port.getBlockPos())==port&&level.getBlockEntity(owner.getBlockPos())==owner
                    &&linked.getAsBoolean();
            }
            @Override public long getAmountAsLong(){return valid()?owner.energy().getAmountAsLong():0;}
            @Override public long getCapacityAsLong(){return valid()?owner.energy().getCapacityAsLong():0;}
            @Override public int insert(int amount,TransactionContext transaction){return valid()?owner.energy().insert(amount,transaction):0;}
            @Override public int extract(int amount,TransactionContext transaction){return 0;}
        };
    }
}
