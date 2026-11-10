package com.euvcraft.lithmod.mc.multiblock.linear;

import com.euvcraft.lithmod.core.DieTypes;
import com.euvcraft.lithmod.core.FoupContents;
import com.euvcraft.lithmod.core.multiblock.*;
import com.euvcraft.lithmod.core.routing.FabProgress.Stage;
import com.euvcraft.lithmod.core.routing.G3EtchTuning;
import com.euvcraft.lithmod.core.routing.G3StageQuality;
import com.euvcraft.lithmod.core.routing.PolishCalibration;
import com.euvcraft.lithmod.core.t7.G3Preparation;
import com.euvcraft.lithmod.mc.FoupItems;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.g3.*;
import com.euvcraft.lithmod.mc.registry.ModDataComponents;
import com.euvcraft.lithmod.mc.registry.ModItems;
import com.euvcraft.lithmod.mc.routing.FoupRouting;
import com.euvcraft.lithmod.mc.routing.FoupRoutingData;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.Level;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** Paid planarization and implant/activation share the G3 vacuum, rinse and recovery lifecycle. */
public final class T8StageBlockEntity extends G3ControllerBlockEntity {
    private @Nullable BlockPos headPosition;
    private boolean specialtyHead;
    private boolean acceptedDose;
    private boolean frozenSpecialtyHead;
    private int doseOffset;
    private int doseTrim;
    private int frozenQuality = 1000;
    private @Nullable UUID polishHead;
    private int targetPressure;
    private int targetSpeed;
    private int pressure = PolishCalibration.INITIAL_SETTING;
    private int speed = PolishCalibration.INITIAL_SETTING;
    private boolean testedPolish;
    private int acceptedPolish;
    private final SimpleContainer polishSample = new SimpleContainer(1) {
        @Override public void setChanged() {
            super.setChanged();
            T8StageBlockEntity.this.setChanged();
        }
    };

    public T8StageBlockEntity(BlockPos pos, BlockState state, boolean planarizer) {
        super(planarizer ? LinearMachines.PLANARIZER_BE.get() : LinearMachines.IMPLANTER_BE.get(),
                pos, state, G3MachineKind.DEPOSITION_ETCHER, 3, 1);
    }

    public boolean planarizer() {
        return getBlockState().is(LinearMachines.PLANARIZER.get());
    }

    @Override protected String machineId() { return planarizer() ? "t8_planarizer" : "t8_implanter"; }
    @Override protected int expectedFootprint() { return planarizer() ? 45 : 105; }

    @Override
    protected AbstractContainerMenu createMachineMenu(int id, Inventory inventory, FabMachineBlockEntity ignored) {
        return planarizer() ? new PlanarizerCabinetMenu(id, inventory, this) : new G3DepositionEtcherMenu(id, inventory, this);
    }

    @Override
    public MatchResult matchStructure(MultiblockPos anchor, MultiblockDirection facing,
            Function<MultiblockPos, BlockKind> lookup) {
        return G3StructureMatcher.matchT8Stage(anchor, facing, planarizer(), lookup);
    }

    private Block specializedHead() {
        return planarizer() ? G3Machines.MEMORY_UNIFORMITY_HEAD.get() : G3Machines.COMPUTE_DOSE_HEAD.get();
    }

    public boolean acceptsHead(Block block) { return block == specializedHead(); }

    @Override
    protected boolean observeModules(List<BlockPos> members) {
        var world = getLevel();
        if (!(world instanceof ServerLevel)) return false;
        BlockPos next = null;
        boolean specialized = false;
        for (var pos : members) {
            var block = world.getBlockState(pos).getBlock();
            if (block != G3Machines.DEPOSITION_MODULE.get() && block != specializedHead()) continue;
            if (next != null) return false;
            next = pos;
            specialized = block == specializedHead();
        }
        if (next == null) return false;
        if (planarizer() && world.getBlockEntity(next) instanceof G3MemberBlockEntity head) {
            UUID installation = head.polishInstallation();
            if (!installation.equals(polishHead)) resetPolish(installation);
        }
        if (!Objects.equals(next, headPosition) || specialized != specialtyHead) {
            headPosition = next;
            specialtyHead = specialized;
            doseOffset = ThreadLocalRandom.current().nextInt(3, 9) * (specialized ? 1 : -1);
            doseTrim = 0;
            acceptedDose = false;
            setChanged();
        }
        return true;
    }

