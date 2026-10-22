package com.euvcraft.lithmod.mc.multiblock.assembly;

import com.euvcraft.lithmod.core.multiblock.*;
import com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedMachines;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public final class AssemblyFormation {
    public static final StructureShape ASSEMBLY_SHAPE = new StructureShape(2, 2, 2);

    private final AssemblyControllerBlockEntity owner;
    private final ContainerData data = new SimpleContainerData(1);
    private List<BlockPos> members = List.of();
    private boolean formed;
    private boolean validating;
    private String failure = "Unformed";

    public AssemblyFormation(AssemblyControllerBlockEntity owner) {
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

    public ContainerData data() {
        return data;
    }

    public void validate() {
        var machine = owner.machine();
        var level = machine.getLevel();
        if (validating || level == null || level.isClientSide() || machine.isRemoved()) {
            return;
        }
        validating = true;
        try {
            var anchor = machine.getBlockPos();
            if (level.getBlockEntity(anchor) != machine) {
                detach();
                return;
            }
            var state = level.getBlockState(anchor);
            if (!(state.getBlock() instanceof AssemblyControllerBlock)) {
                detach();
                return;
            }
            Direction facing = state.getValue(AdvancedMachines.ADVANCED_CASING.get().getStateDefinition().getProperty("facing"));
            // Use the facing from the controller block state
            var controllerFacing = state.getValue(com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedVisualProperties.FACING);
            var result = owner.matchStructure(
                new MultiblockPos(anchor.getX(), anchor.getY(), anchor.getZ()),
                MultiblockDirection.valueOf(controllerFacing.name()),
                p -> kind(new BlockPos(p.x(), p.y(), p.z()))
            );
            if (!result.success()) {
                failure = result.failureReason();
                detach();
                return;
            }
            var next = result.memberPositions().stream().map(p -> new BlockPos(p.x(), p.y(), p.z())).toList();
            for (var old : members) {
                if (!next.contains(old)) {
                    clear(old);
                }
            }
            members = next;
            formed = true;
            data.set(0, 1);
            failure = "";
            for (int i = 0; i < members.size(); i++) {
                var pos = members.get(i);
                if (level.getBlockEntity(pos) instanceof com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedMemberBlockEntity member) {
                    member.attach(owner, true);
                }
                visual(pos, true, i, controllerFacing);
            }
        } finally {
            validating = false;
        }
    }

    private BlockKind kind(BlockPos pos) {
        var machine = owner.machine();
        var level = machine.getLevel();
        if (level == null || !level.hasChunkAt(pos)) {
            return BlockKind.OTHER;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        var block = level.getBlockState(pos).getBlock();
        if (pos.equals(machine.getBlockPos())) {
            return entity == machine ? BlockKind.CONTROLLER : BlockKind.OTHER;
        }
        if (!(entity instanceof com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedMemberBlockEntity member)) {
            return BlockKind.OTHER;
        }
        var other = member.controller();
        if (other != null && other != owner && other.isFormed() && !other.machine().isRemoved()
                && level.getBlockEntity(other.machine().getBlockPos()) == other.machine()) {
            return BlockKind.OTHER;
        }
        return member.structureKind();
    }

    public void detach() {
        formed = false;
        data.set(0, 0);
        for (var pos : members) {
            clear(pos);
        }
        members = List.of();
        var machine = owner.machine();
        if (machine.getLevel() != null && machine.getLevel().getBlockEntity(machine.getBlockPos()) == machine) {
            visual(machine.getBlockPos(), false, 0, Direction.NORTH);
        }
    }

    private void clear(BlockPos pos) {
        var level = owner.machine().getLevel();
        if (level == null || !level.hasChunkAt(pos)) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedMemberBlockEntity member
                && member.controller() == owner) {
            member.attach(null, false);
            visual(pos, false, 0, Direction.NORTH);
        }
    }

    private void visual(BlockPos pos, boolean formed, int part, Direction facing) {
        var level = owner.machine().getLevel();
        if (level == null) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedVisualProperties.FORMED)) {
            return;
        }
        var next = state.setValue(com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedVisualProperties.FORMED, formed)
                .setValue(com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedVisualProperties.PART, formed ? part : 0)
                .setValue(com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedVisualProperties.LARGE, false);
        if (formed) {
            next = next.setValue(com.euvcraft.lithmod.mc.multiblock.advanced.AdvancedVisualProperties.FACING, facing);
        }
        if (next != state) {
            level.setBlock(pos, next, 2);
        }
    }
}