package com.euvcraft.lithmod.mc.multiblock.advanced;
import net.minecraft.world.level.block.state.properties.*;
import com.euvcraft.lithmod.core.multiblock.*;
import com.euvcraft.lithmod.mc.multiblock.EnergyBufferProvider;
import com.euvcraft.lithmod.mc.machine.packaging.TestPackageBlockEntity;
import com.euvcraft.lithmod.mc.registry.ModItems;

import com.euvcraft.lithmod.core.multiblock.BlockKind;
import com.euvcraft.lithmod.core.multiblock.MatchResult;
import com.euvcraft.lithmod.core.multiblock.MultiblockDirection;
import com.euvcraft.lithmod.core.multiblock.MultiblockPos;
import com.euvcraft.lithmod.core.multiblock.StructureMatcher;
import com.euvcraft.lithmod.core.multiblock.StructureShape;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.FabConfig;
import com.euvcraft.lithmod.mc.FoupItems;
import com.euvcraft.lithmod.mc.registry.ModDataComponents;
import com.euvcraft.lithmod.mc.registry.ModItems;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.machine.FabMachineMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Base class for advanced multiblock controllers.
 *
 * <p>Shared logic for formation validation, member management, energy buffer, and menu handling.
 * Subclasses provide the machine ID, shape, slot layout, and processing logic.</p>
 */
public abstract class AdvancedControllerBlockEntity extends FabMachineBlockEntity implements EnergyBufferProvider {

    // Formation state
    private final Set<BlockPos> members = new LinkedHashSet<>();
    private boolean formed;
    private boolean revalidating;
    private String failureReason = "";
    private int ticksSinceValidation;
    private int formationChanges;

    public static final int REVALIDATE_INTERVAL = 20;
    private boolean needsValidation = true;

    // Energy buffer (from FabMachineBlockEntity)
    // Inventory slots defined by subclasses

