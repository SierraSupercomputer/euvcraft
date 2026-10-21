package com.euvcraft.lithmod.mc.multiblock.support;

import com.euvcraft.lithmod.core.WaterTank;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.transfer.FluidTransfer;
import net.neoforged.neoforge.transfer.FluidVariant;
import net.neoforged.neoforge.transfer.TransferHandler;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Block entity for the support water inlet.
 *
 * <p>Exposes {@link Capabilities#Fluid#BLOCK} on all faces while the inlet is part of a
 * formed, owned purifier. The handler delegates to the controller's shared {@link WaterTank},
 * accepting only water (minecraft:water) and only insertion (fill). Extraction is always rejected.
 *
 * <p>Retained handlers revalidate member/owner/world identity and formed/live membership
 * ON EVERY operation; no ghost writes after removal/unform/reassignment/reload.
 * Capability cache invalidation on attach/detach/lifecycle so pipes rediscover after formation.
 */
public class SupportWaterInletBlockEntity extends BlockEntity {

    private @Nullable SupportMachineBlockEntity controller;
    private boolean formed;
    private int part;
    private @Nullable Direction facing;

    // Capability cache for the fluid handler; invalidated on attach/detach/formation change
    private @Nullable BlockCapabilityCache<IFluidHandler, SupportWaterInletBlockEntity> fluidCache;

    protected SupportWaterInletBlockEntity(BlockPos pos, BlockState state) {
        super(SupportMachines.SUPPORT_MEMBER_BE.get(), pos, state);
    }

    /** The controller this inlet belongs to, or null when it belongs to none. */
    public @Nullable SupportMachineBlockEntity controller() {
        return this.controller;
    }

    /** Whether this inlet is part of a formed machine. */
    public boolean isMemberOfFormedMachine() {
        return this.formed;
    }

    /** Visual part index 0..11 for formed members, 0 when unformed. */
    public int part() {
        return this.part;
    }

    /** Horizontal facing of the machine this inlet belongs to, or null when unformed. */
    public @Nullable Direction facing() {
        return this.facing;
    }

    /**
     * Records a new formed state, controller link, part index, and facing. Called by the controller, never by a player.
     *
     * @param controller the owning controller, or null to detach this member
     * @param formed whether the machine is formed
     * @param part visual part index 0..11 (ignored when formed is false)
     * @param facing horizontal facing of the machine (ignored when formed is false)
     */
    public void attach(@Nullable SupportMachineBlockEntity controller, boolean formed, int part,
                       @Nullable Direction facing) {
        boolean changed = this.controller != controller || this.formed != formed || this.part != part || this.facing != facing;
        this.controller = controller;
        this.formed = formed;
        this.part = formed ? part : 0;
        this.facing = formed ? facing : null;
        setChanged();
        if (changed) {
            invalidateFluidCache();
        }
    }

    /**
     * Drops the formed state when the owning controller is gone or has unformed.
     *
     * <p>Called once per server tick. The controller normally clears this itself the moment it
     * re-validates; this is the backstop for the case where the controller block itself was removed
     * and there is nobody left to push an update. The departed controller must never clear a
     * <em>foreign</em> live owner's membership.
     */
    public void serverTick() {
        if (level == null || level.isClientSide()) return;
        boolean live = controller != null && !controller.isRemoved()
            && level.getBlockEntity(controller.getBlockPos()) == controller
            && controller.isFormed() && controller.members().contains(worldPosition);
        BlockState state = level.getBlockState(worldPosition);
        boolean persistedFormed = state.hasProperty(SupportCasingBlock.FORMED)
            && state.getValue(SupportCasingBlock.FORMED);
        boolean stalePart = state.hasProperty(SupportCasingBlock.PART)
            && state.getValue(SupportCasingBlock.PART) != 0;
        if (!live && (formed || controller != null || persistedFormed || stalePart)) {
            attach(null, false, 0, null);
            clearFormedState();
        }
    }

    /** Clear coarse visual state even if no ephemeral owner was restored from disk. */
    private void clearFormedState() {
        BlockState state = level.getBlockState(worldPosition);
        BlockState next = state;
        if (next.hasProperty(SupportCasingBlock.FORMED)) next = next.setValue(SupportCasingBlock.FORMED, false);
        if (next.hasProperty(SupportCasingBlock.PART)) next = next.setValue(SupportCasingBlock.PART, 0);
        if (next != state) level.setBlock(worldPosition, next, 3);
    }

    /** Returns the controller's water tank if this inlet is part of a valid, formed purifier; null otherwise. */
    private @Nullable WaterTank getTank() {
        if (!formed || controller == null) return null;
        // Revalidate live identity: controller must still exist, be this BE, be formed, and claim this position
        if (level == null || level.getBlockEntity(controller.getBlockPos()) != controller) return null;
        if (!controller.isFormed()) return null;
        if (!controller.members().contains(worldPosition)) return null;
        // Only purifier has a water tank
        if (controller.kind() != SupportMachineKind.PURIFIER) return null;
        return controller.waterTank();
    }

    /**
     * Fluid handler that delegates to the controller's WaterTank.
     * Insert-only, water-only, all faces.
     */
    private IFluidHandler createFluidHandler() {
        return new IFluidHandler() {
            @Override
            public int getTanks() {
                return 1;
            }

            @Override
            public FluidStack getFluidInTank(int tank) {
                WaterTank tankObj = getTank();
                if (tankObj == null) return FluidStack.EMPTY;
                // Return a snapshot of water amount
                return new FluidStack(net.minecraft.world.level.material.Fluids.WATER, tankObj.amount());
            }

            @Override
            public int getTankCapacity(int tank) {
                WaterTank tankObj = getTank();
                return tankObj == null ? 0 : tankObj.capacity();
            }

            @Override
            public boolean isFluidValid(int tank, FluidStack stack) {
                // Only accept minecraft:water (ordinary water, not flowing/metadata variants)
                return stack != null && stack.is(net.minecraft.world.level.material.Fluids.WATER);
            }

            @Override
            public int fill(FluidStack resource, FluidAction action) {
                WaterTank tankObj = getTank();
                if (tankObj == null || resource == null || resource.isEmpty()) return 0;
                if (!isFluidValid(0, resource)) return 0;
                int amount = resource.getAmount();
                if (amount <= 0) return 0;
                // Simulate first
                int accepted = tankObj.fill(amount, true);
                if (accepted > 0 && action.execute()) {
                    tankObj.fill(accepted, false);
                    controller.setChanged();
                }
                return accepted;
            }

            @Override
            public FluidStack drain(FluidStack resource, FluidAction action) {
                // Extraction never allowed on inlet
                return FluidStack.EMPTY;
            }

            @Override
            public FluidStack drain(int maxDrain, FluidAction action) {
                // Extraction never allowed on inlet
                return FluidStack.EMPTY;
            }
        };
    }

    /** Gets the fluid capability cache, creating it if needed. */
    private BlockCapabilityCache<IFluidHandler, SupportWaterInletBlockEntity> getFluidCache() {
        if (fluidCache == null) {
            fluidCache = new BlockCapabilityCache<>(
                Capabilities.Fluid.BLOCK,
                this,
                (be, side) -> be.createFluidHandler()
            );
        }
        return fluidCache;
    }

    /** Invalidates the fluid capability cache so pipes rediscover after formation changes. */
    private void invalidateFluidCache() {
        if (fluidCache != null) {
            fluidCache.invalidate();
            fluidCache = null;
        }
    }

    // ---------------------------------------------------------------- Capability registration

    /**
     * Registers the fluid capability for the water inlet block entity type.
     * Called from SupportMachines.registerCapabilities.
     */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            Capabilities.Fluid.BLOCK,
            SupportMachines.SUPPORT_MEMBER_BE.get(),
            (blockEntity, side) -> {
                if (blockEntity instanceof SupportWaterInletBlockEntity inlet) {
                    return inlet.getFluidCache();
                }
                return null;
            }
        );
    }

    // ---------------------------------------------------------------- Persistence

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("formed", formed);
        tag.putInt("part", part);
        if (facing != null) tag.putString("facing", facing.getSerializedName());
        if (controller != null) {
            tag.putLong("controllerPos", controller.getBlockPos().asLong());
        }
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.formed = tag.getBoolean("formed");
        this.part = tag.getInt("part");
        if (tag.contains("facing")) {
            this.facing = Direction.fromName(tag.getString("facing"));
        }
        // Controller link is NOT persisted; it is rebuilt by the controller's revalidate()
        this.controller = null;
    }
}