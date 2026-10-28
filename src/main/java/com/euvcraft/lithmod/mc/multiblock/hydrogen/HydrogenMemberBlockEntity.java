package com.euvcraft.lithmod.mc.multiblock.hydrogen;

import com.euvcraft.lithmod.core.WaterTank;
import com.euvcraft.lithmod.core.multiblock.BlockKind;
import com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedMemberBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedOwner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Shared member block entity for the hydrogen purifier: casing, power port, gas inlet, gas outlet, operating panel.
 * Extends AdvancedMemberBlockEntity directly (UtilityPortBlockEntity is final).
 * Uses the new NeoForge transfer API (ResourceHandler<FluidResource>) for fluid handling.
 */
public final class HydrogenMemberBlockEntity extends AdvancedMemberBlockEntity {

    private final BlockKind kind;

    public HydrogenMemberBlockEntity(BlockPos pos, BlockState state) {
        super(HydrogenMachines.MEMBER_BE.get(), pos, state);
        this.kind = computeKind();
    }


    @Override public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inventory, net.minecraft.world.entity.player.Player player) {
        if (structureKind() != BlockKind.OPERATING_PANEL) return null;
        var below = belowOwner();
        return below instanceof HydrogenControllerBlockEntity owner ? new HydrogenMenu(id, inventory, owner) : null;
    }

    private BlockKind computeKind() {
        var block = getBlockState().getBlock();
        if (block instanceof HydrogenMemberBlock hmb) {
            return hmb.kind();
        }
        return BlockKind.OTHER;
    }

    public BlockKind kind() {
        return kind;
    }

    @Override public boolean supportsOwner(AdvancedOwner owner) {
        return owner instanceof HydrogenControllerBlockEntity;
    }

    @Override
    protected boolean energyPort() {
        return kind == BlockKind.POWER_PORT;
    }

    @Override
    public com.euvcraft.lithmod.core.multiblock.BlockKind structureKind() {
        return kind;
    }

    /** Returns the fluid handler capability for gas inlet/outlet, or null for other kinds. */
    public @Nullable ResourceHandler<FluidResource> handler() {
        if (kind != BlockKind.GAS_INLET && kind != BlockKind.GAS_OUTLET) {
            return null;
        }
        var owner = controller();
        if (owner instanceof HydrogenControllerBlockEntity controller) {
            return new HydrogenFluidHandler(this, controller);
        }
        return null;
    }

    /**
     * Fluid handler for gas inlet (accepts raw H2) and gas outlet (outputs fab H2).
     * Uses the new NeoForge transfer API (ResourceHandler<FluidResource>).
     */
    public static final class HydrogenFluidHandler implements ResourceHandler<FluidResource> {

        private final HydrogenMemberBlockEntity member;
        private final HydrogenControllerBlockEntity expected;
        private final FluidResource fabFluid;
        private @Nullable FluidResource rawFluid;

        HydrogenFluidHandler(HydrogenMemberBlockEntity member, HydrogenControllerBlockEntity expected) {
            this.expected = expected;
            this.member = member;
            // Fabricated hydrogen from this mod
            this.fabFluid = FluidResource.of(HydrogenFluids.FAB_HYDROGEN.get());
            // Raw hydrogen from Arcforge - resolved lazily
            this.rawFluid = null;
        }

        private @Nullable FluidResource rawFluid() {
            if (rawFluid != null) return rawFluid;
            return HydrogenFluids.rawHydrogenFluid().map(FluidResource::of).orElse(null);
        }

        private @Nullable HydrogenControllerBlockEntity liveController() {
            var level = member.getLevel();
            if (level == null || level.isClientSide() || member.isRemoved() || level.getBlockEntity(member.getBlockPos()) != member) return null;
            if (!(member.controller() instanceof HydrogenControllerBlockEntity owner) || owner != expected || owner.isRemoved() || !level.hasChunkAt(owner.getBlockPos()) || level.getBlockEntity(owner.getBlockPos()) != owner) return null;
            owner.revalidate();
            return member.controller() == owner && member.isMemberOfFormedCell() && owner.isFormed() && owner.members().contains(member.getBlockPos()) ? owner : null;
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public FluidResource getResource(int index) {
            Objects.checkIndex(index, 1);
            var controller = liveController();
            if (controller == null) return FluidResource.EMPTY;
            if (member.kind == BlockKind.GAS_INLET) {
                // Inlet exposes raw hydrogen if available
                var rf = rawFluid();
                return rf != null && controller.rawHydrogen.amount() > 0 ? rf : FluidResource.EMPTY;
            } else if (member.kind == BlockKind.GAS_OUTLET) {
                // Outlet exposes fab hydrogen if available
                return controller.pureHydrogen.amount() > 0 ? fabFluid : FluidResource.EMPTY;
            }
            return FluidResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int index) {
            Objects.checkIndex(index, 1);
            var controller = liveController();
            if (controller == null) return 0;
            if (member.kind == BlockKind.GAS_INLET) {
                return controller.rawHydrogen.amount();
            } else if (member.kind == BlockKind.GAS_OUTLET) {
                return controller.pureHydrogen.amount();
            }
            return 0;
        }

        @Override
        public long getCapacityAsLong(int index, FluidResource resource) {
            Objects.checkIndex(index, 1);
            var controller = liveController();
            if (controller == null) return 0;
            if (member.kind == BlockKind.GAS_INLET) {
                var rf = rawFluid();
                return rf != null && (resource.isEmpty() || resource.equals(rf)) ? controller.rawHydrogen.capacity() : 0;
            } else if (member.kind == BlockKind.GAS_OUTLET) {
                return resource.isEmpty() || resource.equals(fabFluid) ? controller.pureHydrogen.capacity() : 0;
            }
            return 0;
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            Objects.checkIndex(index, 1);
            var controller = liveController();
            if (controller == null) return false;
            if (member.kind == BlockKind.GAS_INLET) {
                var rf = rawFluid();
                return rf != null && resource.equals(rf);
            } else if (member.kind == BlockKind.GAS_OUTLET) {
                return resource.equals(fabFluid);
            }
            return false;
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            Objects.checkIndex(index, 1);
            Objects.requireNonNull(resource);
            Objects.requireNonNull(transaction);
            if (resource.isEmpty() || amount < 0) return 0;
            var controller = liveController();
            if (controller == null || member.kind != BlockKind.GAS_INLET) return 0;
            var rf = rawFluid();
            if (rf == null || !resource.equals(rf)) return 0;
            int filled = controller.rawHydrogen.fill(amount, true);
            if (filled > 0) {
                controller.rawJournal.updateSnapshots(transaction);
                controller.rawHydrogen.fill(filled, false);
                member.setChanged();
            }
            return filled;
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            Objects.checkIndex(index, 1);
            Objects.requireNonNull(resource);
            Objects.requireNonNull(transaction);
            if (resource.isEmpty() || amount < 0) return 0;
            var controller = liveController();
            if (controller == null || member.kind != BlockKind.GAS_OUTLET) return 0;
            if (!resource.equals(fabFluid)) return 0;
            int available = controller.pureHydrogen.amount();
            int drained = Math.min(amount, available);
            if (drained > 0) {
                controller.pureJournal.updateSnapshots(transaction);
                controller.pureHydrogen.drainExact(drained, false);
                member.setChanged();
            }
            return drained;
        }
    }
}