package com.euvcraft.lithmod.mc.cleanroom;


import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jspecify.annotations.Nullable;

/**
 * Block entity for cleanroom ports. Power/item/fluid each use a saved transactional bridge buffer;
 * each server tick offers buffered resources to both axis neighbors with alternating priority.
 *
 * <p>Each port role (power, fluid, item) exposes exactly one capability matching its type
 * on the two axis faces (facing and opposite). Transverse and null faces are refused.</p>
 *
 * <p>Retained capability views reject changed role/axis, removed/replaced/unloaded owner.
 * World shutdown ({@link #setRemoved()}) does not inspect neighbors or chunks.</p>
 */
public class CleanroomPortBlockEntity extends BlockEntity {

    /** Independent power endpoint lets two conduit networks exchange FE across the wall. */
    private final CleanroomPowerBuffer power = new CleanroomPowerBuffer(this);
    /** Independent item buffer (9 slots) for bidirectional conduit exchange. */
    private final CleanroomResourceBuffer.ItemBuffer items = new CleanroomResourceBuffer.ItemBuffer(this);
    /** Independent fluid buffer (1 tank) for bidirectional conduit exchange. */
    private final CleanroomResourceBuffer.FluidBuffer fluids = new CleanroomResourceBuffer.FluidBuffer(this);

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
        fluids.refresh();
        return fluids.view(side);
    }

    /**
     * Returns the item handler for the given side, or null if this is not an item port
     * or the side is not on the through-wall axis.
     */
    public @Nullable ResourceHandler<ItemResource> items(@Nullable Direction side) {
        if (role() != CleanroomPortBlock.PortRole.ITEM) return null;
        if (side == null || side != outsideSide() && side != insideSide()) return null;
        items.refresh();
        return items.view(side);
    }

    /** Deliver buffered FE/items/fluids to adjacent receivers; conduit networks can pull directly. */
    public void serverTick() {
        switch (role()) {
            case POWER -> power.tick();
            case ITEM -> items.tick();
            case FLUID -> fluids.tick();
            default -> {}
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("portEnergy", power.getAmountAsInt());
        items.save(out.child("portItems"));
        fluids.save(out.child("portFluids"));
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        power.refresh();
        power.set(Math.max(0, in.getIntOr("portEnergy", 0)));
        items.load(in.child("portItems"));
        fluids.load(in.child("portFluids"));
    }

    @Override
    public void setRemoved() {
        // Do NOT inspect neighbors or chunks on world shutdown.
        // The capability views will naturally return empty/null on subsequent calls.
        super.setRemoved();
    }

    /**
     * Called before the block entity is removed from the world (e.g., block broken).
     * Drops buffered item stacks exactly once. Fluids are lost like FE.
     */
    public void preRemoveSideEffects() {
        if (role() == CleanroomPortBlock.PortRole.ITEM) {
            var level = getLevel();
            if (level != null && !level.isClientSide()) {
                items.dropContents(level, getBlockPos());
            }
        }
        // Fluids lost on break like FE - no action needed.
        // Power buffer is saved via portEnergy in saveAdditional.
    }
}