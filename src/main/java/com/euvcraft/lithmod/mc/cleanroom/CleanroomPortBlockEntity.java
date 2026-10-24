package com.euvcraft.lithmod.mc.cleanroom;


import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Block entity for cleanroom ports. Power uses a saved transactional bridge buffer; item/fluid handlers forward along the
 * facing axis to the adjacent block on the opposite side.
 *
 * <p>Each port role (power, fluid, item) exposes exactly one capability matching its type.
 * The handler is a dynamic lens — it does not cache the neighbor or its capability, and
 * re-queries on every operation. If the port is removed, the neighbor is unloaded/replaced,
 * or the capability changes, operations immediately refuse or return empty without loading chunks.</p>
 *
 * <p>Recursion across chained or mutually-facing ports is guarded using a bounded
 * {@link ThreadLocal} key for each transfer type, preventing infinite loops and duplicate
 * resource/FE accounting.</p>
 *
 * <p>World shutdown ({@link #setRemoved()}) does not inspect neighbors or chunks.</p>
 */
public class CleanroomPortBlockEntity extends BlockEntity {

    /** Independent power endpoint lets two conduit networks exchange FE across the wall. */
    private final CleanroomPowerBuffer power = new CleanroomPowerBuffer(this);
    /** Thread-local recursion guard for fluid transfers. */
    private static final ThreadLocal<Integer> FLUID_DEPTH = ThreadLocal.withInitial(() -> 0);
    /** Thread-local recursion guard for item transfers. */
    private static final ThreadLocal<Integer> ITEM_DEPTH = ThreadLocal.withInitial(() -> 0);
    /** Maximum allowed port chain depth. */
    private static final int MAX_CHAIN_DEPTH = 8;

    public CleanroomPortBlockEntity(BlockPos pos, BlockState state) {
        super(CleanroomBlocks.PORT_BE.get(), pos, state);
    }

    /** Returns the port role for this block entity's block state. */
    private CleanroomPortBlock.PortRole role() {
        return CleanroomPortBlock.detectRole(getBlockState());
    }

    /** Returns the facing direction (points from outside into the cleanroom). */
    private Direction facing() {
        return getBlockState().getValue(CleanroomPortBlock.FACING);
    }

    /** Returns the side on which this port receives queries (the outside face). */
    private Direction outsideSide() {
        return facing();
    }

    /** Returns the side on which this port forwards queries (the inside face). */
    private Direction insideSide() {
        return facing().getOpposite();
    }

    /**
     * Returns the energy handler for the given side, or null if this is not a power port
     * or the side is not on the through-wall axis.
     */
    public @Nullable EnergyHandler energy(@Nullable Direction side) {
        if (role() != CleanroomPortBlock.PortRole.POWER) return null;
        if (side == null || side != outsideSide() && side != insideSide()) return null; // both axis faces, no transverse/null
        power.refresh();
        return power.view(side);
    }

    /**
     * Returns the fluid handler for the given side, or null if this is not a fluid port
     * or the side is not on the through-wall axis.
     */
    public @Nullable ResourceHandler<FluidResource> fluids(@Nullable Direction side) {
        if (role() != CleanroomPortBlock.PortRole.FLUID) return null;
        if (side == null || side != outsideSide() && side != insideSide()) return null;
        return new ForwardingFluidHandler(this, side);
    }

    /**
     * Returns the item handler for the given side, or null if this is not an item port
     * or the side is not on the through-wall axis.
     */
    public @Nullable ResourceHandler<ItemResource> items(@Nullable Direction side) {
        if (role() != CleanroomPortBlock.PortRole.ITEM) return null;
        if (side == null || side != outsideSide() && side != insideSide()) return null;
        return new ForwardingItemHandler(this, side);
    }

    /** Server tick — currently no-op, but kept for symmetry with other port BEs. */
    public void serverTick() {
        if (role() == CleanroomPortBlock.PortRole.POWER) power.tick();
    }

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput out) {
        super.saveAdditional(out); out.putInt("portEnergy", power.getAmountAsInt());
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput in) {
        super.loadAdditional(in); power.refresh(); power.set(Math.max(0, in.getIntOr("portEnergy", 0)));
    }

    @Override
    public void setRemoved() {
        // Do NOT inspect neighbors or chunks on world shutdown.
        // The dynamic lenses will naturally return empty/null on subsequent calls.
        super.setRemoved();
    }

    // ============================================================
    // Forwarding Energy Handler
    // ============================================================

    // ============================================================
    // Forwarding Fluid Handler
    // ============================================================

    private static final class ForwardingFluidHandler implements ResourceHandler<FluidResource> {
        private final CleanroomPortBlockEntity port;
        private final Direction side;

        ForwardingFluidHandler(CleanroomPortBlockEntity port, Direction side) {
            this.port = port; this.side = side;
        }

        private @Nullable ResourceHandler<FluidResource> neighborHandler() {
            Level level = port.getLevel();
            if (level == null || level.isClientSide() || port.isRemoved() || !level.hasChunkAt(port.getBlockPos()) || level.getBlockEntity(port.getBlockPos()) != port) return null;
            if(port.role()!=CleanroomPortBlock.PortRole.FLUID || side!=port.insideSide()&&side!=port.outsideSide())return null;
            BlockPos neighborPos = port.worldPosition.relative(side.getOpposite());
            if (!level.hasChunkAt(neighborPos)) return null;
            return level.getCapability(Capabilities.Fluid.BLOCK, neighborPos, side);
        }

        private boolean enterRecursion() {
            int depth = FLUID_DEPTH.get();
            if (depth >= MAX_CHAIN_DEPTH) return false;
            FLUID_DEPTH.set(depth + 1);
            return true;
        }

        private void exitRecursion() {
            FLUID_DEPTH.set(FLUID_DEPTH.get() - 1);
        }

        @Override
        public int size() {
            if (!enterRecursion()) return 0;
            try {
                ResourceHandler<FluidResource> handler = neighborHandler();
                return handler != null ? handler.size() : 0;
            } finally {
                exitRecursion();
            }
        }

        @Override
        public FluidResource getResource(int index) {
            if (!enterRecursion()) return FluidResource.EMPTY;
            try {
                ResourceHandler<FluidResource> handler = neighborHandler();
                return handler != null ? handler.getResource(index) : FluidResource.EMPTY;
            } finally {
                exitRecursion();
            }
        }

        @Override
        public long getAmountAsLong(int index) {
            if (!enterRecursion()) return 0L;
            try {
                ResourceHandler<FluidResource> handler = neighborHandler();
                return handler != null ? handler.getAmountAsLong(index) : 0L;
            } finally {
                exitRecursion();
            }
        }

        @Override
        public long getCapacityAsLong(int index, FluidResource resource) {
            if (!enterRecursion()) return 0L;
            try {
                ResourceHandler<FluidResource> handler = neighborHandler();
                return handler != null ? handler.getCapacityAsLong(index, resource) : 0L;
            } finally {
                exitRecursion();
            }
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            if (!enterRecursion()) return false;
            try {
                ResourceHandler<FluidResource> handler = neighborHandler();
                return handler != null && handler.isValid(index, resource);
            } finally {
                exitRecursion();
            }
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            Objects.requireNonNull(resource);
            Objects.requireNonNull(transaction);
            if (resource.isEmpty() || amount <= 0) return 0;
            if (!enterRecursion()) return 0;
            try {
                ResourceHandler<FluidResource> handler = neighborHandler();
                return handler != null ? handler.insert(index, resource, amount, transaction) : 0;
            } finally {
                exitRecursion();
            }
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            Objects.requireNonNull(resource);
            Objects.requireNonNull(transaction);
            if (resource.isEmpty() || amount <= 0) return 0;
            if (!enterRecursion()) return 0;
            try {
                ResourceHandler<FluidResource> handler = neighborHandler();
                return handler != null ? handler.extract(index, resource, amount, transaction) : 0;
            } finally {
                exitRecursion();
            }
        }
    }

    // ============================================================
    // Forwarding Item Handler
    // ============================================================

    private static final class ForwardingItemHandler implements ResourceHandler<ItemResource> {
        private final CleanroomPortBlockEntity port;
        private final Direction side;

        ForwardingItemHandler(CleanroomPortBlockEntity port, Direction side) {
            this.port = port; this.side = side;
        }

        private @Nullable ResourceHandler<ItemResource> neighborHandler() {
            Level level = port.getLevel();
            if (level == null || level.isClientSide() || port.isRemoved() || !level.hasChunkAt(port.getBlockPos()) || level.getBlockEntity(port.getBlockPos()) != port) return null;
            if(port.role()!=CleanroomPortBlock.PortRole.ITEM || side!=port.insideSide()&&side!=port.outsideSide())return null;
            BlockPos neighborPos = port.worldPosition.relative(side.getOpposite());
            if (!level.hasChunkAt(neighborPos)) return null;
            return level.getCapability(Capabilities.Item.BLOCK, neighborPos, side);
        }

        private boolean enterRecursion() {
            int depth = ITEM_DEPTH.get();
            if (depth >= MAX_CHAIN_DEPTH) return false;
            ITEM_DEPTH.set(depth + 1);
            return true;
        }

        private void exitRecursion() {
            ITEM_DEPTH.set(ITEM_DEPTH.get() - 1);
        }

        @Override
        public int size() {
            if (!enterRecursion()) return 0;
            try {
                ResourceHandler<ItemResource> handler = neighborHandler();
                return handler != null ? handler.size() : 0;
            } finally {
                exitRecursion();
            }
        }

        @Override
        public ItemResource getResource(int index) {
            if (!enterRecursion()) return ItemResource.EMPTY;
            try {
                ResourceHandler<ItemResource> handler = neighborHandler();
                return handler != null ? handler.getResource(index) : ItemResource.EMPTY;
            } finally {
                exitRecursion();
            }
        }

        @Override
        public long getAmountAsLong(int index) {
            if (!enterRecursion()) return 0L;
            try {
                ResourceHandler<ItemResource> handler = neighborHandler();
                return handler != null ? handler.getAmountAsLong(index) : 0L;
            } finally {
                exitRecursion();
            }
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            if (!enterRecursion()) return 0L;
            try {
                ResourceHandler<ItemResource> handler = neighborHandler();
                return handler != null ? handler.getCapacityAsLong(index, resource) : 0L;
            } finally {
                exitRecursion();
            }
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            if (!enterRecursion()) return false;
            try {
                ResourceHandler<ItemResource> handler = neighborHandler();
                return handler != null && handler.isValid(index, resource);
            } finally {
                exitRecursion();
            }
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            Objects.requireNonNull(resource);
            Objects.requireNonNull(transaction);
            if (resource.isEmpty() || amount <= 0) return 0;
            if (!enterRecursion()) return 0;
            try {
                ResourceHandler<ItemResource> handler = neighborHandler();
                return handler != null ? handler.insert(index, resource, amount, transaction) : 0;
            } finally {
                exitRecursion();
            }
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            Objects.requireNonNull(resource);
            Objects.requireNonNull(transaction);
            if (resource.isEmpty() || amount <= 0) return 0;
            if (!enterRecursion()) return 0;
            try {
                ResourceHandler<ItemResource> handler = neighborHandler();
                return handler != null ? handler.extract(index, resource, amount, transaction) : 0;
            } finally {
                exitRecursion();
            }
        }
    }
}