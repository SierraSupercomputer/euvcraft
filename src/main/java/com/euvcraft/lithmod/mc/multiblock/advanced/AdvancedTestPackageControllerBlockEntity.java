package com.euvcraft.lithmod.mc.multiblock.advanced;

import com.euvcraft.lithmod.core.FoupContents;
import com.euvcraft.lithmod.core.MachineDefinition;
import com.euvcraft.lithmod.core.PackagingPlan;
import com.euvcraft.lithmod.core.PackagingRefusalReason;
import com.euvcraft.lithmod.core.PackagingRules;
import com.euvcraft.lithmod.core.PackagingRun;
import com.euvcraft.lithmod.core.RunSpec;
import com.euvcraft.lithmod.core.Tier;
import com.euvcraft.lithmod.core.TierRegistry;
import com.euvcraft.lithmod.core.multiblock.BlockKind;
import com.euvcraft.lithmod.core.multiblock.MatchResult;
import com.euvcraft.lithmod.core.multiblock.MultiblockDirection;
import com.euvcraft.lithmod.core.multiblock.MultiblockPos;
import com.euvcraft.lithmod.core.multiblock.StructureMatcher;
import com.euvcraft.lithmod.core.multiblock.StructureShape;
import com.euvcraft.lithmod.mc.ChipCatalog;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.FabYieldService;
import com.euvcraft.lithmod.mc.FoupItems;
import com.euvcraft.lithmod.mc.data.SupportLineDataLoader;
import com.euvcraft.lithmod.mc.data.TierDataLoader;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.machine.FabMachineMenu;
import com.euvcraft.lithmod.mc.machine.packaging.TestPackageBlockEntity;
import com.euvcraft.lithmod.mc.registry.ModItems;
import com.euvcraft.lithmod.mc.registry.ModDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Advanced Test Package Controller Block Entity.
 *
 * <p>Registry ID: advanced_test_package. Shape: 2 wide x 2 tall x 3 deep (12 blocks).
 * Subclasses {@link TestPackageBlockEntity} to REUSE queue/judgment/conservation logic.
 * Adds multiblock formation validation, protected constructor taking BE type, and processingAllowed gate.
 *
 * <p>Overrides: machineId = "advanced_test_package", installedGeneration = 2, processingAllowed = isFormed().
 * Admission refusal uses installedGeneration. serverTick honors processingAllowed before ANY admission/advance.
 * Shared inventory/queue remains base private; preserves all prior runtime behavior.
 * Definition: capacity 1_000_000, max_receive 20_000, energy_per_tick 100, ticks_per_wafer 10, package_per_wafer 1,
 * input_slots 2, output_slots 6. All balance editable JSON.
 */
public class AdvancedTestPackageControllerBlockEntity extends TestPackageBlockEntity {

    // Formation state (added on top of TestPackageBlockEntity)
    private final Set<BlockPos> members = new LinkedHashSet<>();
    private boolean formed;
    private boolean revalidating;
    private String failureReason = "";
    private int ticksSinceValidation;
    private int formationChanges;

    public static final int REVALIDATE_INTERVAL = 20;
    private boolean needsValidation = true;

