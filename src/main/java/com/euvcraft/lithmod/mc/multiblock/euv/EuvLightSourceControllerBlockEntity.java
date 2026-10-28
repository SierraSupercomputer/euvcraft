package com.euvcraft.lithmod.mc.multiblock.euv;

import com.euvcraft.lithmod.core.*;
import com.euvcraft.lithmod.core.multiblock.*;
import com.euvcraft.lithmod.core.t7.*;
import com.euvcraft.lithmod.mc.*;
import com.euvcraft.lithmod.mc.data.*;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.SimpleContainer;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

/**
 * EUV Light Source controller block entity.
 * Consumes tin (via operating slot) and fab-grade H2, maintains local vacuum baseline.
 * Output reserved synchronously to scanner only for paid exposure.
 * One source per scanner, short straight enclosed beamline, configurable max 16 default.
 */
public class EuvLightSourceControllerBlockEntity extends FabMachineBlockEntity implements EuvFormationOwner {

    // Slots: 0 = tin input, 1 = reserve output (internal), 2 = gas buffer (internal)
    public static final int SLOT_TIN_INPUT = 0;
    public static final int SLOT_RESERVE_OUTPUT = 1;
    public static final int SLOT_GAS_BUFFER = 2;
    public static final int SLOT_COUNT = 3;

    // Formation
    private final EuvLightSourceFormation formation = new EuvLightSourceFormation(this);
    private boolean formed;

    // Resources
    public final WaterTank hydrogenTank = new WaterTank(8000); // H2 stored as "water" equivalent
    private VacuumChamberState vacuumState = VacuumChamberState.evacuated();
    private final VacuumPumpSpec vacuumSpec;

    // Tin storage (in mB equivalent, 1 tin item = 100 mB)
    private int tinStored = 0;
    private static final int MAX_TIN = 50000;

    // Reservation
    private @Nullable UUID reservedScannerId;
    private int reservedExposureTicks;
    private static final int MAX_BEAMLINE_LENGTH = 16;

    // Production
    private int euVOutputStored = 0; // EUV energy units stored for scanner
    private static final int MAX_EUV_OUTPUT = 10000;
    private static final int EUV_PER_EXPOSURE = 100;

    // Tin tag for item detection
    private static final TagKey<Item> TIN_TAG = TagKey.create(Registries.ITEM, ResourceLocation.parse("c:ingots/tin"));

    public EuvLightSourceControllerBlockEntity(BlockPos pos, BlockState state) {
        super(EuvMachines.LIGHT_SOURCE_CONTROLLER_BE.get(), pos, state, SLOT_COUNT, 0);
        this.vacuumSpec = createVacuumSpec();
    }

    private VacuumPumpSpec createVacuumSpec() {
        return new VacuumPumpSpec(
                2000,  // pumpRateMicroTorrPerTick
                50,    // idleLeakMicroTorrPerTick
                100,   // processLoadMicroTorrPerTick
                500,   // operatingPressureMicroTorr
                20000, // reserveCapacityMicroTorr
                500    // reserveRechargeMicroTorrPerTick
        );
    }

    @Override
    protected String machineId() {
        return "euv_light_source";
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.lithmod.euv_light_source_controller");
    }

    @Override
    public FabMachineBlockEntity machine() {
        return this;
    }

    @Override
    public EuvLightSourceFormation formation() {
        return formation;
    }

    @Override
    public boolean isFormed() {
        return formed;
    }

    @Override
    public List<BlockPos> members() {
        return formation.members();
    }

    @Override
    public String failureReason() {
        return formation.failureReason();
    }

    @Override
    public MatchResult matchStructure(MultiblockPos anchor, MultiblockDirection facing,
            Function<MultiblockPos, BlockKind> lookup) {
        return LightSourceStructureMatcher.match(anchor, facing, lookup);
    }

    @Override
    public void revalidate() {
        formation.validate();
    }

    @Override
    public void setRemoved() {
        formation.unload();
        super.setRemoved();
    }

    @Override
    public void onFormed() {
        this.formed = true;
    }

    @Override
    public void onUnformed() {
        this.formed = false;
        reservedScannerId = null;
        reservedExposureTicks = 0;
    }

    public boolean canReserveFor(UUID scannerId, int exposureTicks) {
        return reservedScannerId == null
                && isFormed()
                && hydrogenTank.amount() >= exposureTicks * 10
                && tinStored >= exposureTicks * 5
                && vacuumState.canProcess(vacuumSpec)
                && euVOutputStored == 0;
    }

    public boolean reserveForScanner(UUID scannerId, int exposureTicks) {
        if (!canReserveFor(scannerId, exposureTicks)) return false;
        reservedScannerId = scannerId;
        reservedExposureTicks = exposureTicks;
        hydrogenTank.drainExact(exposureTicks * 10, false);
        tinStored -= exposureTicks * 5;
        setChanged();
        return true;
    }

    public void releaseReservation() {
        reservedScannerId = null;
        reservedExposureTicks = 0;
        setChanged();
    }

