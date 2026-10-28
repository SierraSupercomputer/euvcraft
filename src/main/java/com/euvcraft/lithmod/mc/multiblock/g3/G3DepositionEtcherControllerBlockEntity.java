package com.euvcraft.lithmod.mc.multiblock.g3;

import com.euvcraft.lithmod.core.FoupContents;
import com.euvcraft.lithmod.core.FoupDisposal;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * G3 Deposition Etcher controller block entity.
 * Requires formed enclosed cleanroom, fresh unstarted electronic-grade lot.
 * Vacuum dep/etch -> UPW rinse -> powered dry, distinct phase and phase progress visible.
 * Local vacuum baseline functional; explicit services seam for optional central vacuum.
 * One water tank transactional fluid inlet based on ScannerWaterHandler/UtilityFluidHandler.
 * Costs supplied by definition params; frozen at start, phases pause properly and consume actual UPW once.
 * Preparation result integration uses FoupContents.preparedG3 API (domain worker dependency isolated).
 * Cleanroom structural breach scraps pending wafers via FoupDisposal; distinguish routine utility shortage.
 * Persistent contamination/recovery APIs from domain worker later; hooks/report isolated.
 */
public class G3DepositionEtcherControllerBlockEntity extends G3ControllerBlockEntity {

    // Slot indices: 0 = FOUP input, 1 = FOUP output (prepared)
    public static final int SLOT_FOUP_INPUT = 0;
    public static final int SLOT_FOUP_OUTPUT = 1;

    public G3DepositionEtcherControllerBlockEntity(BlockPos pos, BlockState state) {
        super(G3Machines.DEPOSITION_ETCHER_CONTROLLER_BE.get(), pos, state,
                G3MachineKind.DEPOSITION_ETCHER, 1, 1);
    }

    @Override
    protected String machineId() {
        return "g3_deposition_etcher";
    }

    /** Current prep phase for UI display. */
    public PrepPhase currentPhase() {
        return this.prepPhase;
    }

    /** Phase progress (0..duration). */
    public int phaseProgress() {
        return this.phaseProgress;
    }

    /** Phase duration. */
    public int phaseDuration() {
        return this.phaseDuration;
    }

    /** Whether the current phase is paused (power loss, output blocked, vacuum not ready, water shortage). */
    public boolean isPhasePaused() {
        if (!this.formed) return true;
        return switch (this.prepPhase) {
            case VACUUM_DEP_ETCH -> this.vacuumState != null && this.vacuumSpec != null
                    && !this.vacuumState.canProcess(this.vacuumSpec);
            case UPW_RINSE -> this.waterTank != null && this.waterTank.amount() < this.phaseWaterMb;
            case POWERED_DRY, IDLE, COMPLETE -> false;
        } || (this.energySource().getAmountAsLong() < this.phaseEnergyPerTick);
    }

    /** Vacuum chamber pressure (µTorr), or -1 if not applicable. */
    public int vacuumPressure() {
        return this.vacuumState != null ? this.vacuumState.pressureMicroTorr() : -1;
    }

    /** Vacuum operating pressure (µTorr), or -1 if not applicable. */
    public int vacuumOperatingPressure() {
        return this.vacuumSpec != null ? this.vacuumSpec.operatingPressureMicroTorr() : -1;
    }

    /** Water tank amount (mB), or -1 if not applicable. */
    public int waterAmount() {
        return this.waterTank != null ? this.waterTank.amount() : -1;
    }

    /** Water tank capacity (mB), or -1 if not applicable. */
    public int waterCapacity() {
        return this.waterTank != null ? this.waterTank.capacity() : -1;
    }

    /** Contamination phase for UI. */
    public com.euvcraft.lithmod.core.t7.MachineContaminationState.Phase contaminationPhase() {
        return this.contaminationState.phase();
    }
}