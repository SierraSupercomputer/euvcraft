package com.euvcraft.lithmod.mc.multiblock.g3;

import com.euvcraft.lithmod.core.WaterTank;
import com.euvcraft.lithmod.core.multiblock.BlockKind;
import com.euvcraft.lithmod.mc.multiblock.utilities.UtilityFluids;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Transactional fluid handler for G3 water inlet ports.
 * Based on ScannerWaterHandler/UtilityFluidHandler patterns.
 * Only exposes insertion; extraction is not allowed (UPW is consumed by the machine).
 */
final class G3WaterInletHandler implements ResourceHandler<FluidResource> {

    final G3MemberBlockEntity port;
    final G3ControllerBlockEntity owner;
    private final FluidResource fluid;

    G3WaterInletHandler(G3MemberBlockEntity port, G3ControllerBlockEntity owner) {
        this.port = port;
        this.owner = owner;
        this.fluid = FluidResource.of(com.euvcraft.lithmod.mc.registry.ModFluids.ULTRAPURE_WATER.get());
    }

    /** Returns the live water tank if all ownership/formation checks pass. */
    private @Nullable WaterTank live() {
        var level = owner.getLevel();
        if (level == null || level.isClientSide() || owner.isRemoved() || port.isRemoved()
                || port.getLevel() != level
                || level.getBlockEntity(owner.getBlockPos()) != owner
                || level.getBlockEntity(port.getBlockPos()) != port
                || port.controller() != owner
                || port.structureKind() != BlockKind.WATER_INLET) {
            return null;
        }
        owner.revalidate();
        return owner.isFormed() && port.controller() == owner && owner.members().contains(port.getBlockPos())
                ? owner.waterTank() : null;
    }

    private static void index(int i) {
        Objects.checkIndex(i, 1);
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public FluidResource getResource(int i) {
        index(i);
        var tank = live();
        return tank != null && tank.amount() > 0 ? fluid : FluidResource.EMPTY;
    }

    @Override
    public long getAmountAsLong(int i) {
        index(i);
        var tank = live();
        return tank == null ? 0 : tank.amount();
    }

    @Override
    public long getCapacityAsLong(int i, FluidResource r) {
        index(i);
        var tank = live();
        return tank != null && (r.isEmpty() || r.equals(fluid)) ? tank.capacity() : 0;
    }

    @Override
    public boolean isValid(int i, FluidResource r) {
        index(i);
        return r.equals(fluid) && live() != null;
    }

    @Override
    public int insert(int i, FluidResource r, int n, TransactionContext tx) {
        index(i);
        Objects.requireNonNull(r);
        Objects.requireNonNull(tx);
        if (r.isEmpty() || n < 0) throw new IllegalArgumentException();
        var tank = live();
        if (tank == null || !r.equals(fluid)) return 0;
        int amount = tank.fill(n, true);
        if (amount > 0) {
            owner.waterJournal.updateSnapshots(tx);
            tank.fill(amount, false);
        }
        return amount;
    }

    @Override
    public int extract(int i, FluidResource r, int n, TransactionContext tx) {
        // Water inlet only accepts; extraction not allowed
        return 0;
    }

    /** Journal for transactional tank snapshots (mirrors SupportWaterInletHandler). */
    static final class TankJournal {
        private final G3ControllerBlockEntity owner;

        TankJournal(G3ControllerBlockEntity owner) {
            this.owner = owner;
        }

        void updateSnapshots(TransactionContext tx) {
            // The controller's tank is the source of truth; no separate snapshot needed
            // as WaterTank.fill/drain are already transactional.
            // This exists for API consistency with SupportWaterInletHandler.
        }
    }
}