    public boolean consumeForExposure() {
        if (reservedScannerId == null || reservedExposureTicks <= 0) return false;
        int produced = Math.min(EUV_PER_EXPOSURE, MAX_EUV_OUTPUT - euVOutputStored);
        euVOutputStored += produced;
        reservedExposureTicks--;
        if (reservedExposureTicks == 0) {
            reservedScannerId = null;
        }
        setChanged();
        return produced > 0;
    }

    public int extractEuvOutput(int amount, boolean simulate) {
        if (amount <= 0 || euVOutputStored == 0) return 0;
        int extracted = Math.min(amount, euVOutputStored);
        if (!simulate) {
            euVOutputStored -= extracted;
            setChanged();
        }
        return extracted;
    }

    public int tinStored() {
        return tinStored;
    }

    public int insertTin(int amount, boolean simulate) {
        if (amount <= 0) return 0;
        int space = MAX_TIN - tinStored;
        if (space <= 0) return 0;
        int accepted = Math.min(amount, space);
        if (!simulate) {
            tinStored += accepted;
            setChanged();
        }
        return accepted;
    }

    public VacuumChamberState vacuumState() {
        return vacuumState;
    }

    public VacuumPumpSpec vacuumSpec() {
        return vacuumSpec;
    }

    public VacuumTickResult tickVacuum(int centralService, boolean localPumpPowered) {
        VacuumTickResult result = vacuumState.tick(vacuumSpec, VacuumMode.IDLE, centralService, localPumpPowered);
        this.vacuumState = result.state();
        return result;
    }

    public int vacuumCentralDemand() {
        return vacuumState.centralDemandMicroTorrPerTick(vacuumSpec, VacuumMode.IDLE);
    }

    public EnergyHandler energyHandler() {
        return new EnergyHandler() {
            @Override
            public long getAmountAsLong() { return buffer().getAmountAsLong(); }
            @Override
            public long getCapacityAsLong() { return buffer().getCapacityAsLong(); }
            @Override
            public int insert(int amount, TransactionContext transaction) { return buffer().insert(amount, transaction); }
            @Override
            public int extract(int amount, TransactionContext transaction) { return buffer().extract(amount, transaction); }
        };
    }

    @Override
    public void serverTick() {
        super.serverTick();
        if (!(level instanceof ServerLevel)) return;

        // Process tin input from slot 0
        ItemStack tinStack = slot(SLOT_TIN_INPUT);
        if (!tinStack.isEmpty() && tinStack.is(TIN_TAG)) {
            int inserted = insertTin(tinStack.getCount() * 100, true);
            if (inserted > 0) {
                insertTin(inserted, false);
                tinStack.shrink(inserted / 100);
                if (tinStack.isEmpty()) clearSlot(SLOT_TIN_INPUT);
            }
        }

        // Tick vacuum
        int centralService = 0;
        boolean localPumpPowered = buffer().getAmountAsLong() > 0;
        tickVacuum(centralService, localPumpPowered);

        // Auto-produce EUV if reserved and has resources
        if (reservedScannerId != null && reservedExposureTicks > 0) {
            if (hydrogenTank.amount() >= 10 && tinStored >= 5) {
                consumeForExposure();
            }
        }

        publishProgress();
    }

    private void publishProgress() {
        data().set(DATA_ENERGY, buffer().getAmountAsInt());
        data().set(DATA_CAPACITY, buffer().getCapacityAsInt());
        data().set(100, hydrogenTank.amount());
        data().set(101, tinStored);
        data().set(102, vacuumState.pressureMicroTorr() / 1000);
        data().set(103, euVOutputStored);
        data().set(104, reservedScannerId != null ? 1 : 0);
        data().set(105, reservedExposureTicks);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        formed = input.getIntOr("lsFormed", 0) != 0;
        hydrogenTank.restoreAmount(Math.clamp(input.getIntOr("lsHydrogen", 0), 0, hydrogenTank.capacity()));
        tinStored = Math.clamp(input.getIntOr("lsTin", 0), 0, MAX_TIN);
        this.vacuumState = new VacuumChamberState(
                input.getIntOr("lsVacPressure", 0),
                input.getIntOr("lsVacReserve", 0));
        reservedScannerId = input.getString("lsReservedScanner").orElse("").isEmpty()
                ? null : UUID.fromString(input.getString("lsReservedScanner").orElse(""));
        reservedExposureTicks = input.getIntOr("lsReservedTicks", 0);
        euVOutputStored = Math.clamp(input.getIntOr("lsEuvOutput", 0), 0, MAX_EUV_OUTPUT);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("lsFormed", formed ? 1 : 0);
        output.putInt("lsHydrogen", hydrogenTank.amount());
        output.putInt("lsTin", tinStored);
        output.putInt("lsVacPressure", vacuumState.pressureMicroTorr());
        output.putInt("lsVacReserve", vacuumState.reserveMicroTorr());
        if (reservedScannerId != null) output.putString("lsReservedScanner", reservedScannerId.toString());
        output.putInt("lsReservedTicks", reservedExposureTicks);
        output.putInt("lsEuvOutput", euVOutputStored);
    }

    @Override
    protected AbstractContainerMenu createMachineMenu(int containerId, Inventory playerInventory, FabMachineBlockEntity machine) {
        return new EuvLightSourceOperatingMenu(containerId, playerInventory, this, worldPosition);
    }
}