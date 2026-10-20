package com.euvcraft.lithmod.mc.multiblock;

import com.mojang.serialization.Codec;
import com.euvcraft.lithmod.core.multiblock.BlockKind;
import com.euvcraft.lithmod.core.multiblock.MatchResult;
import com.euvcraft.lithmod.core.multiblock.MultiblockDirection;
import com.euvcraft.lithmod.core.multiblock.MultiblockPos;
import com.euvcraft.lithmod.core.multiblock.StructureMatcher;
import com.euvcraft.lithmod.core.multiblock.StructureShape;
import com.euvcraft.lithmod.core.*;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.FabYieldService;
import com.euvcraft.lithmod.mc.FoupItems;
import com.euvcraft.lithmod.mc.LineLoadoutReader;
import com.euvcraft.lithmod.mc.data.TierDataLoader;
import com.euvcraft.lithmod.mc.data.DieRulesDataLoader;
import com.euvcraft.lithmod.mc.data.SupportLineDataLoader;
import com.euvcraft.lithmod.mc.registry.ModDataComponents;
import com.euvcraft.lithmod.mc.registry.ModItems;
import com.euvcraft.lithmod.mc.machine.process.ProcessCellRunPersistence;
import com.euvcraft.lithmod.mc.machine.process.ProcessCellRunPersistence.Contract;
import com.euvcraft.lithmod.mc.machine.process.ProcessCellRunPersistence.Saved;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;
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
public class ProcessCellControllerBlockEntity extends FabMachineBlockEntity implements EnergyBufferProvider, ProcessEnvironment {

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
    /** M1 stub; later milestones discover actual support-line machines. */
    public static final Map<String, Integer> M1_STUB_GENERATIONS = Map.of(
        SupportLines.SUBSTRATE, 1, SupportLines.MASK_SHOP, 1,
        SupportLines.DEPOSITION_ETCH, 1, SupportLines.UTILITIES, 1, SupportLines.TEST_PACKAGING, 1);
    private @Nullable ProcessRun run;
    private @Nullable Contract contract;
    private @Nullable Saved pendingRun;
    private @Nullable RefusalReason lastRefusal;
    private boolean abortLatched;
    private boolean needsValidation = true;

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

    /** Geometry and the pure run have one server-side owner. Never tick the generic recipe runner. */
    @Override
    public void serverTick() {
        if (!(level instanceof ServerLevel)) return;
        refreshDefinition();
        if (needsValidation || ++ticksSinceValidation >= REVALIDATE_INTERVAL) {
            needsValidation = false;
            revalidate();
        }
        if (slot(SLOT_FOUP).isEmpty() && abortLatched) releaseStoppedLot();
        if (pendingRun != null) {
            try {
                contract = pendingRun.contract();
                run = pendingRun.restore(FoupItems.trustedContentsOf(slot(SLOT_FOUP)));
                pendingRun = null;
            } catch (RuntimeException malformed) { stopInvalidRun(); }
        }
        if (run != null && run.state() == ProcessState.FINISHED) {
            FoupContents current = trustedLot();
            if (!Objects.equals(current, run.lot())) { run = null; contract = null; }
        }
        if (run == null && !abortLatched && formed && definition().isPresent()) tryStartRun();
        if (run != null && run.state() != ProcessState.FINISHED) {
            if (!inputsMatch()) stopInvalidRun();
            else {
                ProcessRun.Snapshot before = run.snapshot();
                try {
                    ProcessTickResult result = run.tick(this);
                    for (ProcessRunEvent event : result.events()) {
                        if (event.type() == ProcessRunEvent.Type.MASK_USE_CONSUMED) {
                            MaskData mask = slot(SLOT_MASK).get(ModDataComponents.MASK_DATA.get());
                            MaskData next = mask.consumeUse();
                            if (next.isSpent()) clearSlot(SLOT_MASK);
                            else {
                                ItemStack updated = slot(SLOT_MASK).copy();
                                updated.set(ModDataComponents.MASK_DATA.get(), next);
                                setSlot(SLOT_MASK, updated);
                            }
                        }
                    }
                    if (!before.equals(run.snapshot())) setChanged();
                } catch (RuntimeException invalid) {
                    // No refund or retry after a rejected private contract/yield operation.
                    stopInvalidRun();
                }
            }
        }
        publishProgress();
    }

    private @Nullable FoupContents trustedLot() {
        ItemStack stack = slot(SLOT_FOUP);
        return stack.getCount() == 1 && FoupItems.isFoup(stack) ? FoupItems.trustedContentsOf(stack) : null;
    }

