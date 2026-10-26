package com.euvcraft.lithmod.mc.multiblock.reflow;

import com.euvcraft.lithmod.core.multiblock.BlockKind;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Thermal calibration console: one coupon slot, the three control values, the live nominal
 * preview curve and, only after a server-paid test, the measured curve.
 *
 * <p>The player inventory is part of the same opaque 320x238 window, below the graph, exactly
 * like the scanner diagnostic console. Only public graph samples cross the menu; the response
 * seed, the random roll of an admitted assembly and every private job payload stay on the
 * server.</p>
 */
public final class ReflowCalibrationMenu extends AbstractContainerMenu {

    public static final int CAL_FORMED = 0;
    public static final int CAL_ACCEPTED = 1;
    public static final int CAL_READY = 2;
    public static final int CAL_LOCKED = 3;
    public static final int CAL_PAUSED = 4;
    public static final int CAL_REASON = 5;
    public static final int CAL_HEATER = 6;
    public static final int CAL_CONVEYOR = 7;
    public static final int CAL_AIRFLOW = 8;
    public static final int CAL_TESTED = 9;
    public static final int CAL_MEASURED_QUALITY = 10;
    public static final int CAL_MIN_QUALITY = 11;
    public static final int CAL_ACCEPTED_PERMILLE = 12;
    public static final int CAL_ACCEPTED_QUALITY = 13;
    public static final int CAL_TEST_FE = 14;
    public static final int CAL_COUPON_OK = 15;
    public static final int CAL_ENERGY = 16;
    public static final int CAL_CAPACITY = 17;
    public static final int CAL_ACCEPT_READY = 18;
    public static final int CAL_THERMAL_VALID = 19;
    public static final int CAL_PROBLEMS = 20;
    public static final int CAL_MIN_PEAK = 21;
    public static final int CAL_MAX_PEAK = 22;
    public static final int CAL_MIN_PERMILLE = 23;
    public static final int CAL_MAX_PERMILLE = 24;
    public static final int CAL_COUPON_COUNT = 25;
    public static final int CAL_MEASURED_HEATER = 26;
    public static final int CAL_MEASURED_CONVEYOR = 27;
    public static final int CAL_MEASURED_AIRFLOW = 28;
    /**
     * Server-synced rule bounds, in hundredths of their JSON unit: ramp/dwell/cooling bounds
     * are the datapack hundredths values, peak bounds are centi-degrees. The measured metrics
     * use the same scale so the screen readouts always match the JSON rule bands, without any
     * client-side definition read.
     */
    public static final int CAL_MIN_RAMP = 29;
    public static final int CAL_MAX_RAMP = 30;
    public static final int CAL_MIN_DWELL = 31;
    public static final int CAL_MAX_DWELL = 32;
    public static final int CAL_MIN_COOLING = 33;
    public static final int CAL_MAX_COOLING = 34;
    public static final int CAL_MEASURED_RAMP = 35;
    public static final int CAL_MEASURED_PEAK = 36;
    public static final int CAL_MEASURED_DWELL = 37;
    public static final int CAL_MEASURED_COOLING = 38;
    public static final int CAL_PREVIEW_START = 39;
    public static final int CAL_MEASURED_START = CAL_PREVIEW_START + 49;
    public static final int CAL_COUNT = CAL_MEASURED_START + 49;

    /** Menu click ids. Six +/- buttons, then test and accept. */
    public static final int BUTTON_HEATER_MINUS = 0;
    public static final int BUTTON_HEATER_PLUS = 1;
    public static final int BUTTON_CONVEYOR_MINUS = 2;
    public static final int BUTTON_CONVEYOR_PLUS = 3;
    public static final int BUTTON_AIRFLOW_MINUS = 4;
    public static final int BUTTON_AIRFLOW_PLUS = 5;
    public static final int BUTTON_TEST = 6;
    public static final int BUTTON_ACCEPT = 7;

    private final @Nullable ReflowControllerBlockEntity oven;
    private final @Nullable ReflowMemberBlockEntity panel;
    private final BlockPos panelPos;
    private final ContainerData values;

