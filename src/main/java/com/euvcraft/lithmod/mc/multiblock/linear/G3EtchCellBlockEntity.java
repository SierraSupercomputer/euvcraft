package com.euvcraft.lithmod.mc.multiblock.linear;

import com.euvcraft.lithmod.core.DieTypes;
import com.euvcraft.lithmod.core.FoupContents;
import com.euvcraft.lithmod.core.routing.G3EtchTuning;
import com.euvcraft.lithmod.mc.machine.FabMachineBlockEntity;
import com.euvcraft.lithmod.mc.multiblock.g3.*;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public final class G3EtchCellBlockEntity extends G3ControllerBlockEntity {
    private enum Profile {
        NAND, COMPUTE, MEMORY;

        static @Nullable Profile forLot(FoupContents lot) {
            return switch (lot.run().dieType()) {
                case DieTypes.COMPUTE -> COMPUTE;
                case DieTypes.MEMORY -> MEMORY;
                case DieTypes.NAND -> NAND;
                default -> null;
            };
        }
    }

    private Profile profile = Profile.NAND;
    private @Nullable BlockPos module;
    private int offset;
    private int trim;
    private boolean calibrated;
    private int frozenQuality = 1000;
    private int frozenProfile = -1;

    public G3EtchCellBlockEntity(BlockPos pos, BlockState state) {
        super(LinearMachines.G3_ETCH_BE.get(), pos, state, G3MachineKind.DEPOSITION_ETCHER, 3, 1);
    }

    @Override protected String machineId() { return "g3_etch_cell"; }
    @Override protected boolean etching() { return true; }

    @Override
    protected AbstractContainerMenu createMachineMenu(int id, Inventory inventory, FabMachineBlockEntity ignored) {
        return new G3DepositionEtcherMenu(id, inventory, this);
    }

    /** The specialty block replaces the existing process module in the middle layers. */
    public boolean observeModules(List<BlockPos> members) {
        var world = getLevel();
        if (!(world instanceof ServerLevel)) return false;
        Profile next = Profile.NAND;
        BlockPos nextModule = null;
        boolean legacy = false;
        for (var pos : members) {
            var block = world.getBlockState(pos).getBlock();
            if (block == G3Machines.DEPOSITION_MODULE.get()) legacy = true;
            Profile specialized = block == G3Machines.COMPUTE_ETCH_MODULE.get() ? Profile.COMPUTE
                    : block == G3Machines.MEMORY_ETCH_MODULE.get() ? Profile.MEMORY : Profile.NAND;
            if (specialized == Profile.NAND) continue;
            if (nextModule != null) return false;
            next = specialized;
            nextModule = pos;
        }
        if (legacy && nextModule != null) return false;
        if (next != profile || !Objects.equals(module, nextModule)) resetTuning(next, nextModule);
        return true;
    }

    public void checkInstalledModule() {
        var world = getLevel();
        if (module == null || world == null || !world.hasChunkAt(module)) return;
        var expected = profile == Profile.COMPUTE ? G3Machines.COMPUTE_ETCH_MODULE : G3Machines.MEMORY_ETCH_MODULE;
        if (!world.getBlockState(module).is(expected.get())) resetTuning(Profile.NAND, null);
    }

    private void resetTuning(Profile next, @Nullable BlockPos nextModule) {
        profile = next;
        module = nextModule;
        offset = next == Profile.NAND ? 0 : ThreadLocalRandom.current().nextInt(1, 9)
                * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
        trim = 0;
        calibrated = false;
        setChanged();
    }

    public boolean tune(int command) {
        revalidate();
        if (!(getLevel() instanceof ServerLevel) || !isFormed() || hasJob() || profile == Profile.NAND) return false;
        if (command == 0) {
            if (tuningQuality() < minimumQuality()) return false;
            calibrated = true;
        } else {
            int next = Math.clamp(trim + command, -G3EtchTuning.TRIM_LIMIT, G3EtchTuning.TRIM_LIMIT);
            if (next == trim) return false;
            trim = next;
            calibrated = false;
        }
        setChanged();
        return true;
    }

    private int tolerance(int cores) {
        return profile == Profile.COMPUTE
                ? G3EtchTuning.computeTolerance(Math.clamp(p("compute_depth_tolerance", 12), 1, 32),
                        cores, p("compute_cores_per_tolerance_step", 3))
                : Math.clamp(p("memory_uniformity_tolerance", 14), 1, 32);
    }

    private int tuningQuality() {
        return G3EtchTuning.quality(offset + trim, tolerance(1));
    }

    private int minimumQuality() {
        return Math.clamp(p("etch_calibration_min_permille", 500), 1, 1000);
    }

    @Override
    protected Status prepSetupStatus(FoupContents lot) {
        if (Profile.forLot(lot) != profile) return Status.WRONG_ETCH_MODULE;
        if (profile != Profile.NAND && (!calibrated || tuningQuality() < minimumQuality())) {
            return Status.ETCH_CALIBRATION_REQUIRED;
        }
        return Status.IDLE;
    }

    @Override
    protected int freezeEtchSetup(FoupContents lot, int baseDrawFloor) {
        frozenProfile = profile.ordinal();
        frozenQuality = 1000;
        if (profile == Profile.COMPUTE) {
            int quality = G3EtchTuning.quality(offset + trim, tolerance(lot.run().cores()));
            int floor = Math.clamp(p("compute_depth_yield_floor_permille", 550), 1, 1000);
            frozenQuality = floor + (1000 - floor) * quality / 1000;
        } else if (profile == Profile.MEMORY) {
            int spread = Math.clamp(p("memory_max_uniformity_spread_permille", 250), 0, 999);
            return Math.max(1, baseDrawFloor - spread * (1000 - tuningQuality()) / 1000);
        }
        return baseDrawFloor;
    }

    @Override protected int etchQualityPermille() { return frozenQuality; }

    @Override
    protected boolean activeEtchSetupMatches() {
        return frozenProfile == -1 || frozenProfile == profile.ordinal();
    }

    @Override
    public int publicData(int index) {
        return switch (index) {
            case 19 -> profile.ordinal() + 1;
            case 20 -> offset + trim;
            case 21 -> tuningQuality();
            case 22 -> calibrated && tuningQuality() >= minimumQuality() ? 1 : 0;
            case 23 -> minimumQuality();
            default -> super.publicData(index);
        };
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("etchProfile", profile.ordinal());
        if (module != null) out.store("etchModule", BlockPos.CODEC, module);
        out.putInt("etchOffset", offset);
        out.putInt("etchTrim", trim);
        out.putBoolean("etchCalibrated", calibrated);
        out.putInt("etchFrozenQuality", frozenQuality);
        out.putInt("etchFrozenProfile", frozenProfile);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        profile = Profile.values()[Math.clamp(in.getIntOr("etchProfile", 0), 0, Profile.values().length - 1)];
        module = in.read("etchModule", BlockPos.CODEC).orElse(null);
        offset = Math.clamp(in.getIntOr("etchOffset", 0), -8, 8);
        trim = Math.clamp(in.getIntOr("etchTrim", 0), -G3EtchTuning.TRIM_LIMIT, G3EtchTuning.TRIM_LIMIT);
        calibrated = in.getBooleanOr("etchCalibrated", false);
        frozenQuality = Math.clamp(in.getIntOr("etchFrozenQuality", 1000), 1, 1000);
        frozenProfile = Math.clamp(in.getIntOr("etchFrozenProfile", -1), -1, Profile.values().length - 1);
    }
}
