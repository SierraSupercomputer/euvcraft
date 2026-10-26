package com.euvcraft.lithmod.mc.multiblock.reflow;

import com.euvcraft.lithmod.mc.machine.screen.AdvancedMachineScreen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Upper operating panel: real chip/blank input and product/scrap output. */
public final class ReflowOperatingScreen extends AdvancedMachineScreen<ReflowOperatingMenu> {

    public ReflowOperatingScreen(ReflowOperatingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 238);
    }

    @Override
    protected boolean formed() {
        return menu.formed();
    }

    @Override
    protected boolean cabinet() {
        return false;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        line(graphics, title, 8, 6, 160);
        line(graphics, playerInventoryTitle, 8, 72, 160);
        line(graphics, Component.literal(menu.energy() + " / " + menu.energyCapacity() + " FE"), 30, 20, 138);
        line(graphics, Component.literal("Chip     Blank    Product   Scrap"), 26, 20, 150);
        line(graphics, Component.literal(formed() ? "Formed" : "Unformed: build 3x3x2"), 8, 168, 160);
        if (menu.calibrated()) {
            line(graphics, Component.literal("Calibration Q" + menu.acceptedQuality()
                    + " x" + menu.acceptedPermille() / 1000.0), 8, 180, 160);
        } else {
            line(graphics, Component.literal("Uncalibrated: use the thermal panel"), 8, 180, 160);
        }
        if (menu.inputsLocked()) {
            line(graphics, Component.literal("Assembly " + menu.progress() + "/" + menu.ticks()
                    + (menu.paused() ? " paused" : "")), 8, 192, 160);
        } else {
            line(graphics, Component.literal("Idle: slot 1 chip, slot 2 blank"), 8, 192, 160);
        }
        line(graphics, Component.literal(reasonText(menu.reason())), 8, 204, 160);
        line(graphics, Component.literal("Failed assembly yields " + menu.scrapPerFailure()
                + "x assembly_scrap"), 8, 216, 160);
    }

    private static String reasonText(int reason) {
        ReflowControllerBlockEntity.PauseReason[] values = ReflowControllerBlockEntity.PauseReason.values();
        if (reason < 0 || reason >= values.length) {
            return "";
        }
        return switch (values[reason]) {
            case NONE -> "";
            case UNFORMED -> "Paused: structure incomplete";
            case NO_DEFINITION -> "Paused: reflow_oven definition missing";
            case NOT_CALIBRATED -> "Paused: accept a thermal calibration";
            case CALIBRATION_INVALIDATED -> "Paused: contamination latched, recalibrate";
            case THERMAL_DATA_MISSING -> "Thermal parameters missing or invalid";
            case NO_RECIPE -> "No matching T6 chip + blank recipe";
            case ROOM_UNAVAILABLE -> "Paused: cleanroom chunk unavailable";
            case NO_OUTPUT_ROOM -> "Paused: output slots full";
            case NO_ENERGY -> "Paused: not enough FE";
        };
    }
}
