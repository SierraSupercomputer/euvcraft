package com.euvcraft.lithmod.mc.multiblock.g3;

import com.euvcraft.lithmod.mc.machine.screen.AdvancedMachineScreen;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import static com.euvcraft.lithmod.mc.multiblock.g3.PlanarizerCabinetMenu.*;

public final class PlanarizerCabinetScreen extends AdvancedMachineScreen<PlanarizerCabinetMenu> {
    private final Map<Integer, Button> controls = new HashMap<>();

    public PlanarizerCabinetScreen(PlanarizerCabinetMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override protected boolean formed() { return menu.formed(); }

    private void control(int id, String label, int x, int y, int width) {
        controls.put(id, addRenderableWidget(Button.builder(Component.literal(label), button -> {
            if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }).bounds(leftPos + x, topPos + y, width, 14).build()));
    }

    @Override
    protected void init() {
        super.init();
        controls.clear();
        control(PRESSURE_DOWN, "-", 30, 17, 17);
        control(PRESSURE_UP, "+", 79, 17, 17);
        control(SPEED_DOWN, "-", 101, 17, 17);
        control(SPEED_UP, "+", 151, 17, 17);
        control(5, "Repair", 125, 41, 43);
        control(TEST_POLISH, "Test polish", 30, 57, 65);
        control(ACCEPT_POLISH, "Accept", 102, 57, 66);
        control(6, "Vacuum", 115, 213, 53);
        updateControls();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateControls();
    }

    private void updateControls() {
        for (int id = PRESSURE_DOWN; id <= ACCEPT_POLISH; id++) controls.get(id).active = menu.canTune();
        controls.get(TEST_POLISH).active &= menu.getSlot(0).hasItem();
        controls.get(ACCEPT_POLISH).active &= menu.minimumQuality() > 0 && menu.testQuality() >= menu.minimumQuality();
        controls.get(5).active = formed() && menu.value(7) != 0;
        controls.get(6).active = formed();
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        line(graphics, title, 8, 6, 160);
        line(graphics, Component.literal("Press"), 49, 20, 29);
        line(graphics, Component.literal("Speed"), 119, 20, 32);
        line(graphics, Component.literal("Sample"), 31, 32, 39);
        line(graphics, Component.literal("Slurry"), 66, 32, 32);
        line(graphics, Component.literal("Pad"), 95, 32, 25);
        line(graphics, playerInventoryTitle, 8, 73, 160);
        line(graphics, Component.literal(menu.energy() + " / " + menu.energyCapacity() + " FE"), 8, 166, 160);
        String head = menu.value(25) == 2 ? "Memory head" : "Standard head";
        line(graphics, Component.literal((formed() ? "Formed | " : "Unformed | ") + head), 8, 177, 160);
        line(graphics, Component.literal("Pressure " + menu.pressure() + " | Speed " + menu.speed()), 8, 188, 160);
        String result = menu.testQuality() < 0 || menu.minimumQuality() == 0 ? "Test a deposited wafer"
                : "Removal " + menu.removalError() + " | Edge " + menu.edgeError();
        line(graphics, Component.literal(result), 8, 199, 160);
        String quality = menu.testQuality() < 0 ? "Quality: —" : "Quality: " + menu.testQuality() / 10 + "%";
        line(graphics, Component.literal(quality), 8, 211, 102);
        String status = menu.acceptedQuality() >= Math.max(1, menu.minimumQuality()) ? "Accepted " + menu.acceptedQuality() / 10 + "%"
                : menu.testQuality() >= 0 ? "Target both errors to 0" : "Calibration required";
        line(graphics, Component.literal(status + (menu.value(11) == 1 ? " | Isolated" : "")), 8, 226, 160);
    }
}
