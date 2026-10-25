package com.euvcraft.lithmod.mc.cleanroom;

import com.euvcraft.lithmod.mc.data.MachineDefinitionLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

/**
 * Saved transactional resource buffers for cleanroom item/fluid ports.
 * <p>
 * Each buffer exposes a NeoForge {@link ResourceHandler} on the two axis faces
 * (facing and opposite). Transverse and null faces are refused. The buffer
 * is bidirectional and shared: conduits push/pull generic capabilities on both
 * sides. No production Arcforge dependency exists.
 * </p>
 *
 * <p>
 * Each server tick the buffer offers its contents to both axis neighbor handlers,
 * alternating priority, with one shared budget. It does NOT automatically pull
 * resources from neighbors. All moves use {@link ResourceHandlerUtil} and
 * {@link Transaction} to atomically move resources, preventing duplication and
 * honoring nested rollback.
 * </p>
 *
 * <p>
 * Retained capability views reject changed role/axis, removed/replaced/unloaded
 * owner, following the {@link CleanroomPowerBuffer} live guard pattern.
 * </p>
 */
public final class CleanroomResourceBuffer {

    /**
     * Transactional item buffer backed by {@link ItemStacksResourceHandler} (9 slots).
     */
    public static final class ItemBuffer extends ItemStacksResourceHandler {
        private final CleanroomPortBlockEntity owner;
        private int pushLimit;
        private boolean reverse;

        public ItemBuffer(CleanroomPortBlockEntity owner) {
            super(9);
            this.owner = owner;
        }

        /**
         * Refreshes capacity and transfer limits from the machine definition registry.
         */
        public void refresh() {
            var registry = MachineDefinitionLoader.getRegistry();
            var definition = registry == null ? null : registry.byId("cleanroom_item_port").orElse(null);
            pushLimit = definition == null ? 0 : definition.paramOrDefault("transfer_per_tick", 0);
            // ItemStacksResourceHandler uses per-slot max stack size; no separate capacity field needed.
        }

        /**
         * Checks if the given side is a live axis face for this item port.
         */
        private boolean live(Direction side) {
            var level = owner.getLevel();
            if (level == null || level.isClientSide() || owner.isRemoved()
                    || !level.hasChunkAt(owner.getBlockPos()) || level.getBlockEntity(owner.getBlockPos()) != owner
                    || CleanroomPortBlock.detectRole(owner.getBlockState()) != CleanroomPortBlock.PortRole.ITEM) return false;
            var facing = owner.getBlockState().getValue(CleanroomPortBlock.FACING);
            return side == facing || side == facing.getOpposite();
        }

