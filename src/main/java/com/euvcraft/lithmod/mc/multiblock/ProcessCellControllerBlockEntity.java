package com.euvcraft.lithmod.mc.multiblock;

import com.euvcraft.lithmod.core.multiblock.BlockKind;
import com.euvcraft.lithmod.core.multiblock.MatchResult;
import com.euvcraft.lithmod.core.multiblock.MultiblockDirection;
import com.euvcraft.lithmod.core.multiblock.MultiblockPos;
import com.euvcraft.lithmod.core.multiblock.StructureMatcher;
import com.euvcraft.lithmod.core.multiblock.StructureShape;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The Process Cell controller block entity: it owns the cell's energy buffer and is the only place
 * that decides whether the cell is formed.
 *
 * <p>Geometry comes from {@link StructureMatcher} in core, which is the M1-03c implementation: for a
 * facing {@code f} the box starts at the controller's own cell and extends {@code length} cells
 * along {@code back = -f}, {@code width} cells along {@code right} and {@code height} cells up. The
 * controller may sit at any of the four front-face positions, so all four are tried and the first
 * full match wins. Nothing here re-derives that geometry.</p>
 *
 * <p><b>Why a re-validation is not free.</b> When a match fails, {@link MatchResult} carries no
 * member list, so the positions from the last good match have to be kept in order to clear their
 * formed flags. {@link #members()} is that list; it is cleared on failure only after the previous
 * members have been updated.</p>
 */
public class ProcessCellControllerBlockEntity extends BlockEntity implements EnergyBufferProvider {

    /** Buffer size in FE. */
    public static final int ENERGY_CAPACITY = 100_000;
    /** Largest single insert accepted, in FE. */
    public static final int ENERGY_MAX_INSERT = 10_000;
    /** Ticks between periodic re-validations, as a backstop for edits nothing else noticed. */
    public static final int REVALIDATE_INTERVAL = 20;

    private final ControllerEnergy energy;
    /** The twelve positions of the current, or last, formation. */
    private final Set<BlockPos> members = new LinkedHashSet<>();
    private boolean formed;
    private boolean revalidating;
    private String failureReason = "";
    private int ticksSinceValidation;

    public ProcessCellControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModMultiblocks.PROCESS_CELL_CONTROLLER_BE.get(), pos, state);
        this.energy = new ControllerEnergy(this);
    }

    // ---------------------------------------------------------------- energy

    /**
     * The cell's energy buffer: accepts from every side, refuses every extraction.
     *
     * @return the handler, never null
     */
    @Override
    public EnergyHandler energy() {
        return this.energy;
    }

    /**
     * Puts the buffer into a known state, clamped to capacity. Used by tests.
     *
     * @param amount the FE to store
     */
    public void setStoredEnergy(int amount) {
        this.energy.set(Math.max(0, Math.min(amount, this.energy.getCapacityAsInt())));
    }

    /**
     * The buffer, exposed as its concrete type so a test can {@code set} it. The capability path
     * only ever offers {@link #energy()}.
     */
    public ControllerEnergy buffer() {
        return this.energy;
    }

    // ---------------------------------------------------------------- formation state

    /** Whether all twelve cells are currently in place. */
    public boolean isFormed() {
        return this.formed;
    }

    /** Why the last match failed, or the empty string when it succeeded. */
    public String failureReason() {
        return this.failureReason;
    }

    /** The twelve positions of the current or last formation, in matcher order. */
    public List<BlockPos> members() {
        return List.copyOf(this.members);
    }

    /** How many times formation has been reported since this block entity was created. */
    private int formationChanges;

    /** Returns the number of formation transitions, for tests. */
    public int formationChanges() {
        return this.formationChanges;
    }

    // ---------------------------------------------------------------- lifecycle hooks for M1-09

    /**
     * Called when the cell becomes formed, after the twelve members exist and have been flagged.
     *
     * <p>Protected on purpose: M1-09 subclasses this block entity to give the cell a behaviour, and
     * an overridable-but-not-internal hook is the whole point of the method. It is not on
     * {@link EnergyBufferProvider}, because Java forbids narrowing an interface method to protected.</p>
     */
    protected void onFormed() {
    }

    /**
     * Called when the cell stops being formed, after the members have been unflagged.
     *
     * @see #onFormed()
     */
    protected void onUnformed() {
    }

    // ---------------------------------------------------------------- ticking

    /** One server tick: re-validate now and then, so a missed edit still heals. */
    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (++this.ticksSinceValidation >= REVALIDATE_INTERVAL) {
            this.ticksSinceValidation = 0;
            revalidate();
        }
    }

    /**
     * Re-runs the match and pushes the answer to every member.
     *
     * <p>Public because the game tests drive it directly: waiting out
     * {@link #REVALIDATE_INTERVAL} ticks would make every test a timing question, and the point of
     * these tests is the geometry, not the scheduler.</p>
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

        Direction facing = getBlockState().getValue(ProcessCellControllerBlock.FACING);
        MatchResult result = StructureMatcher.match(
            toCore(worldPosition),
            toCore(facing),
            StructureShape.PROCESS_CELL,
            this::blockKindAt);

        boolean nowFormed = result.success();
        List<BlockPos> nextMembers = nowFormed ? toMinecraft(result.memberPositions()) : List.of();
        boolean membershipChanged = !this.members.equals(new LinkedHashSet<>(nextMembers));

        // The reason is refreshed on every pass, not only on a transition: a controller that has
        // never formed still has to be able to say why, which is the whole point of showing the
        // player a message when they right-click it.
        this.failureReason = nowFormed ? "" : result.failureReason();

        if (nowFormed != this.formed || membershipChanged) {
            // The last good members have to be unflagged before the new ones are flagged, because a
            // failed match reports no members at all and would otherwise leave them lit forever.
            List<BlockPos> previous = members();
            this.formed = nowFormed;
            this.members.clear();
            this.members.addAll(nextMembers);

            updateMembers(previous);
            updateMembers(members());
            setChanged();
            this.formationChanges++;

            if (nowFormed) {
                onFormed();
            } else {
                onUnformed();
            }
        } else if (this.formed) {
            // The membership is unchanged, but a member's own block state can still have been reset
            // by an edit that replaced the block, which clears FORMED. updateMembers only writes a
            // state that differs, so re-running it here repairs the flags without counting as a
            // transition and without firing the hooks again.
            updateMembers(members());
        }
    }

    /** Flags or unflags every listed member and rewires its controller link. */
    private void updateMembers(List<BlockPos> positions) {
        if (level == null) {
            return;
        }
        for (BlockPos pos : positions) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof ProcessCellMemberBlockEntity member) {
                member.attach(this, this.formed);
            }
            BlockState state = level.getBlockState(pos);
            // Only write a state that actually differs. Writing unconditionally would notify the
            // neighbours, whose neighborChanged handler calls straight back into revalidate().
            if (state.getBlock() instanceof ProcessCellCasingBlock) {
                if (state.getValue(ProcessCellCasingBlock.FORMED) != this.formed) {
                    level.setBlock(pos, state.setValue(ProcessCellCasingBlock.FORMED, this.formed), 3);
                }
            } else if (state.getBlock() instanceof ProcessCellPowerPortBlock) {
                if (state.getValue(ProcessCellPowerPortBlock.FORMED) != this.formed) {
                    level.setBlock(pos, state.setValue(ProcessCellPowerPortBlock.FORMED, this.formed), 3);
                }
            } else if (state.getBlock() instanceof ProcessCellControllerBlock) {
                if (state.getValue(ProcessCellControllerBlock.FORMED) != this.formed) {
                    level.setBlock(pos, state.setValue(ProcessCellControllerBlock.FORMED, this.formed), 3);
                }
            }
        }
    }

    /**
     * Reports what the cell sees at a position.
     *
     * <p>A block that already belongs to a <em>different</em> formed cell reads as
     * {@link BlockKind#OTHER}. That is what stops two adjacent cells from claiming the same twelve
     * blocks: the second controller finds the first one's members already spoken for.</p>
     */
    private BlockKind blockKindAt(MultiblockPos corePos) {
        if (level == null) {
            return BlockKind.OTHER;
        }
        BlockPos pos = new BlockPos(corePos.x(), corePos.y(), corePos.z());
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);

        if (blockEntity instanceof ProcessCellControllerBlockEntity otherController) {
            if (otherController != this && otherController.isFormed()) {
                return BlockKind.OTHER;
            }
            return BlockKind.CONTROLLER;
        }
        if (blockEntity instanceof ProcessCellMemberBlockEntity member) {
            if (member.isMemberOfFormedCell() && member.controller() != this) {
                return BlockKind.OTHER;
            }
            return state.getBlock() instanceof ProcessCellPowerPortBlock
                ? BlockKind.POWER_PORT
                : BlockKind.CASING;
        }
        if (state.getBlock() instanceof ProcessCellPowerPortBlock) {
            return BlockKind.POWER_PORT;
        }
        if (state.getBlock() instanceof ProcessCellCasingBlock) {
            return BlockKind.CASING;
        }
        return BlockKind.OTHER;
    }

    // ---------------------------------------------------------------- persistence

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.energy.deserialize(input);
        this.formed = input.getInt("formed").orElse(0) != 0;
        this.failureReason = input.getString("failureReason").orElse("");
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        this.energy.serialize(output);
        output.putInt("formed", this.formed ? 1 : 0);
        output.putString("failureReason", this.failureReason);
    }

    /**
     * Members are not persisted on purpose: they are derived from the world, and re-deriving them
     * on load is what keeps a saved cell honest if the blocks around it moved.
     */

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

    /**
     * The cell's real energy storage.
     *
     * <p>{@code maxExtract} is zero, which is the whole of "a power port never gives energy back"
     * and needs no wrapper class. The controller itself does not spend energy yet; M1-09 will, and
     * when it does it will need the two-object split
     * {@code FabMachineBlockEntity.MachineEnergy}/{@code WithdrawNothing} uses, because a buffer
     * that refuses extraction is a buffer that cannot pay for its own work.</p>
     */
    public static final class ControllerEnergy extends SimpleEnergyHandler {

        private final BlockEntity owner;

        ControllerEnergy(BlockEntity owner) {
            super(ENERGY_CAPACITY, ENERGY_MAX_INSERT, 0);
            this.owner = owner;
        }

        @Override
        protected void onEnergyChanged(int previousAmount) {
            this.owner.setChanged();
        }
    }
}