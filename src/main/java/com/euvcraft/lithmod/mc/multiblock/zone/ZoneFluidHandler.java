package com.euvcraft.lithmod.mc.multiblock.zone;

import com.euvcraft.lithmod.core.WaterTank;
import com.euvcraft.lithmod.core.multiblock.BlockKind;
import com.euvcraft.lithmod.mc.multiblock.utilities.UtilityFluids;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * The zone water inlet's ultrapure-water lens over the controller's one UPW tank and its
 * one journal, shared with every other view of the tank.
 *
 * <p>Only {@link UtilityFluids#ULTRAPURE_WATER} is accepted. Ordinary water is refused and
 * UPW has no bucket, so there is no bucket conversion path either. Extraction is always
 * zero: the zone has no outlet kind. A stale lens — a replaced controller, a re-linked
 * port, an unformed machine or an unloaded chunk — fails closed and never follows a
 * replacement or a newly linked owner, exactly like the utility plant's lens.</p>
 */
final class ZoneFluidHandler implements ResourceHandler<FluidResource> {
    private static final FluidResource ULTRAPURE = FluidResource.of(UtilityFluids.ULTRAPURE_WATER.get());

    final ZoneMemberBlockEntity port;
    final ZoneControllerBlockEntity owner;

    ZoneFluidHandler(ZoneMemberBlockEntity port, ZoneControllerBlockEntity owner) {
        this.port = port;
        this.owner = owner;
    }

    private @Nullable WaterTank live() {
        var level = owner.getLevel();
        if (level == null || level.isClientSide() || port.isRemoved() || owner.isRemoved()
            || port.getLevel() != level
            || level.getBlockEntity(owner.getBlockPos()) != owner
            || level.getBlockEntity(port.getBlockPos()) != port
            || port.controller() != owner) return null;
        // A caller may retain this lens across edits without neighbour notification.
        owner.revalidate();
        return owner.isFormed() && port.controller() == owner
            && owner.members().contains(port.getBlockPos())
            && port.structureKind() == BlockKind.WATER_INLET
            ? owner.upw
            : null;
    }

    private static void index(int i) { Objects.checkIndex(i, 1); }

    private static void transfer(FluidResource r, int n, TransactionContext tx) {
        Objects.requireNonNull(r);
        Objects.requireNonNull(tx);
        if (r.isEmpty() || n < 0) throw new IllegalArgumentException();
    }

    @Override public int size() { return 1; }

    @Override public FluidResource getResource(int i) {
        index(i);
        var tank = live();
        return tank != null && tank.amount() > 0 ? ULTRAPURE : FluidResource.EMPTY;
    }

    @Override public long getAmountAsLong(int i) {
        index(i);
        var tank = live();
        return tank == null ? 0 : tank.amount();
    }

    @Override public long getCapacityAsLong(int i, FluidResource r) {
        index(i);
        var tank = live();
        return tank != null && (r.isEmpty() || r.equals(ULTRAPURE)) ? tank.capacity() : 0;
    }

    @Override public boolean isValid(int i, FluidResource r) {
        index(i);
        return ULTRAPURE.equals(r) && live() != null;
    }

    @Override public int insert(int i, FluidResource r, int n, TransactionContext tx) {
        index(i);
        transfer(r, n, tx);
        var tank = live();
        if (tank == null || !r.equals(ULTRAPURE)) return 0;
        int amount = tank.fill(n, true);
        if (amount > 0) {
            owner.waterJournal.updateSnapshots(tx);
            tank.fill(amount, false);
        }
        return amount;
    }

    @Override public int extract(int i, FluidResource r, int n, TransactionContext tx) {
        index(i);
        transfer(r, n, tx);
        Objects.requireNonNull(tx);
        return 0;
    }
}
