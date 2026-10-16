package com.euvcraft.lithmod.mc.block.entity;

import com.euvcraft.lithmod.core.EnergyBuffer;
import com.euvcraft.lithmod.core.multiblock.BlockKind;
import com.euvcraft.lithmod.core.multiblock.MultiblockPos;
import com.euvcraft.lithmod.core.multiblock.MultiblockDirection;
import com.euvcraft.lithmod.core.multiblock.EnergyBufferProvider;
import com.euvcraft.lithmod.core.multiblock.MatchResult;
import com.euvcraft.lithmod.core.multiblock.StructureMatcher;
import com.euvcraft.lithmod.core.multiblock.StructureShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Block entity for the Process Cell Controller.
 * Handles multiblock formation, validation, and energy storage.
 */
public class ProcessCellControllerBlockEntity extends BlockEntity implements EnergyBufferProvider {

    public static final int REVALIDATE_INTERVAL = 20; // ticks

    private final EnergyBuffer.Impl energyBuffer;
    private boolean formed = false;
    private List<MultiblockPos> memberPositions = new ArrayList<>();
    private String failureReason = "";
    private int revalidateTimer = 0;

    public ProcessCellControllerBlockEntity(BlockPos pos, BlockState state) {
        super(com.euvcraft.lithmod.mc.registry.ModBlocks.PROCESS_CELL_CONTROLLER_BE.get(), pos, state);
        this.energyBuffer = new EnergyBuffer.Impl(100000, 10000); // 100k FE capacity, 10k FE/t max receive
    }

    @Override
    public EnergyBuffer getEnergyBuffer() {
        return energyBuffer;
    }

    public boolean isFormed() {
        return formed;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public List<MultiblockPos> getMemberPositions() {
        return memberPositions;
    }

    /**
     * Called every server tick.
     * Revalidates the structure periodically and updates block states.
     */
    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }

        // Periodic revalidation
        revalidateTimer++;
        if (revalidateTimer >= REVALIDATE_INTERVAL) {
            revalidateTimer = 0;
            revalidate();
        }
    }

    /**
     * Called when a neighbor block changes.
     * Triggers immediate revalidation.
     */
    public void onNeighborChange() {
        if (level != null && !level.isClientSide()) {
            revalidate();
        }
    }

    /**
     * Called when this block is placed.
     * Triggers immediate revalidation.
     */
    public void onPlaced() {
        if (level != null && !level.isClientSide()) {
            revalidate();
        }
    }

    /**
     * Called when the structure is broken (this block removed).
     */
    public void onStructureBroken() {
        if (formed) {
            unformStructure();
        }
    }

    /**
     * Called when the multiblock becomes formed.
     */
    public void onFormed() {
        // Override in subclasses for M1-09
    }

    /**
     * Called when the multiblock becomes unformed.
     */
    public void onUnformed() {
        // Override in subclasses for M1-09
    }

    /**
     * Validates the multiblock structure and updates formed state.
     */
    private void revalidate() {
        if (level == null) return;

        // Get the controller's facing from block state
        Direction facing = getBlockState().getValue(com.euvcraft.lithmod.mc.block.ProcessCellControllerBlock.FACING);
        com.euvcraft.lithmod.core.multiblock.MultiblockDirection coreFacing = convertFacing(facing);

        // Check structure
        MatchResult result = StructureMatcher.match(
            convertPos(worldPosition),
            coreFacing,
            StructureShape.PROCESS_CELL,
            this::getBlockKindAt
        );

        boolean wasFormed = formed;
        formed = result.success();
        failureReason = result.failureReason();
        memberPositions = result.memberPositions();

        if (formed != wasFormed) {
            if (formed) {
                onFormed();
            } else {
                onUnformed();
            }
            updateMemberBlockStates();
            setChanged();
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        } else if (formed) {
            // Even if still formed, update member positions in case they changed
            updateMemberBlockStates();
        }
    }

    /**
     * Gets the BlockKind at a world position for structure matching.
     */
    private BlockKind getBlockKindAt(com.euvcraft.lithmod.core.multiblock.MultiblockPos corePos) {
        if (level == null) return BlockKind.OTHER;

        net.minecraft.core.BlockPos mcPos = new net.minecraft.core.BlockPos(corePos.x(), corePos.y(), corePos.z());
        BlockState state = level.getBlockState(mcPos);
        BlockEntity be = level.getBlockEntity(mcPos);

        // Check if this block is already part of another formed structure
        if (be instanceof ProcessCellControllerBlockEntity otherController) {
            if (otherController != this && otherController.isFormed()) {
                return BlockKind.OTHER; // Will cause "already part of another structure" via custom check
            }
        }
        if (be instanceof ProcessCellCasingBlockEntity casing) {
            if (casing.isFormed() && casing.getController() != this) {
                return BlockKind.OTHER;
            }
        }
        if (be instanceof ProcessCellPowerPortBlockEntity port) {
            if (port.isFormed() && port.getController() != this) {
                return BlockKind.OTHER;
            }
        }

        if (state.getBlock() instanceof com.euvcraft.lithmod.mc.block.ProcessCellControllerBlock) {
            return BlockKind.CONTROLLER;
        } else if (state.getBlock() instanceof com.euvcraft.lithmod.mc.block.ProcessCellCasingBlock) {
            return BlockKind.CASING;
        } else if (state.getBlock() instanceof com.euvcraft.lithmod.mc.block.ProcessCellPowerPortBlock) {
            return BlockKind.POWER_PORT;
        } else {
            return BlockKind.OTHER;
        }
    }

    /**
     * Updates the FORMED state on all member blocks.
     */
    private void updateMemberBlockStates() {
        if (level == null) return;

        for (com.euvcraft.lithmod.core.multiblock.MultiblockPos corePos : memberPositions) {
            net.minecraft.core.BlockPos mcPos = new net.minecraft.core.BlockPos(corePos.x(), corePos.y(), corePos.z());
            BlockState state = level.getBlockState(mcPos);
            BlockEntity be = level.getBlockEntity(mcPos);

            if (state.getBlock() instanceof com.euvcraft.lithmod.mc.block.ProcessCellControllerBlock) {
                level.setBlock(mcPos, state.setValue(com.euvcraft.lithmod.mc.block.ProcessCellControllerBlock.FORMED, formed), 3);
            } else if (state.getBlock() instanceof com.euvcraft.lithmod.mc.block.ProcessCellCasingBlock) {
                level.setBlock(mcPos, state.setValue(com.euvcraft.lithmod.mc.block.ProcessCellCasingBlock.FORMED, formed), 3);
                if (be instanceof ProcessCellCasingBlockEntity casing) {
                    casing.setController(this);
                }
            } else if (state.getBlock() instanceof com.euvcraft.lithmod.mc.block.ProcessCellPowerPortBlock) {
                level.setBlock(mcPos, state.setValue(com.euvcraft.lithmod.mc.block.ProcessCellPowerPortBlock.FORMED, formed), 3);
                if (be instanceof ProcessCellPowerPortBlockEntity port) {
                    port.setController(this);
                }
            }
        }
    }

    /**
     * Unforms the structure - sets all members to unformed.
     */
    private void unformStructure() {
        formed = false;
        failureReason = "structure broken";
        memberPositions.clear();
        updateMemberBlockStates();
    }

    /**
     * Converts Minecraft Direction to core MultiblockDirection.
     */
    private com.euvcraft.lithmod.core.multiblock.MultiblockDirection convertFacing(net.minecraft.core.Direction facing) {
        return switch (facing) {
            case NORTH -> com.euvcraft.lithmod.core.multiblock.MultiblockDirection.NORTH;
            case SOUTH -> com.euvcraft.lithmod.core.multiblock.MultiblockDirection.SOUTH;
            case EAST -> com.euvcraft.lithmod.core.multiblock.MultiblockDirection.EAST;
            case WEST -> com.euvcraft.lithmod.core.multiblock.MultiblockDirection.WEST;
            default -> com.euvcraft.lithmod.core.multiblock.MultiblockDirection.NORTH;
        };
    }

    /**
     * Converts Minecraft BlockPos to core MultiblockPos.
     */
    private com.euvcraft.lithmod.core.multiblock.MultiblockPos convertPos(net.minecraft.core.BlockPos pos) {
        return new com.euvcraft.lithmod.core.multiblock.MultiblockPos(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energyBuffer.setStored(input.getLong("energy").orElse(0L));
        formed = input.getInt("formed").orElse(0) != 0;
        failureReason = input.getString("failureReason").orElse("");

        // Load member positions from individual int arrays
        int count = input.getInt("memberCount").orElse(0);
        memberPositions.clear();
        for (int i = 0; i < count; i++) {
            int x = input.getInt("memberX" + i).orElse(0);
            int y = input.getInt("memberY" + i).orElse(0);
            int z = input.getInt("memberZ" + i).orElse(0);
            memberPositions.add(new com.euvcraft.lithmod.core.multiblock.MultiblockPos(x, y, z));
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong("energy", energyBuffer.stored());
        output.putInt("formed", formed ? 1 : 0);
        output.putString("failureReason", failureReason);

        output.putInt("memberCount", memberPositions.size());
        for (int i = 0; i < memberPositions.size(); i++) {
            com.euvcraft.lithmod.core.multiblock.MultiblockPos pos = memberPositions.get(i);
            output.putInt("memberX" + i, pos.x());
            output.putInt("memberY" + i, pos.y());
            output.putInt("memberZ" + i, pos.z());
        }
    }

    /**
     * Called when a player interacts with this block or any member of the formed structure.
     */
    public void onInteract(Player player) {
        if (formed) {
            player.sendSystemMessage(Component.literal("Process Cell controller at " + worldPosition));
        } else {
            player.sendSystemMessage(Component.literal("Process Cell not formed: " + failureReason));
        }
    }
}