    protected AdvancedControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
                                             int inputSlots, int outputSlots) {
        super(type, pos, state, inputSlots, outputSlots);
        // buffer, items, data, runner initialized in base
    }

    /** The shape of this advanced multiblock. */
    protected abstract StructureShape shape();

    /** The BlockKind to report for this controller. */
    protected abstract BlockKind controllerKind();

    /** The BlockKind to report for the operating panel. */
    protected abstract BlockKind panelKind();

    /** The BlockKind to report for power ports. */
    protected abstract BlockKind powerPortKind();

    /** The BlockKind to report for casing. */
    protected abstract BlockKind casingKind();

    /** Whether this is a large (deposition_etcher) multiblock. */
    protected abstract boolean isLarge();

    // ---------------------------------------------------------------- formation state

    /** Whether all cells are currently in place. */
    public boolean isFormed() {
        return this.formed;
    }

    /** Why the last match failed, or empty string when it succeeded. */
    public String failureReason() {
        return this.failureReason;
    }

    /** The positions of the current or last formation, in matcher order. */
    public List<BlockPos> members() {
        return List.copyOf(this.members);
    }

    /** Returns the number of formation transitions, for tests. */
    public int formationChanges() {
        return this.formationChanges;
    }

    // ---------------------------------------------------------------- ticking

    @Override
    public void serverTick() {
        if (!(level instanceof ServerLevel)) return;
        refreshDefinition();
        if (needsValidation || ++ticksSinceValidation >= REVALIDATE_INTERVAL) {
            needsValidation = false;
            revalidate();
        }
        publishProgress();
    }

    private void publishProgress() {
        data().set(DATA_ENERGY, buffer().getAmountAsInt());
        data().set(DATA_CAPACITY, buffer().getCapacityAsInt());
        data().set(DATA_PROGRESS, 0); // subclasses override with actual progress
        data().set(DATA_TICKS, 0);
    }

    @Override
    public boolean structureFormed() {
        return !needsValidation && formed && definition().isPresent();
    }

    @Override
    public int extractEnergy(int amount, boolean simulate) {
        if (amount <= 0) return 0;
        return simulate ? buffer().getAmountAsLong() >= amount ? amount : 0 : energySource().draw(amount);
    }

    /**
     * Re-runs the match and pushes the answer to every member.
     */
    public void revalidate() {
        if (level == null || level.isClientSide() || this.revalidating) {
            return;
        }
        this.revalidating = true;
        try {
            runValidation();
        } finally {
            this.revalidating = false;
        }
    }

    private void runValidation() {
        this.ticksSinceValidation = 0;

        Direction facing = getBlockState().getValue(AdvancedControllerBlock.FACING);
        MatchResult result = StructureMatcher.match(
                toCore(worldPosition),
                toCore(facing),
                shape(),
                this::blockKindAt);

        boolean nowFormed = result.success();
        List<BlockPos> nextMembers = nowFormed ? toMinecraft(result.memberPositions()) : List.of();
        boolean membershipChanged = !this.members.equals(new LinkedHashSet<>(nextMembers));

        this.failureReason = nowFormed ? "" : result.failureReason();

        if (nowFormed != this.formed || membershipChanged) {
            List<BlockPos> previous = members();
            this.formed = nowFormed;
            this.members.clear();
            this.members.addAll(nextMembers);

            updateMembers(previous, facing, false);
            updateMembers(members(), facing, nowFormed);
            setChanged();
            this.formationChanges++;

            if (nowFormed) {
                onFormed();
            } else {
                onUnformed();
            }
        } else if (this.formed) {
            updateMembers(members(), facing, true);
        }
    }

    /**
     * Called when the cell becomes formed.
     */
    protected void onFormed() {}

    /**
     * Called when the cell stops being formed.
     */
    protected void onUnformed() {}

    /** Flags or unflags every listed member and rewires its controller link with PART and FACING. */
    private void updateMembers(List<BlockPos> positions, Direction facing, boolean formed) {
        if (level == null) return;
        List<BlockPos> current = members();
        for (BlockPos pos : positions) {
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity instanceof AdvancedControllerBlockEntity core && core != this) continue;
            if (entity instanceof AdvancedMemberBlockEntity member) {
                AdvancedControllerBlockEntity owner = member.controller();
                if (owner != null && owner != this && level.getBlockEntity(owner.getBlockPos()) == owner
                        && owner.isFormed() && owner.members().contains(pos)) continue;
            }
            int part = formed ? current.indexOf(pos) : 0;
            if (part < 0) continue;
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof AdvancedControllerBlock)
                    && !(state.getBlock() instanceof AdvancedCasingBlock)
                    && !(state.getBlock() instanceof AdvancedPowerPortBlock)
                    && !(state.getBlock() instanceof AdvancedOperatingPanelBlock)) continue;
            if (entity instanceof AdvancedMemberBlockEntity member) {
                member.attach(formed ? this : null, formed, part, formed ? facing : null, isLarge());
            }
            BlockState next = state.setValue(AdvancedCasingBlock.FORMED, formed)
                .setValue(AdvancedCasingBlock.PART, part);
            if (formed) next = next.setValue(AdvancedCasingBlock.FACING, facing);
            next = next.setValue(AdvancedCasingBlock.LARGE, isLarge());
            if (next != state) level.setBlock(pos, next, 3);
        }
    }

    /**
     * Reports what the cell sees at a position for the matcher.
     */
    private BlockKind blockKindAt(MultiblockPos corePos) {
        if (level == null || level.getBlockEntity(worldPosition) != this) {
            return BlockKind.OTHER;
        }
        BlockPos pos = new BlockPos(corePos.x(), corePos.y(), corePos.z());
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);

        if (blockEntity instanceof AdvancedControllerBlockEntity otherController) {
            if (otherController != this && otherController.isFormed()) {
                return BlockKind.OTHER;
            }
            return controllerKind();
        }
        if (blockEntity instanceof AdvancedOperatingPanelBlockEntity panel) {
            if (panel.isMemberOfFormedCell() && panel.controller() != this) {
                return BlockKind.OTHER;
            }
            return panelKind();
        }
        if (blockEntity instanceof AdvancedMemberBlockEntity member) {
            if (member.isMemberOfFormedCell() && member.controller() != this) {
                return BlockKind.OTHER;
            }
            return member.blockKind();
        }
        if (state.getBlock() instanceof AdvancedPowerPortBlock) {
            return powerPortKind();
        }
        if (state.getBlock() instanceof AdvancedCasingBlock) {
            return casingKind();
        }
        return BlockKind.OTHER;
    }

    // ---------------------------------------------------------------- persistence

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.formed = input.getInt("formed").orElse(0) != 0;
        this.failureReason = input.getString("failureReason").orElse("");
        needsValidation = true;
        publishProgress();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("formed", this.formed ? 1 : 0);
        output.putString("failureReason", this.failureReason);
    }

    // ---------------------------------------------------------------- MenuProvider

    @Override
    protected AbstractContainerMenu createMachineMenu(int containerId, Inventory playerInventory, FabMachineBlockEntity machine) {
        return createAdvancedMenu(containerId, playerInventory, this);
    }

    protected abstract FabMachineMenu createAdvancedMenu(int containerId, Inventory playerInventory, AdvancedControllerBlockEntity machine);

    // ---------------------------------------------------------------- conversions

    private static MultiblockPos toCore(BlockPos pos) {
        return new MultiblockPos(pos.getX(), pos.getY(), pos.getZ());
    }

    private static MultiblockDirection toCore(Direction facing) {
        return switch (facing) {
            case NORTH -> MultiblockDirection.NORTH;
            case SOUTH -> MultiblockDirection.SOUTH;
            case EAST -> MultiblockDirection.EAST;
            case WEST -> MultiblockDirection.WEST;
            default -> MultiblockDirection.NORTH;
        };
    }

    private static List<BlockPos> toMinecraft(List<MultiblockPos> positions) {
        List<BlockPos> converted = new ArrayList<>(positions.size());
        for (MultiblockPos pos : positions) {
            converted.add(new BlockPos(pos.x(), pos.y(), pos.z()));
        }
        return converted;
    }
}