    private void tryStartRun() {
        FoupContents lot = trustedLot();
        if (lot == null) { lastRefusal = RefusalReason.NO_LOT; return; }
        if (lot.isEmpty()) { lastRefusal = RefusalReason.EMPTY_LOT; return; }
        ItemStack maskStack = slot(SLOT_MASK);
        MaskData mask = maskStack.get(ModDataComponents.MASK_DATA.get());
        if (maskStack.getCount() != 1 || !maskStack.is(ModItems.MASK.get()) || mask == null) {
            lastRefusal = RefusalReason.NO_MASK; return;
        }
        var registry = TierDataLoader.getRegistry();
        Tier tier = registry.byIndex(mask.tierIndex()).orElse(null);
        if (tier == null || tier.index() > 3) { lastRefusal = RefusalReason.NO_MASK; return; }
        LineLoadout loadout = LineLoadoutReader.read(slot(SLOT_LIGHT_SOURCE), slot(SLOT_OPTICS),
            slot(SLOT_STAGE), slot(SLOT_CLEANROOM), slot(SLOT_METROLOGY), slot(SLOT_RESIST), mask.tierIndex());
        DefaultStatRules rules = new DefaultStatRules(registry);
        FabSettings settings = EUVCRAFTMod.settings();
        GenerationMatrix matrix = SupportLineDataLoader.getMatrix();

        // Derive actual installed generations for T3 admission:
        // SUBSTRATE from trusted lot's wafer grade, MASK_SHOP from mask shopGeneration, other three lines at G1 for M1
        int substrateGen = lot.grade().generation();
        int maskShopGen = mask.shopGeneration();
        Map<String, Integer> installedGenerations = Map.of(
            SupportLines.SUBSTRATE, substrateGen,
            SupportLines.MASK_SHOP, maskShopGen,
            SupportLines.DEPOSITION_ETCH, 1,
            SupportLines.UTILITIES, 1,
            SupportLines.TEST_PACKAGING, 1
        );

        Integer cores = (tier.index() == 3 && DieTypes.COMPUTE.equals(mask.dieType())) ? 1 : null;
        ProcessRunRequest request = new ProcessRunRequest(lot, mask.dieType(), tier, cores,
            mask.usesRemaining(), rules.stats(loadout.toLineComponents(), tier.index()),
            rules.canRunTier(loadout.toLineComponents(), tier.index()),
            DieRulesDataLoader.getRules().canMake(mask.dieType(), tier), installedGenerations,
            matrix, settings, definition().orElseThrow().energyPerTick(tier, settings));
        ProcessStartResult result = ProcessRun.start(request);
        if (result instanceof RefusalReason refusal) { lastRefusal = refusal; return; }
        Map<String, List<Integer>> required = new LinkedHashMap<>();
        for (String line : SupportLines.ALL) required.put(line,
            registry.all().stream().map(t -> matrix.requiredGeneration(line, t)).toList());
        List<ItemStack> components = new ArrayList<>();
        for (int index = SLOT_LIGHT_SOURCE; index <= SLOT_METROLOGY; index++) components.add(slot(index).copy());
        ItemStack carrier = slot(SLOT_FOUP).copy();
        carrier.remove(ModDataComponents.FOUP_CONTENTS.get());
        try {
            contract = new Contract(tier, mask.dieType(), mask.usesRemaining(), tier.layerTicks(settings),
                settings.resistPerLayer(), request.energyPerTick(), request.lineStats(), registry.all(),
                required, maskStack.copy(), components, slot(SLOT_RESIST).copyWithCount(1), lot.lotId(), carrier, lot.grade());
            run = (ProcessRun) result;
            lastRefusal = null;
            setChanged();
        } catch (RuntimeException invalid) { stopInvalidRun(); }
    }

    private boolean inputsMatch() {
        if (contract == null || run == null || !Objects.equals(trustedLot(), run.lot())) return false;
        ItemStack carrier = slot(SLOT_FOUP).copy();
        carrier.remove(ModDataComponents.FOUP_CONTENTS.get());
        if (!ItemStack.isSameItemSameComponents(carrier, contract.foupTemplate())
                || !sameSingleton(slot(SLOT_MASK), contract.mask())) return false;
        for (int i = 0; i < 5; i++)
            if (!sameSingleton(slot(SLOT_LIGHT_SOURCE + i), contract.components().get(i))) return false;
        return slot(SLOT_RESIST).isEmpty() || canTopUpResist(slot(SLOT_RESIST));
    }