    @Override
    protected void checkInstalledModule() {
        var world = getLevel();
        if (headPosition == null || world == null || !world.hasChunkAt(headPosition)) return;
        if (!world.getBlockState(headPosition).is(specialtyHead ? specializedHead() : G3Machines.DEPOSITION_MODULE.get())) {
            headPosition = null;
            acceptedDose = false;
            if (planarizer()) resetPolish(null);
            setChanged();
        }
    }

    private void resetPolish(@Nullable UUID installation) {
        polishHead = installation;
        targetPressure = ThreadLocalRandom.current().nextInt(6, 15);
        targetSpeed = ThreadLocalRandom.current().nextInt(6, 15);
        pressure = speed = PolishCalibration.INITIAL_SETTING;
        testedPolish = false;
        acceptedPolish = 0;
        setChanged();
    }

    public SimpleContainer polishSample() { return polishSample; }

    public boolean acceptsPolishSample(ItemStack stack) {
        return planarizer() && validPrepCarrier(stack);
    }

    private int polishMinimum() {
        return Math.clamp(p("polish_min_permille", 500), 1, 1000);
    }

    private PolishCalibration.Measurement polishMeasurement() {
        int removalTolerance = Math.clamp(p("polish_removal_tolerance", 16), 1, 64);
        int edgeTolerance = Math.clamp(p(specialtyHead ? "polish_memory_edge_tolerance" : "polish_edge_tolerance",
                specialtyHead ? 8 : 16), 1, 64);
        return PolishCalibration.measure(pressure, speed, targetPressure, targetSpeed, removalTolerance, edgeTolerance);
    }

    public boolean polishCommand(int command) {
        if (!(getLevel() instanceof ServerLevel) || !planarizer() || hasJob()) return false;
        revalidate();
        if (!isFormed() || polishHead == null || !contaminationState().isOperational()) return false;
        switch (command) {
            case PlanarizerCabinetMenu.PRESSURE_DOWN, PlanarizerCabinetMenu.PRESSURE_UP,
                    PlanarizerCabinetMenu.SPEED_DOWN, PlanarizerCabinetMenu.SPEED_UP -> {
                boolean adjustsPressure = command <= PlanarizerCabinetMenu.PRESSURE_UP;
                int current = adjustsPressure ? pressure : speed;
                int direction = command == PlanarizerCabinetMenu.PRESSURE_DOWN
                        || command == PlanarizerCabinetMenu.SPEED_DOWN ? -1 : 1;
                int changed = Math.clamp(current + direction, 0, PolishCalibration.MAX_SETTING);
                if (changed == current) return false;
                if (adjustsPressure) pressure = changed;
                else speed = changed;
                testedPolish = false;
                acceptedPolish = 0;
            }
            case PlanarizerCabinetMenu.TEST_POLISH -> {
                var sample = polishSample.getItem(0);
                if (!hasPoweredRoom() || !acceptsPolishSample(sample)) return false;
                var remaining = FoupItems.trustedContentsOf(sample).withoutWaferAt(0);
                var returned = sample.copy();
                returned.set(ModDataComponents.FOUP_CONTENTS.get(), remaining);
                FoupRouting.refresh(returned);
                int cost = fe(p("polish_test_fe", 1500));
                if (buffer().getAmountAsLong() < cost || energySource().draw(cost) != cost) return false;
                polishSample.setItem(0, returned);
                testedPolish = true;
            }
            case PlanarizerCabinetMenu.ACCEPT_POLISH -> {
                if (!testedPolish) return false;
                int quality = polishMeasurement().quality();
                if (quality < polishMinimum()) return false;
                acceptedPolish = quality;
            }
            default -> { return false; }
        }
        setChanged();
        return true;
    }

