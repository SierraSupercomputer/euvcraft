package com.euvcraft.lithmod.mc.multiblock.euv;

import com.euvcraft.lithmod.core.multiblock.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Formation lifecycle for EUV scanner (5x7x5 = 175 cells).
 * Owns formed boolean, facing, part indices 0..174.
 * Unload-safe teardown, no stale claims, bounded loaded lookup.
 */
public final class EuvFormation implements Formation {

    private final EuvFormationOwner owner;
    private final net.minecraft.world.inventory.ContainerData data = new net.minecraft.world.inventory.SimpleContainerData(1);
    private List<BlockPos> members = List.of();
    private boolean formed;
    private boolean validating;
    private String failure = "Unformed";

    public EuvFormation(EuvFormationOwner owner) {
        this.owner = owner;
    }

    public boolean formed() {
        return formed;
    }

    public String failureReason() {
        return failure;
    }

    public List<BlockPos> members() {
        return members;
    }

    public net.minecraft.world.inventory.ContainerData data() {
        return data;
    }

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
            if (!(state.getBlock() instanceof EuvScannerControllerBlock)) {
                detach();
                return;
            }
            Direction facing = state.getValue(EuvScannerControllerBlock.FACING);
            var result = owner.matchStructure(
                    new MultiblockPos(anchor.getX(), anchor.getY(), anchor.getZ()),
                    MultiblockDirection.valueOf(facing.name()),
                    p -> kind(new BlockPos(p.x(), p.y(), p.z())));
            if (!result.success()) {
                failure = result.failureReason();
                detach();
                return;
            }
            var next = result.memberPositions().stream()
                    .map(p -> new BlockPos(p.x(), p.y(), p.z())).toList();

            // Unflag old members not in new set
            for (var old : members) {
                if (!next.contains(old)) clear(old);
            }

            members = next;
            formed = true;
            data.set(0, 1);
            failure = "";

            for (int i = 0; i < members.size(); i++) {
                var pos = members.get(i);
                if (level.getBlockEntity(pos) instanceof EuvScannerMemberBlockEntity member) {
                    member.attach(owner, true);
                }
                visual(pos, true, i, facing);
            }
        } finally {
            validating = false;
        }
    }

    private BlockKind kind(BlockPos pos) {
        var machine = owner.machine();
        var level = machine.getLevel();
        if (level == null || !level.hasChunkAt(pos)) return BlockKind.OTHER;
        BlockEntity entity = level.getBlockEntity(pos);
        var block = level.getBlockState(pos).getBlock();

        if (pos.equals(machine.getBlockPos())) return entity == machine ? BlockKind.CONTROLLER : BlockKind.OTHER;
        if (!(entity instanceof EuvScannerMemberBlockEntity member)) return BlockKind.OTHER;
        if (!member.supportsOwner(owner)) return BlockKind.OTHER;
        var other = member.controller();
        if (other != null && other != owner && other.isFormed() && !other.machine().isRemoved()
                && level.getBlockEntity(other.machine().getBlockPos()) == other.machine()) return BlockKind.OTHER;
        return member.structureKind();
    }

    /**
     * Unload - called when chunk unloads or block entity removed.
     * Never queries or mutates world. Loaded members discard stale links on next tick.
     */
    public void unload() {
        formed = false;
        data.set(0, 0);
        members = List.of();
    }

    /**
     * Detach - normal structure failure, refreshes loaded visuals immediately.
     */
    public void detach() {
        formed = false;
        data.set(0, 0);
        for (var pos : members) clear(pos);
        members = List.of();
        var machine = owner.machine();
        if (machine.getLevel() != null && machine.getLevel().getBlockEntity(machine.getBlockPos()) == machine) {
            visual(machine.getBlockPos(), false, 0, Direction.NORTH);
        }
    }

    private void clear(BlockPos pos) {
        var level = owner.machine().getLevel();
        if (level == null || !level.hasChunkAt(pos)) return;
        if (level.getBlockEntity(pos) instanceof EuvScannerMemberBlockEntity member
                && member.controller() == owner) {
            member.attach(null, false);
            visual(pos, false, 0, Direction.NORTH);
        }
    }

    private void visual(BlockPos pos, boolean formed, int part, Direction facing) {
        var level = owner.machine().getLevel();
        if (level == null) return;
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(EuvScannerControllerBlock.FORMED)) return;
        var next = state.setValue(EuvScannerControllerBlock.FORMED, formed)
                .setValue(EuvScannerControllerBlock.PART, formed ? part : 0);
        if (formed) next = next.setValue(EuvScannerControllerBlock.FACING, facing);
        if (next != state) level.setBlock(pos, next, 2);
    }
    @Override public MatchResult matchStructure(MultiblockPos anchor,MultiblockDirection facing,java.util.function.Function<MultiblockPos,BlockKind> lookup){return owner.matchStructure(anchor,facing,lookup);}
}