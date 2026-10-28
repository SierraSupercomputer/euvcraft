package com.euvcraft.lithmod.mc.multiblock.g3;

import com.euvcraft.lithmod.core.multiblock.BlockKind;
import com.euvcraft.lithmod.core.multiblock.MatchResult;
import com.euvcraft.lithmod.core.multiblock.MultiblockDirection;
import com.euvcraft.lithmod.core.multiblock.MultiblockPos;
import com.euvcraft.lithmod.core.multiblock.StructureShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * G3 formation lifecycle — owns the formed state, member list, and visual updates.
 *
 * <p>Supports both 3x3x5 (writer, 45 cells) and 5x5x7 (deposition etcher, 175 cells) shapes.
 * Visual properties: FORMED boolean, PART index 0..174, FACING.</p>
 *
 * <p>Removal may run after this chunk left the loaded map. Never query or mutate the world
 * in {@link #unload()}. Loaded members discard stale links on their next tick; capability
 * lenses reject removed owners. Normal structure failures use {@link #detach()} to refresh
 * loaded visuals immediately.</p>
 */
public final class G3Formation {

    private final G3ControllerOwner owner;
    private final List<BlockPos> members = new ArrayList<>();
    private boolean formed;
    private boolean validating;
    private String failureReason = "Unformed";
    private boolean needsValidation = true;
    private int ticksSinceValidation;

    public G3Formation(G3ControllerOwner owner) {
        this.owner = owner;
    }

    public boolean formed() {
        return formed;
    }

    public String failureReason() {
        return failureReason;
    }

    public List<BlockPos> members() {
        return List.copyOf(members);
    }

    public boolean needsValidation() {
        return needsValidation;
    }

    public int ticksSinceValidation() {
        return ticksSinceValidation;
    }

    public void setNeedsValidation(boolean needsValidation) {
        this.needsValidation = needsValidation;
    }

    public void setTicksSinceValidation(int ticks) {
        this.ticksSinceValidation = ticks;
    }

    public void setValidating(boolean validating) {
        this.validating = validating;
    }

    public void setFailureReason(String reason) {
        this.failureReason = reason;
    }

    public void setFormed(boolean formed) {
        this.formed = formed;
    }

    public void setMembers(List<BlockPos> members) {
        this.members.clear();
        this.members.addAll(members);
    }

    /** Runs the structure match and updates member links and visual state. */
    public void validate() {
        var machine = owner.machine();
        var level = machine.getLevel();
        if (validating || level == null || level.isClientSide() || machine.isRemoved()) return;
        validating = true;
        try {
            var anchor = machine.getBlockPos();
            if (level.getBlockEntity(anchor) != machine) {
                detach();
                return;
            }
            var state = level.getBlockState(anchor);
            if (!(state.getBlock() instanceof G3ControllerBlock)) {
                detach();
                return;
            }
            Direction facing = state.getValue(G3ControllerBlock.FACING);
            StructureShape shape = owner.large() ? G3StructureMatcher.DEPOSITION_ETCHER_SHAPE : G3StructureMatcher.WRITER_SHAPE;

            MatchResult result = owner.matchStructure(
                    new MultiblockPos(anchor.getX(), anchor.getY(), anchor.getZ()),
                    MultiblockDirection.valueOf(facing.name()),
                    pos -> kind(new BlockPos(pos.x(), pos.y(), pos.z())));

            if (!result.success()) {
                failureReason = result.failureReason();
                detach();
                return;
            }

            var next = result.memberPositions().stream().map(p -> new BlockPos(p.x(), p.y(), p.z())).toList();
            for (var old : members) if (!next.contains(old)) clear(old);
            members.clear();
            members.addAll(next);
            formed = true;
            failureReason = "";

            for (int i = 0; i < members.size(); i++) {
                var pos = members.get(i);
                if (level.getBlockEntity(pos) instanceof G3MemberBlockEntity member) {
                    member.attach(owner.machine(), true, i, facing);
                }
                visual(pos, true, i, facing);
            }
        } finally {
            validating = false;
        }
    }

    /** Called when the controller chunk unloads. Never accesses the world. */
    public void unload() {
        formed = false;
        members.clear();
    }

    /** Called when the structure becomes invalid while the chunk is loaded. */
    public void detach() {
        formed = false;
        for (var pos : members) clear(pos);
        members.clear();
        var machine = owner.machine();
        if (machine.getLevel() != null && machine.getLevel().getBlockEntity(machine.getBlockPos()) == machine) {
            visual(machine.getBlockPos(), false, 0, Direction.NORTH);
        }
    }

    private void clear(BlockPos pos) {
        var level = owner.machine().getLevel();
        if (level == null || !level.hasChunkAt(pos)) return;
        if (level.getBlockEntity(pos) instanceof G3MemberBlockEntity member && member.controller() == owner.machine()) {
            member.attach(null, false, 0, null);
            visual(pos, false, 0, Direction.NORTH);
        }
    }

    private void visual(BlockPos pos, boolean formed, int part, Direction facing) {
        var level = owner.machine().getLevel();
        if (level == null) return;
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(G3CasingBlock.FORMED)) return;
        var next = state.setValue(G3CasingBlock.FORMED, formed)
                .setValue(G3CasingBlock.PART, formed ? part : 0)
                .setValue(G3CasingBlock.FACING, formed ? facing : Direction.NORTH);
        if (next != state) level.setBlock(pos, next, 2);
    }

    private BlockKind kind(BlockPos pos) {
        var machine = owner.machine();
        var level = machine.getLevel();
        if (level == null || !level.hasChunkAt(pos)) return BlockKind.OTHER;
        BlockEntity entity = level.getBlockEntity(pos);
        var block = level.getBlockState(pos).getBlock();

        if (pos.equals(machine.getBlockPos())) {
            return entity == machine ? BlockKind.CONTROLLER : BlockKind.OTHER;
        }
        if (!(entity instanceof G3MemberBlockEntity member)) return BlockKind.OTHER;
        if (!member.supportsOwner(owner.machine())) return BlockKind.OTHER;
        var other = member.controller();
        if (other != null && other != owner && other.isFormed() && !other.machine().isRemoved()
                && level.getBlockEntity(other.machine().getBlockPos()) == other.machine()) {
            return BlockKind.OTHER;
        }
        return member.structureKind();
    }
}