    @Override
    protected boolean validPrepCarrier(ItemStack stack) {
        if (stack.isEmpty() || stack.getCount() != 1 || !FoupItems.isFoup(stack) || !FoupRouting.matchesPrivateLot(stack)) return false;
        var lot = FoupItems.trustedContentsOf(stack);
        if (lot.isEmpty() || !lot.isG3Prepared() || lot.isContaminated()) return false;
        var route = stack.get(ModDataComponents.FOUP_ROUTING.get());
        if (planarizer()) return route.progress().stage() == Stage.DEPOSITED && lot.run() == null;
        var coating = stack.get(ModDataComponents.COATING_RECEIPT.get());
        return route.progress().stage() == Stage.ETCHED && route.progress().tier() == 8
                && coating != null && coating.tier() == 8 && lot.lotLayer() == coating.layers();
    }

    @Override
    public boolean acceptsPrepReagent(int index, ItemStack stack) {
        return index == 1 && stack.is(planarizer() ? ModItems.CMP_SLURRY.get() : ModItems.DOPANT_CHARGE.get())
                || index == 2 && planarizer() && stack.is(ModItems.POLISH_PAD.get());
    }

    private int reagentCount() { return Math.clamp(p("reagent_per_lot", 1), 1, 64); }
    private int padCount() { return Math.clamp(p("pads_per_lot", 1), 1, 64); }

    @Override
    protected boolean prepReagentsAvailable() {
        return acceptsPrepReagent(1, slot(1)) && slot(1).getCount() >= reagentCount()
                && (!planarizer() || acceptsPrepReagent(2, slot(2)) && slot(2).getCount() >= padCount());
    }

    @Override
    protected void reservePrepReagents() {
        consumePrepReagent(1, reagentCount());
        if (planarizer()) consumePrepReagent(2, padCount());
    }

    private int tolerance() {
        return Math.clamp(p(specialtyHead ? "precision_dose_tolerance" : "dose_tolerance", specialtyHead ? 8 : 14), 1, 32);
    }

    private int doseQuality() { return G3EtchTuning.quality(doseOffset + doseTrim, tolerance()); }
    private int minimumQuality() { return Math.clamp(p("dose_min_permille", 500), 1, 1000); }

    public boolean tuneDose(int command) {
        revalidate();
        if (!(getLevel() instanceof ServerLevel) || !isFormed() || hasJob() || planarizer()) return false;
        if (command == 0) {
            if (doseQuality() < minimumQuality()) return false;
            acceptedDose = true;
        } else {
            int next = Math.clamp(doseTrim + command, -G3EtchTuning.TRIM_LIMIT, G3EtchTuning.TRIM_LIMIT);
            if (next == doseTrim) return false;
            doseTrim = next;
            acceptedDose = false;
        }
        setChanged();
        return true;
    }

    @Override
    protected Status prepSetupStatus(FoupContents lot) {
        if (planarizer()) return acceptedPolish >= polishMinimum() ? Status.IDLE : Status.ETCH_CALIBRATION_REQUIRED;
        if (DieTypes.COMPUTE.equals(lot.run().dieType()) != specialtyHead) return Status.WRONG_ETCH_MODULE;
        return acceptedDose && doseQuality() >= minimumQuality() ? Status.IDLE : Status.ETCH_CALIBRATION_REQUIRED;
    }

    @Override
    protected int freezeEtchSetup(FoupContents lot, int baseDrawFloor) {
        frozenSpecialtyHead = specialtyHead;
        int minimum = Math.clamp(p(planarizer() ? "polish_yield_floor_permille" : "dose_yield_floor_permille", 600), 1, 1000);
        int quality = planarizer() ? acceptedPolish : doseQuality();
        frozenQuality = minimum + (1000 - minimum) * quality / 1000;
        return planarizer() && specialtyHead ? Math.max(baseDrawFloor, Math.clamp(p("memory_uniformity_floor_permille", 960), 1, 1000)) : baseDrawFloor;
    }

    @Override protected boolean activeEtchSetupMatches() { return specialtyHead == frozenSpecialtyHead; }

    @Override
    protected void completePreparation(ItemStack carrier, FoupContents lot, int mean, int[] draws) {
        for (int i = 0; i < draws.length; i++) draws[i] = Math.max(1, (draws[i] * frozenQuality + 500) / 1000);
        var preparation = G3StageQuality.combine(lot.g3Preparation(), G3Preparation.commit(mean, draws));
        carrier.set(ModDataComponents.FOUP_CONTENTS.get(), new FoupContents(lot.lotId(), lot.capacity(),
                lot.wafers(), lot.run(), lot.grade(), lot.preparationGeneration(), lot.roomExposure(),
                lot.roomYieldPermille(), preparation));
        var route = carrier.get(ModDataComponents.FOUP_ROUTING.get());
        var updated = planarizer()
                ? new FoupRoutingData(route.progress().planarized(), frozenSpecialtyHead ? DieTypes.MEMORY : "", 0, false)
                : new FoupRoutingData(route.progress().implanted(), route.dieType(), route.cores(), false);
        carrier.set(ModDataComponents.FOUP_ROUTING.get(), updated);
    }

