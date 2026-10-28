package com.euvcraft.lithmod.mc.multiblock.euv;

import com.euvcraft.lithmod.mc.multiblock.utilities.UtilityFluids;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import java.util.Objects;

/** Fluid handler for EUV Light Source tin inlet (uses UPW fluid as placeholder for tin). */
public final class EuvLightSourceTinHandler implements ResourceHandler<FluidResource> {

    private final EuvLightSourceMemberBlockEntity port;
    private final EuvLightSourceControllerBlockEntity owner;
    private final FluidResource fluid = FluidResource.of(UtilityFluids.ULTRAPURE_WATER.get()); // Placeholder

    public EuvLightSourceTinHandler(EuvLightSourceMemberBlockEntity port, EuvLightSourceControllerBlockEntity owner) {
        this.port = port;
        this.owner = owner;
    }

    private EuvLightSourceControllerBlockEntity live() {
        var level = owner.getLevel();
        if (level == null || level.isClientSide() || owner.isRemoved() || port.isRemoved()
                || port.getLevel() != level
                || level.getBlockEntity(owner.getBlockPos()) != owner
                || level.getBlockEntity(port.getBlockPos()) != port
                || port.controller() != owner
                || port.structureKind() != com.euvcraft.lithmod.core.multiblock.BlockKind.FLUID_OUTLET) return null;
        owner.revalidate();
        return owner.isFormed() && owner.members().contains(port.getBlockPos()) ? owner : null;
    }

    private static void index(int i) { Objects.checkIndex(i, 1); }

    @Override public int size() { return 1; }

    @Override public FluidResource getResource(int i) {
        index(i);
        return FluidResource.EMPTY; // Tin is item-based, not fluid
    }

    @Override public long getAmountAsLong(int i) {
        index(i);
        var ls = live();
        return ls == null ? 0 : ls.tinStored();
    }

    @Override public long getCapacityAsLong(int i, FluidResource r) {
        index(i);
        var ls = live();
        return ls != null ? 50000 : 0;
    }

    @Override public boolean isValid(int i, FluidResource r) {
        index(i);
        return live() != null;
    }

    @Override public int insert(int i, FluidResource r, int n, TransactionContext tx) {
        index(i);
        Objects.requireNonNull(r);
        Objects.requireNonNull(tx);
        if (r.isEmpty() || n < 0) throw new IllegalArgumentException();
        var ls = live();
        if (ls == null) return 0;
        int accepted = ls.insertTin(n, true);
        if (accepted > 0) {
            ls.insertTin(accepted, false);
        }
        return accepted;
    }

    @Override public int extract(int i, FluidResource r, int n, TransactionContext tx) {
        index(i);
        Objects.requireNonNull(r);
        Objects.requireNonNull(tx);
        if (r.isEmpty() || n < 0) throw new IllegalArgumentException();
        return 0;
    }
}