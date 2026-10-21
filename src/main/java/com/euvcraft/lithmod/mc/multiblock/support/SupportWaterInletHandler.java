package com.euvcraft.lithmod.mc.multiblock.support;

import com.euvcraft.lithmod.core.WaterTank;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;
import java.util.Objects;

/** Per-inlet lens over the controller's ONE journal and existing bucket/process tank.
 * Transactions are synchronous server-thread operations, as in Arcforge's conduit network.
 * This object captures membership identity: a retained old lens cannot follow a different owner.
 */
final class SupportWaterInletHandler implements ResourceHandler<FluidResource> {
    private static final FluidResource WATER = FluidResource.of(Fluids.WATER);
    private final SupportMemberBlockEntity member;
    private final SupportMachineBlockEntity owner;
    SupportWaterInletHandler(SupportMemberBlockEntity member, SupportMachineBlockEntity owner) {
        this.member = member; this.owner = owner;
    }
    private @Nullable WaterTank liveTank() {
        var level = member.getLevel();
        if (level == null || level.isClientSide() || member.isRemoved() || owner.isRemoved()
            || owner.getLevel() != level || level.getBlockEntity(member.getBlockPos()) != member
            || level.getBlockEntity(owner.getBlockPos()) != owner
            || !(member.getBlockState().getBlock() instanceof SupportWaterInletBlock)
            || member.controller() != owner || !member.isMemberOfFormedMachine()
            || owner.kind() != SupportMachineKind.PURIFIER) return null;
        // A caller may retain this lens across edits without neighbour notification.
        owner.revalidate();
        return owner.isFormed() && member.controller() == owner && member.isMemberOfFormedMachine()
            && owner.members().contains(member.getBlockPos()) ? owner.waterTank() : null;
    }
    private static void index(int index) { Objects.checkIndex(index, 1); }
    private static void transfer(FluidResource resource, int amount) {
        Objects.requireNonNull(resource);
        if (resource.isEmpty() || amount < 0) throw new IllegalArgumentException("nonempty resource and nonnegative amount required");
    }
    @Override public int size() { return 1; }
    @Override public FluidResource getResource(int index) {
        index(index); var tank = liveTank(); return tank != null && tank.amount() > 0 ? WATER : FluidResource.EMPTY;
    }
    @Override public long getAmountAsLong(int index) { index(index); var tank = liveTank(); return tank == null ? 0 : tank.amount(); }
    @Override public long getCapacityAsLong(int index, FluidResource resource) {
        index(index); var tank = liveTank(); return tank != null && (resource.isEmpty() || WATER.equals(resource)) ? tank.capacity() : 0;
    }
    @Override public boolean isValid(int index, FluidResource resource) { index(index); return WATER.equals(resource) && liveTank() != null; }
    @Override public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        index(index); transfer(resource, amount); Objects.requireNonNull(transaction);
        var tank = liveTank();
        if (tank == null || !WATER.equals(resource) || amount == 0) return 0;
        int accepted = tank.fill(amount, true);
        if (accepted > 0) { owner.waterJournal.updateSnapshots(transaction); tank.fill(accepted, false); }
        return accepted;
    }
    @Override public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        index(index); transfer(resource, amount); Objects.requireNonNull(transaction); return 0;
    }
    /** A single journal per controller, shared by every inlet and side. */
    static final class TankJournal extends SnapshotJournal<WaterSnapshot> {
        private final SupportMachineBlockEntity owner;
        TankJournal(SupportMachineBlockEntity owner) { this.owner = owner; }
        @Override protected WaterSnapshot createSnapshot() {
            WaterTank tank = Objects.requireNonNull(owner.waterTank()); return new WaterSnapshot(tank, tank.amount());
        }
        @Override protected void revertToSnapshot(WaterSnapshot snapshot) { snapshot.tank().restoreAmount(snapshot.amount()); }
        @Override protected void onRootCommit(WaterSnapshot original) {
            var level = owner.getLevel();
            if (level != null && !owner.isRemoved() && level.getBlockEntity(owner.getBlockPos()) == owner) owner.setChanged();
        }
    }
    private record WaterSnapshot(WaterTank tank, int amount) {}
}
