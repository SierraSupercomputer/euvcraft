package com.euvcraft.lithmod.mc.multiblock.support;

import com.euvcraft.lithmod.core.WaterTank;
import com.euvcraft.lithmod.core.multiblock.BlockKind;
import com.euvcraft.lithmod.core.multiblock.MatchResult;
import com.euvcraft.lithmod.core.multiblock.MultiblockDirection;
import com.euvcraft.lithmod.core.multiblock.MultiblockPos;
import com.euvcraft.lithmod.core.multiblock.SupportStructureMatcher;
import com.euvcraft.lithmod.mc.multiblock.EnergyBufferProvider;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The block entity behind both support controllers: Boule Purifier and E-beam Mask Writer.
 *
 * <p><b>Foundation only, no jobs.</b> {@link #serverTick()} deliberately does NOT run the inherited
 * recipe runner from {@link FabMachineBlockEntity}: a support machine with no jobs must never
 * silently start an owner-less recipe. Each tick only refreshes the definition, re-validates
 * formation, and publishes the public menu data. Bucket exchange, refinement and mask selection
 * come later.</p>
 *
 * <p>Geometry comes from the pure core {@link SupportStructureMatcher}: 2 wide x 2 tall x 3 deep,
 * the controller on the lower front row in either column, one or two power ports, everything else
 * casing, no operating panel, in the canonical ordered part index {@code u*6 + v*3 + d}.</p>
 *
 * <p><b>Ownership rules.</b> Like the fixed Process Cell classes: a replaced core must never be
 * reclaimed by its departed owner; stale, removed or reloaded members reset their visual state; a
 * foreign live owner's membership is never cleared. Never shares members with another live
 * owner.</p>
 *
 * <p><b>No generic capabilities.</b> The controller exposes no item and no fluid capability and no
 * FE; the only FE that reaches the world is a formed, owned power port's view of the buffer.</p>
 */
public class SupportMachineBlockEntity extends FabMachineBlockEntity implements EnergyBufferProvider {

    public static final int REVALIDATE_INTERVAL = 20;

    // Slot indices: purifier 0 boule, 1 water bucket, 2 empty-bucket-out, 3 pure-boule-out;
    //              ebeam    0 blank plate, 1 redstone, 2 mask-out.
    public static final int SLOT_IN_0 = 0;
    public static final int SLOT_IN_1 = 1;

    /** Purifier's water tank starts at 16000 mB; the definition param may resize it. */
    public static final int DEFAULT_WATER_CAPACITY_MB = 16_000;
    /** Purifier definition param: water tank capacity in mB. */
    public static final String PARAM_WATER_CAPACITY = "water_capacity";

    /** The twelve positions of the current, or last, formation. */
    private final Set<BlockPos> members = new LinkedHashSet<>();
    private boolean formed;
    private boolean revalidating;
    private boolean needsValidation = true;
    private String failureReason = "";
    private int ticksSinceValidation;

    /** The purifier's water; null on the e-beam, which owns no functional water. */
    private @Nullable WaterTank water;
    private int formationChanges;

    public SupportMachineBlockEntity(BlockPos pos, BlockState state) {
        super(SupportMachines.SUPPORT_CONTROLLER_BE.get(), pos, state,
            kind(state).inputSlots(), kind(state).outputSlots());
        if (kind() == SupportMachineKind.PURIFIER) {
            // Initial 16000 mB; refreshDefinition (already run by the base constructor) resizes the
            // capacity from the definition param. Amount is preserved when capacity changes.
            this.water = new WaterTank(DEFAULT_WATER_CAPACITY_MB);
        }
    }

    /** Static-side kind lookup so the base constructor can size the inventory from it. */
    private static SupportMachineKind kind(BlockState state) {
        SupportMachineKind kind = SupportMachineKind.of(state);
        if (kind == null) {
            throw new IllegalArgumentException(
                "Support controller block entity requires a support controller block, got " + state);
        }
        return kind;
    }

    /** The kind derived from the actual block state at this position. */
    public final SupportMachineKind kind() {
        return kind(this.getBlockState());
    }

    @Override
    protected String machineId() {
        // machineId() is read by the base constructor; getBlockState() is valid by then because the
        // BlockEntity super constructor has already stored the state.
        return kind().id();
    }

    // ---------------------------------------------------------------- water

    /** The purifier's tank, or null on the e-beam. */
    public @Nullable WaterTank waterTank() {
        return this.water;
    }

    /** Water currently stored in mB; 0 on the e-beam. */
    public int waterAmount() {
        return this.water == null ? 0 : this.water.amount();
    }

    /** Tank capacity in mB from the definition param; 0 on the e-beam. */
    public int waterCapacity() {
        return this.water == null ? 0 : this.water.capacity();
    }

    // ---------------------------------------------------------------- formation state

    /** Whether all twelve blocks are currently in place. */
    public boolean isFormed() {
        return this.formed;
    }

    /** Why the last match failed, or the empty string when it succeeded. */
    public String failureReason() {
        return this.failureReason;
    }

    /** The twelve positions of the current or last formation, in matcher (u*6+v*3+d) order. */
    public List<BlockPos> members() {
        return List.copyOf(this.members);
    }

    /** Number of formation transitions since this block entity was created; for tests. */
    public int formationChanges() {
        return this.formationChanges;
    }

    // ---------------------------------------------------------------- ticking

    /**
     * Foundation tick: definition, formation, public data. Never the recipe runner.
     *
     * <p>The energy numbers live in the base {@link FabMachineBlockEntity#serverTick()} path, which
     * is deliberately not called: a support machine has no jobs in this milestone, so there is
     * nothing to spend FE on yet and the runner must not adopt a recipe the owner never accepted.</p>
     */
    @Override
    public void serverTick() {
        if (!(level instanceof net.minecraft.server.level.ServerLevel)) return;
        refreshDefinition();
        syncWaterCapacity();
        if (needsValidation || ++ticksSinceValidation >= REVALIDATE_INTERVAL) {
            needsValidation = false;
            revalidate();
        }
        publishData();
    }

    /** Publishes the public menu values; progress stays zero until the job phase exists. */
    private void publishData() {
        data().set(FabMachineBlockEntity.DATA_ENERGY, buffer().getAmountAsInt());
        data().set(FabMachineBlockEntity.DATA_CAPACITY, buffer().getCapacityAsInt());
        data().set(FabMachineBlockEntity.DATA_PROGRESS, 0);
        data().set(FabMachineBlockEntity.DATA_TICKS, 0);
    }

    // ---------------------------------------------------------------- capability lens

    // The controller's buffer lens is the inherited public final
    // FabMachineBlockEntity.energy(): it accepts insertion on every side and refuses every
    // extraction. No capability is registered for the controller block entity type at all, so
    // this lens only reaches the world through a formed, owned power port.

    // ---------------------------------------------------------------- definition (water)

    /**
     * Syncs the tank capacity from the definition's water_capacity param.
     *
     * <p>{@code applyDefinition} is final in the base class, so the water side of the definition
     * rides behind it: every caller of {@code refreshDefinition()} — the tick, the load path, the
     * constructor — follows up with this method. The tank is resized, which preserves the stored
     * water even if the new capacity is smaller ({@link WaterTank#resize} contract). A param
     * outside the tank's strict bounds ({@code 1..1_000_000}) is ignored rather than applied, so a
     * broken definition cannot crash every tick; the previously valid capacity stays in force.</p>
     */
    private void syncWaterCapacity() {
        if (this.water == null) {
            return;
        }
        Optional<Integer> param = definition().flatMap(def -> def.param(PARAM_WATER_CAPACITY));
        if (param.isEmpty()) {
            return;
        }
        int requested = param.get();
        if (requested < 1 || requested > WaterTank.MAX_CAPACITY) {
            return;
        }
        this.water.resize(requested);
    }

    // ---------------------------------------------------------------- revalidation

    /**
     * Re-runs the match and pushes the answer to every member.
     *
     * <p>Public because the game tests drive it directly (see the Process Cell tests) instead of
     * waiting out {@link #REVALIDATE_INTERVAL} ticks.</p>
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

        Direction facing = getBlockState().getValue(SupportMachineControllerBlock.FACING);
        MatchResult result = SupportStructureMatcher.match(
                toCore(worldPosition),
                toCore(facing),
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
            // Membership unchanged, but a member's block state can still have been reset by an edit
            // that replaced the block (clearing FORMED/PART/FACING). updateMembers only writes a
            // state that differs, so re-running it repairs the flags without a transition.
            updateMembers(members(), facing, true);
        }
    }

    /** Formation hook for the job phase; fires after all members are flagged. */
    protected void onFormed() {
    }

    /** Unformation hook for the job phase; fires after all members are unflagged. */
    protected void onUnformed() {
    }

    /** Flags or unflags every listed member and rewires its controller link with PART and FACING. */
    private void updateMembers(List<BlockPos> positions, Direction facing, boolean formed) {
        if (level == null) return;
        List<BlockPos> current = members();
        for (BlockPos pos : positions) {
            BlockEntity entity = level.getBlockEntity(pos);
            // A departed controller must never modify the replacement at its own old location.
            if (entity instanceof SupportMachineBlockEntity supportController && supportController != this) continue;
            if (entity instanceof SupportMemberBlockEntity member) {
                SupportMachineBlockEntity owner = member.controller();
                // Never clear a foreign live owner's member.
                if (owner != null && owner != this && level.getBlockEntity(owner.getBlockPos()) == owner
                        && owner.isFormed() && owner.members().contains(pos)) continue;
            }
            int part = formed ? current.indexOf(pos) : 0;
            if (part < 0) continue;
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof SupportMachineControllerBlock)
                    && !(state.getBlock() instanceof SupportCasingBlock)
                    && !(state.getBlock() instanceof SupportPowerPortBlock)) continue;
            if (entity instanceof SupportMemberBlockEntity member) {
                member.attach(formed ? this : null, formed, part, formed ? facing : null);
            }
            BlockState next = state.setValue(SupportMachineControllerBlock.FORMED, formed)
                .setValue(SupportMachineControllerBlock.PART, part);
            if (formed) next = next.setValue(SupportMachineControllerBlock.FACING, facing);
            if (next != state) level.setBlock(pos, next, 3);
        }
    }

    /**
     * Reports what the machine sees at a position.
     *
     * <p>A block that already belongs to a different formed machine reads as
     * {@link BlockKind#OTHER}, which stops two adjacent machines from claiming the same twelve
     * blocks. The block entity at the controller position must still be this one: another
     * {@link SupportMachineKind} or a foreign controller instance at this position is OTHER.</p>
     */
    private BlockKind blockKindAt(MultiblockPos corePos) {
        if (level == null || level.getBlockEntity(worldPosition) != this) {
            return BlockKind.OTHER;
        }
        BlockPos pos = new BlockPos(corePos.x(), corePos.y(), corePos.z());
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);

        if (blockEntity instanceof SupportMachineBlockEntity otherController) {
            if (otherController != this && otherController.isFormed()) {
                return BlockKind.OTHER;
            }
            return BlockKind.CONTROLLER;
        }
        if (blockEntity instanceof SupportMemberBlockEntity member) {
            if (member.isMemberOfFormedMachine() && member.controller() != this) {
                return BlockKind.OTHER;
            }
            return state.getBlock() instanceof SupportPowerPortBlock
                    ? BlockKind.POWER_PORT
                    : BlockKind.CASING;
        }
        if (state.getBlock() instanceof SupportPowerPortBlock) {
            return BlockKind.POWER_PORT;
        }
        if (state.getBlock() instanceof SupportCasingBlock) {
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
        this.needsValidation = true;
        // The base load path already ran refreshDefinition; apply the definition's water capacity
        // before restoring the persisted amount, so a valid save round-trips capacity and amount.
        syncWaterCapacity();
        // Formation member list remains world-derived (not persisted). The water amount is:
        if (this.water != null) {
            Integer amount = input.getInt("supportWaterAmount").orElse(null);
            // Strict bounds: a save written out of bounds must never be adopted. A negative or
            // over-hard-bound value resets the tank to the post-definition state instead of
            // crashing or inventing water.
            if (amount != null && amount >= 0 && amount <= WaterTank.MAX_CAPACITY) {
                // A valid save can legitimately carry an amount between the current capacity and
                // the hard bound (the definition shrank between sessions); WaterTank accepts that
                // and rejects further fill until room returns.
                this.water = new WaterTank(this.water.capacity(), amount);
            }
        }
        publishData();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("formed", this.formed ? 1 : 0);
        output.putString("failureReason", this.failureReason);
        if (this.water != null) {
            output.putInt("supportWaterAmount", this.water.amount());
        }
        // Capacity is definition data, not saved.
    }

    // ---------------------------------------------------------------- MenuProvider

    @Override
    protected AbstractContainerMenu createMachineMenu(int containerId, Inventory playerInventory,
                                                      FabMachineBlockEntity machine) {
        return SupportMachineMenu.forMachine(containerId, playerInventory, this);
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
}