    private static boolean sameSingleton(ItemStack current, ItemStack template) {
        return current.getCount() == 1 && ItemStack.isSameItemSameComponents(current, template);
    }

    /** Abort releases input locks, but automatic startup remains latched until FOUP removal. */
    public void abortRun() {
        if (!(level instanceof ServerLevel) || !inputsLocked()) return;
        stopInvalidRun();
        publishProgress();
    }

    private void stopInvalidRun() {
        run = null; contract = null; pendingRun = null;
        abortLatched = true; lastRefusal = null;
        setChanged();
    }

    private void releaseStoppedLot() {
        abortLatched = false; run = null; contract = null; pendingRun = null; lastRefusal = null;
        setChanged();
    }

    private void observeFoupRemoval(int index, ItemStack previous) {
        if (index == SLOT_FOUP && slot(index).isEmpty()) releaseStoppedLot();
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        ItemStack previous = slot(index).copy();
        ItemStack removed = super.removeItem(index, count);
        observeFoupRemoval(index, previous);
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        ItemStack previous = slot(index).copy();
        ItemStack removed = super.removeItemNoUpdate(index);
        observeFoupRemoval(index, previous);
        return removed;
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        ItemStack previous = slot(index).copy();
        super.setItem(index, stack);
        observeFoupRemoval(index, previous);
    }

    @Override
    public void clearContent() {
        super.clearContent();
        releaseStoppedLot();
    }

    public boolean inputsLocked() { return pendingRun != null || run != null && run.state() != ProcessState.FINISHED; }
    public boolean aborted() { return abortLatched; }
    public boolean canTopUpResist(ItemStack stack) {
        Contract accepted = contract != null ? contract : pendingRun == null ? null : pendingRun.contract();
        return accepted != null && !stack.isEmpty()
            && ItemStack.isSameItemSameComponents(stack, accepted.resistTemplate());
    }
    public @Nullable RefusalReason lastRefusal() { return lastRefusal; }
    public ProcessState processState() { return run == null ? ProcessState.IDLE : run.state(); }
    public int currentLayer() { return run == null ? 0 : Math.min(run.currentLayer(), totalLayers()); }
    public int progressTicks() { return run == null ? 0 : run.progressTicks(); }
    public int totalLayers() { return contract == null ? 0 : contract.tier().layers(); }
    public int tierIndex() { return contract == null ? 0 : contract.tier().index(); }
    public @Nullable String dieType() { return contract == null ? null : contract.dieType(); }

    private void publishProgress() {
        data().set(DATA_ENERGY, buffer().getAmountAsInt());
        data().set(DATA_CAPACITY, buffer().getCapacityAsInt());
        data().set(DATA_PROGRESS, progressTicks());
        data().set(DATA_TICKS, contract == null ? 0 : contract.layerTicks());
    }

