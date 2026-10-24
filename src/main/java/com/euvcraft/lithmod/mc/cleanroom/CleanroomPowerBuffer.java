package com.euvcraft.lithmod.mc.cleanroom;

import com.euvcraft.lithmod.mc.data.MachineDefinitionLoader;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** Real FE endpoint between independent conduit networks. No Arcforge class dependency. */
final class CleanroomPowerBuffer extends SimpleEnergyHandler {
    private final CleanroomPortBlockEntity owner;
    private int pushLimit;
    private boolean reverse;

    CleanroomPowerBuffer(CleanroomPortBlockEntity owner) { super(0, 0, 0); this.owner = owner; }

    void refresh() {
        var registry = MachineDefinitionLoader.getRegistry();
        var definition = registry == null ? null : registry.byId("cleanroom_power_port").orElse(null);
        capacity = definition == null ? 0 : definition.energyCapacity();
        maxInsert = definition == null ? 0 : definition.maxReceive();
        pushLimit = definition == null ? 0 : definition.params().getOrDefault("transfer_per_tick", definition.maxReceive());
        maxExtract = pushLimit;
        // Never clamp stored energy after a data reload lowers the capacity.
    }

    private boolean live(Direction side) {
        var level = owner.getLevel();
        if (level == null || level.isClientSide() || owner.isRemoved()
                || !level.hasChunkAt(owner.getBlockPos()) || level.getBlockEntity(owner.getBlockPos()) != owner
                || CleanroomPortBlock.detectRole(owner.getBlockState()) != CleanroomPortBlock.PortRole.POWER) return false;
        var facing = owner.getBlockState().getValue(CleanroomPortBlock.FACING);
        return side == facing || side == facing.getOpposite();
    }

    EnergyHandler view(Direction side) {
        return new EnergyHandler() {
            @Override public long getAmountAsLong() { return live(side) ? CleanroomPowerBuffer.this.getAmountAsLong() : 0; }
            @Override public long getCapacityAsLong() { if (!live(side)) return 0; refresh(); return CleanroomPowerBuffer.this.getCapacityAsLong(); }
            @Override public int insert(int amount, TransactionContext tx) { if (!live(side)) return 0; refresh(); return CleanroomPowerBuffer.this.insert(amount, tx); }
            @Override public int extract(int amount, TransactionContext tx) { if (!live(side)) return 0; refresh(); return CleanroomPowerBuffer.this.extract(amount, tx); }
        };
    }

    void tick() {
        var facing = owner.getBlockState().getValue(CleanroomPortBlock.FACING);
        if (!live(facing)) return;
        refresh();
        int budget = pushLimit;
        var first = reverse ? facing : facing.getOpposite();
        reverse = !reverse;
        // Alternate priority so a bidirectional storage endpoint cannot starve the other side.
        budget -= push(first, budget);
        if (budget > 0) push(first.getOpposite(), budget);
    }

    private int push(Direction direction, int budget) {
        if (budget <= 0 || getAmountAsInt() <= 0) return 0;
        var level = owner.getLevel();
        var targetPos = owner.getBlockPos().relative(direction);
        if (level == null || !level.hasChunkAt(targetPos)) return 0;
        var target = level.getCapability(Capabilities.Energy.BLOCK, targetPos, direction.getOpposite());
        if (target == null) return 0; // Conduits pull from our exposed buffer using their own network tick.
        try (var tx = Transaction.openRoot()) {
            int inserted = target.insert(Math.min(budget, getAmountAsInt()), tx);
            if (inserted <= 0 || extract(inserted, tx) != inserted) return 0;
            tx.commit();
            return inserted;
        }
    }

    @Override protected void onEnergyChanged(int previousAmount) { owner.setChanged(); }
}