    public ReflowCalibrationMenu(int id, Inventory inventory,
            @Nullable ReflowControllerBlockEntity oven, BlockPos panelPos) {
        super(ReflowMachines.CALIBRATION_MENU.get(), id);
        this.oven = oven;
        this.panelPos = panelPos.immutable();
        this.panel = oven != null && oven.getLevel() != null
                && oven.getLevel().getBlockEntity(panelPos) instanceof ReflowMemberBlockEntity member
                        ? member : null;

        SimpleContainer fallback = new SimpleContainer(ReflowControllerBlockEntity.SLOT_COUNT);
        net.minecraft.world.Container container = oven != null ? oven : fallback;
        addSlot(new Slot(container, ReflowControllerBlockEntity.SLOT_COUPON, 283, 157) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return !lockedNow() && stack.is(ReflowMachines.THERMAL_TEST_COUPON.get());
            }

            @Override
            public boolean mayPickup(Player player) {
                return !lockedNow();
            }

            @Override
            public void setChanged() {
                super.setChanged();
                if (oven != null) {
                    oven.setChanged();
                }
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 79 + column * 18, 190 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 79 + column * 18, 248));
        }

        this.values = oven == null ? new SimpleContainerData(CAL_COUNT) : new ContainerData() {
            @Override
            public int get(int index) {
                return calibrationValue(index);
            }

            @Override
            public void set(int index, int value) {
                // Server-authored values only.
            }

            @Override
            public int getCount() {
                return CAL_COUNT;
            }
        };
        addDataSlots(this.values);
    }

    public ReflowCalibrationMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, null, buffer.readBlockPos());
    }

    private boolean lockedNow() {
        if (oven != null) {
            return oven.inputsLocked();
        }
        return values != null && values.get(CAL_LOCKED) != 0;
    }

    private int calibrationValue(int index) {
        ReflowControllerBlockEntity controller = oven;
        if (controller == null) {
            return 0;
        }
        if (index >= CAL_PREVIEW_START && index < CAL_PREVIEW_START + 49) {
            return curveValue(controller.previewCurve(), index - CAL_PREVIEW_START);
        }
        if (index >= CAL_MEASURED_START && index < CAL_MEASURED_START + 49) {
            double[] measured = controller.measuredCurve();
            return measured == null ? 0 : curveValue(measured, index - CAL_MEASURED_START);
        }
        return switch (index) {
            case CAL_FORMED -> controller.isFormed() ? 1 : 0;
            case CAL_ACCEPTED -> controller.calibrationAccepted() ? 1 : 0;
            case CAL_READY -> controller.readyForBatch() ? 1 : 0;
            case CAL_LOCKED -> controller.inputsLocked() ? 1 : 0;
            case CAL_PAUSED -> controller.paused() ? 1 : 0;
            case CAL_REASON -> controller.pauseReason().ordinal();
            case CAL_HEATER -> controller.controls().heater();
            case CAL_CONVEYOR -> controller.controls().conveyor();
            case CAL_AIRFLOW -> controller.controls().airflow();
            case CAL_TESTED -> controller.measured() ? 1 : 0;
            case CAL_MEASURED_QUALITY -> controller.measuredQuality();
            case CAL_MIN_QUALITY -> controller.minimumQuality();
            case CAL_ACCEPTED_PERMILLE -> controller.acceptedPermille();
            case CAL_ACCEPTED_QUALITY -> controller.acceptedQuality();
            case CAL_TEST_FE -> controller.testFe();
            case CAL_COUPON_OK -> controller.couponReady() ? 1 : 0;
            case CAL_ENERGY -> controller.buffer().getAmountAsInt();
            case CAL_CAPACITY -> controller.buffer().getCapacityAsInt();
            case CAL_ACCEPT_READY -> controller.acceptReady() ? 1 : 0;
            case CAL_THERMAL_VALID -> controller.hasThermalParameters() ? 1 : 0;
            case CAL_PROBLEMS -> controller.measuredProblems();
            case CAL_MIN_PEAK -> controller.minPeakHundredths();
            case CAL_MAX_PEAK -> controller.maxPeakHundredths();
            case CAL_MIN_PERMILLE -> controller.minMultiplierPermille();
            case CAL_MAX_PERMILLE -> controller.maxMultiplierPermille();
            case CAL_COUPON_COUNT -> controller.couponCount();
            case CAL_MEASURED_HEATER -> controller.measuredHeater();
            case CAL_MEASURED_CONVEYOR -> controller.measuredConveyor();
            case CAL_MEASURED_AIRFLOW -> controller.measuredAirflow();
            case CAL_MIN_RAMP -> hundredths(controller, ReflowControllerBlockEntity.ThermalSettings::minRamp);
            case CAL_MAX_RAMP -> hundredths(controller, ReflowControllerBlockEntity.ThermalSettings::maxRamp);
            case CAL_MIN_DWELL -> hundredths(controller, ReflowControllerBlockEntity.ThermalSettings::minDwell);
            case CAL_MAX_DWELL -> hundredths(controller, ReflowControllerBlockEntity.ThermalSettings::maxDwell);
            case CAL_MIN_COOLING -> hundredths(controller, ReflowControllerBlockEntity.ThermalSettings::minCooling);
            case CAL_MAX_COOLING -> hundredths(controller, ReflowControllerBlockEntity.ThermalSettings::maxCooling);
            case CAL_MEASURED_RAMP -> metricHundredths(controller.measuredRamp());
            case CAL_MEASURED_PEAK -> metricHundredths(controller.measuredPeak());
            case CAL_MEASURED_DWELL -> metricHundredths(controller.measuredDwell());
            case CAL_MEASURED_COOLING -> metricHundredths(controller.measuredCooling());
            default -> 0;
        };
    }

    /** Hundredths of one {@code ThermalSettings} band from the datapack rules, 0 when missing. */
    private static int hundredths(ReflowControllerBlockEntity controller,
            java.util.function.ToDoubleFunction<ReflowControllerBlockEntity.ThermalSettings> band) {
        return controller.thermalSettings()
                .map(s -> metricHundredths(band.applyAsDouble(s)))
                .orElse(0);
    }

    private static int metricHundredths(double value) {
        return Double.isFinite(value) ? (int) Math.round(value * 100.0) : 0;
    }

    private static int curveValue(double[] curve, int index) {
        if (curve == null || index < 0 || index >= curve.length || !Double.isFinite(curve[index])) {
            return 0;
        }
        return (int) Math.round(curve[index] * 100.0);
    }

    public int value(int index) {
        return values.get(index);
    }

    public boolean formed() {
        return value(CAL_FORMED) == 1;
    }

    public boolean tested() {
        return value(CAL_TESTED) == 1;
    }

    @Override
    public boolean stillValid(Player player) {
        if (oven == null || panel == null) {
            return false;
        }
        if (oven.getLevel() != player.level() || oven.isRemoved() || oven.getLevel() == null) {
            return false;
        }
        if (oven.getLevel().getBlockEntity(oven.getBlockPos()) != oven
                || oven.getLevel().getBlockEntity(panelPos) != panel) {
            return false;
        }
        return panel.structureKind() == BlockKind.CALIBRATION_PANEL
                && panel.reflowController() == oven
                && oven.isFormed()
                && oven.members().contains(panelPos)
                && player.distanceToSqr(panelPos.getX() + 0.5, panelPos.getY() + 0.5,
                        panelPos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer) || player.containerMenu != this
                || !stillValid(player) || oven == null || oven.inputsLocked()) {
            return false;
        }
        if (id >= BUTTON_HEATER_MINUS && id <= BUTTON_AIRFLOW_PLUS) {
            return oven.adjustThermalControl(id);
        }
        if (id == BUTTON_TEST) {
            return oven.testThermal();
        }
        if (id == BUTTON_ACCEPT) {
            return oven.acceptThermal();
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || oven == null || oven.inputsLocked()
                || !stillValid(player)) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!stack.is(ReflowMachines.THERMAL_TEST_COUPON.get())
                || !moveItemStackTo(stack, 0, 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, original);
        oven.setChanged();
        return original;
    }
}