    @Override
    public int publicData(int index) {
        return switch (index) {
            case 19 -> planarizer() ? 0 : 2;
            case 20 -> doseOffset + doseTrim;
            case 21 -> doseQuality();
            case 22 -> acceptedDose && doseQuality() >= minimumQuality() ? 1 : 0;
            case 23 -> minimumQuality();
            case 25 -> planarizer() ? (specialtyHead ? 2 : 1) : (specialtyHead ? 4 : 3);
            case 26 -> pressure;
            case 27 -> speed;
            case 28 -> testedPolish ? polishMeasurement().removalError() : 0;
            case 29 -> testedPolish ? polishMeasurement().edgeError() : 0;
            case 30 -> testedPolish ? polishMeasurement().quality() : -1;
            case 31 -> acceptedPolish;
            case 32 -> polishMinimum();
            default -> super.publicData(index);
        };
    }

    @Override
    protected void dropExtraContents(@Nullable Level world, BlockPos pos) {
        super.dropExtraContents(world, pos);
        dropContainer(world, pos, polishSample);
        polishSample.clearContent();
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        if (headPosition != null) out.store("t8Head", BlockPos.CODEC, headPosition);
        out.putBoolean("t8Specialty", specialtyHead);
        out.putBoolean("t8DoseAccepted", acceptedDose);
        out.putInt("t8DoseOffset", doseOffset);
        out.putInt("t8DoseTrim", doseTrim);
        out.putBoolean("t8FrozenHead", frozenSpecialtyHead);
        out.putInt("t8FrozenQuality", frozenQuality);
        if (planarizer()) {
            if (polishHead != null) out.store("polishHead", UUIDUtil.CODEC, polishHead);
            out.store("polishSample", ItemStack.OPTIONAL_CODEC, polishSample.getItem(0));
            out.putInt("polishTargetPressure", targetPressure);
            out.putInt("polishTargetSpeed", targetSpeed);
            out.putInt("polishPressure", pressure);
            out.putInt("polishSpeed", speed);
            out.putBoolean("polishTested", testedPolish);
            out.putInt("polishAccepted", acceptedPolish);
        }
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        headPosition = in.read("t8Head", BlockPos.CODEC).orElse(null);
        specialtyHead = in.getBooleanOr("t8Specialty", false);
        acceptedDose = in.getBooleanOr("t8DoseAccepted", false);
        doseOffset = Math.clamp(in.getIntOr("t8DoseOffset", 0), -8, 8);
        doseTrim = Math.clamp(in.getIntOr("t8DoseTrim", 0), -G3EtchTuning.TRIM_LIMIT, G3EtchTuning.TRIM_LIMIT);
        frozenSpecialtyHead = in.getBooleanOr("t8FrozenHead", false);
        frozenQuality = Math.clamp(in.getIntOr("t8FrozenQuality", 1000), 1, 1000);
        if (planarizer()) {
            polishHead = in.read("polishHead", UUIDUtil.CODEC).orElse(null);
            polishSample.setItem(0, in.read("polishSample", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
            targetPressure = Math.clamp(in.getIntOr("polishTargetPressure", 10), 6, 14);
            targetSpeed = Math.clamp(in.getIntOr("polishTargetSpeed", 10), 6, 14);
            pressure = Math.clamp(in.getIntOr("polishPressure", 10), 0, PolishCalibration.MAX_SETTING);
            speed = Math.clamp(in.getIntOr("polishSpeed", 10), 0, PolishCalibration.MAX_SETTING);
            testedPolish = polishHead != null && in.getBooleanOr("polishTested", false);
            acceptedPolish = testedPolish ? Math.clamp(in.getIntOr("polishAccepted", 0), 0, 1000) : 0;
        }
    }
}