    // The protected constructor for subclasses (as per requirement)
    protected AdvancedTestPackageControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
                                                        int inputSlots, int outputSlots) {
        super(type, pos, state, inputSlots, outputSlots);
        // Don't call super constructor logic that conflicts - we'll initialize our own state
    }

    // Public constructor for the actual BE type
    public AdvancedTestPackageControllerBlockEntity(BlockPos pos, BlockState state) {
        this(AdvancedMachines.ADVANCED_TEST_PACKAGE_CONTROLLER_BE.get(), pos, state,
             TestPackageBlockEntity.INPUT_SLOTS, TestPackageBlockEntity.SLOT_COUNT - TestPackageBlockEntity.INPUT_SLOTS);
        // The base TestPackageBlockEntity constructor calls super with SLOT_COUNT
        // We need to re-initialize properly
        initializeAdvanced();
    }

    private void initializeAdvanced() {
        // Override base initialization for multiblock
        this.formed = false;
        this.needsValidation = true;
    }

    @Override
    protected String machineId() {
        return "advanced_test_package";
    }

    @Override
    protected StructureShape shape() {
        return AdvancedFormation.ADVANCED_TEST_PACKAGE_SHAPE;
    }

    @Override
    protected BlockKind controllerKind() {
        return BlockKind.CONTROLLER;
    }

    @Override
    protected BlockKind panelKind() {
        return BlockKind.OPERATING_PANEL;
    }

    @Override
    protected BlockKind powerPortKind() {
        return BlockKind.POWER_PORT;
    }

    @Override
    protected BlockKind casingKind() {
        return BlockKind.CASING;
    }

    @Override
    protected boolean isLarge() {
        return false; // 2x2x3 is small
    }

    /** The packaging generation this machine has installed (G2). */
    @Override
    public int installedGeneration() {
        return 2;
    }

    /** Whether processing is allowed: only when the multiblock is formed. */
    protected boolean processingAllowed() {
        return this.formed;
    }

    // ---------------------------------------------------------------- formation state

    /** Whether all cells are currently in place. */
    @Override
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

        // Formation revalidation (from multiblock logic)
        if (needsValidation || ++ticksSinceValidation >= REVALIDATE_INTERVAL) {
            needsValidation = false;
            revalidate();
        }

        // processingAllowed gate: before ANY admission/advance
        if (!processingAllowed()) {
            // Still refresh definition and publish progress while paused
            refreshDefinition();
            publishProgress();
            return;
        }

        // Delegate to base TestPackageBlockEntity.serverTick() for queue logic
        // But we need to call the base class's serverTick logic, not our overridden one
        super.serverTick();
    }

    private void publishProgress() {
        // Sync base data (energy, progress) + formation state
        this.data().set(FabMachineBlockEntity.DATA_ENERGY, this.buffer().getAmountAsInt());
        this.data().set(FabMachineBlockEntity.DATA_CAPACITY, this.buffer().getCapacityAsInt());
        // The base class publishes its own progress/ticks in its publish()
        // Call the base's publish method indirectly
        publishBaseProgress();
    }

    private void publishBaseProgress() {
        // Replicate TestPackageBlockEntity.publish() logic
        PackagingRun run = this.run;
        PackagingPlan plan = this.plan;

        int done = run == null ? 0 : run.wafersDone();
        int total = run == null ? 0 : run.totalWafers();
        long progress = 0;
        long ticks = 0;
        if (run != null && plan != null) {
            long perWafer = run.ticksPerWafer();
            progress = done * perWafer + run.ticksPaidOnCurrentWafer();
            ticks = total * perWafer;
        }

        this.data().set(FabMachineBlockEntity.DATA_PROGRESS, clampToInt(progress));
        this.data().set(FabMachineBlockEntity.DATA_TICKS, clampToInt(ticks));
    }

    private static int clampToInt(long value) {
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, value));
    }

    @Override
    public boolean structureFormed() {
        return !needsValidation && formed && definition().isPresent();
    }

    // ---------------------------------------------------------------- formation validation

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

    protected void onFormed() {}
    protected void onUnformed() {}

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

    // ---------------------------------------------------------------- menu

    @Override
    protected AbstractContainerMenu createMachineMenu(int containerId, Inventory playerInventory, FabMachineBlockEntity machine) {
        return new AdvancedTestPackageMenu(containerId, playerInventory, this);
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

    // ---------------------------------------------------------------- persistence

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("formed", this.formed ? 1 : 0);
        output.putString("failureReason", this.failureReason);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.formed = input.getInt("formed").orElse(0) != 0;
        this.failureReason = input.getString("failureReason").orElse("");
        needsValidation = true;
    }
}