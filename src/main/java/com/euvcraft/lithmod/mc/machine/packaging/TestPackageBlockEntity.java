package com.euvcraft.lithmod.mc.machine.packaging;

import com.euvcraft.lithmod.core.DieTypes;
import com.euvcraft.lithmod.core.FoupContents;
import com.euvcraft.lithmod.core.GenerationMatrix;
import com.euvcraft.lithmod.core.MachineDefinition;
import com.euvcraft.lithmod.core.PackagingPlan;
import com.euvcraft.lithmod.core.PackagingRefusalReason;
import com.euvcraft.lithmod.core.PackagingRules;
import com.euvcraft.lithmod.core.PackagingRun;
import com.euvcraft.lithmod.core.RunSpec;
import com.euvcraft.lithmod.core.Tier;
import com.euvcraft.lithmod.core.TierRegistry;
import com.euvcraft.lithmod.mc.ChipCatalog;
import com.euvcraft.lithmod.mc.EUVCRAFTMod;
import com.euvcraft.lithmod.mc.FabYieldService;
import com.euvcraft.lithmod.mc.FoupItems;
import com.euvcraft.lithmod.mc.data.SupportLineDataLoader;
import com.euvcraft.lithmod.mc.data.TierDataLoader;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Test and Package machine: one finished lot in, chips and scrap out.
 *
 * <p>This machine does not use {@code MachineRecipeRunner}. Its product is not an
 * {@code ItemStack} a recipe can assemble: it is the result of judging a sealed lot of
 * wafers, and the judgment is a server-side roll that must happen exactly once per lot.
 * The base class still owns the energy buffer, the slots, the definition refresh and the
 * drop-on-break behaviour; this class owns only the queue.</p>
 *
 * <h2>The anti-duplication rule</h2>
 *
 * <p>A lot is judged and <em>consumed</em> in the same server tick a run starts. Admission
 * reads the FOUP with {@link FoupItems#trustedContentsOf}, resolves the frozen
 * {@link RunSpec}'s tier, die type and cores, checks the lot against
 * {@link PackagingRules} and calls {@link FabYieldService#evaluateLot} once. The pass
 * flags are stored in an immutable {@link PackagingPlan} inside a {@link PackagingRun},
 * and the FOUP slot is immediately replaced by an empty lot of the same item and the same
 * capacity with a fresh lot id. Nothing keeps the judged {@link FoupContents}, its rolls
 * or its {@code effectiveYield}: only the plan's flags and the public design survive.</p>
 *
 * <p>Breaking the machine mid-run therefore drops the returned empty FOUP, the real output
 * written so far and any real inventory, and loses the wafers that were still queued. The
 * original lot cannot be recovered and cannot be judged a second time, which is the whole
 * point: the alternative would let a player reload the same lot for two sets of chips.</p>
 *
 * <h2>The queue</h2>
 *
 * <p>{@link PackagingRun} works one wafer at a time. Each tick this machine preflights the
 * packages the wafer needs (only a passing wafer needs any), then the room in the correct
 * output bank, then the full energy for the tick, and hands all three to
 * {@link PackagingRun#tick}. The base class's {@code energySource().draw} is already
 * transactional full-or-zero, so {@code draw(cost) == cost} is the all-or-nothing payment
 * the run asks for and also answers {@code true} for a zero cost. A blocked tick moves
 * nothing and pays nothing. When a tick completes a wafer the packages are consumed and
 * the output written in that same tick.</p>
 *
 * <h2>Data, and what is frozen</h2>
 *
 * <ul>
 *   <li>the wafer count, the chip and package amounts and the per-tick energy are frozen
 *       at the accepted start, so editing the config or the definition mid-run does not
 *       change a queue already in flight;</li>
 *   <li>a definition that is missing or has unusable parameters pauses an accepted queue
 *       and preserves it — the base class's fail-closed answer closes the buffer, and the
 *       queue is deliberately not reset, so restoring the definition resumes it exactly
 *       where it stopped rather than erasing the wafers already consumed;</li>
 *   <li>an impossible configuration (the buffer cannot pay one tick, the package amount
 *       cannot fit one slot, the per-wafer output cannot fit its bank) pauses the machine
 *       <em>before</em> the lot is consumed, never after.</li>
 * </ul>
 *
 * <h2>What the menu is told</h2>
 *
 * <p>The base class's four synced values are left alone and this machine owns three more:
 * a coarse state, the wafers done and the wafer total. The state is either idle, running,
 * paused or one of the coarse admission refusals; a pause never says <em>why</em> it
 * paused. The pass flags, the current wafer's result and the frozen parameters are
 * server-only and appear in no menu, tooltip, network packet, log line or
 * {@code toString}.</p>
 */
public class TestPackageBlockEntity extends FabMachineBlockEntity {

    /** The machine id, matching {@code data/lithmod/machines/test_package.json}. */
    public static final String MACHINE_ID = "test_package";

    /** Slot 0: the sealed lot the machine judges. */
    public static final int FOUP_SLOT = 0;
    /** Slot 1: the packaging consumable; only {@code chip_package} is accepted. */
    public static final int PACKAGE_SLOT = 1;
    /** How many slots at the front hold input; the rest are outputs. */
    public static final int INPUT_SLOTS = 2;
    /** First of the four chip output slots. */
    public static final int CHIP_SLOT_FIRST = 2;
    /** How many chip output slots there are. */
    public static final int CHIP_SLOT_COUNT = 4;
    /** First of the two scrap output slots. */
    public static final int SCRAP_SLOT_FIRST = 6;
    /** How many scrap output slots there are. */
    public static final int SCRAP_SLOT_COUNT = 2;
    /** How many output slots there are in total. */
    public static final int OUTPUT_SLOTS = CHIP_SLOT_COUNT + SCRAP_SLOT_COUNT;
    /** Total slot count: two inputs and six outputs. */
    public static final int SLOT_COUNT = INPUT_SLOTS + OUTPUT_SLOTS;

    /** Name of the definition param that gives the ticks one wafer takes. */
    public static final String TICKS_PER_WAFER_PARAM = "ticks_per_wafer";
    /** Name of the definition param that gives the packages a passing wafer consumes. */
    public static final String PACKAGE_PER_WAFER_PARAM = "package_per_wafer";

    /**
     * The packaging generation this machine has installed.
     *
     * <p>Hard-coded on purpose, exactly like the Pattern Generator's mask shop generation:
     * the machine <em>is</em> the generation 1 tool, so the support-line gate is asked as
     * generation 1 rather than read from something a player could swap. Raising it is a
     * later milestone's change to this constant and to nothing else.</p>
     */
    public static final int INSTALLED_GENERATION = 1;

    /** Nothing to show: no queue and nothing refused. */
    public static final int STATE_IDLE = 0;
    /** A queue is working or has worked its last wafer this tick. */
    public static final int STATE_RUNNING = 1;
    /** A queue exists or a lot is waiting, but the machine cannot proceed. */
    public static final int STATE_PAUSED = 2;
    /**
     * First of the five admission-refusal states: {@code STATE_REFUSED_BASE + reason.ordinal()}.
     *
     * <p>The ordinals of {@link PackagingRefusalReason} are the wire order, so the machine
     * never keeps a second copy of the reason list.</p>
     */
    public static final int STATE_REFUSED_BASE = 3;

    /** Index of the coarse state inside this machine's own three synced values. */
    public static final int QUEUE_DATA_STATE = 0;
    /** Index of the wafers-done count inside this machine's own three synced values. */
    public static final int QUEUE_DATA_DONE = 1;
    /** Index of the wafer total inside this machine's own three synced values. */
    public static final int QUEUE_DATA_TOTAL = 2;
    /** How many values this machine adds to the base class's four. */
    public static final int QUEUE_DATA_COUNT = 3;

    /** The NBT child holding this machine's server-only queue snapshot. */
    private static final String STATE_KEY = "packaging";
    private static final String FLAGS_KEY = "flags";
    private static final String CHIPS_PER_WAFER_KEY = "chips_per_wafer";
    private static final String PACKAGES_PER_WAFER_KEY = "packages_per_wafer";
    private static final String TICKS_PER_WAFER_KEY = "ticks_per_wafer";
    private static final String ENERGY_PER_TICK_KEY = "energy_per_tick";
    private static final String INDEX_KEY = "index";
    private static final String PAID_TICKS_KEY = "paid_ticks";
    private static final String DIE_TYPE_KEY = "die_type";
    private static final String TIER_KEY = "tier";

    /** The vanilla maximum an output or consumable slot holds. */
    private static final int MAX_STACK = 64;

    /** The three values this machine syncs on top of the base class's four. */
    private final ContainerData queueData = new SimpleContainerData(QUEUE_DATA_COUNT);

    /** The queue, or {@code null} when no lot has been accepted. Server-only. */
    private PackagingRun run;
    /** The judged plan the run works through; the same immutable plan the run holds. */
    private PackagingPlan plan;
    /** Public frozen die type of the accepted lot, for catalog resolution. */
    private String designDieType;
    /** Public frozen node tier of the accepted lot, for catalog resolution. */
    private int designTierIndex;
    /** Why the lot in the input slot was refused, or {@code null}. Recomputed each tick. */
    private PackagingRefusalReason refusal;
    /** Started but blocked on packages, output room, energy or data. Not persisted. */
    private boolean paused;

    public TestPackageBlockEntity(BlockPos pos, BlockState state) {
        this(PackagingMachines.TEST_PACKAGE_BE.get(), pos, state, INPUT_SLOTS, OUTPUT_SLOTS);
    }

    protected TestPackageBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state, int inputs, int outputs) {
        super(type,pos,state,inputs,outputs);
    }
    protected int installedGeneration() { return INSTALLED_GENERATION; }
    protected boolean processingAllowed() { return true; }

    @Override
    protected String machineId() {
        return MACHINE_ID;
    }

    @Override
    protected AbstractContainerMenu createMachineMenu(int containerId, Inventory playerInventory,
                                                       FabMachineBlockEntity machine) {
        return new TestPackageMenu(containerId, playerInventory, machine);
    }

    // ------------------------------------------------------------------
    // What the menu reads
    // ------------------------------------------------------------------

    /**
     * The three values this machine syncs for its own menu, after the base class's four.
     *
     * <p>Kept out of {@link #DATA_COUNT} for the same reason the Pattern Generator's status
     * value is: the base four are every machine's contract, and a machine that needed more
     * would force them on every other machine's menu. The menu registers this after the
     * base values on both sides, so it is always the fifth, sixth and seventh on the wire.</p>
     */
    public final ContainerData queueData() {
        return this.queueData;
    }

    /** The coarse state: idle, running, paused or an admission refusal. */
    public final int state() {
        return this.queueData.get(QUEUE_DATA_STATE);
    }

    /** Wafers fully completed; the finished queue's total once it has run out. */
    public final int wafersDone() {
        return this.queueData.get(QUEUE_DATA_DONE);
    }

    /** Wafers the accepted lot held, or zero before any lot was accepted. */
    public final int wafersTotal() {
        return this.queueData.get(QUEUE_DATA_TOTAL);
    }

    /** Whether the queue is accepted and not yet finished. */
    public final boolean isQueueActive() {
        return this.run != null && !this.run.isFinished();
    }

    /** Started but stopped for want of a resource or of usable data. */
    public final boolean isPaused() {
        return this.paused;
    }

    /**
     * Why the lot currently in the input slot was refused, or {@code null}.
     *
     * <p>A coarse public reason: which admission check the presented lot failed. An input
     * that holds no sealed lot at all, and a sealed lot holding no wafers — including the
     * empty lot this machine returns when a queue starts — are treated as nothing being
     * offered rather than as refusals, so {@link PackagingRefusalReason#NO_LOT} is not
     * produced here. The machine never has a reason while a queue is in flight, because a
     * lot is only examined when nothing is being worked.</p>
     */
    public final PackagingRefusalReason refusalReason() {
        return this.refusal;
    }

    /** Whether a stack is the packaging consumable this machine's package slot accepts. */
    public static boolean isPackage(ItemStack stack) {
        return stack.is(ModItems.CHIP_PACKAGE.get());
    }

    // ------------------------------------------------------------------
    // Ticking
    // ------------------------------------------------------------------

    /**
     * One server tick.
     *
     * <p>Replaces the base implementation because this machine's product is a judged lot
     * rather than a recipe result; the base implementation would run
     * {@code MachineRecipeRunner} over recipes that do not exist for this machine id. It
     * calls {@link #refreshDefinition() the definition refresh} and nothing else of the
     * base tick, so a data change resizes the real buffer and closes it when the definition
     * goes away, and the queue this class owns is never touched by that answer.</p>
     */
    @Override
    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        refreshDefinition();
        if (!processingAllowed()) { this.paused=isQueueActive(); publish(); return; }

        boolean changed;
        if (isQueueActive()) {
            if (definition().isEmpty()) {
                this.paused = true;
                publish();
                return;
            }
            // Accepted work uses its frozen parameters. Current admission settings
            // govern the next lot, even when those settings are temporarily invalid.
            changed = advance();
        } else {
            MachineParams params = resolveParams();
            if (params == null) {
                this.paused = false;
                publish();
                return;
            }
            changed = attemptAdmission(params);
        }
        publish();
        if (changed) {
            setChanged();
        }
    }

    /**
     * The queue and the machine's own numbers, from the definition and the config.
     *
     * <p>Every number comes from data, with no Java copy of any of them. A definition that
     * is absent, or that declares a tick count, package amount or per-wafer output the
     * machine's own banks cannot hold, yields {@code null}: nothing may run on a guessed
     * number, and an impossible configuration must never consume a lot.</p>
     */
    private MachineParams resolveParams() {
        MachineDefinition definition = definition().orElse(null);
        if (definition == null) {
            return null;
        }
        int ticksPerWafer = definition.param(TICKS_PER_WAFER_PARAM).orElse(0);
        if (ticksPerWafer < 1) {
            return null;
        }
        int packagesPerWafer = definition.param(PACKAGE_PER_WAFER_PARAM).orElse(0);
        if (packagesPerWafer < 1 || packagesPerWafer > MAX_STACK) {
            return null;
        }
        int chipsPerWafer = EUVCRAFTMod.settings().chipsPerWafer();
        if (chipsPerWafer < 1
                || chipsPerWafer > CHIP_SLOT_COUNT * MAX_STACK
                || chipsPerWafer > SCRAP_SLOT_COUNT * MAX_STACK) {
            return null;
        }
        return new MachineParams(ticksPerWafer, packagesPerWafer, chipsPerWafer);
    }

    /**
     * Offers the stack in the input slot for judgment, and starts the queue when it is
     * accepted.
     *
     * <p>Nothing is consumed unless the whole gate passes and the plan is built: the FOUP
     * is replaced only after the judgment has been taken, so a refused or unrepresentable
     * lot stays exactly where the player put it.</p>
     *
     * @return whether this tick consumed a lot and started a queue
     */
    private boolean attemptAdmission(MachineParams params) {
        ItemStack foupStack = slot(FOUP_SLOT);

        // An empty slot, a stack that carries no sealed lot at all, and a lot with no wafers
        // are all "nothing is being offered", so none of them is an admission failure. The
        // first two are unreachable through the menu, because the FOUP slot only ever
        // accepts a stack carrying a sealed lot; the third is the empty lot this machine
        // itself returns when a queue starts, and reporting a refusal for the machine's own
        // returned FOUP would clear the completed status the finished queue is still
        // showing. Nothing is read from a lot-less stack, so no per-tick warning is raised
        // for one either.
        if (foupStack.isEmpty() || !FoupItems.isFoup(foupStack)) {
            this.refusal = null;
            this.paused = false;
            return false;
        }
        FoupContents lot = FoupItems.trustedContentsOf(foupStack);
        if (lot.isEmpty()) {
            this.refusal = null;
            this.paused = false;
            return false;
        }

        PackagingRefusalReason reason = admissionRefusal(lot);
        if (reason != null) {
            this.refusal = reason;
            this.paused = false;
            return false;
        }

        // The lot is eligible. The machine itself must be able to pay for one tick before
        // anything is consumed, or the lot would vanish into a queue that can never finish.
        Tier tier = tierForIndex(lot.run().tierIndex());
        MachineDefinition definition = definition().orElse(null);
        if (definition == null) {
            this.refusal = null;
            this.paused = true;
            return false;
        }
        int perTick = definition.energyPerTick(tier, EUVCRAFTMod.settings());
        if (definition.energyCapacity() < perTick) {
            this.refusal = null;
            this.paused = true;
            return false;
        }
        return startLot(foupStack, lot, tier, perTick, params);
    }

    /**
     * The admission gate, in the order {@link PackagingRules} runs from the lot outwards.
     *
     * <p>The lot is only read through the trusted read; the tier, the catalog answer and the
     * generation matrix are resolved here and handed to the pure rule. Unknown data — a tier
     * the registry cannot name, or a matrix that has not been loaded — fails closed with the
     * one coarse reason that covers the environment rather than letting the lot in.</p>
     *
     * @param lot the trusted contents of the input slot, not null and holding at least one
     *            wafer
     * @return {@code null} when the lot may be judged, otherwise the refusal reason
     */
    private PackagingRefusalReason admissionRefusal(FoupContents lot) {
        RunSpec spec = lot.run();
        if (spec == null) {
            return PackagingRefusalReason.NO_SPEC;
        }
        Tier tier = tierForIndex(spec.tierIndex());
        GenerationMatrix matrix = SupportLineDataLoader.getMatrix();
        if (tier == null || matrix == null) {
            return PackagingRefusalReason.GENERATION_UNMET;
        }
        boolean chipItemAvailable = ChipCatalog.chipFor(spec.dieType(), spec.tierIndex()).isPresent();
        return PackagingRules.validate(lot, tier, chipItemAvailable, matrix, installedGeneration());
    }

    /**
     * Judges the lot once and consumes it.
     *
     * <p>The order is the whole of the anti-duplication rule. The judgment and the plan are
     * built first; only then is the FOUP replaced. The replacement is a new empty lot of the
     * same item and the capacity the judged lot had, with a fresh lot id — never
     * {@code emptyFoup(currentConfigCapacity)}, because the configured capacity may have
     * moved since the lot was made.</p>
     *
     * @return whether the lot was consumed
     */
    private boolean startLot(ItemStack foupStack, FoupContents lot, Tier tier, int perTick,
                             MachineParams params) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        RunSpec spec = lot.run();
        PackagingPlan judged;
        PackagingRun queue;
        try {
            boolean[] flags = FabYieldService.evaluateLot(serverLevel, lot, tier,
                spec.dieType(), spec.coresOrNull());
            List<Boolean> passFlags = new ArrayList<>(flags.length);
            for (boolean flag : flags) {
                passFlags.add(flag);
            }
            judged = new PackagingPlan(passFlags, params.chipsPerWafer(), params.packagesPerWafer());
            queue = new PackagingRun(judged, params.ticksPerWafer(), perTick);
        } catch (RuntimeException unrepresentable) {
            // A lot the gate let through but the judgment or the plan could not express is
            // left untouched rather than half-consumed.
            this.refusal = null;
            this.paused = true;
            return false;
        }

        this.plan = judged;
        this.run = queue;
        this.designDieType = spec.dieType();
        this.designTierIndex = spec.tierIndex();
        this.refusal = null;
        this.paused = false;

        // Consume the judged lot this instant: the slot carries an empty lot of the same
        // item and capacity, so the original lot can never be judged a second time.
        setSlot(FOUP_SLOT, FoupItems.with(foupStack.getItem(),
            FoupContents.empty(UUID.randomUUID(), lot.capacity())));
        return true;
    }

    /**
     * One tick of the accepted queue.
     *
     * <p>The order is packages, then output room, then the full energy payment — all inside
     * {@link PackagingRun#tick}, so a blocked tick buys nothing. The wafer's target bank is
     * chosen from the plan, which is server-only data and is never synced.</p>
     *
     * @return whether the queue moved this tick
     */
    private boolean advance() {
        PackagingRun run = this.run;
        PackagingPlan plan = this.plan;
        if (run == null || plan == null || run.isFinished()) {
            this.paused = false;
            return false;
        }

        Item chipItem = ChipCatalog.chipFor(this.designDieType, this.designTierIndex).orElse(null);
        if (chipItem == null) {
            // The catalog lost the design the queue was accepted under: pause rather than
            // write anything, because there is no item to write.
            this.paused = true;
            return false;
        }

        int waferIndex = run.wafersDone();
        boolean passing = plan.passes(waferIndex);
        int bankFirst = passing ? CHIP_SLOT_FIRST : SCRAP_SLOT_FIRST;
        int bankCount = passing ? CHIP_SLOT_COUNT : SCRAP_SLOT_COUNT;
        Item bankItem = passing ? chipItem : scrapItem();

        int doneBefore = run.wafersDone();
        int paidBefore = run.ticksPaidOnCurrentWafer();

        PackagingRun.Completion completion = run.tick(
            needed -> packageCount() >= needed,
            requested -> bankRoom(bankFirst, bankCount, bankItem) >= requested,
            cost -> energySource().draw(cost) == cost);

        if (completion == null) {
            boolean progressed = run.wafersDone() != doneBefore
                || run.ticksPaidOnCurrentWafer() != paidBefore;
            this.paused = !progressed;
            return progressed;
        }

        // The completion and the wafer's progress moved in the same step, so this output is
        // written once and the packages are consumed once, in this tick.
        if (completion.packagesUsed() > 0) {
            consumePackages(completion.packagesUsed());
        }
        insertIntoBank(completion.passing() ? CHIP_SLOT_FIRST : SCRAP_SLOT_FIRST,
            completion.passing() ? CHIP_SLOT_COUNT : SCRAP_SLOT_COUNT,
            completion.passing() ? chipItem : scrapItem(),
            completion.amount());
        this.paused = false;
        return true;
    }

    /** The scrap every failing wafer yields. */
    private static Item scrapItem() {
        return ModItems.CHIP_SCRAP.get();
    }

    /** How many packages the package slot holds right now. */
    private int packageCount() {
        ItemStack stack = slot(PACKAGE_SLOT);
        return stack.is(ModItems.CHIP_PACKAGE.get()) ? stack.getCount() : 0;
    }

    /**
     * Takes the packages a finished wafer used out of the package slot.
     *
     * <p>Called only on the tick a completion event arrives, so the amount consumed is
     * always the amount the run reported, never a guess.</p>
     */
    private void consumePackages(int amount) {
        ItemStack stack = slot(PACKAGE_SLOT);
        if (!stack.is(ModItems.CHIP_PACKAGE.get())) {
            return;
        }
        stack.shrink(amount);
        if (stack.isEmpty()) {
            clearSlot(PACKAGE_SLOT);
        } else {
            setChanged();
        }
    }

    /**
     * How many more items the given output bank could take.
     *
     * <p>Denominates the preflight: a wafer is only started when the whole of its output
     * fits in its own bank, so no wafer is ever output in part or dropped.</p>
     */
    private int bankRoom(int first, int count, Item item) {
        int room = 0;
        ItemStack product = new ItemStack(item);
        for (int index = first; index < first + count; index++) {
            ItemStack stack = slot(index);
            if (stack.isEmpty()) {
                room += product.getMaxStackSize();
            } else if (ItemStack.isSameItemSameComponents(stack, product)) {
                room += Math.max(0, stack.getMaxStackSize() - stack.getCount());
            }
        }
        return room;
    }

    /**
     * Writes a completed wafer's output across its bank.
     *
     * <p>Exactly the amount the completion event reported, spread over the slots that
     * already hold that item and then the empty ones. The preflight in {@link #advance()}
     * has already proved the bank has the room.</p>
     */
    private void insertIntoBank(int first, int count, Item item, int amount) {
        int remaining = amount;
        ItemStack product = new ItemStack(item);
        for (int index = first; index < first + count && remaining > 0; index++) {
            ItemStack stack = slot(index);
            if (stack.isEmpty()) {
                int placed = Math.min(remaining, product.getMaxStackSize());
                setSlot(index, new ItemStack(item, placed));
                remaining -= placed;
            } else if (ItemStack.isSameItemSameComponents(stack, product)) {
                int placed = Math.min(remaining, Math.max(0, stack.getMaxStackSize() - stack.getCount()));
                if (placed > 0) {
                    stack.setCount(stack.getCount() + placed);
                    remaining -= placed;
                    setChanged();
                }
            }
        }
    }

    /**
     * Resolves a node tier index against the loaded tier registry.
     *
     * @param index the 1-based tier index of a frozen run spec
     * @return the tier, or {@code null} when the registry is not loaded or names no such tier
     */
    private static Tier tierForIndex(int index) {
        TierRegistry tiers = TierDataLoader.getRegistry();
        if (tiers == null) {
            return null;
        }
        for (Tier tier : tiers.all()) {
            if (tier.index() == index) {
                return tier;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Publishing
    // ------------------------------------------------------------------

    /**
     * Publishes the queue's coarse state and counts to the menu, and the machine's overall
     * progress to the base class's two progress values.
     *
     * <p>The counts are read back off the run, so a finished queue keeps reporting its total
     * until a new lot is accepted. Nothing here is per-wafer result data.</p>
     */
    private void publish() {
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

        this.data().set(DATA_ENERGY, (int) this.buffer().getAmountAsLong());
        this.data().set(DATA_PROGRESS, clampToInt(progress));
        this.data().set(DATA_TICKS, clampToInt(ticks));

        this.queueData.set(QUEUE_DATA_STATE, currentState());
        this.queueData.set(QUEUE_DATA_DONE, done);
        this.queueData.set(QUEUE_DATA_TOTAL, total);
    }

    /** The coarse state, from the queue first and the admission answer second. */
    private int currentState() {
        if (isQueueActive()) {
            return this.paused ? STATE_PAUSED : STATE_RUNNING;
        }
        if (this.refusal != null) {
            return STATE_REFUSED_BASE + this.refusal.ordinal();
        }
        return this.paused ? STATE_PAUSED : STATE_IDLE;
    }

    private static int clampToInt(long value) {
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, value));
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ValueOutput state = output.child(STATE_KEY);
        PackagingRun run = this.run;
        PackagingPlan plan = this.plan;
        if (run == null || plan == null || this.designDieType == null) {
            return;
        }

        PackagingRun.Snapshot snapshot = run.save();
        StringBuilder flags = new StringBuilder(plan.size());
        for (int index = 0; index < plan.size(); index++) {
            flags.append(plan.passes(index) ? '1' : '0');
        }

        state.putString(FLAGS_KEY, flags.toString());
        state.putInt(CHIPS_PER_WAFER_KEY, plan.chipsPerWafer());
        state.putInt(PACKAGES_PER_WAFER_KEY, plan.packagesPerWafer());
        state.putInt(TICKS_PER_WAFER_KEY, snapshot.ticksPerWafer());
        state.putInt(ENERGY_PER_TICK_KEY, snapshot.energyPerTick());
        state.putInt(INDEX_KEY, snapshot.currentIndex());
        state.putInt(PAID_TICKS_KEY, snapshot.paidTicks());
        state.putString(DIE_TYPE_KEY, this.designDieType);
        state.putInt(TIER_KEY, this.designTierIndex);
    }

    /**
     * Restores an accepted queue, or drops a snapshot that cannot be trusted.
     *
     * <p>Every parameter, the progress and the resolved design are validated before the
     * queue is adopted: the plan refuses an empty flag list or a per-wafer amount below
     * one, the run snapshot refuses an index outside the plan and a progress inconsistent
     * with the terminal state, and the design must still be a die type the catalog names at
     * the stored tier. A snapshot that fails any of those is discarded entirely — the
     * original lot is not recovered and no output is produced from it, which is the
     * fail-closed answer for a hand-edited or half-written tag.</p>
     *
     * <p>Stale fields are cleared before the saved data is applied, because this method can
     * run on a block entity that was already mid-run. The restore is deliberately
     * <em>not</em> gated on the definition being loaded: a queue accepted while the
     * definition existed must survive its removal and resume when it comes back, so the
     * pause rule lives in {@link #serverTick()} rather than here. Making the restore depend
     * on the data pack would erase the wafers already consumed and hand the player a queue
     * that never ran.</p>
     */
    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        clearQueue();

        ValueInput state = input.childOrEmpty(STATE_KEY);
        try {
            String flags = state.getStringOr(FLAGS_KEY, "");
            int chipsPerWafer = state.getIntOr(CHIPS_PER_WAFER_KEY, 0);
            int packagesPerWafer = state.getIntOr(PACKAGES_PER_WAFER_KEY, 0);
            int ticksPerWafer = state.getIntOr(TICKS_PER_WAFER_KEY, 0);
            int energyPerTick = state.getIntOr(ENERGY_PER_TICK_KEY, -1);
            int index = state.getIntOr(INDEX_KEY, -1);
            int paidTicks = state.getIntOr(PAID_TICKS_KEY, -1);
            String dieType = state.getStringOr(DIE_TYPE_KEY, "");
            int tierIndex = state.getIntOr(TIER_KEY, 0);

            PackagingPlan restoredPlan = planFromFlags(flags, chipsPerWafer, packagesPerWafer);
            PackagingRun restoredRun = new PackagingRun(
                new PackagingRun.Snapshot(restoredPlan, ticksPerWafer, energyPerTick,
                    index, paidTicks));

            if (!DieTypes.ALL.contains(dieType) || tierIndex < 1
                    || ChipCatalog.chipFor(dieType, tierIndex).isEmpty()) {
                throw new IllegalArgumentException(
                    "a packaging snapshot must name a die type the catalog resolves");
            }

            this.plan = restoredPlan;
            this.run = restoredRun;
            this.designDieType = dieType;
            this.designTierIndex = tierIndex;
        } catch (RuntimeException malformed) {
            // Deliberately no logging and no partial recovery: the snapshot either describes
            // a consistent queue or it is dropped.
            clearQueue();
        }
        publish();
    }

    /** Rebuilds the flag list from its saved form, refusing anything that is not a 0 or a 1. */
    private static PackagingPlan planFromFlags(String flags, int chipsPerWafer, int packagesPerWafer) {
        if (chipsPerWafer > MAX_STACK || packagesPerWafer > MAX_STACK) {
            throw new IllegalArgumentException("a packaging snapshot exceeds supported per-wafer quantities");
        }
        if (flags.isEmpty()) {
            throw new IllegalArgumentException("a packaging snapshot needs at least one wafer flag");
        }
        List<Boolean> passFlags = new ArrayList<>(flags.length());
        for (int index = 0; index < flags.length(); index++) {
            char flag = flags.charAt(index);
            if (flag == '1') {
                passFlags.add(Boolean.TRUE);
            } else if (flag == '0') {
                passFlags.add(Boolean.FALSE);
            } else {
                throw new IllegalArgumentException("a packaging snapshot flag must be 0 or 1");
            }
        }
        return new PackagingPlan(passFlags, chipsPerWafer, packagesPerWafer);
    }

    /** Drops the queue and everything derived from it. Never consumes or restores a lot. */
    private void clearQueue() {
        this.run = null;
        this.plan = null;
        this.designDieType = null;
        this.designTierIndex = 0;
        this.refusal = null;
        this.paused = false;
    }

    /**
     * The machine's frozen numbers for one accepted lot: the ticks a wafer takes, the
     * packages a passing wafer uses and the chips (or scrap) a wafer yields.
     */
    private record MachineParams(int ticksPerWafer, int packagesPerWafer, int chipsPerWafer) {
    }
}