        /**
         * Returns a capability view for the given side, or null if not on the axis.
         */
        public @Nullable ResourceHandler<ItemResource> view(Direction side) {
            return new ResourceHandler<>() {
                @Override
                public int size() {
                    return live(side) ? ItemBuffer.this.size() : 0;
                }

                @Override
                public ItemResource getResource(int index) {
                    return live(side) ? ItemBuffer.this.getResource(index) : ItemResource.EMPTY;
                }

                @Override
                public long getAmountAsLong(int index) {
                    return live(side) ? ItemBuffer.this.getAmountAsLong(index) : 0L;
                }

                @Override
                public long getCapacityAsLong(int index, ItemResource resource) {
                    if (!live(side)) return 0L;
                    refresh();
                    return ItemBuffer.this.getCapacityAsLong(index, resource);
                }

                @Override
                public boolean isValid(int index, ItemResource resource) {
                    if (!live(side)) return false;
                    refresh();
                    return ItemBuffer.this.isValid(index, resource);
                }

                @Override
                public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
                    if (!live(side)) return 0;
                    refresh();
                    return ItemBuffer.this.insert(index, resource, amount, transaction);
                }

                @Override
                public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
                    if (!live(side)) return 0;
                    refresh();
                    return ItemBuffer.this.extract(index, resource, amount, transaction);
                }
            };
        }

        /**
         * Server tick: offers buffered items to both axis neighbors, alternating priority.
         */
        public void tick() {
            var facing = owner.getBlockState().getValue(CleanroomPortBlock.FACING);
            if (!live(facing)) return;
            refresh();
            int budget = pushLimit;
            var first = reverse ? facing : facing.getOpposite();
            reverse = !reverse;
            budget -= push(first, budget);
            if (budget > 0) push(first.getOpposite(), budget);
        }

        /**
         * Pushes items from this buffer to the neighbor in the given direction.
         */
        private int push(Direction direction, int budget) {
            if (budget <= 0 || isEmpty()) return 0;
            var level = owner.getLevel();
            var targetPos = owner.getBlockPos().relative(direction);
            if (level == null || !level.hasChunkAt(targetPos)) return 0;
            var target = level.getCapability(Capabilities.Item.BLOCK, targetPos, direction.getOpposite());
            if (target == null) return 0;

            return ResourceHandlerUtil.move(this, target, resource -> true, budget, null);
        }

        private boolean isEmpty() {
            for (int i = 0; i < size(); i++) {
                if (getAmountAsLong(i) > 0) return false;
            }
            return true;
        }

        @Override
        protected int getCapacity(int index, ItemResource resource) {
            return pushLimit > 0 ? super.getCapacity(index, resource) : 0;
        }

        /**
         * Saves the buffer contents to the given output.
         */
        public void save(ValueOutput output) {
            serialize(output);
        }

        /**
         * Loads the buffer contents from the given input.
         */
        public void load(ValueInput input) {
            for (int i = 0; i < size(); i++) set(i, getResource(i).isEmpty() ? getResource(i) : ItemResource.EMPTY, 0);
            deserialize(input);
        }

        /**
         * Drops all buffered item stacks into the world exactly once.
         * Called from preRemoveSideEffects, not from setRemoved.
         */
        public void dropContents(Level level, BlockPos pos) {
            if (level == null || level.isClientSide()) return;
            for (int i = 0; i < size(); i++) {
                ItemStack stack = getStackFrom(getResource(i), (int) getAmountAsLong(i));
                if (!stack.isEmpty()) {
                    net.minecraft.world.entity.item.ItemEntity itemEntity = new net.minecraft.world.entity.item.ItemEntity(
                            level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
                    level.addFreshEntity(itemEntity);
                    set(i, ItemResource.EMPTY, 0);
                }
            }
        }

        @Override
        protected void onContentsChanged(int index, ItemStack previousContents) {
            owner.setChanged();
        }
    }

    /**
     * Transactional fluid buffer backed by {@link FluidStacksResourceHandler} (1 tank).
     */
    public static final class FluidBuffer extends FluidStacksResourceHandler {
        private final CleanroomPortBlockEntity owner;
        private int pushLimit;
        private boolean reverse;

        public FluidBuffer(CleanroomPortBlockEntity owner) {
            super(1, 0);
            this.owner = owner;
        }

        /**
         * Refreshes capacity and transfer limits from the machine definition registry.
         */
        public void refresh() {
            var registry = MachineDefinitionLoader.getRegistry();
            var definition = registry == null ? null : registry.byId("cleanroom_fluid_port").orElse(null);
            capacity = definition == null ? 0 : definition.paramOrDefault("fluid_capacity", 0);
            pushLimit = definition == null ? 0 : definition.paramOrDefault("transfer_per_tick", 0);
            // Keep quantities when capacity shrinks - no clamping here.
        }

        /**
         * Checks if the given side is a live axis face for this fluid port.
         */
        private boolean live(Direction side) {
            var level = owner.getLevel();
            if (level == null || level.isClientSide() || owner.isRemoved()
                    || !level.hasChunkAt(owner.getBlockPos()) || level.getBlockEntity(owner.getBlockPos()) != owner
                    || CleanroomPortBlock.detectRole(owner.getBlockState()) != CleanroomPortBlock.PortRole.FLUID) return false;
            var facing = owner.getBlockState().getValue(CleanroomPortBlock.FACING);
            return side == facing || side == facing.getOpposite();
        }

        /**
         * Returns a capability view for the given side, or null if not on the axis.
         */
        public @Nullable ResourceHandler<FluidResource> view(Direction side) {
            return new ResourceHandler<>() {
                @Override
                public int size() {
                    return live(side) ? FluidBuffer.this.size() : 0;
                }

                @Override
                public FluidResource getResource(int index) {
                    return live(side) ? FluidBuffer.this.getResource(index) : FluidResource.EMPTY;
                }

                @Override
                public long getAmountAsLong(int index) {
                    return live(side) ? FluidBuffer.this.getAmountAsLong(index) : 0L;
                }

                @Override
                public long getCapacityAsLong(int index, FluidResource resource) {
                    if (!live(side)) return 0L;
                    refresh();
                    return FluidBuffer.this.getCapacityAsLong(index, resource);
                }

                @Override
                public boolean isValid(int index, FluidResource resource) {
                    if (!live(side)) return false;
                    refresh();
                    return FluidBuffer.this.isValid(index, resource);
                }

                @Override
                public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
                    if (!live(side)) return 0;
                    refresh();
                    return FluidBuffer.this.insert(index, resource, amount, transaction);
                }

                @Override
                public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
                    if (!live(side)) return 0;
                    refresh();
                    return FluidBuffer.this.extract(index, resource, amount, transaction);
                }
            };
        }

        /**
         * Server tick: offers buffered fluid to both axis neighbors, alternating priority.
         */
        public void tick() {
            var facing = owner.getBlockState().getValue(CleanroomPortBlock.FACING);
            if (!live(facing)) return;
            refresh();
            int budget = pushLimit;
            var first = reverse ? facing : facing.getOpposite();
            reverse = !reverse;
            budget -= push(first, budget);
            if (budget > 0) push(first.getOpposite(), budget);
        }

        /**
         * Pushes fluid from this buffer to the neighbor in the given direction.
         */
        private int push(Direction direction, int budget) {
            if (budget <= 0 || getAmountAsInt(0) <= 0) return 0;
            var level = owner.getLevel();
            var targetPos = owner.getBlockPos().relative(direction);
            if (level == null || !level.hasChunkAt(targetPos)) return 0;
            var target = level.getCapability(Capabilities.Fluid.BLOCK, targetPos, direction.getOpposite());
            if (target == null) return 0;

            return ResourceHandlerUtil.move(this, target, resource -> true, budget, null);
        }

        /**
         * Saves the buffer contents to the given output.
         */
        public void save(ValueOutput output) {
            serialize(output);
        }

        /**
         * Loads the buffer contents from the given input.
         */
        public void load(ValueInput input) {
            for (int i = 0; i < size(); i++) set(i, getResource(i).isEmpty() ? getResource(i) : FluidResource.EMPTY, 0);
            deserialize(input);
        }

        /**
         * Fluids are lost on break like FE - no drop.
         */
        public void clear() {
            if (getAmountAsInt(0) > 0) {
                set(0, FluidResource.EMPTY, 0);
            }
        }

        @Override
        protected void onContentsChanged(int index, FluidStack previousContents) {
            owner.setChanged();
        }
    }
}