package com.euvcraft.lithmod.mc.multiblock.reflow;

import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import org.jspecify.annotations.Nullable;

/**
 * The small status block shared by the controller and operating-panel menus. Both sides get
 * the same number of entries; the server reads the live oven, the client receives the synced
 * values from the vanilla data-slot stream.
 */
final class ReflowStatusData implements ContainerData {

    static final int FORMED = 0;
    static final int CALIBRATED = 1;
    static final int READY = 2;
    static final int LOCKED = 3;
    static final int PAUSED = 4;
    static final int REASON = 5;
    static final int ACCEPTED_PERMILLE = 6;
    static final int ACCEPTED_QUALITY = 7;
    static final int MIN_QUALITY = 8;
    static final int SCRAP_PER_FAILURE = 9;
    static final int THERMAL_VALID = 10;
    static final int COUPON_READY = 11;
    static final int TEST_FE = 12;
    static final int COUNT = 13;

    private final @Nullable ReflowControllerBlockEntity oven;

    private ReflowStatusData(@Nullable ReflowControllerBlockEntity oven) {
        this.oven = oven;
    }

    static ContainerData of(@Nullable ReflowControllerBlockEntity oven) {
        return oven == null ? new SimpleContainerData(COUNT) : new ReflowStatusData(oven);
    }

    @Override
    public int get(int index) {
        if (oven == null) {
            return 0;
        }
        return switch (index) {
            case FORMED -> oven.isFormed() ? 1 : 0;
            case CALIBRATED -> oven.calibrationAccepted() ? 1 : 0;
            case READY -> oven.readyForBatch() ? 1 : 0;
            case LOCKED -> oven.inputsLocked() ? 1 : 0;
            case PAUSED -> oven.paused() ? 1 : 0;
            case REASON -> oven.pauseReason().ordinal();
            case ACCEPTED_PERMILLE -> oven.acceptedPermille();
            case ACCEPTED_QUALITY -> oven.acceptedQuality();
            case MIN_QUALITY -> oven.minimumQuality();
            case SCRAP_PER_FAILURE -> oven.definition()
                    .map(definition -> definition.paramOrDefault("scrap_per_failed_assembly", 1))
                    .orElse(1);
            case THERMAL_VALID -> oven.hasThermalParameters() ? 1 : 0;
            case COUPON_READY -> oven.couponReady() ? 1 : 0;
            case TEST_FE -> oven.testFe();
            default -> 0;
        };
    }

    @Override
    public void set(int index, int value) {
        // Server-authored values only.
    }

    @Override
    public int getCount() {
        return COUNT;
    }
}
