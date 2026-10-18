package com.euvcraft.lithmod.mc.multiblock;

import com.euvcraft.lithmod.core.multiblock.BlockKind;
import com.euvcraft.lithmod.core.multiblock.MatchResult;
import com.euvcraft.lithmod.core.multiblock.MultiblockDirection;
import com.euvcraft.lithmod.core.multiblock.MultiblockPos;
import com.euvcraft.lithmod.core.multiblock.StructureMatcher;
import com.euvcraft.lithmod.core.multiblock.StructureShape;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.machine.process.ProcessCellMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
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
public class ProcessCellControllerBlockEntity extends FabMachineBlockEntity implements EnergyBufferProvider {

    // Slot indices matching the 8-slot layout: 0 FOUP, 1 MASK, 2 RESIST, 3 LIGHT_SOURCE, 4 OPTICS, 5 STAGE, 6 CLEANROOM, 7 METROLOGY
    public static final int SLOT_FOUP = 0;
    public static final int SLOT_MASK = 1;
    public static final int SLOT_RESIST = 2;
    public static final int SLOT_LIGHT_SOURCE = 3;
    public static final int SLOT_OPTICS = 4;
    public static final int SLOT_STAGE = 5;
    public static final int SLOT_CLEANROOM = 6;
    public static final int SLOT_METROLOGY = 7;
    public static final int INPUT_SLOTS = 8;
    public static final int OUTPUT_SLOTS = 0;
    public static final int SLOT_COUNT = 8;

    /** The twelve positions of the current, or last, formation. */
    private final Set<BlockPos> members = new LinkedHashSet<>();
    private boolean formed;
    private boolean revalidating;
    private String failureReason = "";
    private int ticksSinceValidation;

    public static final int REVALIDATE_INTERVAL = 20;

    public ProcessCellControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModMultiblocks.PROCESS_CELL_CONTROLLER_BE.get(), pos, state, INPUT_SLOTS, OUTPUT_SLOTS);
        // machineId() is "process_cell", base constructor calls refreshDefinition() which loads energy numbers from data
    }

    @Override
    protected String machineId() {
        return "process_cell";
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
    @Override
    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        // Formation backstop: periodic re-validation regardless of base tick
        if (++this.ticksSinceValidation >= REVALIDATE_INTERVAL) {
            this.ticksSinceValidation = 0;
            revalidate();
        }
        // Refresh definition so data sizes/closures update (capacity, per-tick cost)
        refreshDefinition();
        // Do NOT call super.serverTick() — that would drive irrelevant generic recipes; no process logic yet
        syncData(); // push energy/progress to menu
        setChanged();
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
        super.loadAdditional(input); // loads inventory and energy via base
        this.formed = input.getInt("formed").orElse(0) != 0;
        this.failureReason = input.getString("failureReason").orElse("");
        // Formation member list remains world-derived (not persisted)
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output); // saves inventory and energy via base
        output.putInt("formed", this.formed ? 1 : 0);
        output.putString("failureReason", this.failureReason);
        // Formation member list not persisted on purpose
    }

    // ---------------------------------------------------------------- MenuProvider

    @Override
    protected AbstractContainerMenu createMachineMenu(int containerId, Inventory playerInventory, FabMachineBlockEntity machine) {
        return new ProcessCellMenu(containerId, playerInventory, this);
    }

    // ---------------------------------------------------------------- extra contents drop

    @Override
    protected void dropExtraContents(@Nullable Level level, BlockPos pos) {
        // Drop our 8-slot inventory when the controller is broken
        if (level != null && !level.isClientSide()) {
            dropContainer(level, pos, this);
        }
    }

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
     * Access to the definition for this machine, for tests and menu energy capacity display.
     * Reads the loaded data-driven definition.
     */
    public Optional<MachineDefinition> definition() {
        MachineDefinitionLoader.Registry registry = MachineDefinitionLoader.getRegistry();
        return registry == null ? Optional.empty() : registry.byId(machineId());
    }
}