    @Override
    public boolean structureFormed() { return !needsValidation && formed && definition().isPresent(); }
    @Override
    public int extractEnergy(int amount, boolean simulate) {
        if (amount <= 0) return 0;
        return simulate ? buffer().getAmountAsLong() >= amount ? amount : 0 : energySource().draw(amount);
    }
    @Override
    public int resistAvailable() { return canTopUpResist(slot(SLOT_RESIST)) ? slot(SLOT_RESIST).getCount() : 0; }
    @Override
    public void consumeResist(int amount) {
        if (amount < 0 || amount > resistAvailable()) throw new IllegalStateException("Invalid resist payment");
        slot(SLOT_RESIST).shrink(amount);
        if (slot(SLOT_RESIST).isEmpty()) clearSlot(SLOT_RESIST);
        setChanged();
    }
    @Override
    public FoupContents applyLayer(FoupContents lot, int layer, double defectFactor) {
        if (!(level instanceof ServerLevel server) || contract == null || !inputsMatch()
                || !Objects.equals(lot, trustedLot())) throw new IllegalStateException("Run inputs changed");
        FoupContents updated = FabYieldService.applyLayerStep(server, lot, contract.tier(),
            contract.dieType(), DieTypes.COMPUTE.equals(contract.dieType()) ? 1 : null, layer, defectFactor);
        ItemStack carrier = slot(SLOT_FOUP).copy();
        ItemStack sealed = FoupItems.with(carrier.getItem(), updated);
        carrier.set(ModDataComponents.FOUP_CONTENTS.get(), sealed.get(ModDataComponents.FOUP_CONTENTS.get()));
        setSlot(SLOT_FOUP, carrier);
        return updated;
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
            // The membership is unchanged, but a member's own block state can still have been reset
            // by an edit that replaced the block, which clears FORMED/PART/FACING. updateMembers only writes a
            // state that differs, so re-running it here repairs the flags without counting as a
            // transition and without firing the hooks again.
            updateMembers(members(), facing, true);
        }
    }

    /** Flags or unflags every listed member and rewires its controller link with PART and FACING. */
    private void updateMembers(List<BlockPos> positions, Direction facing, boolean formed) {
        if (level == null) return;
        List<BlockPos> current = members();
        for (BlockPos pos : positions) {
            BlockEntity entity = level.getBlockEntity(pos);
            // A departed controller must never modify the replacement at its old location.
            if (entity instanceof ProcessCellControllerBlockEntity core && core != this) continue;
            if (entity instanceof ProcessCellMemberBlockEntity member) {
                ProcessCellControllerBlockEntity owner = member.controller();
                if (owner != null && owner != this && level.getBlockEntity(owner.getBlockPos()) == owner
                        && owner.isFormed() && owner.members().contains(pos)) continue;
            }
            int part = formed ? current.indexOf(pos) : 0;
            if (part < 0) continue;
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof ProcessCellControllerBlock)
                    && !(state.getBlock() instanceof ProcessCellCasingBlock)
                    && !(state.getBlock() instanceof ProcessCellPowerPortBlock)
                    && !(state.getBlock() instanceof ProcessCellOperatingPanelBlock)) continue;
            if (entity instanceof ProcessCellMemberBlockEntity member) {
                member.attach(formed ? this : null, formed, part, formed ? facing : null);
            }
            BlockState next = state.setValue(ProcessCellControllerBlock.FORMED, formed)
                .setValue(ProcessCellControllerBlock.PART, part);
            if (formed) next = next.setValue(ProcessCellControllerBlock.FACING, facing);
            if (next != state) level.setBlock(pos, next, 3);
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
        if (level == null || level.getBlockEntity(worldPosition) != this) {
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
        if (blockEntity instanceof ProcessCellOperatingPanelBlockEntity panel) {
            if (panel.isMemberOfFormedCell() && panel.controller() != this) {
                return BlockKind.OTHER;
            }
            return BlockKind.OPERATING_PANEL;
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
        run = null; contract = null; pendingRun = null; lastRefusal = null;
        abortLatched = false;
        needsValidation = true;
        try {
            boolean hasRunField = input.read("processHasRun", Codec.PASSTHROUGH).isPresent();
            boolean abortField = input.read("processAbortLatched", Codec.PASSTHROUGH).isPresent();
            boolean payloadField = input.read("processRun", Codec.PASSTHROUGH).isPresent();
            var hasRun = input.read("processHasRun", Codec.BOOL);
            var stopped = input.read("processAbortLatched", Codec.BOOL);
            abortLatched = stopped.orElse(false);
            // Legacy idle saves have no run keys. Present but malformed or inconsistent
            // keys must never turn accepted partial work into a new automatic admission.
            if ((hasRunField && hasRun.isEmpty()) || (abortField && stopped.isEmpty())
                    || (payloadField && !hasRun.orElse(false))) {
                stopInvalidRun();
            } else if (hasRun.orElse(false)) {
                pendingRun = input.read("processRun", ProcessCellRunPersistence.CODEC).orElse(null);
                if (pendingRun == null || abortLatched) stopInvalidRun();
            }
        } catch (RuntimeException malformed) { stopInvalidRun(); }
        publishProgress();
        // Formation member list remains world-derived (not persisted)
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output); // saves inventory and energy via base
        output.putInt("formed", this.formed ? 1 : 0);
        output.putString("failureReason", this.failureReason);
        output.putBoolean("processAbortLatched", abortLatched);
        Saved saved = pendingRun != null ? pendingRun : run == null ? null : new Saved(contract, run.snapshot());
        output.putBoolean("processHasRun", saved != null);
        if (saved != null) output.store("processRun", ProcessCellRunPersistence.CODEC, saved);
        // Formation member list not persisted on purpose
    }

    // ---------------------------------------------------------------- MenuProvider

    @Override
    protected AbstractContainerMenu createMachineMenu(int containerId, Inventory playerInventory, FabMachineBlockEntity machine) {
        return new ProcessCellMenu(containerId, playerInventory, this);
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
